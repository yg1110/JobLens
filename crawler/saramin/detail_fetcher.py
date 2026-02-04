# -*- coding: utf-8 -*-
from __future__ import annotations

from typing import Dict, Tuple
from urllib.parse import urljoin

from bs4 import BeautifulSoup

from .detail_context import build_detail_context
from .http import fetch_html, post_form
from .models import DetailContext

# =====================================================================
# 사라민 채용 상세는 일반적으로:
# 1) job_url(상세 진입 URL)을 GET (필요한 동적 값/식별자 추출)
# 2) /zf_user/jobs/relay/view-ajax 로 POST (form data) → iframe 포함 HTML 반환
# 3) 반환 HTML에서 iframe src를 뽑아 실제 상세 HTML(iframe)을 GET
# =====================================================================

BASE = "https://www.saramin.co.kr"
VIEW_AJAX_URL = f"{BASE}/zf_user/jobs/relay/view-ajax"


def build_view_ajax_payload(ctx: DetailContext) -> Dict[str, str]:
    """
    view-ajax 엔드포인트에 POST로 보내는 form payload 구성.

    ctx(DetailContext)에는:
    - rec_idx / rec_seq: 공고 식별자
    - view_type, t_ref, t_ref_content, t_ref_scnid: 트래킹/유입경로 관련 파라미터(없으면 서버가 기본값 기대할 수 있음)
    - search_uuid, refer_nonce: 동적 필드(봇 방지/세션 추적/검증 등)
    - searchType/searchword/ref_dp/dpId/...: 검색/추천/디스플레이 관련 파라미터(사라민 내부 로직)
    
    주의:
    - 값이 없는 항목은 "" 로 전송(서버가 키 존재를 기대하는 경우가 있어 key를 유지하는 전략)
    - ctx 내부 필드가 None일 수 있으므로 `or ""`로 안전 보정
    """
    return {
        # 공고 고유 식별자
        "rec_idx": ctx.rec_idx,
        "rec_seq": ctx.rec_seq,

        # UTM 파라미터(없으면 빈값)
        "utm_source": "",
        "utm_medium": "",
        "utm_term": "",
        "utm_campaign": "",

        # view-ajax 응답을 결정하는 핵심 파라미터들(DetailContext에서 생성/세팅됨)
        "view_type": ctx.view_type,
        "t_ref": ctx.t_ref,
        "t_ref_content": ctx.t_ref_content,
        "t_ref_scnid": ctx.t_ref_scnid,

        # 동적으로 주어지는 search_uuid (없으면 빈값이지만, build_detail_context에서 기본 생성해줌)
        "search_uuid": ctx.search_uuid or "",

        # refer는 비워둠(사라민에서 종종 사용되지만 필수 아닐 때가 많음)
        "refer": "",

        # 검색/필터 관련 파라미터들(상황에 따라 서버가 요구할 수 있음)
        "searchType": ctx.searchType,
        "searchword": ctx.searchword,
        "ref_dp": ctx.ref_dp,

        # 아래는 사라민 내부에서 사용되는 필드들로 보이며, 기본적으로 빈값 유지
        "dpId": "",
        "recommendRecIdx": "",

        # 동적으로 주어지는 referNonce(없을 수 있음 → 빈값)
        "referNonce": ctx.refer_nonce or "",

        # 특정 케이스에서만 쓰이는 코드(대부분 빈값)
        "trainingStudentCode": "",
    }


def extract_iframe_src_from_view_ajax_html(html: str) -> str:
    """
    view-ajax 응답 HTML에서 상세 내용을 담고 있는 iframe의 src URL을 찾아 반환한다.

    iframe selector를 여러 개 두는 이유:
    - 사라민 페이지 템플릿/실험(A/B 테스트)/시점에 따라 iframe id/class/name이 바뀔 수 있음
    - 최소한 src가 있는 iframe이라도 잡아내기 위한 fallback 전략

    반환:
    - urljoin(BASE, src): src가 상대경로일 수 있으므로 절대 URL로 보정
    """
    soup = BeautifulSoup(html, "lxml")

    # 우선순위 높은 셀렉터부터 순차적으로 시도
    iframe = (
        soup.select_one("iframe#iframe_content_1")           # 가장 흔한 id
        or soup.select_one("iframe.iframe_content")          # class 기반
        or soup.select_one("iframe[name^='iframe_content']") # name prefix 기반
        or soup.select_one("iframe[src]")                    # 최후: src 가진 iframe 아무거나
    )

    # iframe 또는 src를 찾지 못하면, 응답 일부(head)만 잘라서 에러 메시지에 포함
    # (전체 HTML을 다 넣으면 로그가 너무 커질 수 있어 500자만 사용)
    if not iframe or not iframe.get("src"):
        head = html[:500].replace("\n", " ")
        raise RuntimeError(f"view-ajax 응답에서 iframe src를 찾지 못했습니다. head={head}")

    # src는 상대경로일 수 있으므로 BASE 기준으로 절대 URL로 만들고, 양쪽 공백 제거
    return urljoin(BASE, iframe["src"].strip())


def fetch_detail_iframe_html(
    session,
    job_url: str,
    list_referer: str,
    *,
    timeout: int = 20,
) -> Tuple[str, str]:
    """
    job_url로부터 최종 상세 HTML(iframe 내부)을 가져온다.

    전체 흐름:
    1) build_detail_context:
       - job_url을 GET하여 rec_idx/rec_seq + search_uuid + referNonce 등 동적 필드 확보
       - 서버 요청에 필요한 컨텍스트(DetailContext) 생성
    2) build_view_ajax_payload:
       - 컨텍스트 기반으로 view-ajax POST payload 구성
    3) post_form to VIEW_AJAX_URL:
       - AJAX 엔드포인트로 폼 POST → iframe 포함 HTML 반환
    4) extract_iframe_src_from_view_ajax_html:
       - iframe src 추출 → iframe_url
    5) fetch_html(iframe_url):
       - 실제 상세 HTML(iframe 내부) GET

    반환:
    - (iframe_url, detail_html)
    """
    # 1) 상세 요청에 필요한 컨텍스트(동적 값 포함) 생성
    ctx = build_detail_context(session, job_url=job_url, list_referer=list_referer)

    # 2) view-ajax POST payload 구성
    payload = build_view_ajax_payload(ctx)

    # 3) view-ajax는 XMLHttpRequest 성격을 기대하는 경우가 많아 관련 헤더를 세팅
    headers = {
        "Origin": BASE,  # CORS/검증용
        "Referer": job_url,  # 서버가 상세 진입 경로를 검증할 수 있음
        "X-Requested-With": "XMLHttpRequest",  # AJAX 요청임을 나타냄
        "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8",  # form POST
    }

    # 4) view-ajax 엔드포인트로 폼 POST → iframe을 포함한 HTML을 받음
    view_ajax_html = post_form(session, VIEW_AJAX_URL, payload, headers=headers, timeout=timeout)

    # 5) view-ajax HTML에서 iframe src를 뽑아 실제 상세 URL 구성
    iframe_url = extract_iframe_src_from_view_ajax_html(view_ajax_html)

    # 6) iframe URL로 실제 상세 HTML을 가져옴
    #    이때도 Referer를 job_url로 두어 접근 차단을 피함
    detail_html = fetch_html(session, iframe_url, headers={"Referer": job_url}, timeout=timeout)

    return iframe_url, detail_html
