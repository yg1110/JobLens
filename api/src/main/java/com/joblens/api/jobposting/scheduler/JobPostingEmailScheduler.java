package com.joblens.api.jobposting.scheduler;

import com.joblens.api.jobposting.client.CrawlerClient;
import com.joblens.api.jobposting.notification.JobPostingNotificationService;
import com.joblens.api.jobposting.notification.NotificationProperties;
import com.joblens.api.jobposting.web.dto.CrawlRequest;
import com.joblens.api.jobposting.web.dto.CrawlResponse;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * 매시간 fetch·즉시 발송(08~21시), 매일 09:00 digest 발송.
 * 22:00~08:00(Asia/Seoul) 금지 시간에는 실행하지 않음.
 */
@Component
public class JobPostingEmailScheduler {

    private static final Logger log = LoggerFactory.getLogger(JobPostingEmailScheduler.class);
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);

    private final CrawlerClient crawlerClient;
    private final JobPostingNotificationService notificationService;
    private final NotificationProperties properties;

    public JobPostingEmailScheduler(CrawlerClient crawlerClient,
                                   JobPostingNotificationService notificationService,
                                   NotificationProperties properties) {
        this.crawlerClient = crawlerClient;
        this.notificationService = notificationService;
        this.properties = properties;
    }

    /**
     * 매시 55분: 크롤러 POST /crawl 호출. 5분 후 정각에 fetch 시 데이터 반영.
     */
    @Scheduled(cron = "0 55 7-20 * * ?", zone = "Asia/Seoul")
    @SchedulerLock(name = "hourlyCrawlTrigger", lockAtMostFor = "PT4M", lockAtLeastFor = "PT1M")
    public void hourlyCrawlTrigger() {
        log.info("[Scheduler] hourlyCrawlTrigger 스케줄 실행");
        if (!properties.isEnabled()) {
            log.info("[Scheduler] hourlyCrawlTrigger 스킵 - 알림 비활성화(enabled=false)");
            return;
        }
        if (isQuietHours()) {
            log.info("[Scheduler] hourlyCrawlTrigger 스킵 - 금지 시간대(22:00~08:00)");
            return;
        }
        try {
            CrawlRequest request = CrawlRequest.defaultForHourly();
            CrawlResponse response = crawlerClient.crawl(request);
            log.info("[Scheduler] hourlyCrawlTrigger 정상 완료 count={}", response.getCount());
        } catch (Exception e) {
            log.error("[Scheduler] hourlyCrawlTrigger 실패: {}", e.getMessage(), e);
        }
    }

    /**
     * 매시간 1회 (08:00~21:59 사이만 유효). 22:00~08:00은 금지이므로 스킵.
     */
    @Scheduled(cron = "0 0 8-21 * * ?", zone = "Asia/Seoul")
    @SchedulerLock(name = "hourlyFetchAndImmediateSend", lockAtMostFor = "PT9M", lockAtLeastFor = "PT1M")
    public void hourlyFetchAndImmediateSend() {
        log.info("[Scheduler] hourlyFetchAndImmediateSend 스케줄 실행");
        if (!properties.isEnabled()) {
            log.info("[Scheduler] hourlyFetchAndImmediateSend 스킵 - 알림 비활성화(enabled=false)");
            return;
        }
        if (isQuietHours()) {
            log.info("[Scheduler] hourlyFetchAndImmediateSend 스킵 - 금지 시간대(22:00~08:00)");
            return;
        }
        try {
            notificationService.runHourlyFetchAndImmediateSend();
            log.info("[Scheduler] hourlyFetchAndImmediateSend 정상 완료");
        } catch (Exception e) {
            log.error("[Scheduler] hourlyFetchAndImmediateSend 실패: {}", e.getMessage(), e);
        }
    }

    /**
     * 매일 09:00 digest 1회 발송
     */
    @Scheduled(cron = "0 0 9 * * ?", zone = "Asia/Seoul")
    @SchedulerLock(name = "dailyDigestSend", lockAtMostFor = "PT9M", lockAtLeastFor = "PT1M")
    public void dailyDigestSend() {
        log.info("[Scheduler] dailyDigestSend 스케줄 실행");
        if (!properties.isEnabled()) {
            log.info("[Scheduler] dailyDigestSend 스킵 - 알림 비활성화(enabled=false)");
            return;
        }
        try {
            notificationService.runDailyDigestSend();
            log.info("[Scheduler] dailyDigestSend 정상 완료");
        } catch (Exception e) {
            log.error("[Scheduler] dailyDigestSend 실패: {}", e.getMessage(), e);
        }
    }

    private boolean isQuietHours() {
        String startStr = properties.getQuietHours().getStart();
        String endStr = properties.getQuietHours().getEnd();
        LocalTime start = LocalTime.parse(startStr, TIME_FMT);
        LocalTime end = LocalTime.parse(endStr, TIME_FMT);
        LocalTime now = ZonedDateTime.now(ZONE).toLocalTime();
        if (start.isAfter(end)) {
            return !now.isBefore(start) || now.isBefore(end);
        }
        return !now.isBefore(start) && now.isBefore(end);
    }
}
