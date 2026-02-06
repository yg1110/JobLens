package com.joblens.api.jobposting.web.dto;

public class ScoreFlag {

    public enum Severity {
        INFO,
        WARNING,
        CRITICAL
    }

    private String code;
    private String message;
    private Severity severity;

    public ScoreFlag() {
    }

    public ScoreFlag(String code, String message, Severity severity) {
        this.code = code;
        this.message = message;
        this.severity = severity;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Severity getSeverity() {
        return severity;
    }

    public void setSeverity(Severity severity) {
        this.severity = severity;
    }
}

