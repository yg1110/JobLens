from __future__ import annotations

"""
JobKorea 전용 HTTP 유틸리티 모듈.

`common.http` 에 정의된 공통 HTTP 유틸을 그대로 재사용한다.
"""

from common.http import fetch_html, make_session, post_form, post_json

__all__ = [
    "make_session",
    "fetch_html",
    "post_form",
    "post_json",
]

