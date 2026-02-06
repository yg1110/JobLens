package com.joblens.api.jobposting.web;

import com.joblens.api.jobposting.service.JobPostingService;
import com.joblens.api.jobposting.web.dto.JobPostingRequest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/job-postings")
public class JobPostingController {

    private final JobPostingService service;

    public JobPostingController(JobPostingService service) {
        this.service = service;
    }

    @PostMapping("/bulk")
    public ResponseEntity<JobPostingService.SaveResult> bulkUpsert(
            @RequestBody List<JobPostingRequest> requests
    ) {
        return ResponseEntity.ok(service.upsertAll(requests));
    }
}
