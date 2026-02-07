package com.joblens.api.jobposting.scheduler;

import com.joblens.api.jobposting.notification.JobPostingNotificationService;
import com.joblens.api.jobposting.notification.NotificationProperties;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
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

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);

    private final JobPostingNotificationService notificationService;
    private final NotificationProperties properties;

    public JobPostingEmailScheduler(JobPostingNotificationService notificationService,
                                  NotificationProperties properties) {
        this.notificationService = notificationService;
        this.properties = properties;
    }

    /**
     * 매시간 1회 (08:00~21:59 사이만 유효). 22:00~08:00은 금지이므로 스킵.
     */
    @Scheduled(cron = "0 0 8-21 * * ?", zone = "Asia/Seoul")
    @SchedulerLock(name = "hourlyFetchAndImmediateSend", lockAtMostFor = "PT9M", lockAtLeastFor = "PT1M")
    public void hourlyFetchAndImmediateSend() {
        if (!properties.isEnabled()) {
            return;
        }
        if (isQuietHours()) {
            System.out.println("금지 시간대(22:00~08:00)라 hourly 스킵");
            return;
        }
        System.out.println("hourly fetch & immediate send 시작");
        try {
            notificationService.runHourlyFetchAndImmediateSend();
        } catch (Exception e) {
            System.out.println("hourly fetch & immediate send 실패: " + e.getMessage());
        }
    }

    /**
     * 매일 09:00 digest 1회 발송
     */
    @Scheduled(cron = "0 0 9 * * ?", zone = "Asia/Seoul")
    @SchedulerLock(name = "dailyDigestSend", lockAtMostFor = "PT9M", lockAtLeastFor = "PT1M")
    public void dailyDigestSend() {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            notificationService.runDailyDigestSend();
        } catch (Exception e) {
            System.out.println("daily digest send 실패: " + e.getMessage());
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
