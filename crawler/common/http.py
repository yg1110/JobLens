from __future__ import annotations

import random
import time
from typing import Any, Dict, Optional

import requests


def make_session() -> requests.Session:
    """
    requests.Session을 생성하고 기본 헤더(User-Agent 등)를 세팅한다.

    목적:
    - 사이트가 봇으로 판단해 차단하는 확률을 낮춤(User-Agent)
    - 한국어 페이지 응답을 안정적으로 받기(Accept-Language)
    - HTML/문서 응답을 기본으로 요청(Accept)
    - TCP 연결 재사용(keep-alive)로 속도/안정성 개선

    주의:
    - 헤더는 세션 레벨 기본값이며, fetch_html/post_form에서 headers 인자로
      요청 단위로 덮어쓸 수 있다(예: Referer, Origin 등).
    """
    s = requests.Session()
    s.headers.update(
        {
            # 브라우저처럼 보이는 UA로 설정(차단/리다이렉트 완화에 도움)
            "User-Agent": (
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
                "AppleWebKit/537.36 (KHTML, like Gecko) "
                "Chrome/120.0.0.0 Safari/537.36"
            ),
            # ko-KR 우선, 그 다음 en-US/en 순으로 허용
            "Accept-Language": "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7",
            # HTML/XML 우선, 나머지 타입도 허용
            "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            # 연결 재사용(성능/안정성)
            "Connection": "keep-alive",
        }
    )
    return s


def _decode_response(r: requests.Response) -> str:
    """
    Response를 문자열로 디코딩한다.

    - 서버가 응답 헤더에 인코딩 정보를 제대로 주지 않는 경우를 대비해
      requests 가 추정한 encoding(r.encoding)을 우선 사용하고,
      없거나 실패하면 UTF-8로 폴백한다.
    - r.text 대신 r.content + 명시적 decode 를 사용해 한글 깨짐을 줄인다.
    """
    enc = r.encoding or "utf-8"

    try:
        return r.content.decode(enc, errors="replace")
    except Exception:
        return r.content.decode("utf-8", errors="replace")


def fetch_html(
    session: requests.Session,
    url: str,
    *,
    timeout: int = 20,
    max_retries: int = 3,
    base_sleep: float = 1.2,
    headers: Optional[Dict[str, str]] = None,
) -> str:
    """
    GET 요청으로 HTML(문자열)을 가져온다. (재시도 + 지수형 backoff + 랜덤 지터)

    파라미터:
    - session: requests.Session (쿠키/헤더 유지)
    - url: 요청 URL
    - timeout: 요청 타임아웃(초)
    - max_retries: 최대 재시도 횟수
    - base_sleep: 재시도 대기 기본값(초). attempt에 비례해 증가
    - headers: 요청 단위로 추가/덮어쓸 헤더(예: Referer)
    """
    last_err = None

    for attempt in range(1, max_retries + 1):
        try:
            r = session.get(url, timeout=timeout, headers=headers)
            r.raise_for_status()
            return _decode_response(r)
        except Exception as e:
            last_err = e
            time.sleep(base_sleep * attempt + random.uniform(0.0, 0.8))

    raise RuntimeError(f"GET 실패: {url} / 마지막 에러: {last_err}")


def post_form(
    session: requests.Session,
    url: str,
    data: Dict[str, str],
    *,
    timeout: int = 20,
    max_retries: int = 3,
    base_sleep: float = 1.2,
    headers: Optional[Dict[str, str]] = None,
) -> str:
    """
    application/x-www-form-urlencoded 형태로 POST 요청을 보내고 HTML(문자열)을 반환한다.
    (재시도 + backoff + 지터 패턴은 fetch_html과 동일)

    파라미터:
    - data: form data(dict). requests가 내부적으로 form-encoding 처리
    - headers: 요청 단위 헤더(예: X-Requested-With, Content-Type, Origin, Referer 등)
    """
    last_err = None

    for attempt in range(1, max_retries + 1):
        try:
            r = session.post(url, data=data, timeout=timeout, headers=headers)
            r.raise_for_status()
            return _decode_response(r)
        except Exception as e:
            last_err = e
            time.sleep(base_sleep * attempt + random.uniform(0.0, 0.8))

    raise RuntimeError(f"POST 실패: {url} / 마지막 에러: {last_err}")


def post_json(
    session: requests.Session,
    url: str,
    json_data: Dict[str, Any],
    *,
    timeout: int = 20,
    max_retries: int = 3,
    base_sleep: float = 1.2,
    headers: Optional[Dict[str, str]] = None,
) -> str:
    """
    application/json 형태로 POST 요청을 보내고 응답 본문(문자열)을 반환한다.
    (재시도 + backoff + 지터 패턴은 fetch_html과 동일)

    파라미터:
    - json_data: JSON body 로 전송할 dict
    - headers: 요청 단위 헤더(예: Content-Type, Origin, Referer 등)
    """
    last_err = None

    # Content-Type 기본값 보정
    base_headers = {"Content-Type": "application/json; charset=utf-8"}
    if headers:
        base_headers.update(headers)

    for attempt in range(1, max_retries + 1):
        try:
            r = session.post(url, json=json_data, timeout=timeout, headers=base_headers)
            r.raise_for_status()
            return _decode_response(r)
        except Exception as e:
            last_err = e
            time.sleep(base_sleep * attempt + random.uniform(0.0, 0.8))

    raise RuntimeError(f"POST(JSON) 실패: {url} / 마지막 에러: {last_err}")


__all__ = [
    "make_session",
    "fetch_html",
    "post_form",
    "post_json",
]

