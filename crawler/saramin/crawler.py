# -*- coding: utf-8 -*-
from __future__ import annotations

import json
import random
import time
from dataclasses import asdict
from pathlib import Path
from typing import Dict, List, Optional
from urllib.parse import urlparse

from .models import JobPosting
from .http import make_session, fetch_html
from .list_urls import build_paged_url
from .list_parser import parse_list_page
from .detail_fetcher import fetch_detail_iframe_html
from .detail_parser import parse_detail_sections, split_text_by_headings
from .ocr_image_parser import extract_image_urls, looks_like_image_only_detail, ocr_images_to_text


def crawl_list(base_url: str, pages: int, delay: float) -> List[JobPosting]:
    """
    리스트 페이지(채용 목록)를 페이지 단위로 순회하며 JobPosting 리스트를 수집한다.

    - base_url: 목록 첫 페이지 URL
    - pages: 최대 몇 페이지까지 돌지
    - delay: 각 페이지 요청 사이 기본 대기(랜덤 지연 추가됨)
    """
    # base_url에서 scheme/netloc만 추출해 origin 구성 (예: https://www.saramin.co.kr)
    u = urlparse(base_url)
    base_origin = f"{u.scheme}://{u.netloc}"

    # 공통 세션 생성(쿠키/헤더 유지)
    session = make_session()
    # 일부 사이트는 Referer가 없으면 차단/리다이렉트 발생 가능 → origin을 referer로 고정
    session.headers.update({"Referer": base_origin})

    all_jobs: List[JobPosting] = []
    for page in range(1, pages + 1):
        # 페이지 번호에 맞는 URL 생성
        url = build_paged_url(base_url, page)

        # HTML 다운로드
        html = fetch_html(session, url)

        # 목록 페이지 파싱 → JobPosting 리스트 반환
        # base_origin: 상대경로 URL을 절대경로로 만들 때 사용
        # page: 디버깅/추적용 메타
        jobs = parse_list_page(html, base_origin=base_origin, page=page)

        # 더 이상 채용 공고가 없으면(빈 리스트) 종료
        if not jobs:
            break

        # 누적 저장
        all_jobs.extend(jobs)

        # 사이트 부하/차단 회피용 지연(기본 delay + 0~0.6s)
        time.sleep(delay + random.uniform(0.0, 0.6))

    # URL 기준으로 중복 제거 (마지막에 들어온 데이터로 덮어씀)
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
    각 JobPosting에 대해 상세 페이지(iframe)를 가져와 섹션별 텍스트를 채운다.

    핵심 흐름:
    1) fetch_detail_iframe_html 로 iframe_url + detail_html 획득
    2) parse_detail_sections 로 섹션 파싱(정상 케이스)
    3) 섹션이 비었거나, 이미지 위주 상세면 OCR fallback 실행
    4) OCR 결과를 헤딩 기반으로 다시 섹션화(split_text_by_headings)
    5) 결과를 job.detail_sections에 저장

    파라미터:
    - list_referer: 상세 접근 시 서버가 요구하는 referer(목록 페이지 URL 등)
    - limit: 상위 N개까지만 상세 enrich 수행 (나머지는 그대로 out에 담아 반환)
    - debug: 진행 중 로그 출력
    - ocr: OCR fallback 사용 여부
    - ocr_max_images: OCR에 사용할 이미지 최대 개수(비용/시간 제한)
    """
    session = make_session()

    # limit이 지정되었고(>=0) jobs 길이보다 작으면 그만큼만 처리
    target_count = min(limit, len(jobs)) if (limit is not None and limit >= 0) else len(jobs)

    out: List[JobPosting] = []
    for idx, job in enumerate(jobs, start=1):
        # target_count를 초과하면 상세 enrich는 스킵하고 그대로 반환 목록에 포함
        if idx > target_count:
            out.append(job)
            continue

        try:
            # 상세 iframe HTML을 가져옴
            # - list_referer는 일부 사이트에서 필수(보안/봇 방지)
            iframe_url, detail_html = fetch_detail_iframe_html(
                session,
                job_url=job.url,
                list_referer=list_referer,
            )

            # 모델에 iframe URL 저장
            job.detail_iframe_url = iframe_url

            # 1) 기본 파싱: HTML에서 섹션(예: "자격요건", "담당업무" 등) 추출
            sections = parse_detail_sections(detail_html)

            # 2) OCR 트리거 판단
            need_ocr = False
            if ocr:
                # 섹션 자체가 비어있으면 파싱 실패 가능성 → OCR 후보
                if not sections:
                    need_ocr = True
                # HTML 구조가 이미지로만 구성된 상세 같으면 OCR 후보
                elif looks_like_image_only_detail(detail_html):
                    need_ocr = True
                # 섹션이 "상세" 하나뿐인데 내용이 너무 짧으면(예: placeholder) OCR 후보
                elif (len(sections) == 1 and "상세" in sections and len((sections["상세"] or "").strip()) < 80):
                    need_ocr = True

            # 3) OCR fallback 실행
            if need_ocr:
                # iframe HTML 내 이미지 URL 추출
                # base_url=iframe_url: 상대경로 이미지 URL을 절대경로로 만들 때 사용
                img_urls = extract_image_urls(detail_html, base_url=iframe_url)

                # 이미지들을 OCR 돌려 텍스트화
                # referer=job.url: 이미지 리소스 접근 시 referer 체크하는 경우 대응
                ocr_result = ocr_images_to_text(
                    session,
                    img_urls,
                    referer=job.url,
                    lang="kor+eng",
                    max_images=ocr_max_images,
                )

                if ocr_result.text:
                    # OCR 텍스트를 느슨한 헤딩 분리로 다시 섹션화
                    # - 성공하면 ocr_sections 사용
                    # - 분리 실패하면 {"상세": 전체텍스트}로 폴백
                    ocr_sections = split_text_by_headings(ocr_result.text)
                    sections = ocr_sections if ocr_sections else {"상세": ocr_result.text}
                else:
                    # OCR이 실패했거나 텍스트가 없을 때
                    # 기존 sections가 있으면 유지, 없으면 dict로 초기화
                    sections = sections or {}

                    # 실패 원인 기록(추적용)
                    sections["_ocr_error"] = ocr_result.error or "OCR 실패(원인 미상)"

                    # 실제 OCR에 사용된 이미지가 있으면 함께 기록
                    if ocr_result.used_images:
                        sections["_ocr_images"] = "\n".join(ocr_result.used_images)

            # 최종 섹션 결과 저장
            job.detail_sections = sections

            # 디버그 로그
            if debug:
                print("job_url:", job.url)
                print("iframe_url:", iframe_url)
                print("sections_keys:", list((job.detail_sections or {}).keys()))
                if need_ocr:
                    # 이미지 개수는 extract_image_urls를 다시 호출하므로 비용이 약간 늘어남(현재 코드는 유지)
                    print("need_ocr=True, images:", min(len(extract_image_urls(detail_html, iframe_url)), ocr_max_images))

        except Exception as e:
            # 상세 처리 중 예외 발생 시:
            # - iframe_url은 None 처리
            # - detail_sections에 에러 메시지를 기록
            job.detail_iframe_url = None
            job.detail_sections = {"_error": str(e)}
            if debug:
                print(f"[DETAIL_ERROR] idx={idx} url={job.url} err={e}")

        out.append(job)

        # 요청 간 지연(기본 delay + 0~0.5s)
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
    - dataclasses.asdict로 직렬화 가능한 dict로 변환 후 dump
    """
    # dataclass -> dict 변환(중첩도 재귀 변환)
    payload = [asdict(j) for j in jobs]

    # ensure_ascii=False: 한글 깨짐 방지
    # indent=2: 사람이 읽기 좋은 포맷
    with open(path, "w", encoding="utf-8") as f:
        json.dump(payload, f, ensure_ascii=False, indent=2)
