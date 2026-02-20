package com.joblens.api.jobposting.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class ScoreComponent {

    private String code;
    private String name;
    @JsonIgnore
    private int score;
    @JsonIgnore
    private int maxScore;
    private String reason;

    public ScoreComponent() {
    }

    public ScoreComponent(String code, String name, int score, int maxScore, String reason) {
        this.code = code;
        this.name = name;
        this.score = score;
        this.maxScore = maxScore;
        this.reason = reason;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @JsonIgnore
    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    @JsonIgnore
    public int getMaxScore() {
        return maxScore;
    }

    public void setMaxScore(int maxScore) {
        this.maxScore = maxScore;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}

