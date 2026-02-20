# JobLens API

사람인(Saramin) 크롤러에서 수집한 채용 공고를 DB에 저장하고, **스코어링 엔진**으로 추천 여부를 판단한 뒤 **이메일 알림**을 발송하는 Spring Boot REST API입니다.

---

## Features

- **채용 공고 bulk 저장**: 크롤러 `GET /jobs` API 호출 → JSON → DB upsert
- **스코어링 엔진**: 키워드 기반 점수화(근무지·고용형태·역할·경력·스택·도메인·복지·JD 품질) → 추천/보류/비추천 판정
- **이메일 알림**
  - **매시간(08~21시)**: 크롤러 fetch → 스코어 → 80점 초과 시 즉시 메일 1통 발송
  - **매일 09:00**: digest 메일 (70점 이상·즉시 미발송 건 1통)
- **Swagger UI**: `/swagger-ui.html`로 API 문서·테스트 제공
- **ShedLock**: 분산 환경에서 스케줄러 중복 실행 방지

---

## Project Structure

```
api/
  src/main/java/com/joblens/api/
    ApiApplication.java
    config/
      SchedulerLockConfig.java          # ShedLock JDBC Lock Provider
    email/
      service/EmailService.java
      web/
        EmailController.java            # POST /api/emails/test (테스트 메일)
        dto/TestEmailRequest.java
    jobposting/
      client/
        CrawlerClient.java              # 크롤러 GET /jobs 호출
        CrawlerClientException.java
      domain/
        JobPosting.java
        JobPostingNotification.java     # 알림 발송 이력
        JobPostingNotificationRepository.java
        JobPostingRepository.java
      notification/
        JobPostingNotificationService.java
        NotificationProperties.java
        NotificationTriggerController.java  # POST /api/notifications/trigger/*
      scheduler/
        JobPostingEmailScheduler.java   # 매시·매일 cron
      service/
        JobPostingService.java
        ScoringService.java
        ScoringKeywordsProperties.java
      web/
        JobPostingController.java       # POST /api/job-postings/bulk, /score
        dto/*
```

---

## Requirements

- Java 17+
- PostgreSQL
- Python 크롤러 서버 (기본 `http://localhost:8000`)
- SMTP 서버 (이메일 발송 시)

---

## Configuration

### 1) 환경 변수 / .env

| 변수                      | 설명                      | 기본값                                     |
| ------------------------- | ------------------------- | ------------------------------------------ |
| `POSTGRES_DB`             | JDBC URL                  | `jdbc:postgresql://localhost:5432/joblens` |
| `POSTGRES_USER`           | DB 사용자                 | `joblens`                                  |
| `POSTGRES_PASSWORD`       | DB 비밀번호               | **(필수)**                                 |
| `SMTP_HOST`               | SMTP 호스트               | `smtp.gmail.com`                           |
| `SMTP_PORT`               | SMTP 포트                 | `587`                                      |
| `SMTP_USERNAME`           | SMTP 계정                 | -                                          |
| `SMTP_PASSWORD`           | SMTP 비밀번호             | -                                          |
| `NOTIFICATION_ENABLED`    | 알림 스케줄러 on/off      | `true`                                     |
| `NOTIFICATION_RECIPIENTS` | 수신자 이메일 (쉼표 구분) | -                                          |

`.env` 파일은 프로젝트 루트 또는 `api/` 디렉터리에 두면 `dotenv-java`로 자동 로드됩니다.

### 2) application.yml 요약

- `joblens.scoring.*`: 스코어링 키워드 (role, employment, stack, domain 등)
- `joblens.notification.*`: 임계값(immediate=80, digest=70), quietHours, recipients, crawlerFile
- `joblens.crawler.base-url`: 크롤러 API 주소 (미설정 시 `http://localhost:8000`)

---

## Installation

### 1) PostgreSQL 준비

Docker Compose 사용 시:

```bash
cd api
docker-compose up -d
```

`.env` 또는 환경변수로 `DB_PASSWORD` 설정 후:

```bash
export DB_PASSWORD=your_password
docker-compose up -d
```

### 2) 빌드 및 실행

```bash
./gradlew bootRun
```

또는 `spring-boot-docker-compose`가 있으면 DB 자동 기동 후 실행됩니다.

### 3) Docker로 실행

**API 단일 이미지 (DB는 별도 필요)**

```bash
cd api
docker build -t joblens-api .
docker run -p 8080:8080 \
  -e POSTGRES_DB=jdbc:postgresql://host.docker.internal:5432/joblens \
  -e POSTGRES_USER=joblens \
  -e POSTGRES_PASSWORD=your_password \
  joblens-api
```

**프로젝트 루트에서 Crawler + API + PostgreSQL 통합 실행**

```bash
# 프로젝트 루트(JobLens/)에서
export DB_PASSWORD=your_db_password   # 필수
docker compose up -d
```

- API: `http://localhost:8080`, Crawler: `http://localhost:8000`, PostgreSQL: `localhost:5432`

---

## Usage

### API 엔드포인트

| Method | Path                                | 설명                                          |
| ------ | ----------------------------------- | --------------------------------------------- |
| POST   | `/api/job-postings/bulk`            | 크롤러에서 공고 fetch → DB upsert             |
| POST   | `/api/job-postings/score`           | 크롤러에서 공고 fetch → 스코어링 후 JSON 반환 |
| POST   | `/api/emails/test`                  | 테스트 메일 발송                              |
| POST   | `/api/notifications/trigger/hourly` | 매시 작업 수동 실행 (테스트용)                |
| POST   | `/api/notifications/trigger/digest` | digest 발송 수동 실행 (테스트용)              |

### 예시

**공고 bulk 저장**

```bash
curl -X POST "http://localhost:8080/api/job-postings/bulk?file=saramin_jobs.json"
```

**스코어링**

```bash
curl -X POST "http://localhost:8080/api/job-postings/score?file=saramin_jobs.json"
```

**테스트 메일**

```bash
curl -X POST "http://localhost:8080/api/emails/test" \
  -H "Content-Type: application/json" \
  -d '{"to":"your@email.com","subject":"테스트","body":"본문"}'
```

### Swagger UI

실행 후 `http://localhost:8080/swagger-ui.html` 에서 API 문서·요청 테스트 가능.

---

## Scoring Engine

총점 100점은 **E_stack_fit(기술 스택)**만 사용합니다.  
나머지 항목(A~D, F~H)은 breakdown에만 표시됩니다.

| 항목             | 설명                                                                  |
| ---------------- | --------------------------------------------------------------------- |
| E_stack_fit      | 기술 스택만 총점 반영 (만점 100). e1(100)>e2(80)>e3(60)>e4(40)>e5(20) |
| A_location       | 근무지 (서울/경기/인천 우선) — 참고용                                 |
| B_employment     | 고용형태 — 참고용                                                     |
| C_role_fit       | 역할 — 참고용                                                         |
| D_experience_fit | 경력 — 참고용                                                         |
| F_domain         | 도메인 — 참고용                                                       |
| G_culture        | 복지/워라밸 — 참고용                                                  |
| H_jd_quality     | JD 품질 — 참고용                                                      |

**Hard Filter** (적용 시 즉시 제외, 0점):

- 계약직/기간제/계약 사원

**판정**

- 70점 이상: 추천
- 50~69점: 보류
- 50점 미만: 비추천

---

## Scheduler

| cron                      | 작업                          | 설명                                    |
| ------------------------- | ----------------------------- | --------------------------------------- |
| 매시 08~21시 (Asia/Seoul) | `hourlyFetchAndImmediateSend` | fetch → 스코어 → 80점 초과 시 즉시 메일 |
| 매일 09:00                | `dailyDigestSend`             | 70점 이상·digest 미포함 건 1통 발송     |

- 22:00~08:00: quietHours로 즉시 발송 스킵
- ShedLock으로 다중 인스턴스 시 스케줄 중복 실행 방지

---

## Notes

- 크롤러 서버가 `http://localhost:8000`에서 실행 중이어야 bulk/score API가 동작합니다.
- 이메일 발송은 `.env`의 SMTP 설정이 필요합니다. (Gmail: 앱 비밀번호 사용)
- 알림을 끄려면 `NOTIFICATION_ENABLED=false` 또는 `joblens.notification.enabled: false` 설정.
