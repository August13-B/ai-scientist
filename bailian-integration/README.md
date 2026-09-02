# Bailian Integration Service

Standalone Spring Boot service for Qwen chat, DashScope embeddings, vector ingestion/search, and safe API-call logs.

## Prerequisites

- JDK 17 and Maven 3.9+
- Alibaba Cloud DashScope API key

## Run

```powershell
$env:DASHSCOPE_API_KEY = "your-key"
mvn spring-boot:run
```

Check `http://localhost:8080/actuator/health` after startup. The default vector mode is local memory for demonstrations; set `CHROMA_ENABLED=true` when a Chroma service is available.

## APIs

- `POST /api/llm/chat`: call Qwen
- `POST /api/vectors/documents`: embed and store a text document
- `POST /api/vectors/search`: semantic search
- `GET /api/llm/logs`: retrieve non-sensitive call logs
