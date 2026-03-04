package com.joblens.api.jobposting.scheduler;

import com.joblens.api.jobposting.client.CrawlerClient;
import com.joblens.api.jobposting.notification.JobPostingNotificationService;
import com.joblens.api.jobposting.notification.NotificationProperties;
import com.joblens.api.jobposting.web.dto.jobkorea.JobKoreaCrawlRequest;
import com.joblens.api.jobposting.web.dto.saramin.SaraminCrawlRequest;
import com.joblens.api.jobposting.web.dto.saramin.SaraminCrawlResponse;

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
     * 매시 50분: 사람인 크롤러 POST /crawl/saramin 호출.
     */
    @Scheduled(cron = "0 50 7-20 * * ?", zone = "Asia/Seoul")
    @SchedulerLock(name = "hourlySaraminCrawlTrigger", lockAtMostFor = "PT4M", lockAtLeastFor = "PT1M")
    public void hourlySaraminCrawlTrigger() {
        log.info("[Scheduler] hourlySaraminCrawlTrigger 스케줄 실행");
        if (!properties.isEnabled()) {
            log.info("[Scheduler] hourlySaraminCrawlTrigger 스킵 - 알림 비활성화(enabled=false)");
            return;
        }
        if (isQuietHours()) {
            log.info("[Scheduler] hourlySaraminCrawlTrigger 스킵 - 금지 시간대(22:00~08:00)");
            return;
        }
        try {
            SaraminCrawlRequest request = SaraminCrawlRequest.defaultForHourly();
            SaraminCrawlResponse response = crawlerClient.crawlSaramin(request);
            log.info("[Scheduler] hourlySaraminCrawlTrigger 정상 완료 count={}", response.getCount());
        } catch (Exception e) {
            log.error("[Scheduler] hourlySaraminCrawlTrigger 실패: {}", e.getMessage(), e);
        }
    }

    /**
     * 매시 55분: 잡코리아 크롤러 POST /crawl/jobkorea 호출.
     */
    @Scheduled(cron = "0 55 7-20 * * ?", zone = "Asia/Seoul")
    @SchedulerLock(name = "hourlyJobkoreaCrawlTrigger", lockAtMostFor = "PT4M", lockAtLeastFor = "PT1M")
    public void hourlyJobkoreaCrawlTrigger() {
        log.info("[Scheduler] hourlyJobkoreaCrawlTrigger 스케줄 실행");
        if (!properties.isEnabled()) {
            log.info("[Scheduler] hourlyJobkoreaCrawlTrigger 스킵 - 알림 비활성화(enabled=false)");
            return;
        }
        if (isQuietHours()) {
            log.info("[Scheduler] hourlyJobkoreaCrawlTrigger 스킵 - 금지 시간대(22:00~08:00)");
            return;
        }
        try {
            JobKoreaCrawlRequest request = JobKoreaCrawlRequest.defaultForHourly();
            SaraminCrawlResponse response = crawlerClient.crawlJobkorea(request);
            log.info("[Scheduler] hourlyJobkoreaCrawlTrigger 정상 완료 count={}", response.getCount());
        } catch (Exception e) {
            log.error("[Scheduler] hourlyJobkoreaCrawlTrigger 실패: {}", e.getMessage(), e);
        }
    }

    /**
     * 매시간 1회  (08:00~21:59 사이만 유효)
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
