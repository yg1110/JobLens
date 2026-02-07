package com.joblens.api.email.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "테스트 메일 전송 요청")
public class TestEmailRequest {

    @NotBlank(message = "수신자 이메일은 필수입니다")
    @Email(message = "올바른 이메일 형식이 아닙니다")
    @Schema(description = "수신자 이메일 주소", example = "younggil9488@gmail.com", requiredMode = Schema.RequiredMode.REQUIRED)
    private String to;

    @Schema(description = "메일 제목", example = "JobLens 테스트 메일")
    private String subject;

    @Schema(description = "메일 본문 (미입력 시 기본 텍스트 사용)", example = "JobLens에서 보낸 테스트 메일입니다.")
    private String body;
}
