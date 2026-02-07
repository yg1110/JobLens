package com.joblens.api.jobposting.client;

import com.joblens.api.jobposting.web.dto.JobsFileResponse;
import com.joblens.api.jobposting.web.dto.JobPostingRequest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Collections;
import java.util.List;

/**
 * Python 크롤러 API(GET /jobs)를 호출하는 클라이언트.
 */
@Component
public class CrawlerClient {

    private final RestClient restClient;

    public CrawlerClient(
            @Value("${joblens.crawler.base-url:http://localhost:8000}") String baseUrl
    ) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    /**
     * 크롤러의 GET /jobs 를 호출하여 저장된 공고 목록을 가져온다.
     *
     * @param file 조회할 JSON 파일명 (기본값 사용 시 null)
     * @return 공고 목록
     */
    public List<JobPostingRequest> fetchJobs(String file) {
        String url = file != null && !file.isBlank()
                ? UriComponentsBuilder.fromPath("/jobs").queryParam("file", file).toUriString()
                : "/jobs";

        try {
            JobsFileResponse response = restClient.get()
                    .uri(url)
                    .retrieve()
                    .body(JobsFileResponse.class);

            return response != null && response.getJobs() != null
                    ? response.getJobs()
                    : Collections.emptyList();
        } catch (RestClientException e) {
            throw new CrawlerClientException("크롤러 /jobs 호출 실패: " + e.getMessage(), e);
        }
    }
}
