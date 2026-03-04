package com.joblens.api.jobposting.client;

import com.joblens.api.jobposting.web.dto.JobsFileResponse;
import com.joblens.api.jobposting.web.dto.jobkorea.JobKoreaCrawlRequest;
import com.joblens.api.jobposting.web.dto.jobkorea.JobPostingRequest;
import com.joblens.api.jobposting.web.dto.saramin.SaraminCrawlRequest;
import com.joblens.api.jobposting.web.dto.saramin.SaraminCrawlResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Python 크롤러 API를 호출하는 클라이언트.
 * 크롤러 주소: http://localhost:8000
 * - GET /jobs/saramin, /jobs/jobkorea
 * - POST /crawl/saramin, /crawl/jobkorea
 */
@Component
public class CrawlerClient {

    private static final Logger log = LoggerFactory.getLogger(CrawlerClient.class);

    private final String baseUrl;
    private final RestClient restClient;

    public CrawlerClient(
            @Value("${joblens.crawler.base-url:http://localhost:8000}") String baseUrl
    ) {
        this.baseUrl = baseUrl;
        this.restClient = RestClient.builder()
            .baseUrl(baseUrl)
            .requestFactory(new HttpComponentsClientHttpRequestFactory())
            .build();
    }

    /**
     * 사람인 공고 목록 조회 (GET /jobs/saramin).
     */
    public List<JobPostingRequest> fetchSaraminJobs() {
        return fetchSourceJobs("/jobs/saramin", "saramin");
    }

    /**
     * 잡코리아 공고 목록 조회 (GET /jobs/jobkorea).
     */
    public List<JobPostingRequest> fetchJobkoreaJobs() {
        return fetchSourceJobs("/jobs/jobkorea", "jobkorea");
    }

    /**
     * 사람인 + 잡코리아 공고 목록을 모두 조회한 뒤 URL 기준으로 중복 제거하여 반환한다.
     *
     * @return 통합 공고 목록
     */
    public List<JobPostingRequest> fetchJobs() {
        List<JobPostingRequest> saramin = Collections.emptyList();
        List<JobPostingRequest> jobkorea = Collections.emptyList();

        try {
            saramin = fetchSaraminJobs();
        } catch (CrawlerClientException e) {
            log.warn("[CrawlerClient] 사람인 공고 목록 조회 실패, 무시하고 진행: {}", e.getMessage());
        }

        try {
            jobkorea = fetchJobkoreaJobs();
        } catch (CrawlerClientException e) {
            log.warn("[CrawlerClient] 잡코리아 공고 목록 조회 실패, 무시하고 진행: {}", e.getMessage());
        }

        int saraminCount = saramin != null ? saramin.size() : 0;
        int jobkoreaCount = jobkorea != null ? jobkorea.size() : 0;

        List<JobPostingRequest> merged = new ArrayList<>();
        Set<String> seenUrls = new HashSet<>();

        if (saramin != null) {
            for (JobPostingRequest job : saramin) {
                String url = job.getUrl();
                if (url != null) {
                    if (seenUrls.add(url)) {
                        merged.add(job);
                    }
                } else {
                    merged.add(job);
                }
            }
        }

        if (jobkorea != null) {
            for (JobPostingRequest job : jobkorea) {
                String url = job.getUrl();
                if (url != null) {
                    if (seenUrls.add(url)) {
                        merged.add(job);
                    }
                } else {
                    merged.add(job);
                }
            }
        }

        log.info("[CrawlerClient] 공고 목록 통합 완료 - saramin={}건, jobkorea={}건, 중복 제거 후 {}건",
                saraminCount, jobkoreaCount, merged.size());
        return merged;
    }

    private List<JobPostingRequest> fetchSourceJobs(String url, String source) {
        log.info("[CrawlerClient] 공고 목록 조회 요청 GET {}{} (source={})", baseUrl, url, source);

        try {
            JobsFileResponse response = restClient.get()
                    .uri(url)
                    .retrieve()
                    .body(JobsFileResponse.class);

            int count = response != null ? response.getCount() : 0;
            List<JobPostingRequest> jobs = response != null && response.getJobs() != null
                    ? response.getJobs()
                    : Collections.emptyList();
            log.info("[CrawlerClient] 공고 목록 조회 완료 source={} count={}", source, count);
            return jobs;
        } catch (RestClientException e) {
            log.error("[CrawlerClient] 공고 목록 조회 실패 source={}: {}", source, e.getMessage());
            throw new CrawlerClientException("크롤러 " + url + " 호출 실패: " + e.getMessage(), e);
        }
    }

    /**
     * 사람인 목록/상세 크롤링 실행 (POST /crawl/saramin).
     *
     * @param request 크롤 옵션 (url, pages, detail 등). null 또는 빈 필드 시 크롤러 기본값 사용
     * @return 크롤링 결과 (수집 개수, 저장 여부, 공고 목록)
     */
    public SaraminCrawlResponse crawlSaramin(SaraminCrawlRequest request) {
        return postCrawl("/crawl/saramin", request, "saramin");
    }

    /**
     * 잡코리아 목록/상세 크롤링 실행 (POST /crawl/jobkorea).
     *
     * @param request 크롤 옵션 (url, pages, detail 등). null 또는 빈 필드 시 크롤러 기본값 사용
     * @return 크롤링 결과 (수집 개수, 저장 여부, 공고 목록)
     */
    public SaraminCrawlResponse crawlJobkorea(JobKoreaCrawlRequest request) {
        return postCrawl("/crawl/jobkorea", request, "jobkorea");
    }

    /**
     * 기존 사용 코드를 위한 사람인 크롤링 alias (POST /crawl/saramin).
     */
    public SaraminCrawlResponse crawl(SaraminCrawlRequest request) {
        return crawlSaramin(request);
    }

    private SaraminCrawlResponse postCrawl(String url, Object request, String source) {
        try {
            log.info("[CrawlerClient] 크롤 요청 POST {}{} (source={}) body={}", baseUrl, url, source, request);
        } catch (Exception e) {
            log.info("[CrawlerClient] 크롤 요청 POST {}{} (source={}) - request 직렬화 생략", baseUrl, url, source);
        }

        try {
            SaraminCrawlResponse response = restClient.post()
                .uri(url)
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(SaraminCrawlResponse.class);
            SaraminCrawlResponse result = response != null ? response : new SaraminCrawlResponse();
            int count = result.getCount();
            log.info("[CrawlerClient] 크롤 완료 source={} count={}", source, count);
            return result;
        } catch (RestClientResponseException e) {
            String responseBody = e.getResponseBodyAsString();
            log.error("[CrawlerClient] 크롤 실패 source={}: {} body={}", source, e.getMessage(), responseBody);
            throw new RuntimeException("FastAPI error(source=" + source + "): " + e.getMessage() + " body=" + responseBody, e);
        }
    }
}
