package com.bc.calvalus.processing.fire.format.pixel.meris;

import com.bc.calvalus.commons.CalvalusLogger;
import com.bc.calvalus.commons.Workflow;
import com.bc.calvalus.processing.JobConfigNames;
import com.bc.calvalus.processing.fire.format.PixelProductArea;
import com.bc.calvalus.processing.fire.format.SensorStrategy;
import com.bc.calvalus.processing.fire.format.WorkflowConfig;
import com.bc.calvalus.processing.hadoop.HadoopProcessingService;
import com.bc.calvalus.processing.hadoop.HadoopWorkflowItem;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

import java.io.IOException;

public class MerisStrategy implements SensorStrategy {

    private final MerisPixelProductAreaProvider areaProvider;

    public MerisStrategy() {
        areaProvider = new MerisPixelProductAreaProvider();
    }

    @Override
    public PixelProductArea getArea(String identifier) {
        return areaProvider.getArea(identifier);
    }

    @Override
    public PixelProductArea[] getAllAreas() {
        return areaProvider.getAllAreas();
    }

    @Override
    public Workflow getPixelFormattingWorkflow(WorkflowConfig workflowConfig) {
        Workflow merisFormattingWorkflow = new Workflow.Parallel();
        Workflow areaWorkflow = new Workflow.Sequential();
        Workflow variableWorkflow = new Workflow.Parallel();

        String area = workflowConfig.area;
        HadoopProcessingService processingService = workflowConfig.processingService;
        String userName = workflowConfig.userName;
        String productionName = workflowConfig.productionName;
        Configuration jobConfig = workflowConfig.jobConfig;
        String year = workflowConfig.year;
        String month = workflowConfig.month;

        for (PixelVariableType type : PixelVariableType.values()) {
            CalvalusLogger.getLogger().info(String.format("Creating workflow item for area %s and variable %s.", area, type.name()));
            FirePixelFormatVariableWorkflowItem item = new FirePixelFormatVariableWorkflowItem(processingService, userName, productionName + "_" + area + "_" + type.name(), area, type, jobConfig);
            variableWorkflow.add(item);
        }
        areaWorkflow.add(variableWorkflow);
        FirePixelMergingWorkflowItem mergingWorkflowItem = new FirePixelMergingWorkflowItem(processingService, userName, productionName + "_" + area + "_merging", area, jobConfig);
        mergingWorkflowItem.setInputDir(jobConfig.get(JobConfigNames.CALVALUS_OUTPUT_DIR) + "/" + year + "/" + month + "/" + area + "-to-merge");
        areaWorkflow.add(mergingWorkflowItem);
        merisFormattingWorkflow.add(areaWorkflow);
        return merisFormattingWorkflow;
    }

    private static class MerisPixelProductAreaProvider implements PixelProductAreaProvider {

        enum MerisPixelProductArea {
            NORTH_AMERICA(0, 7, 130, 71, "1", "North America"),
            SOUTH_AMERICA(75, 71, 146, 147, "2", "South America"),
            EUROPE(154, 7, 233, 65, "3", "Europe"),
            ASIA(233, 7, 360, 90, "4", "Asia"),
            AFRICA(154, 65, 233, 130, "5", "Africa"),
            AUSTRALIA(275, 90, 360, 143, "6", "Australia");

            final int left;
            final int top;
            final int right;
            final int bottom;
            final String index;
            final String nicename;

            MerisPixelProductArea(int left, int top, int right, int bottom, String index, String nicename) {
                this.left = left;
                this.top = top;
                this.right = right;
                this.bottom = bottom;
                this.index = index;
                this.nicename = nicename;
            }
        }

        @Override
        public PixelProductArea getArea(String identifier) {
            return translate(MerisPixelProductArea.valueOf(identifier));
        }

        private static PixelProductArea translate(MerisPixelProductArea mppa) {
            return new PixelProductArea(mppa.left, mppa.top, mppa.right, mppa.bottom, mppa.index, mppa.nicename);
        }

        @Override
        public PixelProductArea[] getAllAreas() {
            PixelProductArea[] result = new PixelProductArea[MerisPixelProductArea.values().length];
            MerisPixelProductArea[] values = MerisPixelProductArea.values();
            for (int i = 0; i < values.length; i++) {
                MerisPixelProductArea merisPixelProductArea = values[i];
                result[i] = translate(merisPixelProductArea);
            }
            return result;
        }
    }

    private static class FirePixelFormatVariableWorkflowItem extends HadoopWorkflowItem {

        private final String area;
        private final PixelVariableType variableType;

        FirePixelFormatVariableWorkflowItem(HadoopProcessingService processingService, String userName, String jobName, String area, PixelVariableType variableType, Configuration jobConfig) {
            super(processingService, userName, jobName, jobConfig);
            this.variableType = variableType;
            this.area = area;
        }

        @Override
        public String getOutputDir() {
            String year = getJobConfig().get("calvalus.year");
            String month = getJobConfig().get("calvalus.month");
            return getJobConfig().get(JobConfigNames.CALVALUS_OUTPUT_DIR) + "/" + year + "/" + month + "/" + area + "/" + variableType.name();
        }

        @Override
        protected void configureJob(Job job) throws IOException {
            CalvalusLogger.getLogger().info("Configuring job.");
            job.setInputFormatClass(MerisPixelInputFormat.class);
            job.setMapperClass(MerisPixelMapper.class);
            job.setReducerClass(MerisPixelReducer.class);
            job.setMapOutputKeyClass(Text.class);
            job.setMapOutputValueClass(MerisPixelCell.class);
            job.getConfiguration().set("area", area);
            job.getConfiguration().set("variableType", variableType.name());
            FileOutputFormat.setOutputPath(job, new Path(getOutputDir()));
        }

        @Override
        protected String[][] getJobConfigDefaults() {
            return new String[][]{
            };
        }
    }

    private class FirePixelMergingWorkflowItem extends HadoopWorkflowItem {

        private final String area;

        FirePixelMergingWorkflowItem(HadoopProcessingService processingService, String userName, String jobName, String area, Configuration jobConfig) {
            super(processingService, userName, jobName, jobConfig);
            this.area = area;
        }

        @Override
        public String getOutputDir() {
            String year = getJobConfig().get("calvalus.year");
            String month = getJobConfig().get("calvalus.month");
            return getJobConfig().get(JobConfigNames.CALVALUS_OUTPUT_DIR) + "/" + year + "/" + month + "/" + area + "/" + "final";
        }

        @Override
        protected void configureJob(Job job) throws IOException {
            CalvalusLogger.getLogger().info("Configuring job.");
            job.setInputFormatClass(MerisPixelMergeInputFormat.class);
            job.setMapperClass(MerisPixelMergeMapper.class);
            job.setNumReduceTasks(0);
            job.getConfiguration().set("area", area);
            FileOutputFormat.setOutputPath(job, new Path(getOutputDir()));
        }

        @Override
        protected String[][] getJobConfigDefaults() {
            return new String[0][];
        }

        void setInputDir(String areaOutputDir) {
            getJobConfig().set("inputBaseDir", areaOutputDir);
        }
    }

}
