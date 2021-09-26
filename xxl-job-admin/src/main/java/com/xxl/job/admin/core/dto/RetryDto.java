package com.xxl.job.admin.core.dto;

/**
 * @author jason
 * @date 2021/9/26
 */
public class RetryDto {

    private int jobId;

    private long jobLogId;

    private int executorFailRetryCount;

    private String executorParam;
    private String executorShardingParam;

    public RetryDto(int jobId, long jobLogId, int executorFailRetryCount, String executorParam, String executorShardingParam) {
        this.jobId = jobId;
        this.jobLogId = jobLogId;
        this.executorFailRetryCount = executorFailRetryCount;
        this.executorParam = executorParam;
        this.executorShardingParam = executorShardingParam;
    }

    public int getJobId() {
        return jobId;
    }

    public void setJobId(int jobId) {
        this.jobId = jobId;
    }

    public long getJobLogId() {
        return jobLogId;
    }

    public void setJobLogId(long jobLogId) {
        this.jobLogId = jobLogId;
    }

    public int getExecutorFailRetryCount() {
        return executorFailRetryCount;
    }

    public void setExecutorFailRetryCount(int executorFailRetryCount) {
        this.executorFailRetryCount = executorFailRetryCount;
    }

    public String getExecutorParam() {
        return executorParam;
    }

    public void setExecutorParam(String executorParam) {
        this.executorParam = executorParam;
    }

    public String getExecutorShardingParam() {
        return executorShardingParam;
    }

    public void setExecutorShardingParam(String executorShardingParam) {
        this.executorShardingParam = executorShardingParam;
    }
}
