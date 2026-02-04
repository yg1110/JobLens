# -*- coding: utf-8 -*-
from __future__ import annotations

import re
from typing import Dict, List, Optional

from bs4 import BeautifulSoup


# =====================================================================
# 섹션 타이틀 표준화 맵
# - key: 표준(출력에 사용할) 섹션명
# - value: 인식해야 하는 동의어/변형/오타/띄어쓰기 버전들
#
# 정책:
# - 매칭되면 항상 "표준 섹션명(key)"으로만 반환
# - value 리스트에는 표준 명칭을 포함해도 되고(권장), 포함하지 않아도 됨(아래 구현은 둘 다 지원)
# =====================================================================
SECTION_TITLE_MAP: Dict[str, List[str]] = {
    "모집부문": ["모집부문", "모집 부문", "모집내용", "모집 내용"],
    "모집분야": ["모집분야", "모집 분야"],
    "상세": ["상세", "상세내용", "상세 내용", "상세정보", "상세 정보"],
    "주요업무": ["주요업무", "주요 업무", "업무내용", "업무 내용"],
    "담당업무": ["담당업무", "담당 업무", "담당직무", "담당 직무", "담당 역할"],
    "자격요건": ["자격요건", "자격 요건", "지원자격", "지원 자격", "필수자격요건", "필수 자격요건", "필수 요건"],
    "우대사항": ["우대사항", "우대 사항", "우대요건", "우대 요건", "우대조건", "우대 조건"],
    "근무조건": ["근무조건", "근무 조건", "근무환경", "근무 환경", "근무시간", "근무 시간", "근무형태", "근무 형태"],
    "복리후생": ["복리후생", "복리 후생", "복지", "복지 및 혜택", "복지&혜택", "복지혜택", "복지 혜택"],
    "채용절차": ["채용절차", "채용 절차", "전형절차", "전형 절차", "전형일정", "전형 일정", "채용프로세스", "채용 프로세스"],
    "접수기간": ["접수기간", "접수 기간", "지원기간", "지원 기간", "마감일", "마감 일", "마감", "기간"],
    "접수방법": ["접수방법", "접수 방법", "지원방법", "지원 방법", "지원 절차", "지원절차"],
    "안내사항": ["안내사항", "안내 사항", "유의사항", "유의 사항", "기타사항", "기타 사항", "참고사항", "참고 사항", "유의시항"],  # 오타 포함
    "근무지": ["근무지", "근무 지역", "근무지역", "근무장소", "근무 장소", "근무 위치", "근무위치", "근무처"],
    "문의처": ["문의처", "문의 처", "문의", "연락처", "연락 처"],
    "회사소개": ["회사소개", "회사 소개", "기업소개", "기업 소개"],
    "급여": ["급여", "급여제도", "급여 제도", "연봉", "보상", "보상체계", "보상 체계"],
    "제출서류": ["제출서류", "제출 서류", "제출자료", "제출 자료"],
    "고용형태": ["고용형태", "고용 형태", "채용형태", "채용 형태", "근무형태", "근무 형태"],
    "채용안내": ["채용안내", "채용 안내"],
}

_WS_RE = re.compile(r"[ \t]+")
_PREFIX_NOISE_RE = re.compile(r"^[\s\-\•\*\·\u2022\u25CF\u25AA\u25A0\u25FE\u25FB\u25FC\u25FD\u25AA]+")
_EMOJI_RE = re.compile(r"[\U00010000-\U0010FFFF]", flags=re.UNICODE)


def _clean_text(s: str) -> str:
    s = s.replace("\xa0", " ")
    s = _WS_RE.sub(" ", s)
    return s.strip()


def _node_text(node) -> str:
    if not node:
        return ""
    txt = node.get_text("\n", strip=True).replace("\r", "")
    lines = [_clean_text(line) for line in txt.split("\n")]
    lines = [line for line in lines if line]
    return "\n".join(lines).strip()


def _normalize_heading(s: str) -> str:
    """
    헤더 비교용 정규화:
    - 이모지 제거
    - 라인 앞 기호 제거
    - 공백 제거
    - 소문자화(영문 대비)
    """
    s = _EMOJI_RE.sub("", s or "")
    s = _PREFIX_NOISE_RE.sub("", s)
    s = re.sub(r"\s+", "", s)
    return s.strip().lower()


def split_text_by_headings(full_text: str) -> Dict[str, str]:
    """
    라인 기반 텍스트(full_text)를 섹션 헤더 기준으로 분리한다. (표준화 맵 사용)

    헤더 감지:
    - 완전 일치(정규화 라인 == 정규화 동의어)
    - 포함 매칭(정규화 라인 안에 정규화 동의어가 포함)

    반환:
    - key는 항상 "표준 섹션명"으로 통일
    - 헤더 이전 텍스트는 "상세"로 묶음
    """
    lines = [line.strip() for line in (full_text or "").split("\n")]
    lines = [line for line in lines if line]

    # normalized alias -> canonical(표준명)
    # 예: "유의시항"(정규화) -> "안내사항"
    normalized_alias_to_canonical: Dict[str, str] = {}

    for canonical, aliases in SECTION_TITLE_MAP.items():
        # 표준명 자체도 alias로 포함(리스트에 없어도 인식되도록)
        all_aliases = list(aliases or [])
        if canonical not in all_aliases:
            all_aliases.append(canonical)

        for a in all_aliases:
            na = _normalize_heading(a)
            if na:
                normalized_alias_to_canonical[na] = canonical

    def detect_header(line: str) -> Optional[str]:
        key = _normalize_heading(line)

        # (1) 완전 일치
        if key in normalized_alias_to_canonical:
            return normalized_alias_to_canonical[key]

        # (2) 포함 매칭
        # - 너무 짧은 alias는 오탐이 많아 최소 길이 조건
        for nk, canonical in normalized_alias_to_canonical.items():
            if len(nk) >= 2 and nk in key:
                return canonical

        return None

    sections: Dict[str, List[str]] = {}
    current_title: Optional[str] = None

    for line in lines:
        header = detect_header(line)
        if header:
            current_title = header
            sections.setdefault(current_title, [])
            continue

        if current_title is None:
            current_title = "상세"
            sections.setdefault(current_title, [])
        sections[current_title].append(line)

    out: Dict[str, str] = {}
    for title, body_lines in sections.items():
        body = "\n".join(body_lines).strip()
        if body:
            out[title] = body
    return out


def _should_resplit_from_detail(sections: Dict[str, str]) -> bool:
    """
    parse_detail_sections 결과가 '상세' 한 덩어리로만 나온 경우,
    그 안에 섹션 헤더가 들어있을 가능성이 높으면 재분리를 트리거한다.
    """
    if not sections:
        return True

    if set(sections.keys()) == {"상세"}:
        detail = sections.get("상세", "") or ""
        norm = _normalize_heading(detail)

        # 표준 헤더 키워드(주요한 것들) 기반으로 재분리 필요성 판단
        keywords = [
            "주요업무",
            "담당업무",
            "자격요건",
            "우대사항",
            "근무조건",
            "채용절차",
            "접수기간",
            "복리후생",
            "유의사항",
            "안내사항",
        ]
        return any(_normalize_heading(k) in norm for k in keywords)

    return False


def parse_detail_sections(detail_html: str) -> Dict[str, str]:
    """
    1) dl/dt/dd 구조 파싱 우선
    2) h2/h3/h4 + 다음 형제 블록 파싱
    3) 마지막 fallback: body 텍스트를 split_text_by_headings로 분리
    + 추가: '상세' 몰빵이면 재분리해서 '상세'에 다 들어가는 현상 완화
    """
    soup = BeautifulSoup(detail_html, "lxml")
    for t in soup(["script", "style", "noscript"]):
        t.decompose()

    sections: Dict[str, str] = {}

    # (1) dl dt/dd
    for dl in soup.select("dl"):
        dts = dl.find_all("dt", recursive=False) or dl.select("dt")
        for dt in dts:
            title_raw = _node_text(dt)
            if not title_raw:
                continue

            # dt 제목도 표준화(동의어/오타를 표준명으로)
            # - split_text_by_headings의 detect_header 로직을 재사용하진 않지만,
            #   유사하게 "정규화 -> alias 매칭"을 하고 싶다면 여기서도 동일 맵을 적용할 수 있음.
            #   (현재는 title_raw 그대로 유지. 필요하면 표준화 적용도 도와줄게.)
            title = title_raw

            dd = dt.find_next_sibling("dd") or dt.find_next("dd")
            body = _node_text(dd) if dd else ""
            if body:
                if title in sections:
                    sections[title] = (sections[title] + "\n\n" + body).strip()
                else:
                    sections[title] = body

    if len(sections) >= 2:
        return sections

    # (2) heading 기반
    for h in soup.select("h2, h3, h4"):
        title_raw = _node_text(h)
        if not title_raw:
            continue
        title = title_raw

        body_node = None
        cur = h
        for _ in range(8):
            cur = cur.find_next_sibling()
            if not cur:
                break
            if cur.name in ("div", "section", "article", "ul", "ol", "p", "table"):
                body_node = cur
                break

        body = _node_text(body_node) if body_node else ""
        if body and len(body) >= 10:
            if title in sections:
                sections[title] = (sections[title] + "\n\n" + body).strip()
            else:
                sections[title] = body

    if sections:
        if _should_resplit_from_detail(sections):
            detail = sections.get("상세", "") or ""
            resplit = split_text_by_headings(detail)
            if len(resplit) >= 2:
                return resplit
        return sections

    # (3) fallback: body 전체 텍스트
    main = soup.select_one("body") or soup
    text = _node_text(main)
    if not text:
        return {}

    text = text[:20000]
    splitted = split_text_by_headings(text)
    return splitted if splitted else {"상세": text}
