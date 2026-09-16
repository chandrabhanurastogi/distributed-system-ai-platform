# LLM Fundamentals Service

Service demonstrating LLM integration using Ollama chat APIs and structured response processing.

- **Dedicated Port**: `8082`
- **LLM Provider**: Local Ollama instance (`http://localhost:11434`, model: `llama3.2`)

---

## Running the Service

Ensure Ollama is running and has the `llama3.2` model installed:
```bash
ollama run llama3.2
```

Start the application:
```bash
./gradlew :llm-fundamentals:bootRun
```

---

## API Contract & Documentation

Interactive API UI (Swagger / OpenAPI):
- **Swagger UI**: [http://localhost:8082/swagger-ui/index.html](http://localhost:8082/swagger-ui/index.html)
- **OpenAPI Schema (JSON)**: [http://localhost:8082/v3/api-docs](http://localhost:8082/v3/api-docs)

### Endpoints Contract

| Method | Path | Request Body | Response Status | Response Body |
|---|---|---|---|---|
| `POST` | `/chat` | `[{"role": "user", "content": "Explain recursion in one short sentence."}]` | `200 OK`<br>`503 Service Unavailable` | Plain text reply from model<br>`{"error": "LLM backend unavailable: <details>"}` if Ollama is unreachable |
| `POST` | `/extract-person` | Raw text, e.g. `John is 30 years old.` | `200 OK` | `{"name": "John", "age": 30}` — schema-constrained structured output (Ollama's `format` field), deserialized into a typed object |
| `GET` | `/actuator/health` | *(None)* | `200 OK` | `{"status": "UP", "components": {...}}` |

---

## Testing Endpoints

### 1. Using Browser (Chrome)
- **Swagger UI (Interactive API Testing & Try-it-out)**: Open [http://localhost:8082/swagger-ui/index.html](http://localhost:8082/swagger-ui/index.html)
- **Health Check**: Open [http://localhost:8082/actuator/health](http://localhost:8082/actuator/health)

### 2. Using `curl`

#### Send Single-Turn Chat Prompt:
```bash
curl -i -X POST http://localhost:8082/chat \
  -H "Content-Type: application/json" \
  -d '[
    {"role": "user", "content": "Explain recursion in one short sentence."}
  ]'
```

#### Send Multi-Turn Conversation History:
```bash
curl -i -X POST http://localhost:8082/chat \
  -H "Content-Type: application/json" \
  -d '[
    {"role": "user", "content": "My favourite color is blue. Acknowledge in one short sentence."},
    {"role": "assistant", "content": "Got it, your favourite color is blue."},
    {"role": "user", "content": "What did I say my favourite color was?"}
  ]'
```

#### Structured Output — Extract a Person from Free Text:
```bash
curl -i -X POST http://localhost:8082/extract-person \
  -d "John is 30 years old."
```

#### Test 503 (Ollama Unreachable) — point at a port nothing is listening on:
```bash
./gradlew :llm-fundamentals:bootRun --args='--ollama.base-url=http://localhost:19999'
curl -i -X POST http://localhost:8082/chat -H "Content-Type: application/json" -d '[{"role":"user","content":"hi"}]'
```
