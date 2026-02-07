package com.joblens.api.jobposting.notification;

import java.time.Instant;
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
     * 매시간: fetch → 스코어 → 추천만 저장/갱신 → 80점 초과면 즉시 메일 1회 발송
     */
    @Transactional
    public void runHourlyFetchAndImmediateSend() {
        if (!properties.isEnabled() || properties.getRecipients().isEmpty()) {
            System.out.println("알림 비활성화 또는 수신자 없음, 스킵");
            return;
        }
        String file = properties.getCrawlerFile();
        List<JobPostingRequest> jobs;
        try {
            jobs = crawlerClient.fetchJobs(file);
        } catch (Exception e) {
            System.out.println("크롤러 fetch 실패: " + e.getMessage());
            return;
        }
        int thresholdImmediate = properties.getThreshold().getImmediate();
        for (JobPostingRequest job : jobs) {
            ScoreResponse response = scoringService.score(job);
            if (!DECISION_RECOMMEND.equals(response.getDecision())) {
                continue;
            }
            String postingId = job.getUrl();
            if (postingId == null || postingId.isBlank()) {
                continue;
            }
            int totalScore = response.getTotalScore();

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

            // 즉시 발송: 임계값 초과 시 1회만 발송 (중복 방지용 row lock)
            if (totalScore > thresholdImmediate) {
                Optional<JobPostingNotification> locked = notificationRepository.findByPostingIdForUpdate(postingId);
                if (locked.isPresent() && locked.get().getImmediateSentAt() == null) {
                    try {
                        sendImmediateEmail(job, response);
                        JobPostingNotification toUpdate = locked.get();
                        toUpdate.setImmediateSentAt(Instant.now());
                        notificationRepository.save(toUpdate);
                    } catch (Exception e) {
                        System.out.println("즉시 메일 발송 실패 postingId=" + postingId + ": " + e.getMessage());
                    }
                }
            }
        }
    }

    /**
     * 매일 09:00: digest 대상(즉시 미발송, digest 미포함, 70점 이상) 1통 발송 후 digest_sent_at 갱신
     */
    @Transactional
    public void runDailyDigestSend() {
        if (!properties.isEnabled() || properties.getRecipients().isEmpty()) {
            System.out.println("알림 비활성화 또는 수신자 없음, 스킵");
            return;
        }
        int minScore = properties.getThreshold().getDigest();
        List<JobPostingNotification> eligible = notificationRepository.findEligibleForDigest(minScore);
        if (eligible.isEmpty()) {
            System.out.println("digest 대상 없음");
            return;
        }
        try {
            sendDigestEmail(eligible);
            Instant now = Instant.now();
            for (JobPostingNotification n : eligible) {
                n.setDigestSentAt(now);
                notificationRepository.save(n);
            }
        } catch (Exception e) {
            System.out.println("digest 메일 발송 실패: " + e.getMessage());
            // 실패 시 digest_sent_at 미갱신 → 다음 digest 시 다시 대상에 포함
        }
    }

    /** 단건 즉시 추천 메일 발송 (제목/본문 HTML 생성 후 수신자 목록에 발송) */
    private void sendImmediateEmail(JobPostingRequest job, ScoreResponse response) throws MessagingException {
        String subject = "[JobLens 즉시 추천] " + (job.getCompany() != null ? job.getCompany() : "") + " - " + (job.getTitle() != null ? job.getTitle() : "");
        String html = buildImmediateHtml(job, response);
        emailService.sendHtmlEmailToMany(properties.getRecipients(), subject, html);
    }

    /** digest 메일 1통 발송 (대상 공고 목록을 HTML로 구성) */
    private void sendDigestEmail(List<JobPostingNotification> list) throws MessagingException {
        String subject = "[JobLens] 오늘의 채용 공고 Digest (" + list.size() + "건)";
        String html = buildDigestHtml(list);
        emailService.sendHtmlEmailToMany(properties.getRecipients(), subject, html);
    }

    /** 즉시 추천 메일용 HTML 본문 생성 */
    private String buildImmediateHtml(JobPostingRequest job, ScoreResponse response) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset=\"UTF-8\"></head><body>");
        sb.append("<h2>즉시 추천 공고</h2>");
        sb.append("<p><strong>회사:</strong> ").append(escapeHtml(job.getCompany())).append("</p>");
        sb.append("<p><strong>제목:</strong> ").append(escapeHtml(job.getTitle())).append("</p>");
        sb.append("<p><strong>위치:</strong> ").append(escapeHtml(job.getLocation())).append("</p>");
        sb.append("<p><strong>점수:</strong> ").append(response.getTotalScore()).append("점</p>");
        if (job.getUrl() != null) {
            sb.append("<p><a href=\"").append(escapeHtml(job.getUrl())).append("\">공고 보기</a></p>");
        }
        sb.append("</body></html>");
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
