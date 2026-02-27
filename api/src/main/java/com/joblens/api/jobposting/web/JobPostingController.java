package com.joblens.api.jobposting.web;

import com.joblens.api.jobposting.client.CrawlerClient;
import com.joblens.api.jobposting.service.JobPostingService;
import com.joblens.api.jobposting.service.ScoringService;
import com.joblens.api.jobposting.web.dto.CrawlRequest;
import com.joblens.api.jobposting.web.dto.CrawlResponse;
import com.joblens.api.jobposting.web.dto.JobKoreaCrawlRequest;
import com.joblens.api.jobposting.web.dto.JobPostingRequest;
import com.joblens.api.jobposting.web.dto.ScoreResponse;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/job-postings")
public class JobPostingController {

    private final JobPostingService service;
    private final CrawlerClient crawlerClient;
    private final ScoringService scoringService;

    public JobPostingController(JobPostingService service, CrawlerClient crawlerClient, ScoringService scoringService) {
        this.service = service;
        this.crawlerClient = crawlerClient;
        this.scoringService = scoringService;
    }

    /**
     * Python 크롤러 GET /jobs/saramin, /jobs/jobkorea 를 호출하여
     * 두 소스의 공고를 모두 조회한 뒤 DB에 upsert 한다.
     */
    @Operation(
            summary = "사람인+잡코리아 공고 DB 일괄 upsert",
            description = "크롤러 GET /jobs/saramin, /jobs/jobkorea 결과(중복 URL 제거)를 통합하여 DB에 저장/갱신합니다."
    )
    @PostMapping("/bulk")
    public ResponseEntity<JobPostingService.SaveResult> bulkUpsert() {
        List<JobPostingRequest> jobs = crawlerClient.fetchJobs();
        JobPostingService.SaveResult result = service.upsertAll(jobs);
        return ResponseEntity.ok(result);
    }

    /**
     * Python 크롤러 GET /jobs/saramin, /jobs/jobkorea 로 조회한 통합 공고 목록에 대해
     * Scoring Engine을 적용한다.
     * 총점(100점)은 E_stack_fit(기술 스택)만으로 산출한다.
     */
    @Operation(
            summary = "사람인+잡코리아 공고 목록 스코어링",
            description = "크롤러 GET /jobs/saramin, /jobs/jobkorea 결과를 통합(중복 URL 제거)한 뒤 " +
                          "각 공고에 대해 기술 스택(E_stack_fit)을 기반으로 총점(만점 100점)을 계산합니다. " +
                          "응답은 원본 공고 필드와 함께 스코어 상세(ScoreResponse)를 리스트로 반환합니다."
    )
    @PostMapping("/score")
    public ResponseEntity<List<ScoreResponse>> score() {
        List<JobPostingRequest> jobs = crawlerClient.fetchJobs();
        List<ScoreResponse> responses = jobs.stream()
                .map(scoringService::score)
                .toList();
        return ResponseEntity.ok(responses);
    }

    /**
     * Python 크롤러 POST /crawl/saramin 을 호출하여 사람인 목록/상세 크롤링을 실행한다.
     * 요청 바디가 없으면 스케줄에서 사용하는 기본 옵션(CrawlRequest.defaultForHourly)을 사용한다.
     */
    @Operation(
            summary = "사람인 목록/상세 크롤링 트리거",
            description = "FastAPI 크롤러의 POST /crawl/saramin 엔드포인트를 호출하여 사람인 채용 공고를 크롤링합니다. " +
                          "요청 바디를 생략하거나 null 로 보내면 크롤러의 기본 옵션(CrawlRequest.defaultForHourly)을 사용합니다."
    )
    @PostMapping("/crawl/saramin")
    public ResponseEntity<CrawlResponse> crawlSaramin(@RequestBody(required = false) CrawlRequest request) {
        CrawlRequest effective = request != null ? request : CrawlRequest.defaultForHourly();
        CrawlResponse response = crawlerClient.crawlSaramin(effective);
        return ResponseEntity.ok(response);
    }

    /**
     * Python 크롤러 POST /crawl/jobkorea 를 호출하여 잡코리아 목록/상세 크롤링을 실행한다.
     * 요청 바디가 없으면 기본 옵션(JobKoreaCrawlRequest.defaultForHourly)을 사용한다.
     */
    @Operation(
            summary = "잡코리아 목록/상세 크롤링 트리거",
            description = "FastAPI 크롤러의 POST /crawl/jobkorea 엔드포인트를 호출하여 잡코리아 채용 공고를 크롤링합니다. " +
                          "요청 바디를 생략하거나 null 로 보내면 크롤러의 기본 옵션(JobKoreaCrawlRequest.defaultForHourly)을 사용합니다."
    )
    @PostMapping("/crawl/jobkorea")
    public ResponseEntity<CrawlResponse> crawlJobkorea(@RequestBody(required = false) JobKoreaCrawlRequest request) {
        JobKoreaCrawlRequest effective = request != null ? request : JobKoreaCrawlRequest.defaultForHourly();
        CrawlResponse response = crawlerClient.crawlJobkorea(effective);
        return ResponseEntity.ok(response);
    }

}