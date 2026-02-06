package com.joblens.api.jobposting.service;

import com.joblens.api.jobposting.domain.JobPosting;
import com.joblens.api.jobposting.domain.JobPostingRepository;
import com.joblens.api.jobposting.web.dto.JobPostingRequest;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class JobPostingService {

    private final JobPostingRepository repository;

    public JobPostingService(JobPostingRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public SaveResult upsertAll(List<JobPostingRequest> requests) {
        int inserted = 0;
        int updated = 0;

        for (JobPostingRequest r : requests) {
            JobPosting entity = repository.findByUrl(r.getUrl()).orElseGet(JobPosting::new);
            boolean isNew = (entity.getId() == null);

            entity.setTitle(r.getTitle());
            entity.setCompany(r.getCompany());
            entity.setUrl(r.getUrl());
            entity.setLocation(r.getLocation());
            entity.setJobCondition(r.getJobCondition());
            entity.setSector(r.getSector());
            entity.setDeadline(r.getDeadline());
            entity.setScrapedAt(r.getScrapedAt());
            entity.setSourcePage(r.getSourcePage());
            entity.setDetailIframeUrl(r.getDetailIframeUrl());
            entity.setDetailSections(r.getDetailSections());
            entity.setDetailHtml(r.getDetailHtml());

            repository.save(entity);

            if (isNew) inserted++;
            else updated++;
        }

        return new SaveResult(inserted, updated);
    }

    public record SaveResult(int inserted, int updated) {}
}
