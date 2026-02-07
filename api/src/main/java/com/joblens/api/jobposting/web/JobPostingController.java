package com.joblens.api.jobposting.web;

import com.joblens.api.jobposting.client.CrawlerClient;
import com.joblens.api.jobposting.service.JobPostingService;
import com.joblens.api.jobposting.service.ScoringService;
import com.joblens.api.jobposting.web.dto.JobPostingRequest;
import com.joblens.api.jobposting.web.dto.ScoreResponse;

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
     * Python 크롤러 GET /jobs 를 먼저 호출하여 JSON을 받고,
     * 그 결과를 DB에 저장한다.
     */
    @PostMapping("/bulk")
    public ResponseEntity<JobPostingService.SaveResult> bulkUpsert(
            @RequestParam(value = "file", defaultValue = "saramin_jobs.json") String file
    ) {
        List<JobPostingRequest> jobs = crawlerClient.fetchJobs(file);
        JobPostingService.SaveResult result = service.upsertAll(jobs);
        return ResponseEntity.ok(result);
    }

    /**
     * Python 크롤러 GET /jobs 를 먼저 호출하여 JSON을 받고,
     * 그 결과에 대해 Scoring Engine을 적용한다.
     *
     * - file 파라미터가 있으면: 해당 파일의 공고 목록에 대해 점수화
     * - file 파라미터가 없으면: 크롤러 기본(최근) 결과에 대해 점수화
     */
    @PostMapping("/score")
    public ResponseEntity<List<ScoreResponse>> score(
            @RequestParam(value = "file", defaultValue = "saramin_jobs.json") String file
    ) {
        List<JobPostingRequest> jobs = crawlerClient.fetchJobs(file);
        List<ScoreResponse> responses = jobs.stream()
                .map(scoringService::score)
                .filter(r -> "추천".equals(r.getDecision()))
                .toList();
        return ResponseEntity.ok(responses);
    }
}
