package com.joblens.api.email.web;

import com.joblens.api.email.service.EmailService;
import com.joblens.api.email.web.dto.TestEmailRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/emails")
@Tag(name = "이메일", description = "이메일 전송 API")
public class EmailController {

    private final EmailService emailService;

    public EmailController(EmailService emailService) {
        this.emailService = emailService;
    }

    @PostMapping("/test")
    @Operation(summary = "테스트 메일 전송", description = "Swagger에서 테스트 메일을 보낼 수 있습니다. .env의 SMTP 설정이 필요합니다.")
    public ResponseEntity<Map<String, String>> sendTestEmail(@Valid @RequestBody TestEmailRequest request) {
        String subject = request.getSubject() != null && !request.getSubject().isBlank()
                ? request.getSubject()
                : "JobLens 테스트 메일";
        String body = request.getBody() != null && !request.getBody().isBlank()
                ? request.getBody()
                : "JobLens API에서 발송한 테스트 메일입니다.";

        emailService.sendSimpleEmail(request.getTo(), subject, body);

        return ResponseEntity.ok(Map.of(
                "message", "메일이 성공적으로 발송되었습니다.",
                "to", request.getTo()
        ));
    }
}
