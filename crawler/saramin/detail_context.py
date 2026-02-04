# -*- coding: utf-8 -*-
from __future__ import annotations

import re
import uuid
from typing import Optional, Tuple
from urllib.parse import parse_qs, urlparse

from .http import fetch_html
from .models import DetailContext

# =====================================================================
# 정규식(Regex) 패턴들
# - 사람인/유사 사이트에서 상세 페이지를 구성할 때 rec_idx/rec_seq 같은 식별자를 쓰고,
#   동적으로 생성되는 search_uuid / referNonce 같은 값이 HTML/스크립트/쿼리스트링에 섞여있을 수 있음.
# =====================================================================

# URL 혹은 HTML 문자열 내 "rec_idx=숫자" 추출
_REC_IDX_RE = re.compile(r"rec_idx=(\d+)")
# URL 혹은 HTML 문자열 내 "rec_seq=숫자" 추출
_REC_SEQ_RE = re.compile(r"rec_seq=(\d+)")

# HTML(주로 스크립트/JSON) 내부에서 search_uuid 값 추출
# 예: search_uuid: "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
# 또는 search_uuid="..."
_UUID_RE = re.compile(r"search_uuid[\"']?\s*[:=]\s*[\"']([0-9a-fA-F-]{36})[\"']")

# HTML 내부에서 referNonce 값 추출
# 예: referNonce: "abcdef1234..."
# 또는 referNonce="..."
_NONCE_RE = re.compile(r"referNonce[\"']?\s*[:=]\s*[\"']([0-9a-fA-F]+)[\"']")

# HTML 문자열 안에 쿼리스트링 형태로 들어간 search_uuid 추출
# 예: ...search_uuid=xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx...
_UUID_QS_RE = re.compile(r"search_uuid=([0-9a-fA-F-]{36})")

# HTML 문자열 안에 쿼리스트링 형태로 들어간 referNonce 추출
# 예: ...referNonce=abcdef1234...
_NONCE_QS_RE = re.compile(r"referNonce=([0-9a-fA-F]+)")


def extract_rec_idx_seq_from_url(job_url: str) -> Tuple[str, str]:
    """
    job_url에서 rec_idx, rec_seq를 최대한 안정적으로 추출한다.

    우선순위:
    1) urlparse + parse_qs 로 쿼리스트링에서 rec_idx/rec_seq를 읽는다. (가장 안전)
    2) 쿼리스트링에 없다면, 전체 문자열에서 정규식으로 rec_idx/rec_seq를 찾아본다. (fallback)

    반환:
    - (rec_idx, rec_seq)
    - rec_idx가 없을 수도 있으므로 "" 가능
    - rec_seq는 기본값 "1"로 보정
    """
    # URL을 파싱해서 query 부분만 뽑아냄
    u = urlparse(job_url)

    # query string -> dict(list) 형태로 파싱
    # keep_blank_values=True: rec_seq= 같은 빈 값도 보존
    qs = parse_qs(u.query, keep_blank_values=True)

    # rec_idx는 없으면 ""로, rec_seq는 없으면 기본 "1"
    rec_idx = (qs.get("rec_idx") or [""])[0]
    rec_seq = (qs.get("rec_seq") or ["1"])[0]

    # rec_idx가 쿼리에서 추출됐다면 그 값을 우선 사용
    if rec_idx:
        return rec_idx, (rec_seq or "1")

    # ----- fallback: URL 전체 문자열에서 정규식으로 추출 -----
    m = _REC_IDX_RE.search(job_url)
    if m:
        rec_idx = m.group(1)

    m2 = _REC_SEQ_RE.search(job_url)
    if m2:
        rec_seq = m2.group(1)

    # rec_seq가 비어있다면 "1"로 보정
    return rec_idx, (rec_seq or "1")


def extract_dynamic_fields_from_job_html(job_html: str) -> Tuple[Optional[str], Optional[str], Optional[str]]:
    """
    job 상세 페이지 HTML(job_html)에서 동적으로 필요한 필드를 추출한다.

    추출 대상:
    - search_uuid: 검색 세션/추적용 UUID로 쓰이는 값일 가능성이 큼
    - refer_nonce: referer 기반 검증/CSRF 유사 토큰 역할일 가능성
    - rec_idx: 일부 페이지는 URL이 아닌 HTML 내부 스크립트/데이터에서 rec_idx를 제공할 수 있음

    반환:
    - (search_uuid, refer_nonce, rec_idx)
    - 못 찾으면 None
    """
    search_uuid: Optional[str] = None
    refer_nonce: Optional[str] = None

    # 1) JSON/스크립트 형태( key: "value" or key="value")로 박혀있는 패턴 먼저 시도
    m = _UUID_RE.search(job_html)
    if m:
        search_uuid = m.group(1)

    m = _NONCE_RE.search(job_html)
    if m:
        refer_nonce = m.group(1)

    # 2) 위에서 못 찾으면, HTML 안에 "search_uuid=..." 같은 쿼리스트링 형태로 들어간 경우를 시도
    if not search_uuid:
        m = _UUID_QS_RE.search(job_html)
        if m:
            search_uuid = m.group(1)

    if not refer_nonce:
        m = _NONCE_QS_RE.search(job_html)
        if m:
            refer_nonce = m.group(1)

    # 3) rec_idx는 HTML에도 있을 수 있으니 별도로 탐색 (URL에서 못 뽑혔을 때 fallback로 사용)
    rec_idx: Optional[str] = None
    m = _REC_IDX_RE.search(job_html)
    if m:
        rec_idx = m.group(1)

    return search_uuid, refer_nonce, rec_idx


def build_detail_context(session, job_url: str, list_referer: str) -> DetailContext:
    """
    상세(iframe/API) 요청을 만들기 위한 컨텍스트(DetailContext)를 구성한다.

    흐름:
    1) job_url에서 rec_idx/rec_seq를 먼저 추출
    2) job_url HTML을 fetch하여(Referer 포함) 동적 필드(search_uuid/referNonce/rec_idx)를 추출
    3) rec_idx가 여전히 없으면 에러(상세 요청 자체가 불가능)
    4) search_uuid가 없으면 임의 UUID 생성(최소한 값은 채워서 요청 가능하게)
    5) DetailContext로 반환
    """
    # URL에서 기본 식별자(rec_idx, rec_seq) 추출
    rec_idx, rec_seq = extract_rec_idx_seq_from_url(job_url)

    # 상세 페이지 HTML을 실제로 한 번 가져와서(Referer 강제) 동적 필드들을 수집
    # 일부 사이트는 Referer 없으면 차단/리다이렉트/필드 누락 발생 가능
    job_html = fetch_html(session, job_url, headers={"Referer": list_referer})

    # HTML에서 search_uuid, refer_nonce, rec_idx 추출
    search_uuid, refer_nonce, rec_idx_from_html = extract_dynamic_fields_from_job_html(job_html)

    # URL에서 rec_idx 못 뽑았으면 HTML에서 뽑은 값으로 보정
    if not rec_idx:
        rec_idx = rec_idx_from_html or ""

    # 그래도 rec_idx가 없으면 더 진행 불가 → 명시적으로 실패시키고 원인 확인 유도
    if not rec_idx:
        raise RuntimeError("rec_idx를 추출하지 못했습니다. job_url/job_html 확인 필요")

    # search_uuid가 HTML에서 없다면 임의 생성
    # (서버가 실제로는 특정 값만 허용할 수도 있으므로,
    #  이 경우 요청 실패 시 'HTML에서 실제 search_uuid가 나오는지' 먼저 점검 필요)
    if not search_uuid:
        search_uuid = str(uuid.uuid4())

    # rec_seq는 최종적으로 비었을 수 있으니 "1" 보정
    return DetailContext(
        rec_idx=rec_idx,
        rec_seq=rec_seq or "1",
        search_uuid=search_uuid,
        refer_nonce=refer_nonce,
    )
