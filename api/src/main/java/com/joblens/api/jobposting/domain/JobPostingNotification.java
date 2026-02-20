package com.joblens.api.jobposting.domain;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * 공고별 알림 발송 상태 (즉시 메일 1회, digest 1회 제한용).
 * posting_id = 공고 URL(unique).
 */
@Entity
@Table(name = "job_posting_notification", uniqueConstraints = @UniqueConstraint(columnNames = "posting_id"))
public class JobPostingNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "posting_id", nullable = false, unique = true, length = 1000)
    private String postingId;

    @Column(name = "title", length = 500)
    private String title;

    @Column(name = "company", length = 200)
    private String company;

    @Column(name = "immediate_sent_at")
    private Instant immediateSentAt;

    @Column(name = "digest_sent_at")
    private Instant digestSentAt;

    @Column(name = "last_evaluated_at", nullable = false)
    private Instant lastEvaluatedAt;

    @Column(name = "total_score_snapshot")
    private Integer totalScoreSnapshot;

    /** 매칭된 스택 키워드 스냅샷 (쉼표 구분, 메일 본문 표시용) */
    @Column(name = "matched_stack_snapshot", length = 500)
    private String matchedStackSnapshot;

    protected JobPostingNotification() {
    }

    public JobPostingNotification(String postingId, String title, String company, Instant lastEvaluatedAt, Integer totalScoreSnapshot) {
        this.postingId = postingId;
        this.title = title;
        this.company = company;
        this.lastEvaluatedAt = lastEvaluatedAt;
        this.totalScoreSnapshot = totalScoreSnapshot;
    }

    public Long getId() {
        return id;
    }

    public String getPostingId() {
        return postingId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getCompany() {
        return company;
    }

    public void setCompany(String company) {
        this.company = company;
    }

    public Instant getImmediateSentAt() {
        return immediateSentAt;
    }

    public void setImmediateSentAt(Instant immediateSentAt) {
        this.immediateSentAt = immediateSentAt;
    }

    public Instant getDigestSentAt() {
        return digestSentAt;
    }

    public void setDigestSentAt(Instant digestSentAt) {
        this.digestSentAt = digestSentAt;
    }

    public Instant getLastEvaluatedAt() {
        return lastEvaluatedAt;
    }

    public void setLastEvaluatedAt(Instant lastEvaluatedAt) {
        this.lastEvaluatedAt = lastEvaluatedAt;
    }

    public Integer getTotalScoreSnapshot() {
        return totalScoreSnapshot;
    }

    public void setTotalScoreSnapshot(Integer totalScoreSnapshot) {
        this.totalScoreSnapshot = totalScoreSnapshot;
    }

    public String getMatchedStackSnapshot() {
        return matchedStackSnapshot;
    }

    public void setMatchedStackSnapshot(String matchedStackSnapshot) {
        this.matchedStackSnapshot = matchedStackSnapshot;
    }
}
