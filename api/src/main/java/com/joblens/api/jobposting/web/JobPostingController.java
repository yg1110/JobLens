package com.joblens.api.jobposting.web;

import com.joblens.api.jobposting.client.CrawlerClient;
import com.joblens.api.jobposting.service.JobPostingService;
import com.joblens.api.jobposting.service.ScoringService;
import com.joblens.api.jobposting.web.dto.CrawlRequest;
import com.joblens.api.jobposting.web.dto.CrawlResponse;
import com.joblens.api.jobposting.web.dto.JobPostingRequest;
import com.joblens.api.jobposting.web.dto.ScoreResponse;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

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
     * Python 크롤러 GET /jobs 를 먼저 호출하여 JSON을 받고,
     * 그 결과를 DB에 저장한다.
     */
    @PostMapping("/bulk")
    public ResponseEntity<JobPostingService.SaveResult> bulkUpsert() {
        List<JobPostingRequest> jobs = crawlerClient.fetchJobs();
        JobPostingService.SaveResult result = service.upsertAll(jobs);
        return ResponseEntity.ok(result);
    }

    /**
     * Python 크롤러 GET /jobs 로 조회한 공고 목록에 대해 Scoring Engine을 적용한다.
     */
    @PostMapping("/score")
    public ResponseEntity<List<ScoreResponse>> score() {
        List<JobPostingRequest> jobs = crawlerClient.fetchJobs();
        List<ScoreResponse> responses = jobs.stream()
                .map(scoringService::score)
                .toList();
        return ResponseEntity.ok(responses);
    }

    /**
     * Python 크롤러 POST /crawl 을 호출하여 사람인 목록/상세 크롤링을 실행한다.
     */
    @PostMapping("/crawl")
    public ResponseEntity<CrawlResponse> crawl(@RequestBody CrawlRequest request) {
        CrawlResponse response = crawlerClient.crawl(request);
        return ResponseEntity.ok(response);
    }
}