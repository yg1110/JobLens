package com.joblens.api.jobposting.notification;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 채용 공고 알림 기능 설정.
 * application.yml의 joblens.notification.* 와 환경변수 NOTIFICATION_RECIPIENTS 를 바인딩한다.
 */
@Component
@ConfigurationProperties(prefix = "joblens.notification")
public class NotificationProperties {

    private final Environment environment;

    /** 알림 기능 전체 on/off */
    private boolean enabled = false;

    /** 즉시 발송·digest 점수 임계값 */
    private Threshold threshold = new Threshold();
    /** 즉시 메일 발송 제한 시간대 (향후 확장용) */
    private QuietHours quietHours = new QuietHours();
    /** digest 발송 시각 (HH:mm, 기본 09:00) */
    private String digestTime = "09:00";

    /** 수신 이메일 주소 목록 (쉼표 구분 환경변수로도 설정 가능) */
    private List<String> recipients = new ArrayList<>();
    /** 크롤러에서 가져올 파일명 (예: saramin_jobs.json) */
    private String crawlerFile = "saramin_jobs.json";

    public NotificationProperties(Environment environment) {
        this.environment = environment;
    }

    /** NOTIFICATION_RECIPIENTS 환경변수(쉼표 구분)를 파싱해 recipients에 반영 */
    @PostConstruct
    public void resolveRecipientsFromEnv() {
        String envRecipients = environment.getProperty("NOTIFICATION_RECIPIENTS");
        if (envRecipients != null && !envRecipients.isBlank()) {
            recipients = Arrays.stream(envRecipients.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Threshold getThreshold() {
        return threshold;
    }

    public void setThreshold(Threshold threshold) {
        this.threshold = threshold;
    }

    public QuietHours getQuietHours() {
        return quietHours;
    }

    public void setQuietHours(QuietHours quietHours) {
        this.quietHours = quietHours;
    }

    public String getDigestTime() {
        return digestTime;
    }

    public void setDigestTime(String digestTime) {
        this.digestTime = digestTime;
    }

    public List<String> getRecipients() {
        return recipients;
    }

    public void setRecipients(List<String> recipients) {
        this.recipients = recipients;
    }

    public String getCrawlerFile() {
        return crawlerFile;
    }

    public void setCrawlerFile(String crawlerFile) {
        this.crawlerFile = crawlerFile;
    }

    /** 점수 임계값: immediate=즉시 메일, digest=추천(일일) 메일 포함 */
    public static class Threshold {
        /** 이 점수 이상이면 즉시 메일 1회 발송 (기본 80) */
        private int immediate = 80;
        /** 이 점수 이상이면 추천(digest) 메일 대상 (기본 70) */
        private int digest = 70;

        public int getImmediate() {
            return immediate;
        }

        public void setImmediate(int immediate) {
            this.immediate = immediate;
        }

        public int getDigest() {
            return digest;
        }

        public void setDigest(int digest) {
            this.digest = digest;
        }
    }

    /** 조용 시간대 (HH:mm). 해당 구간에는 즉시 메일 발송 생략 등에 사용 가능 */
    public static class QuietHours {
        private String start = "22:00";
        private String end = "08:00";

        public String getStart() {
            return start;
        }

        public void setStart(String start) {
            this.start = start;
        }

        public String getEnd() {
            return end;
        }

        public void setEnd(String end) {
            this.end = end;
        }
    }
}
