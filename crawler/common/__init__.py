from __future__ import annotations

"""
크롤러 도메인(saramin, jobkorea 등)에서 공통으로 사용하는 유틸리티 모듈 모음.

현재는 HTTP 관련 유틸만 포함되어 있으며,
필요 시 공통 로직을 여기에 추가한 뒤 각 도메인에서 import 해 사용한다.
"""

from .http import fetch_html, make_session, post_form

__all__ = [
    "make_session",
    "fetch_html",
    "post_form",
]

