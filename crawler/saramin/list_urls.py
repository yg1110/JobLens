# -*- coding: utf-8 -*-
from __future__ import annotations

from urllib.parse import parse_qs, urlencode, urlparse, urlunparse


def build_paged_url(base_url: str, page: int) -> str:
    """
    목록(base_url)에 페이지 파라미터(recruitPage)를 주입/갱신하여
    특정 페이지 URL을 생성한다.

    사용 예:
    - base_url: "https://www.saramin.co.kr/zf_user/jobs/list/domestic?...&recruitPage=1"
    - page: 3
    => recruitPage=3 으로 바뀐 URL 반환

    구현 포인트:
    1) urlparse로 base_url을 구성 요소(scheme/netloc/path/query/fragment 등)로 분해
    2) parse_qs로 query string을 dict 형태로 변환
       - keep_blank_values=True: recruitPage= 처럼 빈 값도 유지
       - 값이 list로 나오는 이유: 동일 키가 여러 번 등장할 수 있기 때문
    3) qs["recruitPage"]를 원하는 page로 덮어쓰기(항상 리스트로 넣어야 urlencode가 안전)
    4) urlencode(doseq=True)로 dict(list) 형태를 올바르게 query로 직렬화
    5) urlunparse로 원래 URL 구성 요소를 유지하면서 query만 교체해 최종 URL 생성
    """
    # URL을 구성 요소로 분해
    u = urlparse(base_url)

    # query string -> dict[str, list[str]] 로 파싱
    # 예: "a=1&a=2&b=3" -> {"a": ["1","2"], "b": ["3"]}
    qs = parse_qs(u.query, keep_blank_values=True)

    # 페이지 번호 파라미터를 설정/갱신
    # parse_qs의 값 타입이 list이므로 리스트로 넣는 게 정석
    qs["recruitPage"] = [str(page)]

    # dict(list) -> query string 직렬화
    # doseq=True: 리스트 값을 a=1&a=2 형태로 펼쳐서 인코딩
    new_query = urlencode(qs, doseq=True)

    # 기존 URL의 다른 구성 요소(scheme, netloc, path, fragment 등)는 그대로 유지하고
    # query만 new_query로 교체하여 최종 URL 생성
    return urlunparse((u.scheme, u.netloc, u.path, u.params, new_query, u.fragment))
