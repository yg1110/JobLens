# Saramin Crawler — Python FastAPI (로컬 실행용)
FROM python:3.12-slim

# OCR fallback 사용 시 tesseract (선택)
RUN apt-get update && apt-get install -y --no-install-recommends \
    tesseract-ocr \
    tesseract-ocr-kor \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY . .

# API 서버 포트
EXPOSE 8000

# FastAPI 서버 실행 (호스트 0.0.0.0으로 해야 컨테이너 외부에서 접근 가능)
CMD ["uvicorn", "api_app:app", "--host", "0.0.0.0", "--port", "8000"]
