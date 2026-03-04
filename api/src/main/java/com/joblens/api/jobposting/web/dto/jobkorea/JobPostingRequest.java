package com.joblens.api.jobposting.web.dto.jobkorea;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public class JobPostingRequest {

    private String title;
    private String company;
    private String url;
    private String location;

    @JsonProperty("job_condition")
    private String jobCondition;

    private String sector;
    private String deadline;

    @JsonProperty("scraped_at")
    private Double scrapedAt;

    @JsonProperty("source_page")
    private Integer sourcePage;

    @JsonProperty("detail_iframe_url")
    private String detailIframeUrl;

    @JsonProperty("detail_sections")
    private Map<String, String> detailSections;

    @JsonProperty("detail_html")
    private String detailHtml;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getCompany() { return company; }
    public void setCompany(String company) { this.company = company; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getJobCondition() { return jobCondition; }
    public void setJobCondition(String jobCondition) { this.jobCondition = jobCondition; }

    public String getSector() { return sector; }
    public void setSector(String sector) { this.sector = sector; }

    public String getDeadline() { return deadline; }
    public void setDeadline(String deadline) { this.deadline = deadline; }

    public Double getScrapedAt() { return scrapedAt; }
    public void setScrapedAt(Double scrapedAt) { this.scrapedAt = scrapedAt; }

    public Integer getSourcePage() { return sourcePage; }
    public void setSourcePage(Integer sourcePage) { this.sourcePage = sourcePage; }

    public String getDetailIframeUrl() { return detailIframeUrl; }
    public void setDetailIframeUrl(String detailIframeUrl) { this.detailIframeUrl = detailIframeUrl; }

    public Map<String, String> getDetailSections() { return detailSections; }
    public void setDetailSections(Map<String, String> detailSections) { this.detailSections = detailSections; }

    public String getDetailHtml() { return detailHtml; }
    public void setDetailHtml(String detailHtml) { this.detailHtml = detailHtml; }
}
