package com.joblens.api.jobposting.web.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Python 크롤러 POST /crawl 요청 모델.
 * 사람인 CrawlRequest(api_app.py)와 동일한 필드 구성.
 *
 * - 입력(JSON)은 snake_case와 camelCase 모두 허용 (@JsonAlias)
 * - 직렬화(크롤러로 전송)는 snake_case로 고정 (@JsonProperty)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(
    description = "크롤링 옵션. 생략 시 크롤러 기본값 사용.",
    example = """
    {
      "url": "https://www.saramin.co.kr/zf_user/jobs/list/domestic?page=1&loc_mcd=101000%2C102000%2C108000&cat_kewd=87%2C92%2C86&search_optional_item=n&search_done=y&panel_count=y&preview=y&sort=RD&isAjaxRequest=1&page_count=20&type=domestic&is_param=1&isSearchResultEmpty=1&isSectionHome=0&searchParamCount=2",
      "pages": 2,
      "recruit_page_count": 10,
      "list_delay": 1.8,
      "detail": true,
      "detail_limit": 20,
      "detail_delay": 1.2,
      "ocr": true,
      "ocr_max_images": 3,
      "save_to_file": true
    }
    """
)
public class CrawlRequest {

    /** 매시간 스케줄 크롤링용 기본 사람인 검색 URL */
    private static final String DEFAULT_URL = "https://www.saramin.co.kr/zf_user/jobs/list/job-category?cat_kewd=86%2C87%2C92%2C84&loc_mcd=101000%2C102000%2C108000&keydownAccess=&panel_type=&search_optional_item=n&search_done=y&panel_count=y&preview=y&page=1&page_count=20&sort=RD";

    @Schema(description = "사람인 검색 URL(필터 포함). 미지정 시 크롤러 기본 URL 사용")
    private String url;

    @Schema(description = "목록 페이지 최대 크롤링 페이지 수")
    private Integer pages;

    @JsonProperty("recruit_page_count")
    @JsonAlias({"recruitPageCount"})
    @Schema(description = "페이지당 목록 개수(1, 10, 20, 30, 40, 50, 80, 100 등)", name = "recruit_page_count")
    private Integer recruitPageCount;

    @JsonProperty("list_delay")
    @JsonAlias({"listDelay"})
    @Schema(description = "목록 요청 간 딜레이(초)", name = "list_delay")
    private Double listDelay;

    @Schema(description = "상세(view-ajax) 크롤링 여부")
    private Boolean detail;

    @JsonProperty("detail_limit")
    @JsonAlias({"detailLimit"})
    @Schema(description = "상세 크롤링 개수 제한(미지정 시 전체)", name = "detail_limit")
    private Integer detailLimit;

    @JsonProperty("detail_delay")
    @JsonAlias({"detailDelay"})
    @Schema(description = "상세 요청 간 딜레이(초)", name = "detail_delay")
    private Double detailDelay;

    @Schema(description = "이미지 기반 상세에 대한 OCR fallback 여부")
    private Boolean ocr;

    @JsonProperty("ocr_max_images")
    @JsonAlias({"ocrMaxImages"})
    @Schema(description = "OCR 시 사용할 최대 이미지 개수", name = "ocr_max_images")
    private Integer ocrMaxImages;

    @JsonProperty("save_to_file")
    @JsonAlias({"saveToFile"})
    @Schema(description = "크롤링 결과를 로컬 JSON 파일로 저장 여부", name = "save_to_file")
    private Boolean saveToFile;

    public CrawlRequest() {}

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public Integer getPages() { return pages; }
    public void setPages(Integer pages) { this.pages = pages; }

    public Integer getRecruitPageCount() { return recruitPageCount; }
    public void setRecruitPageCount(Integer recruitPageCount) { this.recruitPageCount = recruitPageCount; }

    public Double getListDelay() { return listDelay; }
    public void setListDelay(Double listDelay) { this.listDelay = listDelay; }

    public Boolean getDetail() { return detail; }
    public void setDetail(Boolean detail) { this.detail = detail; }

    public Integer getDetailLimit() { return detailLimit; }
    public void setDetailLimit(Integer detailLimit) { this.detailLimit = detailLimit; }

    public Double getDetailDelay() { return detailDelay; }
    public void setDetailDelay(Double detailDelay) { this.detailDelay = detailDelay; }

    public Boolean getOcr() { return ocr; }
    public void setOcr(Boolean ocr) { this.ocr = ocr; }

    public Integer getOcrMaxImages() { return ocrMaxImages; }
    public void setOcrMaxImages(Integer ocrMaxImages) { this.ocrMaxImages = ocrMaxImages; }

    public Boolean getSaveToFile() { return saveToFile; }
    public void setSaveToFile(Boolean saveToFile) { this.saveToFile = saveToFile; }

    /**
     * 매시간 55분 스케줄 크롤링에 사용하는 기본 요청.
     * (url, pages=2, recruit_page_count=10, list_delay=1.8, detail=true, detail_limit=20, detail_delay=1.2, ocr=true, ocr_max_images=3, save_to_file=true)
     */
    public static CrawlRequest defaultForHourly() {
        CrawlRequest r = new CrawlRequest();
        r.setUrl(DEFAULT_URL);
        r.setPages(2);
        r.setRecruitPageCount(10);
        r.setListDelay(1.8);
        r.setDetail(true);
        r.setDetailLimit(20);
        r.setDetailDelay(1.2);
        r.setOcr(true);
        r.setOcrMaxImages(3);
        r.setSaveToFile(true);
        return r;
    }
}
