# Saramin Crawler

사람인(Saramin) 검색 결과(목록)에서 채용 공고를 수집하고, 옵션으로 상세 페이지(view-ajax → iframe view-detail)까지 파싱해 **JSON으로 저장**하는 크롤러입니다.  
상세가 이미지 기반으로 제공되는 경우(텍스트 거의 없음)에는 **OCR fallback(pytesseract + pillow)** 을 통해 텍스트를 추출하고, 섹션(주요업무/자격요건/근무조건 등) 단위로 분리합니다.

> ⚠️ 참고: 사람인 페이지 구조/정책은 변할 수 있습니다.  
> 본 프로젝트는 개인 학습/리서치 목적의 예시이며, 서비스 이용약관/robots.txt/법적 규정을 준수하세요.

---

## Features

- **목록 수집**
  - Saramin 검색 URL(필터 포함)을 입력하면 페이지 단위로 공고 수집
  - `recruitPage` 파라미터를 갱신해 다음 페이지로 이동
  - URL 기준 중복 제거

- **상세 수집 (옵션)**
  - `job_url` → `view-ajax` POST → HTML 응답에서 iframe src 추출 → iframe HTML GET
  - 상세 HTML에서 섹션(주요업무/자격요건 등) 구조 파싱
  - 파싱 실패/이미지 위주 상세일 경우 OCR fallback

- **OCR fallback (옵션)**
  - 이미지 기반 상세 페이지에서 이미지 URL 추출 후 OCR 수행
  - OCR 결과 텍스트를 헤더 기반으로 섹션 분리(표준 섹션명으로 정규화)

- **JSON 저장**
  - `JobPosting` dataclass를 `asdict()`로 직렬화하여 파일 저장

---

## Project Structure (예시)

> 파일명은 설명을 위한 예시입니다. 실제 프로젝트 구조에 맞게 조정하세요.

```
saramin/
  __init__.py
  models.py               # JobPosting, DetailContext
  http.py                 # make_session, fetch_html, post_form
  list_urls.py            # build_paged_url
  list_parser.py          # parse_list_page
  detail_context.py       # build_detail_context (rec_idx/seq, uuid, nonce)
  detail_fetcher.py       # fetch_detail_iframe_html (view-ajax → iframe)
  detail_parser.py        # parse_detail_sections, split_text_by_headings (SECTION_TITLE_MAP)
  ocr_image_parser.py     # OCR helpers (extract_image_urls, ocr_images_to_text)
  crawler.py              # crawl_list, enrich_jobs_with_details, save_json
main.py                   # CLI entry
```

---

## Requirements

- Python 3.10+ 권장
- 필수
  - `requests`
  - `beautifulsoup4`
  - `lxml`
- OCR 옵션 사용 시(optional)
  - `pytesseract`
  - `pillow`
  - (시스템) `tesseract-ocr` 바이너리 설치 필요

---

## Installation

### 1) 가상환경 생성(권장)

```bash
python -m venv .venv
source .venv/bin/activate
```

### 2) 패키지 설치

```bash
pip install -r requirements.txt
```

### 3) OCR 사용 시(옵션)

`requirements.txt`에 아래를 포함하세요.

- `pytesseract`
- `pillow`

그리고 OS에 tesseract 설치가 필요합니다.

**macOS (Homebrew)**

```bash
brew install tesseract
# 환경에 따라 언어팩이 별도인 경우
brew install tesseract-lang
```

---

## Usage (CLI)

### 기본: 목록 1페이지 수집 → JSON 저장

```bash
python main.py
```

기본 저장 파일: `saramin_jobs.json`

### 목록 여러 페이지 수집

```bash
python main.py --pages 3
```

### 상세까지 수집 (view-ajax → iframe view-detail)

```bash
python main.py --detail
```

### 상세 수집 개수 제한

```bash
python main.py --detail --detail-limit 20
```

### OCR 활성화 (이미지 기반 상세 fallback)

```bash
python main.py --detail --ocr
```

### OCR 설정

```bash
python main.py --detail --ocr --ocr-lang kor+eng --ocr-max-images 5
```

### 상세 HTML 원문까지 JSON에 포함(파일 커짐 주의)

```bash
python main.py --detail --save-detail-html
```

### 디버그 출력

```bash
python main.py --debug
```

---

## CLI Options

| 옵션                 | 설명                       | 기본값              |
| -------------------- | -------------------------- | ------------------- |
| `--url`              | 사람인 검색 URL(필터 포함) | `DEFAULT_URL`       |
| `--pages`            | 목록 크롤링 페이지 수      | `1`                 |
| `--list-delay`       | 목록 요청 간 딜레이(초)    | `1.8`               |
| `--detail`           | 상세 수집 활성화           | false               |
| `--detail-limit`     | 상세 수집 개수 제한        | None(전체)          |
| `--detail-delay`     | 상세 요청 간 딜레이(초)    | `1.2`               |
| `--save-detail-html` | 상세 HTML을 JSON에 포함    | false               |
| `--ocr`              | OCR 활성화                 | false               |
| `--ocr-lang`         | OCR 언어                   | `kor+eng`           |
| `--ocr-max-images`   | OCR 이미지 상한            | `5`                 |
| `--debug`            | 디버그 출력                | false               |
| `--out`              | 저장 파일명                | `saramin_jobs.json` |

---

## Output JSON Schema

각 항목은 `JobPosting` dataclass 직렬화 결과입니다.

예시:

```json
[
  {
    "title": "프론트엔드 개발자",
    "company": "OO테크",
    "url": "https://www.saramin.co.kr/zf_user/jobs/relay/view?...",
    "location": "서울 강남구",
    "job_condition": "경력 3~7년",
    "sector": "웹개발",
    "deadline": "2026-02-20 마감",
    "scraped_at": 1738800000.0,
    "source_page": 1,
    "detail_iframe_url": "https://www.saramin.co.kr/zf_user/jobs/relay/view-detail?...",
    "detail_sections": {
      "주요업무": "...",
      "자격요건": "...",
      "우대사항": "...",
      "근무조건": "..."
    },
    "detail_html": null
  }
]
```

### `detail_sections` 키 정책

- 가능한 경우 `"주요업무"`, `"자격요건"`, `"근무조건"` 등 **표준 섹션명**으로 정규화됩니다.
  - `detail_parser.py`의 `SECTION_TITLE_MAP`에서 **표준명(key)** 과 **동의어/변형(value)** 를 관리합니다.
- 파싱/OCR 과정에서 문제가 생기면 다음 메타 키가 포함될 수 있습니다.
  - `"_error"`: 상세 수집 단계 예외 메시지
  - `"_ocr_error"`: OCR 준비/실행 실패 메시지
  - `"_ocr_images"`: OCR에 사용된 이미지 URL 목록(줄바꿈 join)

---

## How it Works

### 1) 목록 수집: `crawl_list()`

- `build_paged_url()`로 `recruitPage`를 1..N으로 갱신하며 목록 페이지 요청
- `parse_list_page()`가 제목/회사/링크 등 기본 메타 추출
- URL 기준 중복 제거

### 2) 상세 수집: `enrich_jobs_with_details()`

- `fetch_detail_iframe_html()`
  - `build_detail_context()`로 `rec_idx`, `rec_seq`, `search_uuid`, `referNonce` 등을 확보
  - `/zf_user/jobs/relay/view-ajax`로 POST → 응답 HTML에서 iframe src 추출
  - iframe URL로 상세 HTML GET
- `parse_detail_sections()`로 섹션 파싱
- 이미지 기반 상세면 `looks_like_image_only_detail()`에 의해 OCR fallback 트리거

### 3) OCR fallback (옵션)

- `extract_image_urls()`로 상세 HTML 내 이미지 src 수집 + 필터링
- `ocr_images_to_text()`로 이미지들을 OCR → 텍스트 결합
- `split_text_by_headings()`로 헤더 기반 섹션 분리(표준 섹션명으로 정규화)

---

## Notes / Troubleshooting

### 한글 깨짐

- `http._decode_response()`에서 `r.content.decode()`를 직접 수행하여 requests의 인코딩 오판을 완화합니다.

### 403/차단/빈 응답

- Referer/Origin/X-Requested-With 등의 헤더가 중요합니다.
- 딜레이(`--list-delay`, `--detail-delay`)를 늘려보세요.
- 디버그(`--debug`)로 `iframe_url`, 섹션 키 등을 확인하세요.

### OCR이 동작하지 않음

- `pytesseract`, `pillow` 설치 여부 확인
- OS에 `tesseract-ocr` 바이너리 설치 확인
- 언어팩(ko) 설치 여부 확인 (`kor+eng` 사용 시)

---

## License

- 개인 프로젝트/학습용 예시입니다. 필요에 맞게 라이선스를 명시하세요. (MIT 등)
