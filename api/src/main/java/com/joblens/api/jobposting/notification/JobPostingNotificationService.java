package com.joblens.api.jobposting.notification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.joblens.api.email.service.EmailService;
import com.joblens.api.jobposting.client.CrawlerClient;
import com.joblens.api.jobposting.domain.JobPostingNotification;
import com.joblens.api.jobposting.domain.JobPostingNotificationRepository;
import com.joblens.api.jobposting.service.ScoringService;
import com.joblens.api.jobposting.web.dto.JobPostingRequest;
import com.joblens.api.jobposting.web.dto.ScoreResponse;

import jakarta.mail.MessagingException;

/**
 * 채용 공고 알림 비즈니스 로직.
 * <ul>
 *   <li>매시간: 크롤러 fetch → 스코어 → 추천만 저장/갱신 → 80점 초과 시 즉시 메일 1회</li>
 *   <li>매일 09:00: digest 대상(즉시 미발송, 70점 이상) 1통 발송 후 digest_sent_at 갱신</li>
 * </ul>
 * 실제 스케줄 호출은 {@link com.joblens.api.jobposting.scheduler.JobPostingEmailScheduler}에서 수행.
 */
@Service
public class JobPostingNotificationService {

    private static final Logger log = LoggerFactory.getLogger(JobPostingNotificationService.class);
    /** 스코어 결과 '추천' 판정 시에만 알림 대상으로 처리 */
    private static final String DECISION_RECOMMEND = "추천";

    private final CrawlerClient crawlerClient;
    private final ScoringService scoringService;
    private final JobPostingNotificationRepository notificationRepository;
    private final EmailService emailService;
    private final NotificationProperties properties;

    public JobPostingNotificationService(CrawlerClient crawlerClient,
                                         ScoringService scoringService,
                                         JobPostingNotificationRepository notificationRepository,
                                         EmailService emailService,
                                         NotificationProperties properties) {
        this.crawlerClient = crawlerClient;
        this.scoringService = scoringService;
        this.notificationRepository = notificationRepository;
        this.emailService = emailService;
        this.properties = properties;
    }

    /**
     * 매시간: fetch → 스코어 → 추천만 저장/갱신 → 80점 초과 건을 모아 즉시 메일 1통 발송
     */
    @Transactional
    public void runHourlyFetchAndImmediateSend() {
        log.info("[Notification] 매시간 fetch·즉시발송 작업 시작");
        if (!properties.isEnabled() || properties.getRecipients().isEmpty()) {
            log.info("[Notification] 스킵 - 알림 비활성화 또는 수신자 없음 (enabled={}, recipients={})",
                    properties.isEnabled(), properties.getRecipients().size());
            return;
        }
        List<JobPostingRequest> jobs;
        try {
            jobs = crawlerClient.fetchJobs();
        } catch (Exception e) {
            log.warn("[Notification] 크롤러 fetch 실패, 스킵: {}", e.getMessage());
            return;
        }
        log.info("[Notification] 크롤러 fetch 완료 - 공고 수={}", jobs.size());

        int thresholdImmediate = properties.getThreshold().getImmediate();
        List<ImmediateCandidate> immediateCandidates = new ArrayList<>();
        int recommendCount = 0;
        for (JobPostingRequest job : jobs) {
            ScoreResponse response = scoringService.score(job);
            if (!DECISION_RECOMMEND.equals(response.getDecision())) {
                continue;
            }
            recommendCount++;
            int totalScore = response.getTotalScore();

            String postingId = job.getUrl();
            if (postingId == null || postingId.isBlank()) {
                continue;
            }

            // 없으면 새로 생성, 있으면 조회 후 아래에서 갱신
            JobPostingNotification notification = notificationRepository.findByPostingId(postingId)
                    .orElseGet(() -> {
                        JobPostingNotification n = new JobPostingNotification(
                                postingId,
                                job.getTitle(),
                                job.getCompany(),
                                Instant.now(),
                                totalScore);
                        return notificationRepository.save(n);
                    });

            notification.setLastEvaluatedAt(Instant.now());
            notification.setTotalScoreSnapshot(totalScore);
            notification.setTitle(job.getTitle());
            notification.setCompany(job.getCompany());
            notificationRepository.save(notification);

            // 즉시 발송 대상 수집: 임계값 초과이고 아직 즉시 발송 안 한 건만 (중복 방지용 row lock)
            if (totalScore > thresholdImmediate) {
                Optional<JobPostingNotification> locked = notificationRepository.findByPostingIdForUpdate(postingId);
                if (locked.isPresent() && locked.get().getImmediateSentAt() == null) {
                    immediateCandidates.add(new ImmediateCandidate(job, response, locked.get()));
                }
            }
        }
        log.info("[Notification] 스코어링 완료 - 전체 {}건, 추천 {}건, 즉시발송대상(임계값>{}점) {}건",
                jobs.size(), recommendCount, thresholdImmediate, immediateCandidates.size());

        // 수집한 즉시 추천 건을 한 통으로 발송 후 발송 시각 갱신
        if (!immediateCandidates.isEmpty()) {
            try {
                sendBatchedImmediateEmail(immediateCandidates);
                Instant now = Instant.now();
                for (ImmediateCandidate c : immediateCandidates) {
                    c.notification().setImmediateSentAt(now);
                    notificationRepository.save(c.notification());
                }
                log.info("[Notification] 즉시 추천 이메일 발송 완료 - {}건, 수신자 {}명", immediateCandidates.size(), properties.getRecipients().size());
            } catch (Exception e) {
                log.error("[Notification] 즉시 메일 발송 실패: {}", e.getMessage(), e);
            }
        } else {
            log.info("[Notification] 즉시 발송 대상 없음, 이메일 미발송");
        }
    }

    /** 즉시 추천 메일 1건 분량 (job + 스코어 + 발송 대상 notification) */
    private record ImmediateCandidate(JobPostingRequest job, ScoreResponse response, JobPostingNotification notification) {}

    /**
     * 매일 09:00: 크롤링/스코어 반영 후 digest 대상(즉시 미발송, digest 미포함, 70점 이상) 1통 발송 후 digest_sent_at 갱신
     */
    @Transactional
    public void runDailyDigestSend() {
        log.info("[Notification] daily digest 작업 시작");
        if (!properties.isEnabled() || properties.getRecipients().isEmpty()) {
            log.info("[Notification] 스킵 - 알림 비활성화 또는 수신자 없음");
            return;
        }
        // digest 보내기 전에 최신 크롤링 데이터 fetch → 스코어 → DB 반영 (추천만 저장)
        runHourlyFetchAndImmediateSend();
        int minScore = properties.getThreshold().getDigest();
        List<JobPostingNotification> eligible = notificationRepository.findEligibleForDigest(minScore);
        log.info("[Notification] digest 대상 조회 완료 - 최소점수 {}점 이상, 대상 {}건", minScore, eligible.size());
        if (eligible.isEmpty()) {
            log.info("[Notification] digest 대상 없음, 이메일 미발송");
            return;
        }
        try {
            eligible.sort(Comparator.comparing(JobPostingNotification::getTotalScoreSnapshot,
                    Comparator.nullsLast(Comparator.reverseOrder())));
            sendDigestEmail(eligible);
            Instant now = Instant.now();
            for (JobPostingNotification n : eligible) {
                n.setDigestSentAt(now);
                notificationRepository.save(n);
            }
            log.info("[Notification] digest 이메일 발송 완료 - {}건, 수신자 {}명", eligible.size(), properties.getRecipients().size());
        } catch (Exception e) {
            log.error("[Notification] digest 메일 발송 실패: {}", e.getMessage(), e);
            // 실패 시 digest_sent_at 미갱신 → 다음 digest 시 다시 대상에 포함
        }
    }

    /** 즉시 추천 공고 여러 건을 한 통으로 발송 */
    private void sendBatchedImmediateEmail(List<ImmediateCandidate> candidates) throws MessagingException {
        String subject = "[JobLens 즉시 추천] " + candidates.size() + "건의 추천 공고";
        log.info("[Notification] 즉시 추천 이메일 발송 - subject={}, 수신자 {}명", subject, properties.getRecipients().size());
        String html = buildBatchedImmediateHtml(candidates);
        emailService.sendHtmlEmailToMany(properties.getRecipients(), subject, html);
    }

    /** digest 메일 1통 발송 (대상 공고 목록을 HTML로 구성) */
    private void sendDigestEmail(List<JobPostingNotification> list) throws MessagingException {
        String subject = "[JobLens] 오늘의 채용 공고 Digest (" + list.size() + "건)";
        log.info("[Notification] digest 이메일 발송 - subject={}, 수신자 {}명", subject, properties.getRecipients().size());
        String html = buildDigestHtml(list);
        emailService.sendHtmlEmailToMany(properties.getRecipients(), subject, html);
    }

    /** 즉시 추천 메일용 HTML 본문 생성 (여러 건 목록) */
    private String buildBatchedImmediateHtml(List<ImmediateCandidate> candidates) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset=\"UTF-8\"></head><body>");
        sb.append("<h2>즉시 추천 공고 (").append(candidates.size()).append("건)</h2>");
        sb.append("<ul>");
        for (ImmediateCandidate c : candidates) {
            JobPostingRequest job = c.job();
            ScoreResponse response = c.response();
            sb.append("<li>");
            sb.append("<strong>").append(escapeHtml(job.getCompany())).append("</strong> - ").append(escapeHtml(job.getTitle()));
            sb.append(" | 위치: ").append(escapeHtml(job.getLocation()));
            sb.append(" | 점수: ").append(response.getTotalScore()).append("점");
            if (job.getUrl() != null) {
                sb.append(" <a href=\"").append(escapeHtml(job.getUrl())).append("\">공고 보기</a>");
            }
            sb.append("</li>");
        }
        sb.append("</ul></body></html>");
        return sb.toString();
    }

    /** digest 메일용 HTML 본문 생성 (공고 목록 리스트) */
    private String buildDigestHtml(List<JobPostingNotification> list) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset=\"UTF-8\"></head><body>");
        sb.append("<h2>오늘의 추천 채용 공고 (").append(list.size()).append("건)</h2>");
        sb.append("<ul>");
        for (JobPostingNotification n : list) {
            sb.append("<li>");
            sb.append(escapeHtml(n.getCompany())).append(" - ").append(escapeHtml(n.getTitle()));
            sb.append(" (").append(n.getTotalScoreSnapshot()).append("점) ");
            sb.append("<a href=\"").append(escapeHtml(n.getPostingId())).append("\">공고 보기</a>");
            sb.append("</li>");
        }
        sb.append("</ul></body></html>");
        return sb.toString();
    }

    /** HTML 특수문자 이스케이프 (XSS 방지) */
    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
