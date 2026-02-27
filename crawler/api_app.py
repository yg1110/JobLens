from __future__ import annotations

from dataclasses import asdict
from pathlib import Path
from typing import Dict, List, Optional

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field, ConfigDict

from saramin.crawler import crawl_list, enrich_jobs_with_details, load_json
from saramin.list_urls import with_recruit_page_count
from saramin.models import JobPosting

from jobkorea.crawler import (
    crawl_list as jk_crawl_list,
    enrich_jobs_with_details as jk_enrich_jobs_with_details,
    load_json as jk_load_json,
)
from jobkorea.list_urls import with_page_size

# API 기본 요청값(사람인)
DEFAULT_CRAWL_REQUEST = {
    "url": "https://www.saramin.co.kr/zf_user/jobs/list/domestic?loc_mcd=101000%2C102000%2C108000&cat_kewd=84%2C87%2C86%2C92&job_type=1&exc_keyword=php%2Cjsp%2Cjava%2Cspring&panel_type=&search_optional_item=y&search_done=y&panel_count=y&preview=y&page=1&sort=RD",
    "pages": 3,
    "recruit_page_count": 10,
    "list_delay": 1.8,
    "detail": True,
    "detail_limit": 30,
    "detail_delay": 1.2,
    "ocr": True,
    "ocr_max_images": 3,
    "save_to_file": True,
}

# API 기본 요청값(잡코리아)
DEFAULT_JOBKOREA_CRAWL_REQUEST = {
    "url": "https://www.jobkorea.co.kr/Search?tabType=recruit&Ord=ApplyCloseDtAsc&Page_No=1&duty=1000229%2C1000230%2C1000231%2C1000232&jobtype=1&excludeText=php%2Cjava%2Cspring",
    "pages": 3,
    "recruit_page_count": 10,
    "list_delay": 1.8,
    "detail": True,
    "detail_limit": 30,
    "detail_delay": 1.2,
    "ocr": True,
    "ocr_max_images": 3,
    "save_to_file": True,
}


class CrawlRequest(BaseModel):
    """
    사람인 크롤러 옵션을 HTTP 요청으로 받기 위한 모델.

    CLI 의 인자(main.py 의 argparse)와 거의 동일한 필드 구성을 따른다.
    """

    url: str = Field(
        DEFAULT_CRAWL_REQUEST["url"],
        description="사람인 검색 URL(필터 포함). 생략 시 기본 필터 URL 사용",
    )
    pages: int = Field(
        DEFAULT_CRAWL_REQUEST["pages"],
        ge=1,
        description="목록(list) 페이지를 최대 몇 페이지까지 크롤링할지",
    )
    recruit_page_count: Optional[int] = Field(
        DEFAULT_CRAWL_REQUEST["recruit_page_count"],
        ge=1,
        description="페이지당 목록 개수(recruitPageCount). 1, 10, 20, 30, 40, 50, 80, 100 등. 미지정 시 URL 기존값 유지",
    )
    list_delay: float = Field(
        DEFAULT_CRAWL_REQUEST["list_delay"],
        ge=0.0,
        description="목록 요청 간 기본 딜레이(초)",
    )
    # 상세(detail) 관련 옵션
    detail: bool = Field(
        DEFAULT_CRAWL_REQUEST["detail"],
        description="상세(view-ajax → iframe)까지 크롤링할지 여부",
    )
    detail_limit: Optional[int] = Field(
        DEFAULT_CRAWL_REQUEST["detail_limit"],
        ge=0,
        description="상세 크롤링 개수 제한(미지정 시 전체)",
    )
    detail_delay: float = Field(
        DEFAULT_CRAWL_REQUEST["detail_delay"],
        ge=0.0,
        description="상세 요청 간 딜레이(초)",
    )

    # OCR 관련 옵션
    ocr: bool = Field(
        DEFAULT_CRAWL_REQUEST["ocr"],
        description="이미지 기반 상세에 대해 OCR fallback 활성화 여부",
    )

    ocr_max_images: int = Field(
        DEFAULT_CRAWL_REQUEST["ocr_max_images"],
        ge=1,
        description="OCR 시 사용할 최대 이미지 개수",
    )

    # 파일 저장 관련(옵션)
    save_to_file: bool = Field(
        DEFAULT_CRAWL_REQUEST["save_to_file"],
        description="크롤링 결과를 로컬 JSON 파일로도 저장할지 여부",
    )


class JobKoreaCrawlRequest(BaseModel):
    """
    잡코리아 크롤러 옵션을 HTTP 요청으로 받기 위한 모델.

    사람인 크롤러와 동일하게:
    - 목록(list) 단계 옵션 + 상세(detail) 단계 옵션을 모두 지원한다.
    """

    url: str = Field(
        DEFAULT_JOBKOREA_CRAWL_REQUEST["url"],
        description="잡코리아 검색 URL(필터 포함). 생략 시 예시 검색 URL 사용",
    )
    pages: int = Field(
        DEFAULT_JOBKOREA_CRAWL_REQUEST["pages"],
        ge=1,
        description="목록(list) 페이지를 최대 몇 페이지까지 크롤링할지",
    )
    recruit_page_count: Optional[int] = Field(
        DEFAULT_JOBKOREA_CRAWL_REQUEST["recruit_page_count"],
        ge=1,
        description="페이지당 목록 개수(recruit_page_count). 1, 10, 20, 30, 40, 50, 80, 100 등. 미지정 시 URL 기존값 유지",
    )
    list_delay: float = Field(
        DEFAULT_JOBKOREA_CRAWL_REQUEST["list_delay"],
        ge=0.0,
        description="목록 요청 간 기본 딜레이(초)",
    )

    # 상세(detail) 관련 옵션
    detail: bool = Field(
        DEFAULT_JOBKOREA_CRAWL_REQUEST["detail"],
        description="상세 페이지까지 크롤링할지 여부",
    )
    detail_limit: Optional[int] = Field(
        DEFAULT_JOBKOREA_CRAWL_REQUEST["detail_limit"],
        ge=0,
        description="상세 크롤링 개수 제한(미지정 시 전체)",
    )
    detail_delay: float = Field(
        DEFAULT_JOBKOREA_CRAWL_REQUEST["detail_delay"],
        ge=0.0,
        description="상세 요청 간 딜레이(초)",
    )

    # OCR 관련 옵션
    ocr: bool = Field(
        DEFAULT_JOBKOREA_CRAWL_REQUEST["ocr"],
        description="이미지 기반 상세에 대해 OCR fallback 활성화 여부",
    )
    ocr_max_images: int = Field(
        DEFAULT_JOBKOREA_CRAWL_REQUEST["ocr_max_images"],
        ge=1,
        description="OCR 시 사용할 최대 이미지 개수",
    )

    # 파일 저장 관련(옵션)
    save_to_file: bool = Field(
        DEFAULT_JOBKOREA_CRAWL_REQUEST["save_to_file"],
        description="크롤링 결과를 로컬 JSON 파일로도 저장할지 여부",
    )
class DetailSections(BaseModel):
    """
    상세 섹션 구조.

    파서에서 동적으로 헤딩을 추출하므로 키는 가변적이지만
    자주 나오는 섹션들을 스키마에 명시하고, 그 외 키도 허용한다.
    """

    model_config = ConfigDict(
        extra="allow",
        json_schema_extra={
            "example": {
                "상세": "Flutter 앱 서비스 풀스택 개발자 (서비스 고도화 및 신규 개발)",
                "모집분야": "개발팀 0명",
                "주요업무": "모바일 앱 서비스 고도화 및 신규 기능 개발",
                "자격요건": "Flutter 개발 경력 3년 이상, Dart 언어 숙련",
                "우대사항": "클라우드 인프라 경험, 이커머스 서비스 경험",
                "고용형태": "정규직",
                "근무지": "서울 마포구",
                "급여": "면접 후 결정",
                "채용절차": "서류전형 → 1차면접 → 최종합격",
                "제출서류": "이력서 및 포트폴리오",
                "접수방법": "사람인 입사지원",
                "접수기간": "2026-02-06 ~ 2026-03-07",
                "안내사항": "입사지원 서류에 허위사실이 발견될 경우 채용이 취소될 수 있습니다.",
            }
        },
    )

    상세: Optional[str] = Field(default=None, description="상세 설명 전체 또는 요약")
    모집분야: Optional[str] = Field(default=None, description="모집 분야/포지션 정보")
    주요업무: Optional[str] = Field(default=None, description="주요 업무/담당 업무")
    담당업무: Optional[str] = Field(default=None, description="담당 업무(별도 항목으로 나올 때)")
    자격요건: Optional[str] = Field(default=None, description="지원 자격/요건")
    우대사항: Optional[str] = Field(default=None, description="우대 조건")
    고용형태: Optional[str] = Field(default=None, description="고용 형태(정규직, 계약직 등)")
    근무지: Optional[str] = Field(default=None, description="근무지 상세")
    급여: Optional[str] = Field(default=None, description="급여/연봉 정보")
    채용절차: Optional[str] = Field(default=None, description="채용 절차/전형 단계")
    제출서류: Optional[str] = Field(default=None, description="제출 서류")
    접수방법: Optional[str] = Field(default=None, description="지원/접수 방법")
    접수기간: Optional[str] = Field(default=None, description="접수/지원 기간")
    안내사항: Optional[str] = Field(default=None, description="기타 안내 사항")


class JobPostingResponse(BaseModel):
    """
    응답에서 사용하는 채용 공고 모델.

    dataclass(JobPosting)를 그대로 노출하면 스키마 생성이 불편하므로,
    동일한 필드를 갖는 Pydantic 모델을 별도로 정의한다.
    """

    model_config = ConfigDict(
        json_schema_extra={
            "example": {
                "title": "Flutter 앱 서비스 풀스택 개발자 (서비스 고도화 및 신규 개발)",
                "company": "(주)로운컴퍼니",
                "url": "https://www.saramin.co.kr/zf_user/jobs/relay/view?view_type=search&rec_idx=53024846&location=ts&searchType=search&paid_fl=n&search_uuid=fa9a1af7-a834-47f4-a94a-0ce7e3ad78ec",
                "location": "서울 마포구",
                "job_condition": "서울 마포구 경력3년↑ 학력무관 계약직",
                "sector": "백엔드/서버개발 , 앱개발 , 유지보수 , Git , API 외 수정일 26/02/06",
                "deadline": "~ 03/07(토)",
                "scraped_at": 1770353577.146989,
                "source_page": 1,
                "detail_iframe_url": "https://www.saramin.co.kr/zf_user/jobs/relay/view-detail?rec_idx=53024846&rec_seq=1&t_category=non-logged_relay_view&t_content=view_detail&t_ref=non-logged_relay_view&t_ref_content=SRI_050_VIEW_MTRX_RCT",
                "detail_sections": {
                    "상세": "WE'RE HIRING ...",
                    "모집분야": "개발팀 0명",
                    "주요업무": "모바일 앱 서비스 고도화 및 신규 개발",
                    "자격요건": "Flutter 개발 경력 3년 이상",
                    "안내사항": "입사지원 서류에 허위사실이 발견될 경우 채용이 취소될 수 있습니다.",
                },
                "detail_html": None,
            }
        }
    )

    # 목록(list) 단계 필드
    title: str
    company: str
    url: str
    location: Optional[str] = None
    job_condition: Optional[str] = None
    sector: Optional[str] = None
    deadline: Optional[str] = None
    scraped_at: float = 0.0
    source_page: Optional[int] = None

    # 상세(detail) 단계 필드
    detail_iframe_url: Optional[str] = None
    detail_sections: Optional[DetailSections] = Field(
        default=None,
        description="상세 섹션 정보(상세, 모집분야, 주요업무, 자격요건 등)",
    )
    detail_html: Optional[str] = None

    @classmethod
    def from_dataclass(cls, job: JobPosting) -> "JobPostingResponse":
        """
        dataclass(JobPosting) 인스턴스를 Pydantic 모델로 변환.
        """
        return cls(**asdict(job))


class CrawlResponse(BaseModel):
    """
    크롤링 결과 응답 모델.

    - source: 어떤 도메인(사람인/잡코리아 등)에서 수집됐는지
    - count: 이번 호출에서 새로 수집한 공고 개수
    - saved_to_file: 로컬 파일로 저장되었는지 여부
    - file_path: 저장된 파일 경로 (저장하지 않은 경우 None)
    - jobs: 수집된 채용 공고 리스트
    """
    source: str = Field(..., description="크롤링 소스 도메인 (예: 'saramin', 'jobkorea')")
    count: int = Field(..., description="이번 호출에서 수집된 공고 개수")
    saved_to_file: bool = Field(
        ...,
        description="로컬 JSON 파일 저장 여부",
    )
    file_path: Optional[str] = Field(
        None,
        description="파일 저장 시 실제 경로(미저장 시 None)",
    )
    jobs: List[JobPostingResponse] = Field(
        ...,
        description="크롤링된 채용 공고 목록",
    )


class JobsFileResponse(BaseModel):
    """
    이미 크롤링되어 JSON 파일로 저장된 데이터를 조회할 때 사용하는 응답 모델.

    사람인/잡코리아 모두 동일한 스키마를 사용한다.
    """

    model_config = ConfigDict(
        json_schema_extra={
            "example": {
                "count": 2,
                "file_path": "saramin_jobs.json",
                "jobs": [
                    JobPostingResponse.model_config["json_schema_extra"]["example"]
                ],
            }
        }
    )

    count: int = Field(..., description="파일에서 읽어온 공고 개수")
    file_path: str = Field(..., description="읽어온 JSON 파일 경로")
    jobs: List[JobPostingResponse] = Field(
        ..., description="파일에 저장된 채용 공고 목록"
    )


app = FastAPI(
    title="JobLens Crawler API",
    description="사람인 / 잡코리아 목록/상세 크롤러를 HTTP API 로 감싼 서비스입니다.",
    version="1.1.0",
)


@app.get("/health", tags=["system"])
def health_check() -> Dict[str, str]:
    """
    단순 헬스체크 엔드포인트.
    """
    return {"status": "ok"}

@app.post(
    "/crawl/saramin",
    response_model=CrawlResponse,
    tags=["saramin"],
    summary="사람인 목록/상세 크롤링 실행",
)
def run_crawl(req: CrawlRequest) -> CrawlResponse:
    """
    기존 CLI(main.py) 와 동일한 옵션으로 사람인 크롤러를 실행하는 엔드포인트.

    기본 흐름:
      1) `crawl_list` 로 목록 수집
      2) `detail` 옵션이 켜져 있으면 `enrich_jobs_with_details` 로 상세 수집
      3) 기존 JSON 파일에 있는 공고 URL 은 스킵(신규 공고만 추가)
      4) 필요 시 JSON 파일로 저장
      5) 결과를 바로 JSON 으로 반환
    """
    # 0) 기존 저장 파일에서 이미 수집된 공고 URL 로드 (중복 스킵용)
    existing_jobs = load_json("saramin_jobs.json")
    existing_urls = {j.get("url") for j in existing_jobs if j.get("url")}

    # 1) 목록 수집 (recruitPageCount 지정 시 URL에 반영)
    list_url = req.url
    if req.recruit_page_count is not None:
        list_url = with_recruit_page_count(req.url, req.recruit_page_count)
    jobs = crawl_list(
        base_url=list_url,
        pages=req.pages,
        delay=req.list_delay,
    )

    # 1-1) 이미 saramin_jobs.json에 있는 공고는 스킵
    jobs = [j for j in jobs if j.url not in existing_urls]

    # 1-2) pages / recruit_page_count / detail_limit 조합으로
    #      전체 응답 개수 상한을 계산한다.
    #      - recruit_page_count 가 주어지면: pages * recruit_page_count
    #      - detail_limit 이 주어지면: 위와 detail_limit 중 더 작은 값
    max_jobs: Optional[int] = None
    if req.recruit_page_count is not None and req.recruit_page_count > 0:
        max_jobs = req.pages * req.recruit_page_count
    if req.detail_limit is not None and req.detail_limit >= 0:
        max_jobs = min(max_jobs, req.detail_limit) if max_jobs is not None else req.detail_limit
    if max_jobs is not None:
        jobs = jobs[: max_jobs]

    # 2) 상세 수집(옵션)
    if req.detail:
        jobs = enrich_jobs_with_details(
            jobs,
            list_referer=list_url,
            delay=req.detail_delay,
            limit=req.detail_limit,
            debug=False,  # API 에서는 기본적으로 조용하게 동작
            ocr=req.ocr,
            ocr_max_images=req.ocr_max_images,
        )

    saved_to_file = False
    file_path: Optional[str] = None

    # 3) 파일 저장(옵션): 기존 공고 + 신규 공고 병합 저장
    if req.save_to_file:
        import json

        new_dicts = [asdict(j) for j in jobs]
        all_dicts = existing_jobs + new_dicts
        with open("saramin_jobs.json", "w", encoding="utf-8") as f:
            json.dump(all_dicts, f, ensure_ascii=False, indent=2)
        saved_to_file = True
        file_path = "saramin_jobs.json"

    # 4) 응답 변환
    jobs_resp = [JobPostingResponse.from_dataclass(j) for j in jobs]

    return CrawlResponse(
        source="saramin",
        count=len(jobs_resp),
        saved_to_file=saved_to_file,
        file_path=file_path,
        jobs=jobs_resp,
    )


@app.post(
    "/crawl/jobkorea",
    response_model=CrawlResponse,
    tags=["jobkorea"],
    summary="잡코리아 목록 크롤링 실행",
)
async def run_jobkorea_crawl(req: JobKoreaCrawlRequest) -> CrawlResponse:
    """
    잡코리아 목록 크롤러를 실행하는 엔드포인트.

    기본 흐름:
      1) `jk_crawl_list` 로 목록 수집
      2) 기존 JSON 파일에 있는 공고 URL 은 스킵(신규 공고만 추가)
      3) 필요 시 JSON 파일로 저장
      4) 결과를 바로 JSON 으로 반환
    """
    # 0) 기존 저장 파일에서 이미 수집된 공고 URL 로드 (중복 스킵용)
    existing_jobs = jk_load_json("jobkorea_jobs.json")
    existing_urls = {j.get("url") for j in existing_jobs if j.get("url")}

    # 1) 목록 수집 (recruit_page_count 지정 시 URL에 반영)
    list_url = req.url
    if req.recruit_page_count is not None:
        list_url = with_page_size(req.url, req.recruit_page_count)

    jobs = jk_crawl_list(
        base_url=list_url,
        pages=req.pages,
        delay=req.list_delay,
    )

    # 1-1) 이미 jobkorea_jobs.json 에 있는 공고는 스킵
    jobs = [j for j in jobs if j.url not in existing_urls]

    # 1-2) pages / recruit_page_count / detail_limit 조합으로
    #      전체 응답 개수 상한을 계산한다.
    max_jobs: Optional[int] = None
    if req.recruit_page_count is not None and req.recruit_page_count > 0:
        max_jobs = req.pages * req.recruit_page_count
    if req.detail_limit is not None and req.detail_limit >= 0:
        max_jobs = min(max_jobs, req.detail_limit) if max_jobs is not None else req.detail_limit
    if max_jobs is not None:
        jobs = jobs[: max_jobs]

    # 2) 상세 수집(옵션)
    if req.detail:
        jobs = jk_enrich_jobs_with_details(
            jobs,
            list_referer=list_url,
            delay=req.detail_delay,
            limit=req.detail_limit,
            debug=False,
            ocr=req.ocr,
            ocr_max_images=req.ocr_max_images,
        )

    saved_to_file = False
    file_path: Optional[str] = None

    # 3) 파일 저장(옵션): 기존 공고 + 신규 공고 병합 저장
    if req.save_to_file:
        import json

        new_dicts = [asdict(j) for j in jobs]
        all_dicts = existing_jobs + new_dicts
        with open("jobkorea_jobs.json", "w", encoding="utf-8") as f:
            json.dump(all_dicts, f, ensure_ascii=False, indent=2)
        saved_to_file = True
        file_path = "jobkorea_jobs.json"

    # 4) 응답 변환
    jobs_resp = [JobPostingResponse.from_dataclass(j) for j in jobs]

    return CrawlResponse(
        source="jobkorea",
        count=len(jobs_resp),
        saved_to_file=saved_to_file,
        file_path=file_path,
        jobs=jobs_resp,
    )


@app.get(
    "/jobs/saramin",
    response_model=JobsFileResponse,
    tags=["saramin"],
    summary="사람인 JSON 파일에서 공고 목록 조회",
)
def get_jobs() -> JobsFileResponse:
    """
    로컬에 저장된 사람인 크롤링 결과 JSON 파일을 읽어 공고 목록을 반환한다.
    """
    path = Path("saramin_jobs.json")
    if not path.is_file():
        raise HTTPException(status_code=404, detail=f"file not found: {path}")

    import json

    with path.open("r", encoding="utf-8") as f:
        data = json.load(f)

    # 저장 형식: 루트가 리스트이거나, { "jobs": [...] } 래퍼 객체
    if isinstance(data, list):
        raw_jobs = data
    elif isinstance(data, dict) and "jobs" in data:
        raw_jobs = data["jobs"]
        if not isinstance(raw_jobs, list):
            raise HTTPException(status_code=500, detail="invalid jobs json format: 'jobs' is not a list")
    else:
        raise HTTPException(status_code=500, detail="invalid jobs json format: expected list or object with 'jobs' key")

    jobs = [JobPostingResponse(**item) for item in raw_jobs]

    return JobsFileResponse(count=len(jobs), file_path=str(path), jobs=jobs)


@app.get(
    "/jobs/jobkorea",
    response_model=JobsFileResponse,
    tags=["jobkorea"],
    summary="잡코리아 JSON 파일에서 공고 목록 조회",
)
def get_jobkorea_jobs() -> JobsFileResponse:
    """
    로컬에 저장된 잡코리아 크롤링 결과 JSON 파일을 읽어 공고 목록을 반환한다.
    """
    path = Path("jobkorea_jobs.json")
    if not path.is_file():
        raise HTTPException(status_code=404, detail=f"file not found: {path}")

    import json

    with path.open("r", encoding="utf-8") as f:
        data = json.load(f)

    # 저장 형식: 루트가 리스트이거나, { "jobs": [...] } 래퍼 객체
    if isinstance(data, list):
        raw_jobs = data
    elif isinstance(data, dict) and "jobs" in data:
        raw_jobs = data["jobs"]
        if not isinstance(raw_jobs, list):
            raise HTTPException(status_code=500, detail="invalid jobs json format: 'jobs' is not a list")
    else:
        raise HTTPException(status_code=500, detail="invalid jobs json format: expected list or object with 'jobs' key")

    jobs = [JobPostingResponse(**item) for item in raw_jobs]

    return JobsFileResponse(count=len(jobs), file_path=str(path), jobs=jobs)


