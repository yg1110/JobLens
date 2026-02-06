package com.joblens.api.jobposting.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Python 크롤러 GET /jobs 응답 모델.
 */
public class JobsFileResponse {

    private int count;

    @JsonProperty("file_path")
    private String filePath;

    private List<JobPostingRequest> jobs;

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public List<JobPostingRequest> getJobs() {
        return jobs;
    }

    public void setJobs(List<JobPostingRequest> jobs) {
        this.jobs = jobs;
    }
}
