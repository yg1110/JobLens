# -*- coding: utf-8 -*-
from __future__ import annotations

from dataclasses import dataclass
from typing import Dict, Optional


@dataclass
class JobPosting:
    """
    채용 공고 한 건을 표현하는 데이터 모델.

    이 모델은 2단계 크롤링 구조를 전제로 설계됨:
    1) 목록(list) 페이지에서 최소 메타(제목/회사/URL/요약정보 등)를 수집
    2) 상세(detail) 페이지를 추가로 조회해서 detail_* 필드를 채움(enrich)

    필드 구성:
    - list 단계에서 채워지는 필드: title/company/url/location/job_condition/sector/deadline/...
    - detail 단계에서 채워지는 필드: detail_iframe_url/detail_sections/detail_html
    """

    # -------------------------
    # List page(목록)에서 수집되는 기본 정보
    # -------------------------

    # 공고 제목(필수)
    title: str

    # 회사명(필수로 두었지만, 템플릿에 따라 비어 있을 수 있음)
    company: str

    # 공고 상세로 이동 가능한 URL(중복 제거 키로도 사용)
    url: str

    # 근무지(예: "서울 강남구", "경기 성남시" 등) - 목록에서 선택적으로 제공
    location: Optional[str] = None

    # 근무조건/요약 조건(예: "경력 3~5년", "정규직", "학력무관" 등) - 목록에서 선택적으로 제공
    job_condition: Optional[str] = None

    # 직무/산업 분야(예: "웹개발", "IT·인터넷" 등) - 목록에서 선택적으로 제공
    sector: Optional[str] = None

    # 마감일/접수기간(예: "02/20(금) 마감") - 목록에서 선택적으로 제공
    deadline: Optional[str] = None

    # 수집 시각(Unix timestamp). 목록/상세 어느 단계에서든 기록 가능하지만
    # 현재 구현은 목록 수집 시점(time.time())을 넣는 용도
    scraped_at: float = 0.0

    # 이 공고가 수집된 목록 페이지 번호(추적/디버깅용)
    source_page: Optional[int] = None

    # -------------------------
    # Detail page(상세)에서 enrich 되는 정보
    # -------------------------

    # view-ajax를 통해 추출된 상세 iframe URL (실제 상세 HTML이 있는 곳)
    detail_iframe_url: Optional[str] = None

    # 상세 내용을 "섹션 단위"로 파싱한 결과
    # 예: {"주요업무": "...", "자격요건": "...", "근무조건": "..."}
    detail_sections: Optional[Dict[str, str]] = None

    # 옵션: 상세 원문 HTML 저장(용량이 커질 수 있어 필요할 때만)
    # - 디버깅/재파싱/증적 저장 등에 유용
    detail_html: Optional[str] = None


@dataclass
class DetailContext:
    """
    Saramin 상세(view-ajax) 요청을 구성하기 위한 컨텍스트 모델.

    목적:
    - job_url(상세 진입 URL)에서 rec_idx/rec_seq 및 동적 필드(search_uuid, refer_nonce)를 확보
    - view-ajax POST payload에 필요한 기본 파라미터들을 함께 들고 다님
    - fetch_detail_iframe_html 단계에서 payload를 만들 때 재사용

    주요 필드:
    - rec_idx / rec_seq: 공고 식별자(필수)
    - search_uuid: 검색/세션 추적용 UUID(없으면 생성해도 되지만, 서버 정책에 따라 필수일 수 있음)
    - refer_nonce: Referer 검증/보안 토큰 성격(없을 수 있음)

    payload defaults:
    - 네 캡처 기준으로 기본값을 박아둔 값들
    - 페이지/유입경로/트래킹 관련 파라미터로 보이며, 서버가 특정 키 존재를 기대할 수 있어 기본값 유지
    """

    # 공고 고유 ID (필수)
    rec_idx: str

    # 공고 시퀀스(기본 "1"). 같은 rec_idx라도 변형 공고/다중 포지션 등에 쓰일 수 있음
    rec_seq: str = "1"

    # 검색 세션/추적용 UUID (HTML에서 추출 가능, 없으면 uuid4 생성)
    search_uuid: Optional[str] = None

    # Referer/요청 검증에 쓰일 수 있는 nonce(HTML에서 추출 가능, 없을 수도 있음)
    refer_nonce: Optional[str] = None

    # -------------------------
    # view-ajax payload 기본값들
    # (네 캡처 기준)
    # -------------------------

    # 상세 조회 타입(보통 search)
    view_type: str = "search"

    # 유입 ref(보통 search)
    t_ref: str = "search"

    # ref 컨텐츠(템플릿/지면을 나타내는 값일 가능성)
    t_ref_content: str = "generic"

    # 시나리오/스크린 ID로 보이는 값(캡처 기준 "811")
    t_ref_scnid: str = "811"

    # 검색 타입(캡처 기준 search)
    searchType: str = "search"

    # 검색어(없으면 빈 문자열)
    searchword: str = ""

    # ref_dp: 디스플레이/지면 코드로 보이는 값(캡처 기준)
    ref_dp: str = "SRI_050_VIEW_MTRX_RCT"
