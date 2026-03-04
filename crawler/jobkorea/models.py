from __future__ import annotations

from dataclasses import dataclass
from typing import Dict, Optional


@dataclass
class JobPosting:
    """
    JobKorea 채용 공고 한 건을 표현하는 데이터 모델.

    사람인(saramin) 크롤러에서 사용하는 `JobPosting` 모델과 동일한 구조를 유지해
    상위 레이어(API, 저장 포맷 등)에서 도메인에 관계없이 동일한 필드를 쓸 수 있게 한다.

    2단계 크롤링 구조를 전제로 한다.
    1) 목록(list) 페이지에서 최소 메타(제목/회사/URL/요약정보 등)를 수집
    2) 필요 시 상세(detail) 페이지를 추가 조회하여 detail_* 필드를 채움(enrich)
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

    # 이 공고가 수집된 목록 페이지 번호(추적/디버깅용)
    source_page: Optional[int] = None

    # -------------------------
    # Detail page(상세)에서 enrich 되는 정보
    # -------------------------

    # 상세 페이지(또는 iframe) URL
    detail_iframe_url: Optional[str] = None

    # 상세 내용을 "섹션 단위"로 파싱한 결과
    # 예: {"주요업무": "...", "자격요건": "...", "근무조건": "..."}
    detail_sections: Optional[Dict[str, str]] = None

    # 옵션: 상세 원문 HTML 저장(용량이 커질 수 있어 필요할 때만)
    detail_html: Optional[str] = None

