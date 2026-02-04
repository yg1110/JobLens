# -*- coding: utf-8 -*-
from __future__ import annotations

import random
import time
from typing import Dict, Optional

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
      요청 단위로 덮어쓸 수 있음(예: Referer, Origin 등)
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

    배경(주석에 적어둔 대로):
    - Saramin 계열에서 requests의 인코딩 추정(apparent_encoding/encoding)이
      종종 어긋나 한글이 깨지는 케이스가 있음
    - 그래서 "헤더 기반으로 requests가 잡아둔 encoding(r.encoding)"을 우선 사용하되,
      없거나 실패하면 UTF-8로 폴백한다.

    구현 포인트:
    - r.text를 그대로 쓰지 않고 r.content + 명시적 decode를 사용
      (r.text는 requests가 추정한 인코딩을 내부 적용하기 때문에 깨질 수 있음)
    - errors="replace"로 디코딩 실패 시 예외 대신 대체문자로 치환(크롤링 지속성 확보)
    """
    # requests가 (Content-Type 헤더 등을 바탕으로) 추정한 encoding
    enc = r.encoding
    if not enc:
        # 헤더에 인코딩 정보가 없으면 일반적으로 utf-8을 기본으로 가정
        enc = "utf-8"

    try:
        return r.content.decode(enc, errors="replace")
    except Exception:
        # enc 값이 비정상(예: 잘못된 라벨)일 수 있으므로 utf-8로 최종 폴백
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

    재시도 전략:
    - attempt 1..N
    - 실패 시 sleep = base_sleep * attempt + random(0, 0.8)
      → 단순 고정 대기보다 차단/레이트리밋 회피에 유리
    """
    last_err = None

    for attempt in range(1, max_retries + 1):
        try:
            # headers가 None이면 session 기본 헤더만 사용
            # headers가 dict면 session 기본 + 여기에서 제공한 헤더가 적용(동명 키는 덮어씀)
            r = session.get(url, timeout=timeout, headers=headers)

            # 4xx/5xx면 HTTPError 발생
            r.raise_for_status()

            # 인코딩 이슈를 피하기 위해 별도 디코딩 처리
            return _decode_response(r)

        except Exception as e:
            # 마지막 에러 저장(최종 실패 시 메시지에 포함)
            last_err = e

            # 재시도 전 대기(점진 증가 + 지터)
            time.sleep(base_sleep * attempt + random.uniform(0.0, 0.8))

    # 여기까지 오면 모두 실패 → 호출자가 원인 파악 가능하도록 마지막 에러 포함
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

    사용처 예:
    - Saramin의 view-ajax 같은 엔드포인트에 payload를 보내 iframe HTML을 받아오는 케이스
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
