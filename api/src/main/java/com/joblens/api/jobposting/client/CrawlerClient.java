package com.joblens.api.jobposting.client;

import com.joblens.api.jobposting.web.dto.CrawlRequest;
import com.joblens.api.jobposting.web.dto.CrawlResponse;
import com.joblens.api.jobposting.web.dto.JobsFileResponse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.joblens.api.jobposting.web.dto.JobPostingRequest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;

import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Python 크롤러 API를 호출하는 클라이언트.
 * 크롤러 주소: http://localhost:8000 (GET /jobs, POST /crawl)
 */
@Component
public class CrawlerClient {

    private static final Logger log = LoggerFactory.getLogger(CrawlerClient.class);

    private final String baseUrl;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public CrawlerClient(
            @Value("${joblens.crawler.base-url:http://localhost:8000}") String baseUrl,
            ObjectMapper objectMapper
    ) {
        this.baseUrl = baseUrl;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
            .baseUrl(baseUrl)
            .requestFactory(new HttpComponentsClientHttpRequestFactory())
            .build();
    }

    /**
     * 크롤러의 GET /jobs 를 호출하여 저장된 공고 목록을 가져온다.
     * (Python API: 파라미터 없음, 고정 파일 saramin_jobs.json 에서 조회)
     *
     * @return 공고 목록
     */
    public List<JobPostingRequest> fetchJobs() {
        String url = "/jobs";
        log.info("[CrawlerClient] 공고 목록 조회 요청 GET {}{}", baseUrl, url);

        try {
            JobsFileResponse response = restClient.get()
                    .uri(url)
                    .retrieve()
                    .body(JobsFileResponse.class);

            int count = response != null ? response.getCount() : 0;
            List<JobPostingRequest> jobs = response != null && response.getJobs() != null
                    ? response.getJobs()
                    : Collections.emptyList();
            log.info("[CrawlerClient] 공고 목록 조회 완료 count={}", count);
            return jobs;
        } catch (RestClientException e) {
            log.error("[CrawlerClient] 공고 목록 조회 실패: {}", e.getMessage());
            throw new CrawlerClientException("크롤러 /jobs 호출 실패: " + e.getMessage(), e);
        }
    }

    /**
     * 크롤러의 POST /crawl 을 호출하여 사람인 목록/상세 크롤링을 실행한다.
     *
     * @param request 크롤 옵션 (url, pages, detail 등). null 또는 빈 필드 시 크롤러 기본값 사용
     * @return 크롤링 결과 (수집 개수, 저장 여부, 공고 목록)
     */
    public CrawlResponse crawl(CrawlRequest request) {
        String url = "/crawl";
        try {
            String requestJson = objectMapper.writeValueAsString(request);
            log.info("[CrawlerClient] 크롤 요청 POST {}{} body={}", baseUrl, url, requestJson);
        } catch (Exception e) {
            log.info("[CrawlerClient] 크롤 요청 POST {}{} (request 직렬화 생략)", baseUrl, url);
        }

        try {
            CrawlResponse response = restClient.post()
                .uri(url)
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(CrawlResponse.class);
            CrawlResponse result = response != null ? response : new CrawlResponse();
            int count = result.getCount();
            log.info("[CrawlerClient] 크롤 완료 count={}", count);
            return result;
        } catch (RestClientResponseException e) {
            String responseBody = e.getResponseBodyAsString();
            log.error("[CrawlerClient] 크롤 실패: {} body={}", e.getMessage(), responseBody);
            throw new RuntimeException("FastAPI error: " + e.getMessage() + " body=" + responseBody, e);
        }
    }
}
