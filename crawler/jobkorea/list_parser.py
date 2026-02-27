from __future__ import annotations

import time
from typing import Any, Dict, List, Optional
from urllib.parse import urljoin

from bs4 import BeautifulSoup

from .models import JobPosting


def _pick_text(node) -> Optional[str]:
    """
    BeautifulSoup 노드에서 텍스트를 안전하게 추출한다.
    """
    if not node:
        return None
    txt = node.get_text(" ", strip=True)
    return txt if txt else None


def _normalize_url(base_origin: str, href: str) -> str:
    """
    상대 URL(href)을 절대 URL로 정규화한다.
    """
    if not href:
        return ""
    return urljoin(base_origin, href)


def find_list_items(soup: BeautifulSoup) -> List[Any]:
    """
    JobKorea 목록 페이지에서 "공고 아이템" 노드들을 찾아 반환한다.

    JobKorea의 HTML 구조는 검색 타입/정렬/시간에 따라 바뀌는 경향이 있어
    여러 후보 selector 를 순차적으로 시도한다.
    """
    # 기본 카드형/리스트형 템플릿
    for sel in [
        "div.list-default > ul > li",  # 일반 검색 결과
        "div#dev-gi-list > div",       # 일부 템플릿에서 사용
        "table.lst > tbody > tr",      # 구형 테이블 기반 리스트
    ]:
        items = soup.select(sel)
        if items:
            return items

    # fallback: JobKorea 공고 상세 URL 패턴을 가진 링크들
    return soup.select("a[href*='/Recruit/GI_Read/'], a[href*='/Recruit/GI_Read_VIP/']")


def parse_list_page(html: str, base_origin: str, page: int) -> List[JobPosting]:
    """
    JobKorea 목록 페이지 HTML을 파싱하여 JobPosting 리스트로 변환한다.
    """
    soup = BeautifulSoup(html, "lxml")

    item_nodes = find_list_items(soup)

    jobs: List[JobPosting] = []
    for item in item_nodes:
        # 일부 fallback 케이스: item 자체가 <a> 일 수 있음
        # 이 경우, 상위 컨테이너(카드 전체)를 찾은 뒤 그 컨테이너 기준으로 파싱한다.
        if getattr(item, "name", "") == "a":
            cur = item
            for _ in range(5):
                parent = getattr(cur, "parent", None)
                if not parent or not getattr(parent, "name", None):
                    break
                anchors_in_parent = parent.select(
                    "a[href*='/Recruit/GI_Read/'], a[href*='/Recruit/GI_Read_VIP/']"
                )
                # 같은 공고 상세 링크가 여러 개 들어 있는 카드 컨테이너를 찾으면 거기를 사용
                if len(anchors_in_parent) >= 2:
                    item = parent
                    break
                cur = parent

        # 대표 제목 링크 후보
        title_a = None

        # 1) 신규 리액트 기반 템플릿: 제목 타이포(span)의 클래스가 size18 인 경우가 많음
        title_span = item.select_one("span[class*='Typography_variant_size18']")
        if title_span:
            parent_a = title_span.find_parent("a")
            if parent_a is not None:
                title_a = parent_a

        # 2) 그래도 못 찾으면: 공고 상세 링크(anchor) 목록에서 우선 선택
        if title_a is None:
            anchors = item.select(
                "a[href*='/Recruit/GI_Read/'], a[href*='/Recruit/GI_Read_VIP/']"
            )
            if anchors:
                title_a = anchors[0]
            else:
                # 3) 구 템플릿: 기존 selector 들
                for sel in [
                    "a.title",  # 일반 검색
                    "a[href*='/Recruit/GI_Read/']",
                    "a[href*='/Recruit/GI_Read_VIP/']",
                ]:
                    found = item.select_one(sel)
                    if found:
                        title_a = found
                        break

        title = _pick_text(title_a) or ""
        href = title_a.get("href") if title_a else ""
        url = _normalize_url(base_origin, href)

        # 회사명
        company = ""
        if getattr(item, "name", "") != "a":
            company_a = (
                item.select_one("div.post-list-corp > a")
                or item.select_one("a.name")  # 일부 템플릿
            )

            # 신규 리액트 템플릿: 회사명 타이포(span)의 클래스가 size16 인 경우가 많음
            if not company_a:
                company_span = item.select_one(
                    "a span[class*='Typography_variant_size16']"
                )
                if company_span:
                    parent_a = company_span.find_parent("a")
                    if parent_a is not None:
                        company_a = parent_a

            # 여전히 못 찾으면: 같은 공고 상세 링크(anchor)가 제목/회사 두 번 나오는 구조에서
            # 두 번째 anchor 를 회사명 후보로 사용
            if not company_a:
                anchors = item.select(
                    "a[href*='/Recruit/GI_Read/'], a[href*='/Recruit/GI_Read_VIP/']"
                )
                if len(anchors) >= 2:
                    company_a = anchors[1]

            company = _pick_text(company_a) or ""

        # 부가 정보들(옵션)
        location = None
        job_condition = None
        sector = None
        deadline = None

        if getattr(item, "name", "") != "a":
            # 근무지
            loc_span = (
                item.select_one("p.option > span.loc")
                or item.select_one("span.area")  # 템플릿별 다른 클래스
            )
            location = _pick_text(loc_span)

            # 신규 템플릿: GrayChip 컴포넌트 안에 근무지 / 직무 / 연봉 순으로 위치
            if not location or not sector:
                chips = item.select("div[data-sentry-component='GrayChip'] span")
                if chips:
                    if not location:
                        location = _pick_text(chips[0])
                    if len(chips) >= 2 and not sector:
                        sector = _pick_text(chips[1])

            # 근무조건(경력/학력/고용형태 등)
            cond_span = item.select_one("p.option > span.exp")
            job_condition = _pick_text(cond_span)

            if not job_condition:
                # 신규 템플릿: "신입·경력1년↑" 같은 짧은 경력/조건 문구가 span 텍스트로만 존재
                for span in item.select("span"):
                    txt = _pick_text(span)
                    if not txt:
                        continue
                    if ("신입" in txt or "경력" in txt) and len(txt) <= 30:
                        job_condition = txt
                        break

            # 직무/산업 (구 템플릿 우선)
            sector_span = item.select_one("p.etc")
            if not sector:
                sector = _pick_text(sector_span)

            # 마감일/등록일
            deadline_span = (
                item.select_one("p.option > span.date") or item.select_one("span.date")
            )
            deadline = _pick_text(deadline_span)

            if not deadline:
                # 신규 템플릿: "44분 전 등록", "03/29(일) 마감" 등 span 텍스트로만 존재
                for span in item.select("span"):
                    txt = _pick_text(span)
                    if not txt:
                        continue
                    if "마감" in txt or "등록" in txt:
                        deadline = txt
                        # 마감 정보가 있으면 그것을 우선 사용
                        if "마감" in txt:
                            break

        # 필수 값 검증
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

    # URL 기준 중복 제거
    uniq: Dict[str, JobPosting] = {}
    for j in jobs:
        uniq[j.url] = j

    return list(uniq.values())

