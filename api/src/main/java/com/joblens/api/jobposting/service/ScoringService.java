package com.joblens.api.jobposting.service;

import com.joblens.api.jobposting.web.dto.jobkorea.JobPostingRequest;
import com.joblens.api.jobposting.web.dto.score.ScoreBreakdown;
import com.joblens.api.jobposting.web.dto.score.ScoreComponent;
import com.joblens.api.jobposting.web.dto.score.ScoreFlag;
import com.joblens.api.jobposting.web.dto.score.ScoreResponse;

import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 채용공고 스코어링 서비스.
 * <p>
 * 총점(100) = A_location(10) + C_role_fit(30) + E_stack_fit(60).
 * B/D/F/G/H는 참고용이며 총점에는 미반영.
 * </p>
 */
@Service
public class ScoringService {

    // ---------- 총점 반영 (total = A + C + E) ----------
    /** A_location: 서울/경기/인천, 만점 10 */
    private static final int MAX_LOCATION = 10;
    /** C_role_fit: 풀스택(30) > 프론트(20) > 앱(15) > 백엔드(10), 만점 30 */
    private static final int MAX_ROLE_FIT = 30;
    /** E_stack_fit: 스택 트랙 e1~e5, 만점 60 */
    private static final int MAX_STACK_FIT = 60;

    // ---------- 참고용 (브레이크다운만, 총점 미반영) ----------
    /** B_employment: 정규직 15점 등 */
    private static final int MAX_EMPLOYMENT = 15;
    /** D_experience_fit: 경력 적합도 */
    private static final int MAX_EXPERIENCE_FIT = 10;
    /** F_domain: 제품/혼합/레거시 도메인 */
    private static final int MAX_DOMAIN = 10;
    /** G_culture: 복지/워라밸 신호 */
    private static final int MAX_CULTURE = 10;
    /** H_jd_quality: JD 분량·섹션 수 */
    private static final int MAX_JD_QUALITY = 5;

    private final ScoringKeywordsProperties keywords;

    public ScoringService(ScoringKeywordsProperties keywords) {
        this.keywords = keywords;
    }

    public ScoreResponse score(JobPostingRequest request) {
        ScoreResponse response = new ScoreResponse();
        response.setTitle(request.getTitle());
        ScoreBreakdown breakdown = new ScoreBreakdown();
        response.setBreakdown(breakdown);

        Map<String, List<String>> matchedKeywords = response.getMatchedKeywords();
        List<ScoreFlag> flags = response.getFlags();

        String text = buildSearchText(request);

        // HF-1: 계약직/기간제면 제외, 총점 0
        if (isContractPosition(request, text, matchedKeywords, flags)) {
            response.setExcluded(true);
            response.setDecision("제외");
            response.setTotalScore(0);
            return response;
        }

        ScoreComponent aLocation = scoreLocation(request, matchedKeywords);
        breakdown.setaLocation(aLocation);

        ScoreComponent bEmployment = scoreEmployment(request, text, matchedKeywords);
        breakdown.setbEmployment(bEmployment);

        ScoreComponent cRoleFit = scoreRoleFit(text, matchedKeywords);
        breakdown.setcRoleFit(cRoleFit);

        ScoreComponent dExperienceFit = scoreExperience(text, matchedKeywords);
        breakdown.setdExperienceFit(dExperienceFit);

        StackScoreResult stackScoreResult = scoreStack(text, matchedKeywords);
        breakdown.seteStackFit(stackScoreResult.component());
        response.setMatchedStackKeywords(stackScoreResult.matchedStackKeywords());

        ScoreComponent fDomain = scoreDomain(text, matchedKeywords);
        breakdown.setfDomain(fDomain);

        ScoreComponent gCulture = scoreCulture(text, matchedKeywords);
        breakdown.setgCulture(gCulture);

        ScoreComponent hJdQuality = scoreJdQuality(request, matchedKeywords);
        breakdown.sethJdQuality(hJdQuality);

        // 총점 = A + C + E (B/D/F/G/H 미반영)
        int total = aLocation.getScore() + cRoleFit.getScore() + stackScoreResult.component().getScore();
        response.setTotalScore(total);
        response.setExcluded(false);
        String decision = decide(total);
        response.setDecision(decision);
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

    private ScoreComponent scoreLocation(JobPostingRequest request, Map<String, List<String>> matchedKeywords) {
        String location = Optional.ofNullable(request.getLocation()).orElse("");
        String normalized = location.replaceAll("\\s+", "");

        int score;
        String reason;

        if (normalized.contains("서울") || normalized.contains("경기") || normalized.contains("인천")) {
            score = 10;
            reason = "서울/경기/인천 근무지";
            matchedKeywords.put("A_location", List.of(location));
        } else if (!normalized.isEmpty()) {
            score = 0;
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

        // 무조건 정규직만 만점, 그 외는 0점
        if (employment.containsKey("fulltime")) {
            List<String> hits = findMatched(employment.get("fulltime"), target);
            if (!hits.isEmpty()) {
                score = 15;
                reason = "정규직";
                matched.addAll(hits);
            }
        }
        if (score == 0) {
            if (employment.containsKey("dispatch")) {
                List<String> hits = findMatched(employment.get("dispatch"), target);
                if (!hits.isEmpty()) {
                    reason = "파견/프리랜서 (정규직 아님)";
                    matched.addAll(hits);
                }
            }
            if (employment.containsKey("conversionIntern")) {
                List<String> hits = findMatched(employment.get("conversionIntern"), target);
                if (!hits.isEmpty()) {
                    reason = reason.isEmpty() ? "전환형 인턴 (정규직 아님)" : reason;
                    matched.addAll(hits);
                }
            }
            if (employment.containsKey("intern")) {
                List<String> hits = findMatched(employment.get("intern"), target);
                if (!hits.isEmpty()) {
                    reason = reason.isEmpty() ? "인턴 (정규직 아님)" : reason;
                    matched.addAll(hits);
                }
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

        // 풀스택(30) > 프론트(20) > 앱(15) > 백엔드(10)
        if (role.containsKey("fullstack")) {
            List<String> hits = findMatched(role.get("fullstack"), target);
            if (!hits.isEmpty()) {
                score = 30;
                reason = "풀스택 포지션";
                matched.addAll(hits);
            }
        }
        if (score == 0 && role.containsKey("frontend")) {
            List<String> hits = findMatched(role.get("frontend"), target);
            if (!hits.isEmpty()) {
                score = 20;
                reason = "프론트엔드 포지션";
                matched.addAll(hits);
            }
        }
        if (score == 0 && role.containsKey("app")) {
            List<String> hits = findMatched(role.get("app"), target);
            if (!hits.isEmpty()) {
                score = 15;
                reason = "앱 개발 포지션";
                matched.addAll(hits);
            }
        }
        if (score == 0 && role.containsKey("backend")) {
            List<String> hits = findMatched(role.get("backend"), target);
            if (!hits.isEmpty()) {
                score = 10;
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

    /** E_stack_fit: 트랙 우선순위 e1(60) > e2(48) > e3(36) > e4(24) > e5(12) */
    private static final List<StackTier> STACK_TIERS = List.of(
            new StackTier("e1", 60),
            new StackTier("e2", 48),
            new StackTier("e3", 36),
            new StackTier("e4", 24),
            new StackTier("e5", 12)
    );

    private StackScoreResult scoreStack(String text, Map<String, List<String>> matchedKeywords) {
        Map<String, List<String>> stack = keywords.getStack();
        String target = text.toLowerCase(Locale.ROOT);

        String chosenTrack = null;
        int score = 0;
        List<String> matched = new ArrayList<>();

        for (StackTier tier : STACK_TIERS) {
            chosenTrack = chooseStackIfMatch(tier.code, stack, target, matched, chosenTrack);
            if (chosenTrack != null) {
                score = tier.score;
                break;
            }
        }

        if (!matched.isEmpty()) {
            matchedKeywords.put("E_stack_fit", matched);
        }

        String reason = chosenTrack == null
                ? "핵심 기술 스택 불명확"
                : "스택 트랙: " + chosenTrack.toUpperCase(Locale.ROOT);

        ScoreComponent component = new ScoreComponent("E", "stack_fit", score, MAX_STACK_FIT, reason);
        return new StackScoreResult(component, false, List.copyOf(matched));
    }

    private String chooseStackIfMatch(
            String code,
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

    /** 총점 기준: 70+ 추천, 50+ 보류, 50 미만 비추천 */
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

    private record StackTier(String code, int score) {}

    private record StackScoreResult(
            ScoreComponent component,
            boolean jspDegraded,
            List<String> matchedStackKeywords
    ) {}
}

