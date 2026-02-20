package com.joblens.api.jobposting.web.dto;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ScoreResponse {

    private String title;
    private String decision;
    private boolean excluded;
    private int totalScore;
    private ScoreBreakdown breakdown;
    /** 매칭된 기술 스택 키워드 목록 (예: React, Node.js, Spring). 미매칭 시 빈 목록 */
    private List<String> matchedStackKeywords = new ArrayList<>();
    private Map<String, List<String>> matchedKeywords = new LinkedHashMap<>();
    private List<ScoreFlag> flags = new ArrayList<>();

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }

    public boolean isExcluded() {
        return excluded;
    }

    public void setExcluded(boolean excluded) {
        this.excluded = excluded;
    }

    public int getTotalScore() {
        return totalScore;
    }

    public void setTotalScore(int totalScore) {
        this.totalScore = totalScore;
    }

    public ScoreBreakdown getBreakdown() {
        return breakdown;
    }

    public void setBreakdown(ScoreBreakdown breakdown) {
        this.breakdown = breakdown;
    }

    public List<String> getMatchedStackKeywords() {
        return matchedStackKeywords;
    }

    public void setMatchedStackKeywords(List<String> matchedStackKeywords) {
        this.matchedStackKeywords = matchedStackKeywords != null ? matchedStackKeywords : new ArrayList<>();
    }

    public Map<String, List<String>> getMatchedKeywords() {
        return matchedKeywords;
    }

    public void setMatchedKeywords(Map<String, List<String>> matchedKeywords) {
        this.matchedKeywords = matchedKeywords;
    }

    public List<ScoreFlag> getFlags() {
        return flags;
    }

    public void setFlags(List<ScoreFlag> flags) {
        this.flags = flags;
    }
}

