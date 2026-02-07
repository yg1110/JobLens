# JobLens — 채용공고 자동 수집·필터링·의사결정 지원 (진행중)

사람인 채용공고를 수집하고, **스코어링 엔진**으로 추천/보류/제외를 판단한 뒤 **이메일 알림**을 발송하는 개인용 의사결정 지원 시스템입니다.

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

- **crawler**: 사람인 목록/상세 수집 → JSON 저장, FastAPI로 `/jobs`·`/crawl` 제공
- **api**: 크롤러 `GET /jobs` 호출 → 스코어링 → DB upsert / 이메일 알림

---

## 프로젝트 구조

| 디렉터리 | 설명 |
|----------|------|
| [`crawler/`](crawler/README.md) | Python 크롤러 (사람인 목록·상세, OCR fallback, FastAPI) |
| [`api/`](api/README.md) | Spring Boot API (스코어링, bulk 저장, 이메일 스케줄러) |

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

- **크롤러**가 `saramin_jobs.json`에 공고 저장
- **API**가 `GET /jobs`로 공고 조회 → bulk 저장 또는 스코어링
- **스케줄러**: 매시 fetch → 80점 초과 시 즉시 메일, 매일 09:00 digest

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
- 이메일 알림: 80점 초과 즉시, 70점 이상 digest

### 스코어링 (100점)

| 항목 | 최대 | 설명 |
|------|------|------|
| A_location | 15 | 근무지 (서울/경기/대전 우선) |
| B_employment | 15 | 정규직 > 파견/프리랜서 > 전환형인턴 > 인턴 |
| C_role_fit | 20 | 프론트엔드 > 풀스택 > 백엔드 |
| D_experience_fit | 10 | 3~6년 > 신입/경력 > 7년↑ |
| E_stack_fit | 15 | React/Next > Node/Nest > Spring > Java > JSP > PHP |
| F_domain | 10 | 제품/플랫폼 > 혼합 > 금융SI |
| G_culture | 10 | 복지/워라밸 키워드 |
| H_jd_quality | 5 | JD 상세 분량·섹션 수 |

**Hard Filter (즉시 제외)**

- 계약직/기간제/계약 사원
- 비개발 직무 (마케팅, 디자이너, 영업, 기획자, PM 등)

**판정**: 70점↑ 추천 / 50~69 보류 / 50↓ 비추천

---

## Explainable Logs

스코어링 결과에 breakdown과 flags가 포함됩니다.

```json
{
  "decision": "추천",
  "totalScore": 78,
  "excluded": false,
  "breakdown": {
    "A_location": { "score": 15, "reason": "서울/경기/대전 근무지" },
    "B_employment": { "score": 15, "reason": "정규직" },
    "C_role_fit": { "score": 20, "reason": "프론트엔드 중심 포지션" },
    "D_experience_fit": { "score": 10, "reason": "타깃 3~6년 경력" },
    "E_stack_fit": { "score": 13, "reason": "스택 트랙: E2" },
    "F_domain": { "score": 7, "reason": "제품 + SI/금융 등 혼합 도메인" },
    "G_culture": { "score": 6, "reason": "복지/워라밸 신호 3개 이상" },
    "H_jd_quality": { "score": 2, "reason": "JD 정보가 보통 수준" }
  },
  "flags": []
}
```

---

## 상세 문서

- [crawler/README.md](crawler/README.md) — 크롤러 CLI·API, OCR, 구조
- [api/README.md](api/README.md) — API 엔드포인트, 환경변수, 스케줄러
