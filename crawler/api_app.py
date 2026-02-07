from __future__ import annotations

from dataclasses import asdict
from pathlib import Path
from typing import Dict, List, Optional

from fastapi import FastAPI, HTTPException, Query
from pydantic import BaseModel, Field, ConfigDict

from saramin.crawler import crawl_list, enrich_jobs_with_details
from saramin.list_urls import with_recruit_page_count
from saramin.models import JobPosting

from main import DEFAULT_URL


class CrawlRequest(BaseModel):
    """
    사람인 크롤러 옵션을 HTTP 요청으로 받기 위한 모델.

    CLI 의 인자(main.py 의 argparse)와 거의 동일한 필드 구성을 따른다.
    """

    url: str = Field(
        DEFAULT_URL,
        description="사람인 검색 URL(필터 포함). 생략 시 기본 필터 URL 사용",
    )
    pages: int = Field(
        1,
        ge=1,
        description="목록(list) 페이지를 최대 몇 페이지까지 크롤링할지",
    )
    recruit_page_count: Optional[int] = Field(
        None,
        ge=1,
        description="페이지당 목록 개수(recruitPageCount). 1, 10, 20, 30, 40, 50, 80, 100 등. 미지정 시 URL 기존값 유지",
    )
    list_delay: float = Field(
        1.8,
        ge=0.0,
        description="목록 요청 간 기본 딜레이(초)",
    )
    # 상세(detail) 관련 옵션
    detail: bool = Field(
        False,
        description="상세(view-ajax → iframe)까지 크롤링할지 여부",
    )
    detail_limit: Optional[int] = Field(
        None,
        ge=0,
        description="상세 크롤링 개수 제한(미지정 시 전체)",
    )
    detail_delay: float = Field(
        1.2,
        ge=0.0,
        description="상세 요청 간 딜레이(초)",
    )
    save_detail_html: bool = Field(
        False,
        description="상세 원문 HTML을 결과에 포함할지 여부(파일/응답 크기 증가)",
    )

    # OCR 관련 옵션
    ocr: bool = Field(
        False,
        description="이미지 기반 상세에 대해 OCR fallback 활성화 여부",
    )
    ocr_lang: str = Field(
        "kor+eng",
        description="Tesseract OCR 언어 설정",
    )
    ocr_max_images: int = Field(
        5,
        ge=1,
        description="OCR 시 사용할 최대 이미지 개수",
    )

    # 파일 저장 관련(옵션)
    save_to_file: bool = Field(
        False,
        description="크롤링 결과를 로컬 JSON 파일로도 저장할지 여부",
    )
    out: str = Field(
        "saramin_jobs.json",
        description="파일 저장 시 사용할 파일명",
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
    """
    count: int = Field(..., description="수집된 공고 개수")
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
    """

    model_config = ConfigDict(
        json_schema_extra={
            "example": {
                "count": 2,
                "file_path": "jobs.json",
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
    title="Saramin Crawler API",
    description="사람인 목록/상세 크롤러를 HTTP API 로 감싼 서비스입니다.",
    version="1.0.0",
)


@app.get("/health", tags=["system"])
def health_check() -> Dict[str, str]:
    """
    단순 헬스체크 엔드포인트.
    """
    return {"status": "ok"}

@app.post(
    "/crawl",
    response_model=CrawlResponse,
    tags=["crawl"],
    summary="사람인 목록/상세 크롤링 실행",
)
def run_crawl(req: CrawlRequest) -> CrawlResponse:
    """
    기존 CLI(main.py) 와 동일한 옵션으로 사람인 크롤러를 실행하는 엔드포인트.

    - 기본 흐름:
      1) `crawl_list` 로 목록 수집
      2) `detail` 옵션이 켜져 있으면 `enrich_jobs_with_details` 로 상세 수집
      3) 필요 시 JSON 파일로 저장
      4) 결과를 바로 JSON 으로 반환
    """
    # 1) 목록 수집 (recruitPageCount 지정 시 URL에 반영)
    list_url = req.url
    if req.recruit_page_count is not None:
        list_url = with_recruit_page_count(req.url, req.recruit_page_count)
    jobs = crawl_list(
        base_url=list_url,
        pages=req.pages,
        delay=req.list_delay,
    )

    # 2) 상세 수집(옵션)
    if req.detail:
        jobs = enrich_jobs_with_details(
            jobs,
            list_referer=list_url,
            delay=req.detail_delay,
            save_detail_html=req.save_detail_html,
            limit=req.detail_limit,
            debug=False,  # API 에서는 기본적으로 조용하게 동작
            ocr=req.ocr,
            ocr_lang=req.ocr_lang,
            ocr_max_images=req.ocr_max_images,
        )

    saved_to_file = False
    file_path: Optional[str] = None

    # 3) 파일 저장(옵션)
    if req.save_to_file:
        from saramin.crawler import save_json

        save_json(req.out, jobs)
        saved_to_file = True
        file_path = req.out

    # 4) 응답 변환
    jobs_resp = [JobPostingResponse.from_dataclass(j) for j in jobs]

    return CrawlResponse(
        count=len(jobs_resp),
        saved_to_file=saved_to_file,
        file_path=file_path,
        jobs=jobs_resp,
    )


@app.get(
    "/jobs",
    response_model=JobsFileResponse,
    tags=["crawl"],
    summary="이미 크롤링된 JSON 파일에서 공고 목록 조회",
)
def get_jobs(
    file: str = Query(
        "saramin_jobs.json",
        description="조회할 JSON 파일 경로(기본: saramin_jobs.json)",
    ),
) -> JobsFileResponse:
    """
    로컬에 저장된 크롤링 결과 JSON 파일을 읽어 공고 목록을 반환한다.

    - 기본 파일명은 `saramin_jobs.json`
    - 저장 위치를 바꾼 경우 `?file=경로` 로 지정
    """
    path = Path(file)
    if not path.is_file():
        raise HTTPException(status_code=404, detail=f"file not found: {path}")

    import json

    with path.open("r", encoding="utf-8") as f:
        data = json.load(f)

    # data는 JobPosting dict 리스트라고 가정
    if not isinstance(data, list):
        raise HTTPException(status_code=500, detail="invalid jobs json format")

    jobs = [JobPostingResponse(**item) for item in data]

    return JobsFileResponse(count=len(jobs), file_path=str(path), jobs=jobs)


