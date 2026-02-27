# JobLens — 채용공고 자동 수집·필터링·의사결정 지원 (진행중)

사람인·잡코리아 채용공고를 수집하고, **스코어링 엔진**으로 추천/보류/제외를 판단한 뒤 **이메일 알림**을 발송하는 개인용 의사결정 지원 시스템입니다.

> 목표: "하루 15분 안에 지원 의사결정 끝내기"

---

## 아키텍처

```
┌─────────────┐     GET /jobs      ┌─────────────┐
│   Crawler   │ ─────────────────► │     API     │
│  (Python)   │   saramin_jobs.json│  (Spring)   │
│  :8000      │                    │  :8080      │
└─────────────┘                    └──────┬──────┘
       │                                  │
       │ POST /crawl                      │ Scoring
       │ (옵션: API에서 트리거)            │ → DB 저장
       │                                  │ → 이메일 발송
       ▼                                  ▼
  사람인 사이트                    PostgreSQL + SMTP
```

- **crawler**: 사람인·잡코리아 목록/상세 수집 → `saramin_jobs.json`, `jobkorea_jobs.json` 저장, FastAPI로 `/crawl/saramin`, `/crawl/jobkorea`, `/jobs/saramin`, `/jobs/jobkorea` 제공
- **api**: 크롤러 `GET /jobs/saramin`, `GET /jobs/jobkorea` 호출 → 스코어링 → DB upsert / 이메일 알림

---

## 프로젝트 구조

| 디렉터리                        | 설명                                                                 |
| ------------------------------- | -------------------------------------------------------------------- |
| [`crawler/`](crawler/README.md) | Python 크롤러 (사람인·잡코리아 목록·상세, OCR fallback, FastAPI)     |
| [`api/`](api/README.md)         | Spring Boot API (사람인·잡코리아 통합 스코어링, bulk 저장, 이메일 스케줄러) |

---

## 기능 소개 (스크린샷)

### Crawler — 목록·상세 수집

- 목록 수집(페이지네이션), 상세 페이지 파싱, OCR fallback(이미지 기반 상세)

```python
{
  "title": "IT/디지털 분야 헤드헌터 모집(신입/경력)",
  "company": "(주)스카우트플랜",
  "url": "https://www.saramin.co.kr/zf_user/jobs/relay/view?view_type=search&rec_idx=53039222&location=ts&searchType=search&paid_fl=n&search_uuid=b907b823-2ab5-46ec-a0a8-20409eb44024",
  "location": "서울 강남구",
  "job_condition": "서울 강남구 경력무관 대졸↑ 위촉직·프리랜서",
  "sector": "프론트엔드 , 기술지원 , 정보보안 , API , 데이터분석가 외 등록일 26/02/07",
  "deadline": "채용시",
  "scraped_at": 1770424243.686791,
  "source_page": 1,
  "detail_iframe_url": "https://www.saramin.co.kr/zf_user/jobs/relay/view-detail?rec_idx=53039222&rec_seq=1&t_category=non-logged_relay_view&t_content=view_detail&t_ref=non-logged_relay_view&t_ref_content=SRI_050_VIEW_MTRX_RCT",
  "detail_sections": {
    "상세": "IT/디지털 분야 헤드헌터 모집(신입/경력)",
    "주요업무": "• 신규 고객사 발굴 및 관계관리\n• 후보자 서치, 검증, 추천",
    "자격요건": "• 4년 대졸이상\n• 신입 :\n국내/외 우수기업 출신으로 현업경력 5년 이상\n• 고객지향적 비즈니스 마인드를 보유\n• 일에 대한 프로의식\n• 커뮤니케이션 능력 우수자",
    "고용형태": "프리랜서, 위촉직",
    "근무지": "서울 강남구 테헤란로 311 아남타워빌딩\n• 근무요일/시간 : 유연근무제",
    "채용절차": "서류전형 → 면접전형 → 최종합격",
    "접수기간": "2026-02-07 09시 ~ 채용시",
    "제출서류": "사람인 온라인 이력서",
    "접수방법": "사람인 입사지원",
    "안내사항": "• 입사지원 서류에 허위사실이 발견될 경우, 채용확정 이후라도 채용이 취소될 수 있습니다."
  },
  "detail_html": null
}
```

### Crawler — API (FastAPI)

- `POST /crawl/saramin`, `POST /crawl/jobkorea`, `GET /jobs/saramin`, `GET /jobs/jobkorea` 문서화 (`/docs`)

![크롤러 API 문서](/img/python-api.png)

### API — 스코어링 결과

- 추천/보류/비추천 판정, 항목별 점수 breakdown

```json
{
  "decision": "추천",
  "excluded": false,
  "totalScore": 71,
  "breakdown": {
    "A_location": {
      "code": "A",
      "name": "location",
      "reason": "서울/경기/인천 근무지"
    },
    "B_employment": {
      "code": "B",
      "name": "employment",
      "reason": "정규직"
    },
    "C_role_fit": {
      "code": "C",
      "name": "role_fit",
      "reason": "프론트엔드 중심 포지션"
    },
    "D_experience_fit": {
      "code": "D",
      "name": "experience_fit",
      "reason": "신입·경력 혼합"
    },
    "E_stack_fit": {
      "code": "E",
      "name": "stack_fit",
      "reason": "스택 트랙: E4"
    },
    "F_domain": {
      "code": "F",
      "name": "domain",
      "reason": "도메인 정보 부족"
    },
    "G_culture": {
      "code": "G",
      "name": "culture",
      "reason": "근무제/복지/워라밸 관련 정보 부족"
    },
    "H_jd_quality": {
      "code": "H",
      "name": "jd_quality",
      "reason": "상세 JD 분량 및 섹션이 충분함"
    }
  },
  "matchedStackKeywords": [
    "React",
    "TypeScript",
    "Next.js"
  ],
  "matchedKeywords": {
    "D_experience_fit": [
      "신입",
      "신입/경력"
    ],
    "E_stack_fit": [
      "Java"
    ],
    "H_jd_quality": [
      "length=0",
      "sections=14"
    ],
    "A_location": [
      "서울 영등포구"
    ],
    "C_role_fit": [
      "프론트엔드",
      "frontend"
    ],
    "B_employment": [
      "정규직"
    ]
  },
  "flags": []
}
```

### API — 이메일 알림

- 매시 80점 초과 즉시 메일, 매일 09:00 digest

![이메일 알림 예시1](/img/email1.png)

![이메일 알림 예시2](/img/email2.png)

### API — Swagger UI

- bulk 저장, score, 테스트 메일 등 엔드포인트 (`/swagger-ui.html`)

![API Swagger UI](/img/python-api.png)

---

## Quick Start

### Docker로 통합 실행 (권장)

프로젝트 루트에서 Crawler + API + PostgreSQL을 한 번에 띄웁니다.

```bash
# 루트(JobLens/)에서
export DB_PASSWORD=your_db_password   # 필수. SMTP 등은 선택: SMTP_USERNAME, SMTP_PASSWORD 등
docker compose up -d
```

또는 루트에 `.env` 파일을 만들고 `DB_PASSWORD=your_db_password` 를 넣으면 `docker compose up -d` 만 실행하면 됩니다.

- **Crawler**: http://localhost:8000 (문서: `/docs`)
- **API**: http://localhost:8080 (Swagger: `/swagger-ui.html`)
- **PostgreSQL**: localhost:5432

중지: `docker compose down`

---

### 수동 실행 (로컬에 Python + Java 설치)

#### 1) Crawler 실행

```bash
cd crawler
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
# 목록 + 상세 + OCR: python main.py --detail --ocr
python main.py --detail --out saramin_jobs.json
# API 서버 (api에서 호출용):
uvicorn api_app:app --port 8000
```

#### 2) API 실행

```bash
cd api
# .env 에 POSTGRES_PASSWORD, SMTP_*, NOTIFICATION_RECIPIENTS 등 설정
docker-compose up -d   # PostgreSQL
./gradlew bootRun
```

#### 3) 기본 흐름

- **크롤러**가 `saramin_jobs.json`, `jobkorea_jobs.json`에 공고 저장(또는 FastAPI로 병합 저장)
- **API**가 `GET /jobs/saramin`, `GET /jobs/jobkorea`로 공고 조회 → 통합 후 bulk 저장 또는 스코어링
- **스케줄러**: 매시 사람인·잡코리아 크롤링 트리거 + fetch → 80점 초과 시 즉시 메일, 매일 09:00 digest

---

## 왜 만들었나 (Problem)

- 사람인 신규 공고가 매일 올라오고, 수동 확인·정리 비용이 큼
- 공고 상세에 **이미지로 된 텍스트**가 많아 HTML 파싱만으로는 누락 발생 → **OCR fallback** 필요
- "지원할 공고"를 빠르게 추리기 위해 **설명 가능한(Explainable)** 점수 breakdown 제공

---

## 목표 & 범위

### Outcome

- 신규 공고 자동 수집 (목록 + 상세, OCR 포함)
- 자동 분류: **추천 / 보류 / 제외** + 점수 breakdown (A~H 항목별 사유)
- 이메일 알림: 80점 이상 즉시 발송, 70점 이상 digest(매일 09:00)

### 스코어링 (100점)

총점은 **A_location(10) + C_role_fit(30) + E_stack_fit(60)** 만 반영합니다. B, D, F, G, H는 breakdown(참고)용입니다.

| 항목             | 최대(총점 반영) | 설명                                                         |
| ---------------- | ---------------- | ------------------------------------------------------------ |
| A_location       | 10               | 근무지 (서울/경기/인천) — **총점 반영**                      |
| B_employment     | 15               | 정규직 > 파견/프리랜서 > 전환형인턴 > 인턴 — 참고용           |
| C_role_fit       | 30               | 풀스택 > 프론트 > 앱 > 백엔드 — **총점 반영**                |
| D_experience_fit | 10               | 3~6년 > 신입/경력 > 7년↑ — 참고용                            |
| E_stack_fit      | 60               | e1(60)>e2(48)>e3(36)>e4(24)>e5(12) — **총점 반영**          |
| F_domain         | 10               | 제품/플랫폼 > 혼합 > 금융SI — 참고용                          |
| G_culture        | 10               | 복지/워라밸 키워드 — 참고용                                  |
| H_jd_quality     | 5                | JD 상세 분량·섹션 수 — 참고용                                |

**Hard Filter (즉시 제외)**

- 계약직/기간제/계약 사원

**판정**: 70점↑ 추천 / 50~69 보류 / 50↓ 비추천

---

## Explainable Logs

스코어링 결과에는 위 예시처럼 `breakdown`(A~H 항목별 사유), `matchedKeywords`, `matchedStackKeywords`, `flags`가 함께 포함되어 **왜 이 점수가 나왔는지**를 확인할 수 있습니다.

---

## 상세 문서

- [crawler/README.md](crawler/README.md) — 크롤러 CLI·API, OCR, 구조
- [api/README.md](api/README.md) — API 엔드포인트, 환경변수, 스케줄러
