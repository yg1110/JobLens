package com.joblens.api.jobposting.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Entity
@Table(
    name = "job_postings",
    uniqueConstraints = @UniqueConstraint(name = "uk_job_postings_url", columnNames = "url")
)
public class JobPosting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(nullable = false, length = 200)
    private String company;

    @Column(nullable = false, length = 1000)
    private String url;

    @Column(length = 200)
    private String location;

    @Column(name = "job_condition", length = 300)
    private String jobCondition;

    @Column(length = 500)
    private String sector;

    @Column(length = 50)
    private String deadline;

    @Column(name = "scraped_at")
    private Double scrapedAt;

    @Column(name = "source_page")
    private Integer sourcePage;

    @Column(name = "detail_iframe_url", length = 1000)
    private String detailIframeUrl;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "detail_sections", columnDefinition = "jsonb")
    private Map<String, String> detailSections = new HashMap<>();

    @Column(name = "detail_html", columnDefinition = "text")
    private String detailHtml;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }

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

    public Instant getCreatedAt() { return createdAt; }
}
