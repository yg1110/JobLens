from __future__ import annotations

"""
JobKorea 도메인 크롤러 패키지.

구조는 `saramin` 패키지를 참고해 설계되었으며,
목록(list) 크롤링을 우선 지원하고, 상세(detail) 크롤링은
필요 시 확장하는 것을 전제로 한다.

외부에서는 아래 심볼들을 주로 사용한다.

- JobPosting: 채용 공고 공통 데이터 모델
- crawl_list: JobKorea 목록 페이지 크롤러
- enrich_jobs_with_details: (향후 확장용) 상세 정보 enrich 함수
"""

from .models import JobPosting
from .crawler import crawl_list, enrich_jobs_with_details

__all__ = [
    "JobPosting",
    "crawl_list",
    "enrich_jobs_with_details",
]

