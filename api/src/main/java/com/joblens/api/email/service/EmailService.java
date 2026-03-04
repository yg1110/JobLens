package com.joblens.api.email.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String defaultFrom;

    @Value("${MAIL_FROM:${spring.mail.username:}}")
    private String mailFrom;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /**
     * 단순 텍스트 메일 전송
     */
    public void sendSimpleEmail(String to, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(resolveFrom());
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        mailSender.send(message);
    }

    /**
     * HTML 메일 전송
     */
    public void sendHtmlEmail(String to, String subject, String htmlContent) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom(resolveFrom());
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(htmlContent, true);
        mailSender.send(message);
    }

    /**
     * 동일 내용을 수신자 리스트 각각에게 발송 (알림용)
     */
    public void sendHtmlEmailToMany(java.util.List<String> toList, String subject, String htmlContent) throws MessagingException {
        for (String to : toList) {
            if (to != null && !to.isBlank()) {
                sendHtmlEmail(to, subject, htmlContent);
            }
        }
    }

    
    /**
     * 발신자 이메일 주소
     */
    private String resolveFrom() {
        return "noreply@joblens.com";
    }
}
