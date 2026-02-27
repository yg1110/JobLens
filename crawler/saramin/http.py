from __future__ import annotations

"""
Saramin 전용이 아니라, 공통 HTTP 유틸을 래핑해 노출하는 얇은 모듈이다.

기존 코드와의 호환성을 위해 모듈 경로(`saramin.http`)는 유지하면서
실제 구현은 `common.http` 에서 재사용한다.
"""

from common.http import fetch_html, make_session, post_form

__all__ = [
    "make_session",
    "fetch_html",
    "post_form",
]
