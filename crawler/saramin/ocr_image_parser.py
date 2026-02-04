# -*- coding: utf-8 -*-
from __future__ import annotations

import io
import re
from dataclasses import dataclass
from typing import List, Optional, Tuple
from urllib.parse import urljoin

import requests
from bs4 import BeautifulSoup

# =====================================================================
# Optional dependencies
# - OCR은 환경에 따라 설치가 안 되어 있을 수 있음
# - 설치되어 있지 않으면 기능을 비활성화하고 에러 메시지를 남김
# =====================================================================

try:
    from PIL import Image
except Exception:  # pragma: no cover
    # Pillow가 설치되지 않으면 Image가 None이 되어 OCR을 건너뛰게 됨
    Image = None

try:
    import pytesseract
except Exception:  # pragma: no cover
    # pytesseract가 설치되지 않으면 OCR을 건너뛰게 됨
    pytesseract = None


# 이미지 URL 필터링용 정규식:
# - 확장자가 png/jpg/jpeg/webp인 것만 통과
# - 뒤에 query string이 붙어도 허용(예: .jpg?ver=123)
_IMG_EXT_RE = re.compile(r"\.(png|jpg|jpeg|webp)(\?|$)", re.IGNORECASE)


@dataclass
class OcrResult:
    """
    OCR 처리 결과를 담는 모델.

    - text: OCR로 추출된 전체 텍스트(여러 이미지면 합쳐진 결과)
    - used_images: 실제 OCR에 사용한 이미지 URL 목록(max_images 적용 이후)
    - error: OCR 준비 실패/결과 비어있음 등의 에러 메시지(없으면 None)
    """
    text: str
    used_images: List[str]
    error: Optional[str] = None


def extract_image_urls(detail_html: str, base_url: str) -> List[str]:
    """
    detail_html 안의 img[src]들을 추출해 절대 URL로 정규화한 뒤 반환한다.

    처리 흐름:
    1) BeautifulSoup로 HTML 파싱
    2) 모든 img[src]의 src를 수집
    3) urljoin(base_url, src)로 절대 URL 변환
       - src가 상대경로인 경우를 대비
    4) 너무 잡다한 이미지(아이콘/스프라이트 등) 배제를 위해
       확장자 기반 필터(_IMG_EXT_RE) 적용
    5) 중복 제거(순서 유지) 후 반환

    주의:
    - 확장자 기반 필터는 단순 경험칙이며,
      실제 텍스트가 들어간 이미지가 확장자 없이 CDN 경로로 제공되는 경우엔 누락될 수 있음.
    """
    soup = BeautifulSoup(detail_html, "lxml")
    urls: List[str] = []

    # 1) img[src] 수집
    for img in soup.select("img[src]"):
        src = (img.get("src") or "").strip()
        if not src:
            continue

        # 2) 상대경로 -> 절대경로 보정
        abs_url = urljoin(base_url, src)
        urls.append(abs_url)

    # 3) 확장자 기반으로 "OCR 대상 이미지"만 필터링
    filtered: List[str] = []
    for u in urls:
        if _IMG_EXT_RE.search(u):
            filtered.append(u)

    # 4) 중복 제거(순서 유지)
    seen = set()
    out: List[str] = []
    for u in filtered:
        if u in seen:
            continue
        seen.add(u)
        out.append(u)

    return out


def _download_image_bytes(session: requests.Session, url: str, referer: str, timeout: int = 20) -> bytes:
    """
    이미지 바이너리(bytes)를 다운로드한다.

    - referer를 헤더로 넣는 이유:
      일부 서버는 hotlink 방지로 Referer가 없으면 403을 반환할 수 있음.
    - 실패하면 raise_for_status()로 예외 발생 → 호출부에서 처리
    """
    r = session.get(url, timeout=timeout, headers={"Referer": referer})
    r.raise_for_status()
    return r.content


def _ensure_ocr_ready() -> Optional[str]:
    """
    OCR 실행 가능 여부를 사전 점검한다.

    체크 항목:
    - pytesseract 설치 여부
    - Pillow(PIL) 설치 여부
    - (tesseract 바이너리 설치 여부는 pytesseract 호출 시점에 실패할 수 있음)

    반환:
    - 준비 OK면 None
    - 준비가 안 됐으면 에러 메시지(str)
    """
    if pytesseract is None:
        return "pytesseract가 설치되어 있지 않습니다. (requirements.txt에 pytesseract 추가 필요)"
    if Image is None:
        return "Pillow(PIL)가 설치되어 있지 않습니다. (requirements.txt에 pillow 추가 필요)"
    # tesseract 바이너리 자체가 없는 경우:
    # - pytesseract.image_to_string 호출 시 TesseractNotFoundError 등으로 터질 수 있음
    return None


def ocr_images_to_text(
    session: requests.Session,
    image_urls: List[str],
    *,
    referer: str,
    lang: str = "kor+eng",
    timeout: int = 20,
    max_images: int = 5,
) -> OcrResult:
    """
    여러 이미지에 대해 OCR을 수행한 뒤 결과 텍스트를 합쳐 반환한다.

    파라미터:
    - image_urls: OCR 후보 이미지 URL 목록
    - referer: 이미지 다운로드 시 Referer로 사용할 값(보통 job_url)
    - lang: tesseract 언어 설정(기본 kor+eng)
    - timeout: 이미지 다운로드/요청 타임아웃
    - max_images: OCR 수행 이미지 개수 상한
      (이미지 수가 많으면 OCR 시간이 급격히 증가하므로 상한을 두는 게 일반적)

    반환:
    - OcrResult(text=..., used_images=..., error=...)
    """
    # 0) OCR 실행 환경 체크
    ready_err = _ensure_ocr_ready()
    if ready_err:
        # OCR 불가면 결과는 빈 텍스트 + 에러 메시지
        return OcrResult(text="", used_images=image_urls[:max_images], error=ready_err)

    # 1) 실제로 사용할 이미지 목록(상한 적용)
    used = image_urls[:max_images]
    texts: List[str] = []

    # 2) 이미지별 OCR 수행
    for u in used:
        try:
            # (a) 이미지 다운로드
            b = _download_image_bytes(session, u, referer=referer, timeout=timeout)

            # (b) 바이트 -> PIL 이미지 로딩
            img = Image.open(io.BytesIO(b))

            # (c) 이미지 모드 보정
            # - tesseract가 RGB/L 모드에서 안정적으로 동작하는 편
            # - RGBA/CMYK 등은 RGB로 변환
            if img.mode not in ("RGB", "L"):
                img = img.convert("RGB")

            # (d) OCR 수행
            txt = pytesseract.image_to_string(img, lang=lang)

            # (e) 결과 정리
            txt = (txt or "").strip()
            if txt:
                texts.append(txt)

        except Exception as e:
            # 이미지 하나 OCR 실패해도 전체 파이프라인은 지속
            # (운영에서는 여기서 텍스트에 에러를 섞기보단 별도 필드로 모으는 방식도 고려)
            texts.append(f"[OCR_IMAGE_ERROR] url={u} err={e}")

    # 3) 여러 이미지 OCR 결과를 합침(빈 문자열 제외)
    combined = "\n\n".join(t for t in texts if t).strip()

    # 4) 결과 반환
    # - combined가 비어 있으면 에러 메시지 설정
    return OcrResult(
        text=combined,
        used_images=used,
        error=None if combined else "OCR 결과가 비어있습니다.",
    )


def looks_like_image_only_detail(detail_html: str) -> bool:
    """
    상세 HTML이 "이미지 위주"인지(= 텍스트 파싱이 거의 불가능한지) 대략 판정한다.

    판정 로직(경험칙):
    - script/style/noscript 제거 후
    - 전체 텍스트 길이가 매우 짧고(len(text) < 80)
    - img 태그가 1개 이상이면(img_count >= 1)
    => 이미지 중심 페이지로 판단

    용도:
    - parse_detail_sections 결과가 빈 경우뿐 아니라,
      HTML 구조상 텍스트가 거의 없는 "이미지 공고"에서 OCR fallback을 트리거하기 위함

    주의:
    - 완벽한 판정은 아님(텍스트가 짧은 정상 페이지도 있을 수 있음)
    - threshold(80)와 img_count 조건은 사이트/템플릿에 맞춰 조정 가능
    """
    soup = BeautifulSoup(detail_html, "lxml")

    # 텍스트 추출을 방해하는 태그 제거
    for t in soup(["script", "style", "noscript"]):
        t.decompose()

    # 전체 텍스트 길이(공백 기준으로 이어붙여 측정)
    text = soup.get_text(" ", strip=True)

    # 이미지 개수
    img_count = len(soup.select("img[src]"))

    # 경험칙 기반 판정
    return (len(text) < 80) and (img_count >= 1)
