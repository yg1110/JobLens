package com.joblens.api.jobposting.web.dto.saramin;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.joblens.api.jobposting.web.dto.jobkorea.JobPostingRequest;

import java.util.List;

/**
 * Python 크롤러 POST /crawl 응답 모델.
 */
public class SaraminCrawlResponse {

    private int count;

    @JsonProperty("saved_to_file")
    private boolean savedToFile;

    @JsonProperty("file_path")
    private String filePath;

    private List<JobPostingRequest> jobs;

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public boolean isSavedToFile() {
        return savedToFile;
    }

    public void setSavedToFile(boolean savedToFile) {
        this.savedToFile = savedToFile;
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
