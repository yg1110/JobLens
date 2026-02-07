# JobLens API — Spring Boot (로컬 실행용)
# 1) 빌드 스테이지
FROM eclipse-temurin:17-jdk AS builder

WORKDIR /build

COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .
RUN chmod +x gradlew

# 의존성만 먼저 다운로드 (캐시 활용)
RUN ./gradlew dependencies --no-daemon || true

COPY src src
RUN ./gradlew bootJar --no-daemon

# 2) 실행 스테이지
FROM eclipse-temurin:17-jre

WORKDIR /app

COPY --from=builder /build/build/libs/*.jar app.jar

EXPOSE 8080

# DB/크롤러 URL 등은 docker-compose 또는 환경변수로 주입
ENTRYPOINT ["java", "-jar", "app.jar"]
