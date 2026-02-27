from __future__ import annotations

from typing import Tuple

from .http import fetch_html


def fetch_detail_html(
    session,
    job_url: str,
    *,
    list_referer: str | None = None,
    timeout: int = 20,
) -> str:
    """
    JobKorea 상세 페이지 HTML을 가져온다.

    사람인과 달리 별도의 view-ajax 단계를 거치지 않고,
    목록에서 받은 상세 URL(job_url)을 직접 GET 하는 단일 단계 구조를 기본 가정으로 한다.
    필요한 경우 Referer 를 목록 URL(list_referer) 로 지정해 차단 가능성을 낮춘다.
    """
    headers = {"Referer": list_referer} if list_referer else None
    return fetch_html(session, job_url, timeout=timeout, headers=headers)


def fetch_detail_with_url(
    session,
    job_url: str,
    *,
    list_referer: str | None = None,
    timeout: int = 20,
) -> Tuple[str, str]:
    """
    Saramin 크롤러의 `fetch_detail_iframe_html` 과 유사한 인터페이스를 제공하기 위한 래퍼.

    - 첫 번째 반환값: 상세 페이지 URL (job_url 그대로)
    - 두 번째 반환값: 상세 HTML 문자열
    """
    html = fetch_detail_html(session, job_url, list_referer=list_referer, timeout=timeout)
    return job_url, html

