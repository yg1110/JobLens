package com.joblens.api.jobposting.web;

import com.joblens.api.jobposting.client.CrawlerClient;
import com.joblens.api.jobposting.service.JobPostingService;
import com.joblens.api.jobposting.web.dto.JobPostingRequest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/job-postings")
public class JobPostingController {

    private final JobPostingService service;
    private final CrawlerClient crawlerClient;

    public JobPostingController(JobPostingService service, CrawlerClient crawlerClient) {
        this.service = service;
        this.crawlerClient = crawlerClient;
    }

    /**
     * Python 크롤러 GET /jobs 를 먼저 호출하여 JSON을 받고,
     * 그 결과를 DB에 저장한다.
     */
    @PostMapping("/bulk")
    public ResponseEntity<JobPostingService.SaveResult> bulkUpsert(
            @RequestParam(required = false) String file
    ) {
        List<JobPostingRequest> jobs = crawlerClient.fetchJobs(file);
        JobPostingService.SaveResult result = service.upsertAll(jobs);
        return ResponseEntity.ok(result);
    }
}
