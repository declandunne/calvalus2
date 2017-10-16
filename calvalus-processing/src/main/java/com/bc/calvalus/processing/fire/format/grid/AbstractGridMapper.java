package com.bc.calvalus.processing.fire.format.grid;

import com.bc.calvalus.commons.CalvalusLogger;
import com.bc.calvalus.processing.fire.format.LcRemapping;
import com.bc.ceres.core.ProgressMonitor;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.lib.input.FileSplit;

import java.io.IOException;
import java.time.Year;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.IntStream;

public abstract class AbstractGridMapper extends Mapper<Text, FileSplit, Text, GridCell> {

    protected static final Logger LOG = CalvalusLogger.getLogger();
    private final int targetRasterWidth;
    private final int targetRasterHeight;
    private FireGridDataSource dataSource;

    protected AbstractGridMapper(int targetRasterWidth, int targetRasterHeight) {
        this.targetRasterWidth = targetRasterWidth;
        this.targetRasterHeight = targetRasterHeight;
    }

    public final GridCell computeGridCell(int year, int month, ProgressMonitor pm) throws IOException {
        LOG.info("Computing grid cell...");
        pm.beginTask("Computing grid cell...", targetRasterWidth * targetRasterHeight);
        if (dataSource == null) {
            throw new NullPointerException("dataSource == null");
        }
        int doyFirstOfMonth = Year.of(year).atMonth(month).atDay(1).getDayOfYear();
        int doyLastOfMonth = Year.of(year).atMonth(month).atDay(Year.of(year).atMonth(month).lengthOfMonth()).getDayOfYear();

        int doyFirstHalf = Year.of(year).atMonth(month).atDay(7).getDayOfYear();
        int doySecondHalf = Year.of(year).atMonth(month).atDay(22).getDayOfYear();

        dataSource.setDoyFirstOfMonth(doyFirstOfMonth);
        dataSource.setDoyLastOfMonth(doyLastOfMonth);
        dataSource.setDoyFirstHalf(doyFirstHalf);
        dataSource.setDoySecondHalf(doySecondHalf);

        double[] areas = new double[targetRasterWidth * targetRasterHeight];
        float[] baFirstHalf = new float[targetRasterWidth * targetRasterHeight];
        float[] baSecondHalf = new float[targetRasterWidth * targetRasterHeight];
        float[] coverageFirstHalf = new float[targetRasterWidth * targetRasterHeight];
        float[] coverageSecondHalf = new float[targetRasterWidth * targetRasterHeight];
        float[] patchNumberFirstHalf = new float[targetRasterWidth * targetRasterHeight];
        float[] patchNumberSecondHalf = new float[targetRasterWidth * targetRasterHeight];
        float[] errorsFirstHalf = new float[targetRasterWidth * targetRasterHeight];
        float[] errorsSecondHalf = new float[targetRasterWidth * targetRasterHeight];
        float[] burnableFraction = new float[targetRasterWidth * targetRasterHeight];

        List<float[]> baInLcFirstHalf = new ArrayList<>();
        List<float[]> baInLcSecondHalf = new ArrayList<>();
        for (int c = 0; c < GridFormatUtils.LC_CLASSES_COUNT; c++) {
            float[] firstHalfBaInLcBuffer = new float[targetRasterWidth * targetRasterHeight];
            float[] secondHalfBaInLcBuffer = new float[targetRasterWidth * targetRasterHeight];
            Arrays.fill(firstHalfBaInLcBuffer, 0);
            Arrays.fill(secondHalfBaInLcBuffer, 0);
            baInLcFirstHalf.add(firstHalfBaInLcBuffer);
            baInLcSecondHalf.add(secondHalfBaInLcBuffer);
        }

        int targetPixelIndex = 0;
        for (int y = 0; y < targetRasterHeight; y++) {
            for (int x = 0; x < targetRasterWidth; x++) {
                System.gc();
                SourceData data = dataSource.readPixels(x, y);
                if (data == null) {
                    targetPixelIndex++;
                    continue;
                }
                float baValueFirstHalf = 0.0F;
                float baValueSecondHalf = 0.0F;
                float coverageValueFirstHalf = 0.0F;
                float coverageValueSecondHalf = 0.0F;
                double burnableFractionValue = 0.0;

                int numberOfBurnedPixels = 0;

                for (int i = 0; i < data.burnedPixels.length; i++) {
                    int doy = data.burnedPixels[i];
                    if (isValidFirstHalfPixel(doyFirstOfMonth, doySecondHalf, doy)) {
                        numberOfBurnedPixels++;
                        baValueFirstHalf += data.areas[i];
                        boolean hasLcClass = false;
                        for (int lcClass = 0; lcClass < GridFormatUtils.LC_CLASSES_COUNT; lcClass++) {
                            boolean inLcClass = LcRemapping.isInLcClass(lcClass + 1, data.lcClasses[i]);
                            baInLcFirstHalf.get(lcClass)[targetPixelIndex] += inLcClass ? data.areas[i] : 0.0;
                            if (inLcClass) {
                                hasLcClass = true;
                            }
                        }
                        if (!hasLcClass && data.lcClasses[i] != 0) {
//                            LOG.info("Pixel " + i + " in tile (" + x + "/" + y + ") with LC class " + data.lcClasses[i] + " is not remappable.");
                            if (maskUnmappablePixels()) {
                                baValueFirstHalf -= data.areas[i];
                            }
                        }
                    } else if (isValidSecondHalfPixel(doyLastOfMonth, doyFirstHalf, doy)) {
                        numberOfBurnedPixels++;
                        baValueSecondHalf += data.areas[i];
                        boolean hasLcClass = false;
                        for (int lcClass = 0; lcClass < GridFormatUtils.LC_CLASSES_COUNT; lcClass++) {
                            boolean inLcClass = LcRemapping.isInLcClass(lcClass + 1, data.lcClasses[i]);
                            baInLcSecondHalf.get(lcClass)[targetPixelIndex] += inLcClass ? data.areas[i] : 0.0;
                            if (inLcClass) {
                                hasLcClass = true;
                            }
                        }
                        if (!hasLcClass && data.lcClasses[i] != 0) {
//                            LOG.info("Pixel " + i + " in tile (" + x + "/" + y + ") with LC class " + data.lcClasses[i] + " is not remappable.");
                            if (maskUnmappablePixels()) {
                                baValueSecondHalf -= data.areas[i];
                            }
                        }
                    }

                    burnableFractionValue += data.burnable[i] ? data.areas[i] : 0.0;
                    coverageValueFirstHalf += data.statusPixelsFirstHalf[i] == 1 && data.burnable[i] ? data.areas[i] : 0.0F;
                    coverageValueSecondHalf += data.statusPixelsSecondHalf[i] == 1 && data.burnable[i] ? data.areas[i] : 0.0F;
                    areas[targetPixelIndex] += data.areas[i];
                    validate(areas[targetPixelIndex], targetPixelIndex);
                }

                baFirstHalf[targetPixelIndex] = baValueFirstHalf;
                baSecondHalf[targetPixelIndex] = baValueSecondHalf;
                patchNumberFirstHalf[targetPixelIndex] = data.patchCountFirstHalf;
                patchNumberSecondHalf[targetPixelIndex] = data.patchCountSecondHalf;
                coverageFirstHalf[targetPixelIndex] = getFraction(coverageValueFirstHalf, burnableFractionValue);
                coverageSecondHalf[targetPixelIndex] = getFraction(coverageValueSecondHalf, burnableFractionValue);
                burnableFraction[targetPixelIndex] = getFraction(burnableFractionValue, areas[targetPixelIndex]);
                validate(burnableFraction[targetPixelIndex], baInLcFirstHalf, baInLcSecondHalf, targetPixelIndex, areas[targetPixelIndex]);

                errorsFirstHalf[targetPixelIndex] = getErrorPerPixel(data.probabilityOfBurnFirstHalf, numberOfBurnedPixels);
                errorsSecondHalf[targetPixelIndex] = getErrorPerPixel(data.probabilityOfBurnSecondHalf, numberOfBurnedPixels);

                targetPixelIndex++;
                pm.worked(1);
            }
        }

        predict(baFirstHalf, areas, errorsFirstHalf);
        predict(baSecondHalf, areas, errorsSecondHalf);

        validate(errorsFirstHalf, baFirstHalf);
        validate(errorsSecondHalf, baSecondHalf);

        validate(baFirstHalf, baInLcFirstHalf);
        validate(baSecondHalf, baInLcSecondHalf);

        validate(baFirstHalf, areas);
        validate(baSecondHalf, areas);

        GridCell gridCell = new GridCell();
        gridCell.bandSize = targetRasterWidth * targetRasterHeight;
        gridCell.setBaFirstHalf(baFirstHalf);
        gridCell.setBaSecondHalf(baSecondHalf);
        gridCell.setPatchNumberFirstHalf(patchNumberFirstHalf);
        gridCell.setPatchNumberSecondHalf(patchNumberSecondHalf);
        gridCell.setErrorsFirstHalf(errorsFirstHalf);
        gridCell.setErrorsSecondHalf(errorsSecondHalf);
        gridCell.setBaInLcFirstHalf(baInLcFirstHalf);
        gridCell.setBaInLcSecondHalf(baInLcSecondHalf);
        gridCell.setCoverageFirstHalf(coverageFirstHalf);
        gridCell.setCoverageSecondHalf(coverageSecondHalf);
        gridCell.setBurnableFraction(burnableFraction);
        LOG.info("...done.");
        pm.done();
        return gridCell;
    }

    public final GridCell computeGridCell(int year, int month) throws IOException {
        return computeGridCell(year, month, ProgressMonitor.NULL);
    }

    private void validate(float[] ba, double[] areas) {
        for (int i = 0; i < ba.length; i++) {
            if (ba[i] > areas[i]) {
                throw new IllegalStateException("BA (" + ba[i] + ") > area (" + areas[i] + ") at pixel index " + i);
            }
        }
    }

    protected abstract float getErrorPerPixel(double[] probabilityOfBurn, int numberOfBurnedPixels);

    protected abstract void predict(float[] ba, double[] areas, float[] originalErrors);

    protected abstract void validate(float burnableFraction, List<float[]> baInLcFirst, List<float[]> baInLcSecond, int targetPixelIndex, double area);

    protected abstract boolean maskUnmappablePixels();

    private static void validate(float[] ba, List<float[]> baInLc) {
        int baCount = (int) IntStream.range(0, ba.length).mapToDouble(i -> ba[i]).filter(i -> i != 0).count();
        if (baCount < ba.length * 0.05) {
            // don't throw an error for too few observations
            return;
        }

        float baSum = (float) IntStream.range(0, ba.length).mapToDouble(i -> ba[i]).sum();
        float baInLcSum = 0;
        for (float[] floats : baInLc) {
            baInLcSum += IntStream.range(0, floats.length).mapToDouble(i -> floats[i]).sum();
        }
        if (Math.abs(baSum - baInLcSum) > baSum * 0.05) {
            CalvalusLogger.getLogger().warning("Math.abs(baSum - baInLcSum) > baSum * 0.05:");
            CalvalusLogger.getLogger().warning("baSum = " + baSum);
            CalvalusLogger.getLogger().warning("baInLcSum " + baInLcSum);
//            throw new IllegalStateException("Math.abs(baSum - baInLcSum) > baSum * 0.05");
        }
    }

    private static void validate(float[] errors, float[] ba) {
        for (int i = 0; i < errors.length; i++) {
            float error = errors[i];
            // todo - check!
            if (error > 0 && !(ba[i] > 0)) {
                LOG.warning("error > 0 && !(ba[i] > 0)");
//                throw new IllegalStateException("error > 0 && !(ba[i] > 0)");
            }
            if (Float.isNaN(error)) {
                LOG.warning("error is NaN");
//                throw new IllegalStateException("error is NaN");
            }
        }
    }

    private static void validate(double area, int index) {
        if (area < 0) {
            throw new IllegalStateException("area < 0 at target pixel " + index);
        }
    }

    protected static float getFraction(double value, double area) {
        float fraction = (float) (value / area) >= 1.0F ? 1.0F : (float) (value / area);
        if (Float.isNaN(fraction)) {
            fraction = 0.0F;
        }
        return fraction;
    }

    static boolean isValidFirstHalfPixel(int doyFirstOfMonth, int doySecondHalf, int pixel) {
        return pixel >= doyFirstOfMonth && pixel < doySecondHalf - 6 && pixel != 999 && pixel != GridFormatUtils.NO_DATA;
    }

    static boolean isValidSecondHalfPixel(int doyLastOfMonth, int doyFirstHalf, int pixel) {
        return pixel > doyFirstHalf + 8 && pixel <= doyLastOfMonth && pixel != 999 && pixel != GridFormatUtils.NO_DATA;
    }

    public void setDataSource(FireGridDataSource dataSource) {
        this.dataSource = dataSource;
    }

}
