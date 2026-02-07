package com.joblens.api.jobposting.notification;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 스케줄러 로직을 수동으로 실행하는 API (테스트/디버깅용).
 * 실제 스케줄은 {@link com.joblens.api.jobposting.scheduler.JobPostingEmailScheduler}에서 매시/매일 실행됨.
 */
@RestController
@RequestMapping("/api/notifications")
@Tag(name = "알림 스케줄러", description = "스케줄러 수동 실행 (테스트용). 매시 fetch·즉시 발송 / 매일 digest 발송")
public class NotificationTriggerController {

    private final JobPostingNotificationService notificationService;

    public NotificationTriggerController(JobPostingNotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /** POST /api/notifications/trigger/hourly → 매시간 fetch & 즉시 발송 1회 실행 */
    @PostMapping("/trigger/hourly")
    @Operation(
            summary = "매시 작업 수동 실행",
            description = "스케줄러의 '매시간 fetch & 즉시 발송' 로직을 즉시 1회 실행합니다. " +
                    "크롤러에서 공고를 가져와 스코어 후 80점 초과 시 즉시 메일을 보냅니다. (테스트용)"
    )
    public ResponseEntity<Map<String, String>> triggerHourly() {
        notificationService.runHourlyFetchAndImmediateSend();
        return ResponseEntity.ok(Map.of(
                "message", "hourly fetch & immediate send 실행 완료"
        ));
    }

    /** POST /api/notifications/trigger/digest → 매일 digest 발송 1회 실행 */
    @PostMapping("/trigger/digest")
    @Operation(
            summary = "Digest 발송 수동 실행",
            description = "스케줄러의 '매일 09:00 digest 발송' 로직을 즉시 1회 실행합니다. " +
                    "70점 이상·즉시 미발송·digest 미포함 공고를 모아 1통 발송합니다. (테스트용)"
    )
    public ResponseEntity<Map<String, String>> triggerDigest() {
        notificationService.runDailyDigestSend();
        return ResponseEntity.ok(Map.of(
                "message", "daily digest send 실행 완료"
        ));
    }
}
