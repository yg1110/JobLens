package com.joblens.api.jobposting.web.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.joblens.api.config.CrawlerDefaults;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Python 잡코리아 크롤러 POST /crawl/jobkorea 요청 모델.
 *
 * - Python JobKoreaCrawlRequest(api_app.py)와 동일한 필드 구성
 * - 입력(JSON)은 snake_case와 camelCase 모두 허용 (@JsonAlias)
 * - 직렬화(크롤러로 전송)는 snake_case로 고정 (@JsonProperty)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(
    description = "잡코리아 크롤링 옵션. 생략 시 크롤러 기본값 사용.",
    example = """
    {
      "url": "https://www.jobkorea.co.kr/Search?tabType=recruit&Ord=ApplyCloseDtAsc&Page_No=1&duty=1000229%2C1000230%2C1000231%2C1000232%2C1000233%2C1000234%2C1000235%2C1000236%2C1000237%2C1000239%2C1000240%2C1000238%2C1000241%2C1000242%2C1000243%2C1000244%2C1000245%2C1000246%2C1000247%2C1000417%2C1000418%2C1000419%2C1000420%2C1000421%2C1000422%2C1000423&jobtype=1&filter=3%2C1&excludeText=php%2Csi%2C%ED%97%A4%EB%93%9C",
      "pages": 3,
      "recruit_page_count": 10,
      "list_delay": 1.8,
      "detail": true,
      "detail_limit": 30,
      "detail_delay": 1.2,
      "ocr": true,
      "ocr_max_images": 3,
      "save_to_file": true
    }
    """
)
public class JobKoreaCrawlRequest {

    @Schema(description = "잡코리아 검색 URL(필터 포함). 미지정 시 크롤러 기본 URL 사용")
    private String url;

    @Schema(description = "목록 페이지 최대 크롤링 페이지 수")
    private Integer pages;

    @JsonProperty("recruit_page_count")
    @JsonAlias({"recruitPageCount", "page_size", "pageSize"})
    @Schema(
        description = "페이지당 목록 개수(recruit_page_count). 1, 10, 20, 30, 40, 50, 80, 100 등. 미지정 시 URL 기존값 유지",
        name = "recruit_page_count"
    )
    private Integer recruitPageCount;

    @JsonProperty("list_delay")
    @JsonAlias({"listDelay"})
    @Schema(description = "목록 요청 간 딜레이(초)", name = "list_delay")
    private Double listDelay;

    @Schema(description = "상세 페이지 크롤링 여부")
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

    public JobKoreaCrawlRequest() {}

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
     * 매시간 스케줄 크롤링에 사용하는 기본 요청.
     * (url, pages=1, recruit_page_count=20, list_delay=1.8, detail=true, detail_limit=20, detail_delay=1.2, ocr=true, ocr_max_images=3, save_to_file=true)
     */
    public static JobKoreaCrawlRequest defaultForHourly() {
        JobKoreaCrawlRequest r = new JobKoreaCrawlRequest();
        r.setUrl(CrawlerDefaults.JOBKOREA_DEFAULT_URL);
        r.setPages(3);
        r.setRecruitPageCount(10);
        r.setListDelay(1.8);
        r.setDetail(true);
        r.setDetailLimit(30);
        r.setDetailDelay(1.2);
        r.setOcr(true);
        r.setOcrMaxImages(3);
        r.setSaveToFile(true);
        return r;
    }
}

