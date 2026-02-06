package com.joblens.api.jobposting.client;

/**
 * 크롤러 API 호출 실패 시 발생하는 예외.
 */
public class CrawlerClientException extends RuntimeException {

    public CrawlerClientException(String message) {
        super(message);
    }

    public CrawlerClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
