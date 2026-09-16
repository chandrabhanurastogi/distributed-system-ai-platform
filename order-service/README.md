# Order Service

Responsible for creating and retrieving orders within the distributed platform.

- **Dedicated Port**: `8080`
- **Database**: PostgreSQL (`order-db` on port `5433`)

---

## Running the Service

Ensure `order-db` is running via Docker Compose first:
```bash
docker compose -f docker/docker-compose.yml up -d order-db
```

Start the application:
```bash
./gradlew :order-service:bootRun
```

---

## API Contract & Documentation

Interactive API UI (Swagger / OpenAPI):
- **Swagger UI**: [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)
- **OpenAPI Schema (JSON)**: [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)

### Endpoints Contract

| Method | Path | Request Body | Response Status | Response Body |
|---|---|---|---|---|
| `POST` | `/orders` | *(None)* | `201 Created` | `{"id": 1, "status": "CREATED", "createdAt": "2026-09-16T10:00:00Z"}` *(Headers: `Location: /orders/1`)* |
| `GET` | `/orders/{id}` | *(None)* | `200 OK`<br>`404 Not Found` | `{"id": 1, "status": "CREATED", "createdAt": "2026-09-16T10:00:00Z"}`<br>`{"error": "Order not found: <id>"}` |
| `GET` | `/actuator/health` | *(None)* | `200 OK` | `{"status": "UP", "components": {...}}` |

---

## Testing Endpoints

### 1. Using Browser (Chrome)
- **Swagger UI (Interactive API Testing)**: Open [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)
- **Health Check**: Open [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health)
- **Get Order in Browser**: Open `http://localhost:8080/orders/1`

### 2. Using `curl`

#### Create an Order (`POST /orders`):
```bash
curl -i -X POST http://localhost:8080/orders
```

#### Fetch an Order (`GET /orders/{id}`):
```bash
curl -i http://localhost:8080/orders/1
```

#### Test 404 (Missing Order):
```bash
curl -i http://localhost:8080/orders/9999
```

#### Pass Custom Correlation ID:
```bash
curl -i -H "X-Correlation-Id: test-order-123" http://localhost:8080/orders/1
```
