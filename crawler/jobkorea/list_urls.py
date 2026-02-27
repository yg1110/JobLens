from __future__ import annotations

from urllib.parse import parse_qs, urlencode, urlparse, urlunparse


def build_paged_url(base_url: str, page: int) -> str:
    """
    JobKorea 검색/목록 URL에 페이지 파라미터(Page_No)를 주입/갱신해
    특정 페이지 URL을 생성한다.

    사용 예:
    - base_url: "https://www.jobkorea.co.kr/Search/?stext=python&Page_No=1"
    - page: 3
    => Page_No=3 으로 바뀐 URL 반환
    """
    u = urlparse(base_url)
    qs = parse_qs(u.query, keep_blank_values=True)

    # JobKorea 검색 페이지는 일반적으로 Page_No 로 페이지 번호를 전달한다.
    qs["Page_No"] = [str(page)]

    new_query = urlencode(qs, doseq=True)
    return urlunparse((u.scheme, u.netloc, u.path, u.params, new_query, u.fragment))


def with_page_size(base_url: str, size: int) -> str:
    """
    base_url의 쿼리에서 페이지당 표시 개수(Page_Size) 파라미터를
    설정/갱신한 URL을 반환한다.

    예:
    - size=20, 30, 50 등
    """
    u = urlparse(base_url)
    qs = parse_qs(u.query, keep_blank_values=True)
    qs["Page_Size"] = [str(size)]
    new_query = urlencode(qs, doseq=True)
    return urlunparse((u.scheme, u.netloc, u.path, u.params, new_query, u.fragment))

