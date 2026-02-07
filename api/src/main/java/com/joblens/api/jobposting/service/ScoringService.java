package com.joblens.api.jobposting.service;

import com.joblens.api.jobposting.web.dto.JobPostingRequest;
import com.joblens.api.jobposting.web.dto.ScoreBreakdown;
import com.joblens.api.jobposting.web.dto.ScoreComponent;
import com.joblens.api.jobposting.web.dto.ScoreFlag;
import com.joblens.api.jobposting.web.dto.ScoreResponse;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ScoringService {

    private static final int MAX_LOCATION = 15;
    private static final int MAX_EMPLOYMENT = 15;
    private static final int MAX_ROLE_FIT = 20;
    private static final int MAX_EXPERIENCE_FIT = 10;
    private static final int MAX_STACK_FIT = 15;
    private static final int MAX_DOMAIN = 10;
    private static final int MAX_CULTURE = 10;
    private static final int MAX_JD_QUALITY = 5;

    private final ScoringKeywordsProperties keywords;

    public ScoringService(ScoringKeywordsProperties keywords) {
        this.keywords = keywords;
    }

    public ScoreResponse score(JobPostingRequest request) {
        ScoreResponse response = new ScoreResponse();
        ScoreBreakdown breakdown = new ScoreBreakdown();
        response.setBreakdown(breakdown);

        Map<String, List<String>> matchedKeywords = response.getMatchedKeywords();
        List<ScoreFlag> flags = response.getFlags();

        String text = buildSearchText(request);

        // Hard filter 먼저 적용
        if (isContractPosition(request, text, matchedKeywords, flags)
                || isNonDevRole(request, text, matchedKeywords, flags)) {
            response.setExcluded(true);
            response.setDecision("제외");
            response.setTotalScore(0);
            return response;
        }

        int total = 0;

        ScoreComponent aLocation = scoreLocation(request, matchedKeywords);
        breakdown.setaLocation(aLocation);
        total += aLocation.getScore();

        ScoreComponent bEmployment = scoreEmployment(request, text, matchedKeywords);
        breakdown.setbEmployment(bEmployment);
        total += bEmployment.getScore();

        ScoreComponent cRoleFit = scoreRoleFit(text, matchedKeywords);
        breakdown.setcRoleFit(cRoleFit);
        total += cRoleFit.getScore();

        ScoreComponent dExperienceFit = scoreExperience(text, matchedKeywords);
        breakdown.setdExperienceFit(dExperienceFit);
        total += dExperienceFit.getScore();

        StackScoreResult stackScoreResult = scoreStack(text, matchedKeywords);
        breakdown.seteStackFit(stackScoreResult.component());
        total += stackScoreResult.component().getScore();
        if (stackScoreResult.jspDegraded()) {
            flags.add(new ScoreFlag(
                    "JSP_DOWNGRADE",
                    "JSP 요구사항으로 인한 스택 강등",
                    ScoreFlag.Severity.INFO
            ));
        }

        ScoreComponent fDomain = scoreDomain(text, matchedKeywords);
        breakdown.setfDomain(fDomain);
        total += fDomain.getScore();

        ScoreComponent gCulture = scoreCulture(text, matchedKeywords);
        breakdown.setgCulture(gCulture);
        total += gCulture.getScore();

        ScoreComponent hJdQuality = scoreJdQuality(request, matchedKeywords);
        breakdown.sethJdQuality(hJdQuality);
        total += hJdQuality.getScore();

        response.setTotalScore(total);
        response.setExcluded(false);
        response.setDecision(decide(total));
        return response;
    }

    private String buildSearchText(JobPostingRequest request) {
        StringBuilder sb = new StringBuilder();
        appendIfNotNull(sb, request.getTitle());
        appendIfNotNull(sb, request.getCompany());
        appendIfNotNull(sb, request.getLocation());
        appendIfNotNull(sb, request.getJobCondition());
        appendIfNotNull(sb, request.getSector());
        appendIfNotNull(sb, request.getDetailHtml());
        if (request.getDetailSections() != null) {
            request.getDetailSections().forEach((k, v) -> {
                appendIfNotNull(sb, k);
                appendIfNotNull(sb, v);
            });
        }
        return sb.toString();
    }

    private void appendIfNotNull(StringBuilder sb, String value) {
        if (value != null && !value.isBlank()) {
            sb.append(' ').append(value);
        }
    }

    private boolean isContractPosition(
            JobPostingRequest request,
            String text,
            Map<String, List<String>> matchedKeywords,
            List<ScoreFlag> flags
    ) {
        List<String> contractKeywords = keywords.getHardFilter().getContract();
        String target = ((request.getJobCondition() == null ? "" : request.getJobCondition()) + " " + text)
                .toLowerCase(Locale.ROOT);
        List<String> matched = findMatched(contractKeywords, target);
        if (!matched.isEmpty()) {
            matchedKeywords.put("hardFilter_contract", matched);
            flags.add(new ScoreFlag(
                    "CONTRACT",
                    "계약직/기간제 조건으로 제외",
                    ScoreFlag.Severity.CRITICAL
            ));
            return true;
        }
        return false;
    }

    private boolean isNonDevRole(
            JobPostingRequest request,
            String text,
            Map<String, List<String>> matchedKeywords,
            List<ScoreFlag> flags
    ) {
        List<String> nonDevKeywords = keywords.getHardFilter().getNonDevRoles();
        String target = ((request.getTitle() == null ? "" : request.getTitle()) + " " + text)
                .toLowerCase(Locale.ROOT);
        List<String> matched = findMatched(nonDevKeywords, target);
        if (!matched.isEmpty()) {
            matchedKeywords.put("hardFilter_nonDev", matched);
            flags.add(new ScoreFlag(
                    "NON_DEV_ROLE",
                    "비개발 직무로 판단되어 제외",
                    ScoreFlag.Severity.CRITICAL
            ));
            return true;
        }
        return false;
    }

    private ScoreComponent scoreLocation(JobPostingRequest request, Map<String, List<String>> matchedKeywords) {
        String location = Optional.ofNullable(request.getLocation()).orElse("");
        String normalized = location.replaceAll("\\s+", "");

        int score;
        String reason;

        if (normalized.contains("서울") || normalized.contains("경기") || normalized.contains("대전")) {
            score = 15;
            reason = "서울/경기/대전 근무지";
            matchedKeywords.put("A_location", List.of(location));
        } else if (!normalized.isEmpty()) {
            score = 10;
            reason = "기타 국내 근무지";
            matchedKeywords.put("A_location", List.of(location));
        } else {
            score = 0;
            reason = "근무지 정보 없음";
        }

        return new ScoreComponent("A", "location", score, MAX_LOCATION, reason);
    }

    private ScoreComponent scoreEmployment(
            JobPostingRequest request,
            String text,
            Map<String, List<String>> matchedKeywords
    ) {
        Map<String, List<String>> employment = keywords.getEmployment();
        String target = ((request.getJobCondition() == null ? "" : request.getJobCondition()) + " " + text)
                .toLowerCase(Locale.ROOT);

        int score = 0;
        String reason = "고용 형태 정보 미약";
        List<String> matched = new ArrayList<>();

        if (employment.containsKey("fulltime")) {
            List<String> hits = findMatched(employment.get("fulltime"), target);
            if (!hits.isEmpty()) {
                score = 15;
                reason = "정규직";
                matched.addAll(hits);
            }
        }
        if (score == 0 && employment.containsKey("dispatch")) {
            List<String> hits = findMatched(employment.get("dispatch"), target);
            if (!hits.isEmpty()) {
                score = 12;
                reason = "파견/프리랜서";
                matched.addAll(hits);
            }
        }
        if (score == 0 && employment.containsKey("conversionIntern")) {
            List<String> hits = findMatched(employment.get("conversionIntern"), target);
            if (!hits.isEmpty()) {
                score = 8;
                reason = "전환형 인턴";
                matched.addAll(hits);
            }
        }
        if (score == 0 && employment.containsKey("intern")) {
            List<String> hits = findMatched(employment.get("intern"), target);
            if (!hits.isEmpty()) {
                score = 3;
                reason = "인턴";
                matched.addAll(hits);
            }
        }

        if (!matched.isEmpty()) {
            matchedKeywords.put("B_employment", matched);
        }

        return new ScoreComponent("B", "employment", score, MAX_EMPLOYMENT, reason);
    }

    private ScoreComponent scoreRoleFit(String text, Map<String, List<String>> matchedKeywords) {
        Map<String, List<String>> role = keywords.getRole();
        String target = text.toLowerCase(Locale.ROOT);

        int score = 0;
        String reason = "역할 적합도 낮음";
        List<String> matched = new ArrayList<>();

        // 프론트엔드 > 풀스택 > 백엔드 우선순위
        if (role.containsKey("frontend")) {
            List<String> hits = findMatched(role.get("frontend"), target);
            if (!hits.isEmpty()) {
                score = 20;
                reason = "프론트엔드 중심 포지션";
                matched.addAll(hits);
            }
        }
        if (score == 0 && role.containsKey("fullstack")) {
            List<String> hits = findMatched(role.get("fullstack"), target);
            if (!hits.isEmpty()) {
                score = 17;
                reason = "풀스택 포지션";
                matched.addAll(hits);
            }
        }
        if (score == 0 && role.containsKey("backend")) {
            List<String> hits = findMatched(role.get("backend"), target);
            if (!hits.isEmpty()) {
                score = 14;
                reason = "백엔드 포지션";
                matched.addAll(hits);
            }
        }

        if (!matched.isEmpty()) {
            matchedKeywords.put("C_role_fit", matched);
        }

        return new ScoreComponent("C", "role_fit", score, MAX_ROLE_FIT, reason);
    }

    private ScoreComponent scoreExperience(String text, Map<String, List<String>> matchedKeywords) {
        Map<String, List<String>> experience = keywords.getExperience();
        String target = text.toLowerCase(Locale.ROOT);

        int score = 0;
        String reason = "경력 정보 불명확";
        List<String> matched = new ArrayList<>();

        if (experience.containsKey("target_3_6")) {
            List<String> hits = findMatched(experience.get("target_3_6"), target);
            if (!hits.isEmpty()) {
                score = 10;
                reason = "타깃 3~6년 경력";
                matched.addAll(hits);
            }
        }
        if (score == 0 && experience.containsKey("entry_or_any")) {
            List<String> hits = findMatched(experience.get("entry_or_any"), target);
            if (!hits.isEmpty()) {
                score = 7;
                reason = "신입·경력 혼합";
                matched.addAll(hits);
            }
        }
        if (score == 0 && experience.containsKey("seven_plus")) {
            List<String> hits = findMatched(experience.get("seven_plus"), target);
            if (!hits.isEmpty()) {
                score = 3;
                reason = "7년 이상 경력 위주";
                matched.addAll(hits);
            }
        }
        if (score == 0 && experience.containsKey("long_term")) {
            List<String> hits = findMatched(experience.get("long_term"), target);
            if (!hits.isEmpty()) {
                score = 1;
                reason = "8년 이상/10년 이상 등 상위 연차 위주";
                matched.addAll(hits);
            }
        }

        if (!matched.isEmpty()) {
            matchedKeywords.put("D_experience_fit", matched);
        }

        return new ScoreComponent("D", "experience_fit", score, MAX_EXPERIENCE_FIT, reason);
    }

    private StackScoreResult scoreStack(String text, Map<String, List<String>> matchedKeywords) {
        Map<String, List<String>> stack = keywords.getStack();
        String target = text.toLowerCase(Locale.ROOT);

        boolean jspFound = !findMatched(keywords.getJsp(), target).isEmpty();

        String chosenTrack = null;
        int score = 0;
        List<String> matched = new ArrayList<>();

        // 우선순위: E1 React/Next > E2 Node/Nest > E3 Spring > E4 Java > E5 JSP > E6 PHP
        chosenTrack = chooseStackIfMatch("e1", 15, stack, target, matched, chosenTrack);
        if (chosenTrack == null) {
            chosenTrack = chooseStackIfMatch("e2", 13, stack, target, matched, chosenTrack);
        }
        if (chosenTrack == null) {
            chosenTrack = chooseStackIfMatch("e3", 11, stack, target, matched, chosenTrack);
        }
        if (chosenTrack == null) {
            chosenTrack = chooseStackIfMatch("e4", 9, stack, target, matched, chosenTrack);
        }
        if (chosenTrack == null) {
            chosenTrack = chooseStackIfMatch("e5", 7, stack, target, matched, chosenTrack);
        }
        if (chosenTrack == null) {
            chosenTrack = chooseStackIfMatch("e6", 5, stack, target, matched, chosenTrack);
        }

        if (chosenTrack == null) {
            score = 0;
        } else {
            score = switch (chosenTrack) {
                case "e1" -> 15;
                case "e2" -> 13;
                case "e3" -> 11;
                case "e4" -> 9;
                case "e5" -> 7;
                case "e6" -> 5;
                default -> 0;
            };
        }

        boolean jspDegraded = false;
        if (jspFound && (chosenTrack == null || !"e5".equals(chosenTrack))) {
            // JSP가 포함되면 강제 E5로 강등
            chosenTrack = "e5";
            score = 7;
            jspDegraded = true;
            matched.add("jsp");
        }

        if (!matched.isEmpty()) {
            matchedKeywords.put("E_stack_fit", matched);
        }

        String reason;
        if (chosenTrack == null) {
            reason = "핵심 기술 스택 불명확";
        } else {
            reason = "스택 트랙: " + chosenTrack.toUpperCase(Locale.ROOT)
                    + (jspDegraded ? " (JSP 요구로 인한 강등)" : "");
        }

        ScoreComponent component = new ScoreComponent("E", "stack_fit", score, MAX_STACK_FIT, reason);
        return new StackScoreResult(component, jspDegraded);
    }

    private String chooseStackIfMatch(
            String code,
            int baseScore,
            Map<String, List<String>> stack,
            String target,
            List<String> matched,
            String currentChoice
    ) {
        if (currentChoice != null) {
            return currentChoice;
        }
        List<String> kw = stack.get(code);
        if (kw == null) {
            return null;
        }
        List<String> hits = findMatched(kw, target);
        if (!hits.isEmpty()) {
            matched.addAll(hits);
            return code;
        }
        return null;
    }

    private ScoreComponent scoreDomain(String text, Map<String, List<String>> matchedKeywords) {
        Map<String, List<String>> domain = keywords.getDomain();
        String target = text.toLowerCase(Locale.ROOT);

        List<String> productHits = domain.containsKey("product") ? findMatched(domain.get("product"), target) : List.of();
        List<String> mixedHits = domain.containsKey("mixed") ? findMatched(domain.get("mixed"), target) : List.of();
        List<String> legacyHits = domain.containsKey("legacy_finance") ? findMatched(domain.get("legacy_finance"), target) : List.of();

        int score;
        String reason;
        List<String> matched = new ArrayList<>();

        if (!productHits.isEmpty() && legacyHits.isEmpty()) {
            score = 9;
            reason = "제품/플랫폼/자체 서비스 도메인";
            matched.addAll(productHits);
        } else if ((!productHits.isEmpty() && !legacyHits.isEmpty()) || !mixedHits.isEmpty()) {
            score = 7;
            reason = "제품 + SI/금융 등 혼합 도메인";
            matched.addAll(productHits);
            matched.addAll(mixedHits);
            matched.addAll(legacyHits);
        } else if (!legacyHits.isEmpty()) {
            score = 3;
            reason = "금융 SI/레거시 위주 도메인";
            matched.addAll(legacyHits);
        } else {
            score = 0;
            reason = "도메인 정보 부족";
        }

        if (!matched.isEmpty()) {
            matchedKeywords.put("F_domain", matched);
        }

        return new ScoreComponent("F", "domain", score, MAX_DOMAIN, reason);
    }

    private ScoreComponent scoreCulture(String text, Map<String, List<String>> matchedKeywords) {
        List<String> cultureKeywords = keywords.getCulturePositive();
        String target = text.toLowerCase(Locale.ROOT);

        List<String> matched = findMatched(cultureKeywords, target);
        int score = Math.min(10, matched.size() * 2);
        String reason;
        if (matched.isEmpty()) {
            reason = "근무제/복지/워라밸 관련 정보 부족";
        } else {
            reason = "복지/워라밸 신호 " + matched.size() + "개 이상";
        }

        if (!matched.isEmpty()) {
            matchedKeywords.put("G_culture", matched);
        }

        return new ScoreComponent("G", "culture", score, MAX_CULTURE, reason);
    }

    private ScoreComponent scoreJdQuality(JobPostingRequest request, Map<String, List<String>> matchedKeywords) {
        int length = Optional.ofNullable(request.getDetailHtml()).map(String::length).orElse(0);
        int sectionCount = request.getDetailSections() != null ? request.getDetailSections().size() : 0;

        int score;
        String reason;

        if (length > 8000 || sectionCount >= 8) {
            score = 5;
            reason = "상세 JD 분량 및 섹션이 충분함";
        } else if (length > 4000 || sectionCount >= 5) {
            score = 3;
            reason = "JD 정보가 보통 수준";
        } else if (length > 1000 || sectionCount >= 2) {
            score = 1;
            reason = "JD 정보가 다소 부족함";
        } else {
            score = 0;
            reason = "JD 정보 매우 부족";
        }

        if (score > 0) {
            matchedKeywords.put("H_jd_quality", List.of("length=" + length, "sections=" + sectionCount));
        }

        return new ScoreComponent("H", "jd_quality", score, MAX_JD_QUALITY, reason);
    }

    private String decide(int total) {
        if (total >= 70) {
            return "추천";
        }
        if (total >= 50) {
            return "보류";
        }
        return "비추천";
    }

    private List<String> findMatched(List<String> keywords, String targetLowerCase) {
        if (keywords == null || keywords.isEmpty() || targetLowerCase == null || targetLowerCase.isEmpty()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String kw : keywords) {
            if (kw == null || kw.isBlank()) {
                continue;
            }
            String normalizedKeyword = kw.toLowerCase(Locale.ROOT);
            if (targetLowerCase.contains(normalizedKeyword)) {
                result.add(kw);
            }
        }
        return result;
    }

    private record StackScoreResult(ScoreComponent component, boolean jspDegraded) {
    }
}

