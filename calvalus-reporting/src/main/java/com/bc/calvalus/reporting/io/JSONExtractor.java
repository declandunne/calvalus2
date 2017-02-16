package com.bc.calvalus.reporting.io;

import com.bc.calvalus.reporting.ws.NullUsageStatistic;
import com.bc.calvalus.reporting.ws.UsageStatistic;
import com.bc.wps.utilities.PropertiesWrapper;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;

/**
 * @author hans , muhammad
 */
public class JSONExtractor {

    private static final String INIT_FIRST_DAY = "01";
    private static final String INIT_FIRST_MONTH = "01";

    public List<UsageStatistic> getAllStatistics() throws IOException {
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream(PropertiesWrapper.get("reporting.file"));
        String reportingJsonString = extractJsonString(inputStream);
        Gson gson = new Gson();
        return gson.fromJson(reportingJsonString,
                             new TypeToken<List<UsageStatistic>>() {
                             }.getType());
    }

    public UsageStatistic getSingleStatistic(String jobId) throws IOException {
        List<UsageStatistic> usageStatistics = getAllStatistics();
        for (UsageStatistic usageStatistic : usageStatistics) {
            if (jobId.equalsIgnoreCase(usageStatistic.getJobId())) {
                return usageStatistic;
            }
        }
        return new NullUsageStatistic();
    }

    public Map<String, List<UsageStatistic>> getAllUserUsageStatistic() throws IOException {
        List<UsageStatistic> allStatistics = getAllStatistics();
        ConcurrentHashMap<String, List<UsageStatistic>> groupUserUsageStatistic = new ConcurrentHashMap<>();
        allStatistics.forEach(p -> {
            String user = p.getUser();
            groupUserUsageStatistic.computeIfAbsent(user, userName -> {
                List<UsageStatistic> singleUserStatistic = null;
                try {
                    singleUserStatistic = getSingleUserStatistic(userName);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return singleUserStatistic;
            });
        });
        return groupUserUsageStatistic;
    }

    public List<UsageStatistic> getSingleUserStatistic(String userName) throws IOException {
        List<UsageStatistic> usageStatistics = getAllStatistics();
        List<UsageStatistic> singleUserStatistics = new ArrayList<>();
        for (UsageStatistic usageStatistic : usageStatistics) {
            if (userName.equalsIgnoreCase(usageStatistic.getUser())) {
                singleUserStatistics.add(usageStatistic);
            }
        }
        return singleUserStatistics;
    }


    public Map<String, List<UsageStatistic>> getAllUserUsageBetween(String start, String end) throws IOException {
        Predicate<Long> predicate = filterDateIntervals(start, end);
        List<UsageStatistic> allStatistics = getAllStatistics();
        List<UsageStatistic> usageStatisticList = allStatistics.stream().filter(p -> predicate.test(p.getFinishTime())).collect(Collectors.toList());

        ConcurrentHashMap<String, List<UsageStatistic>> groupUserUsageStatistic = new ConcurrentHashMap<>();
        usageStatisticList.forEach(p -> groupUserUsageStatistic.computeIfAbsent(p.getUser(), stringKey ->
                filterUser(stringKey, usageStatisticList)
        ));
        return groupUserUsageStatistic;
    }

    private List<UsageStatistic> filterUser(String stringKey, List<UsageStatistic> usageStatisticList) {
        return usageStatisticList.stream().filter(p -> p.getUser().equalsIgnoreCase(stringKey)).collect(Collectors.toList());
    }

    public Map<String, List<UsageStatistic>> getAllDateUsageBetween(String start, String end) throws IOException {
        List<UsageStatistic> allStatistics = getAllStatistics();
        Set<String> dates = getDatesBetween(start, end);
        Map<String, List<UsageStatistic>> usageWithDate = new HashMap<>();

        dates.forEach(date -> {
            Predicate<Long> predicate = filterDateIntervals(date, date);
            List<UsageStatistic> usageStatisticList = allStatistics
                    .stream()
                    .filter(p -> predicate.test(p.getFinishTime()))
                    .collect(Collectors.toList());
            usageWithDate.put(date, usageStatisticList);
        });

        return usageWithDate;
    }

    public Map<String, List<UsageStatistic>> getAllQueueUsageBetween(String start, String end) throws IOException {
        Predicate<Long> predicate = filterDateIntervals(start, end);
        List<UsageStatistic> allStatistics = getAllStatistics();
        List<UsageStatistic> usageStatisticList = allStatistics.stream().filter(p -> predicate.test(p.getFinishTime())).collect(Collectors.toList());
        ConcurrentHashMap<String, List<UsageStatistic>> groupUserUsageStatistic = new ConcurrentHashMap<>();

        usageStatisticList.forEach(p -> groupUserUsageStatistic.computeIfAbsent(p.getQueue(), queue ->
                filterQueue(queue, usageStatisticList)
        ));
        return groupUserUsageStatistic;
    }

    private List<UsageStatistic> filterQueue(String queue, List<UsageStatistic> usageStatisticList) {
        return usageStatisticList.stream().filter(p -> p.getQueue().equalsIgnoreCase(queue)).collect(Collectors.toList());
    }


    public List<UsageStatistic> getSingleUserUsageBetween(String user, String startDate, String endDate) throws IOException {
        Predicate<Long> rangePredicate = filterDateIntervals(startDate, endDate);
        List<UsageStatistic> allStatistics = getAllStatistics();
        return getSingleUserRangeStatistic(rangePredicate, user, allStatistics);
    }

    private List<UsageStatistic> getSingleUserRangeStatistic(Predicate<Long> predTime, String userName, List<UsageStatistic> allStatisticUsage) {
        ConcurrentHashMap<String, List<UsageStatistic>> filterUserWithDate = new ConcurrentHashMap<>();
        List<UsageStatistic> userStatisticInYear = new ArrayList<>();
        filterUserWithDate.put(userName, userStatisticInYear);
        allStatisticUsage.forEach(p -> filterUserWithDate.computeIfPresent(p.getUser(), (s, usageStatistics) -> {
            if (predTime.test(p.getFinishTime())) {
                userStatisticInYear.add(p);
            }
            return userStatisticInYear;
        }));
        return userStatisticInYear;
    }

    public List<UsageStatistic> getSingleUserUsageInYear(String user, String year) throws IOException {
        Predicate<FilterUserTimeInterval> yearPredicate = FilterUserTimeInterval::filterYear;
        return getSingleUserDate(yearPredicate, user, year, INIT_FIRST_MONTH, INIT_FIRST_DAY);
    }

    public List<UsageStatistic> getSingleUserUsageInYearMonth(String user, String year, String month) throws IOException {
        Predicate<FilterUserTimeInterval> yearMonthPredicate = FilterUserTimeInterval::filterMonth;
        return getSingleUserDate(yearMonthPredicate, user, year, month, INIT_FIRST_DAY);
    }

    public List<UsageStatistic> getSingleUserUsageYearMonthDay(String user, String year, String month, String day) throws IOException {
        Predicate<FilterUserTimeInterval> ymdPredicate = FilterUserTimeInterval::filterDay;
        return getSingleUserDate(ymdPredicate, user, year, month, day);
    }


    private List<UsageStatistic> getSingleUserDate(Predicate<FilterUserTimeInterval> intervalPredicate,
                                                   String user,
                                                   String yr,
                                                   String mnth,
                                                   String dy) throws IOException {

        final ConcurrentHashMap<String, List<UsageStatistic>> extractUserDate = new ConcurrentHashMap<>();
        final List<UsageStatistic> userStatisticInYear = new ArrayList<>();
        extractUserDate.put(user, userStatisticInYear);
        getAllStatistics().forEach(usage -> extractUserDate.computeIfPresent(usage.getUser(), getUserFromYear(intervalPredicate, yr, mnth, dy, userStatisticInYear, usage)));
        return userStatisticInYear;
    }

    @NotNull
    private BiFunction<String, List<UsageStatistic>, List<UsageStatistic>> getUserFromYear(Predicate<FilterUserTimeInterval> intervalPredicate,
                                                                                           String yr, String mnth, String dy,
                                                                                           List<UsageStatistic> userStatisticInYear,
                                                                                           UsageStatistic usage) {
        return (stringKey, usageStatistics) -> {
            FilterUserTimeInterval filterUserTimeInterval = new FilterUserTimeInterval(usage.getFinishTime(), yr, mnth, dy);

            if (intervalPredicate.test(filterUserTimeInterval)) {
                userStatisticInYear.add(usage);
            }
            return userStatisticInYear;
        };
    }


    @NotNull
    private Predicate<Long> filterDateIntervals(final String startDate, final String endDate) {
        return aLong -> {
            Instant end = LocalDate.parse(endDate).atTime(LocalTime.MAX).toInstant(ZoneOffset.UTC);
            Instant start = LocalDate.parse(startDate).atStartOfDay().toInstant(ZoneOffset.UTC);
            Instant instant = new Date(aLong).toInstant();
            return instant.isAfter(start) && instant.isBefore(end);
        };
    }


    @NotNull
    private String extractJsonString(InputStream inputStream) throws IOException {
        String reportingJsonString = IOUtils.toString(inputStream);
        reportingJsonString = StringUtils.stripEnd(reportingJsonString.trim(), ",");
        reportingJsonString = "[" + reportingJsonString + "]";
        return reportingJsonString;
    }

    Set<String> getDatesBetween(String start, String end) {
        LocalDateTime startOfDay = LocalDate.parse(start).atStartOfDay();
        LocalDateTime endOfDay = LocalDate.parse(end).atStartOfDay();
        long l = Duration.between(startOfDay, endOfDay).toDays();
        Set<String> dates = new TreeSet<>();
        for (int i = 0; i <= l; i++) {
            LocalDate localDate = startOfDay.plusDays(i).toLocalDate();
            dates.add(localDate.toString());
        }
        return dates;
    }

    static class FilterUserTimeInterval {

        private Long finishTime;
        private final String year;
        private final String month;
        private final String day;

        FilterUserTimeInterval(Long finishTime, String year, String month, String day) {
            this.finishTime = finishTime;
            this.year = year;
            this.month = month;
            this.day = day;
        }


        boolean filterMonth() {
            Instant start = Instant.parse(String.format("%s-%s-01T00:00:00.00Z", year, month));
            Instant end = Instant.parse(String.format("%s-0%s-01T00:00:00.00Z", year, Long.parseLong(month) + 1));
            Instant instant = new Date(finishTime).toInstant();
            return instant.isAfter(start) && instant.isBefore(end);

        }

        boolean filterDay() {
            Instant start = Instant.parse(String.format("%s-%s-%sT00:00:00.00Z", year, month, day));
            Instant end = Instant.parse(String.format("%s-%s-%sT23:59:00.00Z", year, month, day));
            Instant instant = new Date(finishTime).toInstant();
            return instant.isAfter(start) && instant.isBefore(end);
        }

        boolean filterYear() {
            Instant start = Instant.parse(String.format("%s-01-01T00:00:00.00Z", year));
            Instant end = Instant.parse(String.format("%d-01-01T00:00:00.00Z", Long.parseLong(year) + 1));
            Instant instant = new Date(finishTime).toInstant();
            return instant.isAfter(start) && instant.isBefore(end);
        }
    }

}
