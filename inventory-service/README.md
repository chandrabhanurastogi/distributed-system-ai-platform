# Inventory Service

Responsible for tracking product inventory quantities and reserving stock within the distributed platform.

- **Dedicated Port**: `8081`
- **Database**: PostgreSQL (`inventory-db` on port `5434`)

---

## Running the Service

Ensure `inventory-db` is running via Docker Compose first:
```bash
docker compose -f docker/docker-compose.yml up -d inventory-db
```

Start the application:
```bash
./gradlew :inventory-service:bootRun
```

---

## API Contract & Documentation

Interactive API UI (Swagger / OpenAPI):
- **Swagger UI**: [http://localhost:8081/swagger-ui/index.html](http://localhost:8081/swagger-ui/index.html)
- **OpenAPI Schema (JSON)**: [http://localhost:8081/v3/api-docs](http://localhost:8081/v3/api-docs)

### Endpoints Contract

| Method | Path | Request Body | Response Status | Response Body |
|---|---|---|---|---|
| `GET` | `/inventory/{sku}` | *(None)* | `200 OK`<br>`404 Not Found` | `{"id": 1, "sku": "WIDGET-1", "quantity": 10, "createdAt": "2026-09-16T10:00:00Z"}`<br>`{"error": "Inventory item not found: <sku>"}` |
| `POST` | `/inventory/{sku}/reserve` | `{"quantity": 2}` | `200 OK`<br>`400 Bad Request`<br>`404 Not Found`<br>`409 Conflict` | `{"id": 1, "sku": "WIDGET-1", "quantity": 8, "createdAt": "2026-09-16T10:00:00Z"}`<br>`{"error": "Reserve quantity must be positive: <n>"}`<br>`{"error": "Inventory item not found: <sku>"}`<br>`{"error": "Insufficient stock for <sku>: requested <n>, available <n>"}` |
| `GET` | `/actuator/health` | *(None)* | `200 OK` | `{"status": "UP", "components": {...}}` |

---

## Testing Endpoints

### 1. Using Browser (Chrome)
- **Swagger UI (Interactive API Testing & Try-it-out)**: Open [http://localhost:8081/swagger-ui/index.html](http://localhost:8081/swagger-ui/index.html)
- **Health Check**: Open [http://localhost:8081/actuator/health](http://localhost:8081/actuator/health)
- **Get Item in Browser**: Open `http://localhost:8081/inventory/WIDGET-1`

### 2. Using `curl`

#### Seed a test item in `inventory-db` (if needed):
```bash
docker exec distributed-microservices-inventory-db-1 psql -U inventory -d inventory-db -c "INSERT INTO inventory_items (sku, quantity) VALUES ('WIDGET-1', 10) ON CONFLICT (sku) DO NOTHING;"
```

#### Fetch Inventory by SKU (`GET /inventory/{sku}`):
```bash
curl -i http://localhost:8081/inventory/WIDGET-1
```

#### Reserve Stock (`POST /inventory/{sku}/reserve`):
```bash
curl -i -X POST http://localhost:8081/inventory/WIDGET-1/reserve \
  -H "Content-Type: application/json" \
  -d '{"quantity": 2}'
```

#### Test 409 Conflict (Insufficient Stock):
```bash
curl -i -X POST http://localhost:8081/inventory/WIDGET-1/reserve \
  -H "Content-Type: application/json" \
  -d '{"quantity": 9999}'
```

#### Test 400 Bad Request (Invalid Quantity):
```bash
curl -i -X POST http://localhost:8081/inventory/WIDGET-1/reserve \
  -H "Content-Type: application/json" \
  -d '{"quantity": 0}'
```

#### Test 404 (Missing SKU):
```bash
curl -i http://localhost:8081/inventory/DOES-NOT-EXIST
```
