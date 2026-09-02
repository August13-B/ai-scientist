# AI Scientist Backend

## Prerequisites

- JDK 17 and Maven 3.9+
- Docker Desktop (for Chroma)
- An Alibaba Cloud DashScope API key

## Configure secrets

In PowerShell, set the key for the current terminal only. Do not place it in `application.yml` or commit it:

```powershell
$env:DASHSCOPE_API_KEY = "your-key"
```

`.env.example` documents the non-secret configuration names. Keep the real key only in your terminal or an untracked local environment file.

## Run

```powershell
docker compose up -d
mvn spring-boot:run
```

Alternatively, after setting `DASHSCOPE_API_KEY`, Docker can build the Java application without a locally installed JDK or Maven:

```powershell
docker compose up --build
```

Check `http://localhost:8080/actuator/health`, then call the Qwen endpoint:

```powershell
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/llm/chat -ContentType 'application/json' -Body '{"prompt":"Give one concise AI research hypothesis."}'
```

`GET /api/llm/logs` returns non-sensitive call logs. The API key and prompt contents are intentionally never logged.

Import `postman/AI-Scientist.postman_collection.json` into Postman to run the validation sequence after startup.

For a command-line acceptance check, run `./scripts/verify.ps1` after the services start.

## Vector APIs

The default vector mode is `memory`, which supports the demo without Docker but is cleared on restart. Set `CHROMA_ENABLED=true` after Docker Hub access is restored to use Chroma persistence.

After Chroma and the backend are running, add cleaned text with metadata:

```powershell
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/vectors/documents -ContentType 'application/json' -Body '{"document":"Transformer models use self-attention.","metadata":{"source":"paper-001","year":2017}}'
```

Search the collection:

```powershell
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/vectors/search -ContentType 'application/json' -Body '{"query":"attention mechanism research","limit":3}'
```
