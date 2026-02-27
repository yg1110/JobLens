from __future__ import annotations

import json
import random
import time
from dataclasses import asdict
from pathlib import Path
from typing import Any, Dict, List, Optional
from urllib.parse import parse_qs, urlparse, urlunparse

from .http import make_session, fetch_html, post_json
from .list_urls import build_paged_url
from .list_parser import parse_list_page
from .models import JobPosting
from .detail_fetcher import fetch_detail_with_url
from saramin.detail_parser import parse_detail_sections, split_text_by_headings
from saramin.ocr_image_parser import (
    extract_image_urls,
    looks_like_image_only_detail,
    ocr_images_to_text,
)


def _build_api_url(base_url: str) -> str:
    """
    검색 페이지 URL 을 기반으로 JobKorea 목록 API URL 을 생성한다.

    예:
    - base_url: https://www.jobkorea.co.kr/Search/?stext=...&Page_No=1&...
    - api_url:  https://www.jobkorea.co.kr/Search/api/display/v2/jobs
    """
    u = urlparse(base_url)
    return urlunparse((u.scheme, u.netloc, "/Search/api/display/v2/jobs", "", "", ""))


def _build_api_payload(base_url: str, page: int) -> Dict[str, Any]:
    """
    검색 페이지 URL 의 쿼리 파라미터를 기반으로 목록 API 에 보낼 JSON payload 를 구성한다.

    실제 필드 구조는 JobKorea 내부 구현에 따라 달라질 수 있으므로,
    여기서는 대표적인 검색 파라미터들을 그대로 JSON 으로 전달한다.
    서버가 쿼리스트링/쿠키/헤더를 함께 참고해 동작하는 구조라면,
    이 payload 는 최소한의 힌트 역할을 한다.
    """
    u = urlparse(base_url)
    qs = parse_qs(u.query, keep_blank_values=True)

    def first(key: str, default: str = "") -> str:
        vals = qs.get(key)
        return vals[0] if vals else default

    payload: Dict[str, Any] = {
        "stext": first("stext"),
        "tabType": first("tabType", "recruit"),
        "ord": first("Ord", "ApplyCloseDtAsc"),
        "pageNo": page,
        "pageSize": int(first("Page_Size", "20")) if first("Page_Size") else 20,
        "duty": first("duty"),
        "jobtype": first("jobtype"),
        "excludeText": first("excludeText"),
    }
    return payload


def _parse_jobs_from_api_response(
    data: Any,
    *,
    base_origin: str,
    page: int,
) -> List[JobPosting]:
    """
    JobKorea 목록 API JSON 응답에서 JobPosting 리스트를 구성한다.

    실제 응답 구조가 변경될 수 있으므로,
    - 최상위 dict 아래에서 "jobs", "JobList", "Items" 같은 리스트 필드를 탐색하고
    - 각 item 에 대해 여러 후보 키(title/company/url 등)를 순차적으로 시도한다.
    """
    if not isinstance(data, dict):
        return []

    # 후보 리스트 필드 이름들
    candidate_keys = ["jobs", "jobList", "JobList", "items", "Items"]
    job_items: Optional[List[Dict[str, Any]]] = None

    for key in candidate_keys:
        value = data.get(key)
        if isinstance(value, list) and value and isinstance(value[0], dict):
            job_items = value  # type: ignore[assignment]
            break

    # 최상위에서 찾지 못한 경우, 한 단계 더 내려가서 탐색
    if job_items is None:
        for v in data.values():
            if isinstance(v, dict):
                for key in candidate_keys:
                    value = v.get(key)
                    if isinstance(value, list) and value and isinstance(value[0], dict):
                        job_items = value  # type: ignore[assignment]
                        break
            if job_items is not None:
                break

    if not job_items:
        return []

    jobs: List[JobPosting] = []
    for item in job_items:
        # 제목
        title = (
            item.get("recruitTitle")
            or item.get("title")
            or item.get("jobTitle")
            or ""
        )

        # 회사명
        company = (
            item.get("companyName")
            or item.get("company")
            or item.get("corpName")
            or ""
        )

        # 상세 URL (상대/절대 모두 허용)
        href = (
            item.get("linkUrl")
            or item.get("url")
            or item.get("recruitUrl")
            or ""
        )
        if href.startswith("http"):
            url = href
        else:
            # 상대경로일 경우 origin 기준으로 보정
            from urllib.parse import urljoin

            url = urljoin(base_origin, href)

        # 부가 정보들
        location = (
            item.get("location")
            or item.get("workArea")
            or item.get("workPlace")
        )
        job_condition = (
            item.get("condition")
            or item.get("jobCondition")
            or item.get("requirement")
        )
        sector = (
            item.get("jobType")
            or item.get("jobCategory")
            or item.get("bizType")
        )
        deadline = (
            item.get("closeDate")
            or item.get("closeDt")
            or item.get("applyEndDt")
        )

        if not title or not url:
            continue

        jobs.append(
            JobPosting(
                title=title,
                company=company,
                url=url,
                location=location,
                job_condition=job_condition,
                sector=sector,
                deadline=deadline,
                scraped_at=time.time(),
                source_page=page,
            )
        )

    return jobs


def crawl_list(base_url: str, pages: int, delay: float) -> List[JobPosting]:
    """
    JobKorea 목록 페이지를 페이지 단위로 순회하며 JobPosting 리스트를 수집한다.

    - base_url: JobKorea 검색/목록 첫 페이지 URL
    - pages: 최대 몇 페이지까지 돌지
    - delay: 각 페이지 요청 사이 기본 대기(랜덤 지연 추가됨)

    구현 우선순위:
    1) JobKorea 내부 목록 API(`/Search/api/display/v2/jobs`)를 활용해 JSON 기반으로 수집을 시도한다.
    2) API 응답 구조 변경/차단 등으로 실패할 경우, 기존 HTML 파싱 방식으로 폴백한다.
    """
    u = urlparse(base_url)
    base_origin = f"{u.scheme}://{u.netloc}"

    # URL 쿼리에서 Page_Size 가 지정된 경우, 한 페이지당 최대 개수를 강제한다.
    # (JobKorea 내부 API가 pageSize를 무시하더라도 여기서 잘라 준다.)
    from urllib.parse import parse_qs

    qs = parse_qs(u.query, keep_blank_values=True)
    page_size_limit: Optional[int] = None
    vals = qs.get("Page_Size")
    if vals:
        try:
            page_size_limit = int(vals[0])
        except (TypeError, ValueError):
            page_size_limit = None

    session = make_session()
    session.headers.update({"Referer": base_origin})

    api_url = _build_api_url(base_url)

    all_jobs: List[JobPosting] = []
    for page in range(1, pages + 1):
        jobs: List[JobPosting] = []

        # 1) API 기반 수집 시도
        try:
            payload = _build_api_payload(base_url, page)
            api_response_text = post_json(
                session,
                api_url,
                json_data=payload,
                headers={
                    "Origin": base_origin,
                    "Referer": base_url,
                },
            )
            data = json.loads(api_response_text)
            jobs = _parse_jobs_from_api_response(
                data,
                base_origin=base_origin,
                page=page,
            )
        except Exception:
            jobs = []

        # 2) API 결과가 없으면 HTML 파싱으로 폴백
        if not jobs:
            url = build_paged_url(base_url, page)
            html = fetch_html(session, url)
            jobs = parse_list_page(html, base_origin=base_origin, page=page)

        # Page_Size 가 지정되어 있으면 한 페이지당 개수 제한
        if page_size_limit is not None and jobs:
            jobs = jobs[:page_size_limit]

        if not jobs:
            break

        all_jobs.extend(jobs)

        # 사이트 부하/차단 회피용 지연(기본 delay + 0~0.6s)
        time.sleep(delay + random.uniform(0.0, 0.6))

    # URL 기준 중복 제거
    uniq: Dict[str, JobPosting] = {}
    for j in all_jobs:
        uniq[j.url] = j

    return list(uniq.values())


def enrich_jobs_with_details(
    jobs: List[JobPosting],
    *,
    list_referer: str,
    delay: float = 1.2,
    limit: Optional[int] = None,
    debug: bool = False,
    ocr: bool = True,
    ocr_max_images: int = 5,
) -> List[JobPosting]:
    """
    JobKorea 상세 페이지를 추가 조회해 JobPosting 을 enrich 하는 함수의 확장 포인트.

    기본 흐름(사람인과 유사):
    1) fetch_detail_with_url 로 상세 HTML 획득
    2) parse_detail_sections 로 섹션 파싱(정상 케이스)
    3) 섹션이 비었거나, 이미지 위주 상세면 OCR fallback 실행
    4) OCR 결과를 헤딩 기반으로 다시 섹션화(split_text_by_headings)
    5) 결과를 job.detail_sections 에 저장

    - list_referer: 상세 접근 시 Referer 로 사용할 목록 URL
    - limit: 상위 N개까지만 상세 enrich 수행(나머지는 목록 정보만 유지)
    - delay: 상세 요청 간 기본 지연(초)
    - debug: 진행 로그 출력 여부
    - ocr: OCR fallback 사용 여부
    - ocr_max_images: OCR 에 사용할 이미지 최대 개수
    """
    session = make_session()

    target_count = min(limit, len(jobs)) if (limit is not None and limit >= 0) else len(jobs)

    out: List[JobPosting] = []
    for idx, job in enumerate(jobs, start=1):
        if idx > target_count:
            out.append(job)
            continue

        try:
            # JobKorea 는 iframe 없이 상세 페이지를 바로 요청하는 구조라
            # fetch_detail_with_url 이 (job_url, detail_html) 을 그대로 반환한다.
            detail_url, detail_html = fetch_detail_with_url(
                session,
                job_url=job.url,
                list_referer=list_referer,
            )

            # Saramin 모델과 동일 필드를 사용하므로 detail_iframe_url 에도 상세 URL 저장
            job.detail_iframe_url = detail_url

            # 1) 기본 파싱: HTML 에서 섹션(예: "자격요건", "담당업무" 등) 추출
            sections = parse_detail_sections(detail_html)

            # 2) OCR 트리거 판단
            need_ocr = False
            if ocr:
                if not sections:
                    need_ocr = True
                elif looks_like_image_only_detail(detail_html):
                    need_ocr = True
                elif (
                    len(sections) == 1
                    and "상세" in sections
                    and len((sections["상세"] or "").strip()) < 80
                ):
                    need_ocr = True

            # 3) OCR fallback 실행
            if need_ocr:
                img_urls = extract_image_urls(detail_html, base_url=detail_url)

                ocr_result = ocr_images_to_text(
                    session,
                    img_urls,
                    referer=job.url,
                    lang="kor+eng",
                    max_images=ocr_max_images,
                )

                if ocr_result.text:
                    ocr_sections = split_text_by_headings(ocr_result.text)
                    sections = ocr_sections if ocr_sections else {"상세": ocr_result.text}
                else:
                    sections = sections or {}
                    sections["_ocr_error"] = ocr_result.error or "OCR 실패(원인 미상)"
                    if ocr_result.used_images:
                        sections["_ocr_images"] = "\n".join(ocr_result.used_images)

            job.detail_sections = sections

            if debug:
                print("job_url:", job.url)
                print("detail_url:", detail_url)
                print("sections_keys:", list((job.detail_sections or {}).keys()))
                if need_ocr:
                    from saramin.ocr_image_parser import extract_image_urls as _extract

                    print(
                        "need_ocr=True, images:",
                        min(len(_extract(detail_html, detail_url)), ocr_max_images),
                    )

        except Exception as e:
            job.detail_iframe_url = None
            job.detail_sections = {"_error": str(e)}
            if debug:
                print(f"[JOBKOREA_DETAIL_ERROR] idx={idx} url={job.url} err={e}")

        out.append(job)
        time.sleep(delay + random.uniform(0.0, 0.5))

    return out


def load_json(path: str) -> List[Dict]:
    """
    JSON 파일에서 채용 공고 목록을 로드한다.
    - 파일이 없거나 형식이 잘못된 경우 빈 리스트 반환
    """
    p = Path(path)
    if not p.is_file():
        return []
    try:
        with p.open("r", encoding="utf-8") as f:
            data = json.load(f)
        if not isinstance(data, list):
            return []
        return data
    except (json.JSONDecodeError, OSError):
        return []


def save_json(path: str, jobs: List[JobPosting]) -> None:
    """
    JobPosting 리스트를 JSON 파일로 저장한다.
    - dataclasses.asdict 로 직렬화 가능한 dict 로 변환 후 dump
    """
    payload = [asdict(j) for j in jobs]
    with open(path, "w", encoding="utf-8") as f:
        json.dump(payload, f, ensure_ascii=False, indent=2)

