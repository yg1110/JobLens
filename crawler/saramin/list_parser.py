# -*- coding: utf-8 -*-
from __future__ import annotations

import time
from typing import Any, Dict, List, Optional
from urllib.parse import urljoin

from bs4 import BeautifulSoup

from .models import JobPosting


def _pick_text(node) -> Optional[str]:
    """
    BeautifulSoup 노드에서 텍스트를 안전하게 추출한다.

    - node가 None이면 None 반환
    - get_text(" ", strip=True)로 내부 텍스트를 공백 기준으로 이어붙임
      (줄바꿈/중복 공백 문제 완화)
    - 최종 텍스트가 비어 있으면 None 반환

    반환:
    - str 또는 None
    """
    if not node:
        return None
    txt = node.get_text(" ", strip=True)
    return txt if txt else None


def _first_match_select(item: BeautifulSoup, selectors: List[str]):
    """
    여러 CSS selector 후보를 순서대로 적용하여
    '처음 매칭되는 노드'를 반환한다.

    목적:
    - 사이트 템플릿이 A/B 테스트 등으로 바뀌어도
      후보 selector를 여러 개 두면 파싱 내구성이 올라감.
    """
    for sel in selectors:
        found = item.select_one(sel)
        if found:
            return found
    return None


def _normalize_url(base_origin: str, href: str) -> str:
    """
    상대 URL(href)을 절대 URL로 정규화한다.

    - href가 비어 있으면 "" 반환
    - urljoin을 사용해 base_origin + href 결합
      (href가 이미 절대 URL이면 그대로 유지됨)

    예:
    base_origin = "https://www.saramin.co.kr"
    href = "/zf_user/jobs/relay/view?rec_idx=..."
    => "https://www.saramin.co.kr/zf_user/jobs/relay/view?rec_idx=..."
    """
    if not href:
        return ""
    return urljoin(base_origin, href)


def find_list_items(soup: BeautifulSoup) -> List[Any]:
    """
    목록 페이지에서 "공고 아이템" 노드들을 찾아 반환한다.

    우선순위:
    1) div 기반 리스트 컨테이너(템플릿에 따라 class가 다를 수 있어 여러 후보 시도)
       - 최소 3개 이상일 때만 '목록 구조로 확신'하고 반환
         (1~2개면 광고/추천/유사 블록일 가능성이 있어 방지)
    2) fallback: 공고 URL 패턴을 가진 a 태그들
       - div 기반 구조 탐지가 실패한 경우라도 최소한 링크라도 수집 가능

    반환:
    - 아이템 노드 리스트 (div.item... 또는 a[href*='/zf_user/jobs/'])
    """
    # div 기반 공고 리스트 탐색(사이트 레이아웃 변화 대응)
    for sel in ["div.item_recruit", "div.list_item", "div.item", "div.content > div.item"]:
        items = soup.select(sel)
        # 일정 개수 이상이면 실제 리스트일 가능성이 높음
        if items and len(items) >= 3:
            return items

    # fallback: 공고 URL 패턴을 가진 링크들(정확도는 낮을 수 있음)
    return soup.select("a[href*='/zf_user/jobs/']")


def parse_list_page(html: str, base_origin: str, page: int) -> List[JobPosting]:
    """
    목록 페이지 HTML을 파싱하여 JobPosting 리스트로 변환한다.

    처리 흐름:
    1) BeautifulSoup 파싱
    2) find_list_items로 "아이템 노드" 후보를 가져옴
    3) 각 아이템에서:
       - 공고 제목/링크 추출 (title_a)
       - 회사명 추출 (company_a)
       - 부가 정보(location/job_condition/sector/deadline) 추출
    4) title/url이 없으면 스킵
    5) url 기준 중복 제거 후 반환

    파라미터:
    - base_origin: 상대 링크를 절대 링크로 만들 때 사용
    - page: 몇 페이지에서 수집됐는지 기록(source_page)
    """
    soup = BeautifulSoup(html, "lxml")

    # 목록 아이템 노드 탐색
    item_nodes = find_list_items(soup)

    jobs: List[JobPosting] = []
    for item in item_nodes:
        # ---------------------------------------------------------
        # 1) 제목 링크(title_a) 노드 찾기
        # ---------------------------------------------------------
        # item 자체가 <a>로 넘어오는 fallback 케이스면 그대로 사용
        if getattr(item, "name", "") == "a":
            title_a = item
        else:
            # div 기반 아이템이면 여러 후보 selector로 제목 링크를 탐색
            title_a = _first_match_select(
                item,
                [
                    "h2.job_tit a",
                    "a.str_tit",
                    "div.area_job h2 a",
                    "div.job_tit a",
                    "a[href*='/zf_user/jobs/']",
                ],
            )

        # 제목 텍스트 추출 (없으면 빈 문자열)
        title = _pick_text(title_a) or ""

        # href 추출 후 절대 URL로 정규화
        href = title_a.get("href") if title_a else ""
        url = _normalize_url(base_origin, href)

        # ---------------------------------------------------------
        # 2) 회사명(company) 추출
        # ---------------------------------------------------------
        company_a = None

        # item이 <a>인 fallback 케이스에서는 회사명을 별도로 찾기 어렵기 때문에 스킵
        if getattr(item, "name", "") != "a":
            company_a = _first_match_select(
                item,
                [
                    "strong.corp_name a",
                    "div.area_corp strong.corp_name a",
                    "div.area_corp strong a",
                    "div.corp_name a",
                ],
            )

        company = _pick_text(company_a) or ""

        # ---------------------------------------------------------
        # 3) 부가 메타 정보들(근무조건/직무분야/마감/지역) 추출
        # ---------------------------------------------------------
        location = None
        job_condition = None
        sector = None
        deadline = None

        # div 기반 아이템에서만 가능한 정보(구조가 있어야 선택 가능)
        if getattr(item, "name", "") != "a":
            # 근무조건(경력/학력/고용형태 등 텍스트가 섞여 있을 수 있음)
            job_condition = _pick_text(_first_match_select(item, ["div.job_condition", "div.job_condition span"]))

            # 직무/산업 분야
            sector = _pick_text(_first_match_select(item, ["div.job_sector", "div.job_sector a"]))

            # 마감일/등록일 등
            deadline = _pick_text(_first_match_select(item, ["div.job_date span.date", "div.job_date", "span.date"]))

            # 근무지(템플릿에 따라 별도 span이 있거나 job_condition의 첫 span인 경우도 있음)
            location = _pick_text(_first_match_select(item, ["span.work_place", "div.job_condition span:nth-of-type(1)"]))

        # ---------------------------------------------------------
        # 4) 필수 값 검증: title/url 없으면 버림
        # ---------------------------------------------------------
        if not title or not url:
            continue

        # ---------------------------------------------------------
        # 5) JobPosting 객체로 적재
        # ---------------------------------------------------------
        jobs.append(
            JobPosting(
                title=title,
                company=company,
                url=url,
                location=location,
                job_condition=job_condition,
                sector=sector,
                deadline=deadline,
                scraped_at=time.time(),  # 수집 시각(Unix timestamp)
                source_page=page,        # 어떤 페이지에서 수집됐는지
            )
        )

    # -------------------------------------------------------------
    # 6) URL 기준 중복 제거
    # - 동일 공고가 여러 블록/추천 영역에 중복 노출될 수 있음
    # - 마지막에 들어온 값을 유지(덮어쓰기)
    # -------------------------------------------------------------
    uniq: Dict[str, JobPosting] = {}
    for j in jobs:
        uniq[j.url] = j

    return list(uniq.values())
