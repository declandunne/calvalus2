package com.bc.calvalus.portal.server.hadoop;

import org.apache.hadoop.mapred.JobStatus;
import org.apache.hadoop.mapreduce.JobID;

/**
 * A Hadoop workflow.
 *
 * @author Norman
 */
class HadoopProduction {
   public static enum Action {
       NONE,
       CANCEL,
       DELETE,
       RESTART,
   }

    private final String id;
    private final String name;
    private final String outputPath;
    private final JobID jobId;
    private JobStatus jobStatus;
    private Action action;

    public HadoopProduction(String id, String name, String outputPath, JobID jobId) {
        this.id = id;
        this.name = name;
        this.outputPath = outputPath;
        this.jobId = jobId;
        this.action = Action.NONE;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getOutputPath() {
        return outputPath;
    }

    public JobID getJobId() {
        return jobId;
    }

    public JobStatus getJobStatus() {
        return jobStatus;
    }

    public void setJobStatus(JobStatus jobStatus) {
        this.jobStatus = jobStatus;
    }

    public Action getAction() {
        return action;
    }

    public void setAction(Action action) {
        this.action = action;
    }
}
