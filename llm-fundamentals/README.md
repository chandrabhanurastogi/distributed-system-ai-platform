# LLM Fundamentals Service

Service demonstrating LLM integration using Ollama chat APIs, structured response processing, and tool calling loops.

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
| `POST` | `/chat/weather` | Raw text prompt, e.g. `What is the weather in Paris right now?` | `200 OK` | Final natural language answer incorporating executed tool output (`"The current weather in Paris is sunny, with a temperature of 22°C."`) |
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

#### Tool Calling Loop (End-to-End Weather Agent Round-Trip):
```bash
curl -i -X POST http://localhost:8082/chat/weather \
  -H "Content-Type: text/plain" \
  -d "What is the weather in Paris right now?"
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

---

## Exploring Ollama's Raw API Directly (bypassing this service)

These hit Ollama's own API on `http://localhost:11434` directly — no Java code
involved. Useful for seeing the *actual* wire format before (or instead of) trusting
what any DTO claims it looks like; several real bugs and misunderstandings this phase
were caught exactly this way, not by reading documentation.

#### Basic chat completion (see the real response shape, including token/usage fields):
```bash
curl -s http://localhost:11434/api/chat -d '{
  "model": "llama3.2",
  "messages": [{"role": "user", "content": "Reply with exactly one word: hello"}],
  "stream": false
}'
```

#### Inspect a model's chat template (why `prompt_eval_count` is never just "the words you typed"):
```bash
ollama show llama3.2 --template
```

#### Structured output — schema-constrained generation via the `format` field:
```bash
curl -s http://localhost:11434/api/chat -d '{
  "model": "llama3.2",
  "messages": [{"role": "user", "content": "Extract the name and age from: John is 30 years old."}],
  "stream": false,
  "format": {
    "type": "object",
    "properties": {
      "name": {"type": "string"},
      "age": {"type": "integer"}
    },
    "required": ["name", "age"]
  }
}'
```
Note: the result lands in `message.content` as a **string** containing JSON text — a
second, explicit parse is required, it is not automatically a nested object.

#### Tool-calling — describing a function and getting a structured tool-call request back:
```bash
curl -s http://localhost:11434/api/chat -d '{
  "model": "llama3.2",
  "messages": [{"role": "user", "content": "What is the weather like in Paris?"}],
  "stream": false,
  "tools": [
    {
      "type": "function",
      "function": {
        "name": "get_weather",
        "description": "Get the current weather for a given city",
        "parameters": {
          "type": "object",
          "properties": {
            "city": {"type": "string", "description": "The city name"}
          },
          "required": ["city"]
        }
      }
    }
  ]
}'
```
Note: unlike structured output, `message.tool_calls[].function.arguments` comes back
as a **real nested JSON object**, not a string — no second parse needed for this one.
Ollama is inconsistent between the two features; verify, don't assume symmetry.
