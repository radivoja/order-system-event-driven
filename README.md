# Order System — Event-Driven Microservices

A small event-driven system consisting of two independent Spring Boot services communicating via **RabbitMQ**.

## Architecture

```
┌──────────────┐       ┌──────────────────┐       ┌─────────────────────┐
│   Client     │ POST  │   Order API      │       │  Inventory Service  │
│  (curl/etc.) ├──────►│   :8080          │       │  :8081              │
│              │ 202   │                  │       │                     │
└──────────────┘◄──────┤  Publishes       │       │  Consumes           │
                       │  OrderCreated    │       │  OrderCreated       │
                       │  event           │       │  event              │
                       └───────┬──────────┘       └──────┬──────────────┘
                               │                         │
                               │  order.created          │
                               ▼                         │
                       ┌─────────────────────────────────┘
                       │       RabbitMQ
                       │  order.events (direct exchange)
                       │  ├── order.created.queue ──► OrderEventConsumer
                       │  │       │ (DLX on failure)
                       │  │       ▼
                       │  │  order.created.dlq ──► DlqConsumer
                       │  │       │
                       │  │  retry < 3? ──YES──► back to order.created.queue
                       │  │       │
                       │  │      NO
                       │  │       ▼
                       │  │  order.parking-lot.queue (manual inspection)
                       │  │
                       │  order.dlx (dead letter exchange)
                       │  └── order.created.dlq
                       └─────────────────────────────────┘

                       ┌─────────────────────────────────┐
                       │       Zipkin  :9411              │
                       │  Distributed tracing UI          │
                       └─────────────────────────────────┘
```

## Tech Stack

- Java 21 (Eclipse Temurin)
- Spring Boot 3.5.14
- Spring AMQP (RabbitMQ)
- RabbitMQ 3.13 (Management Alpine image)
- Micrometer Tracing + Zipkin (distributed tracing)
- Testcontainers + Awaitility (integration tests)
- Maven
- Docker Compose
- Lombok

## Quick Start

### Prerequisites

- Docker & Docker Compose
- Java 21 (for local development and tests)
- Maven 3.9+ (for local development and tests)

### Run

Navigate to the project root directory (where `docker-compose.yml` is located):

```
cd order-system-event-driven
docker compose up --build
```

Wait ~30 seconds for all services to start.

| Service             | URL                          | Notes             |
|:--------------------|:-----------------------------|:------------------|
| Order API           | http://localhost:8080         | REST endpoint     |
| Inventory Service   | http://localhost:8081         | Diagnostic API    |
| RabbitMQ Management | http://localhost:15672        | guest / guest     |
| Zipkin UI           | http://localhost:9411         | Distributed traces|

### Test

Make sure the services are running (`docker compose up --build`) and you are in the project root directory.
Open a **new terminal** and run the following commands:

**Place an order (sufficient stock):**

Linux / Mac / CMD:
```
curl -s -X POST http://localhost:8080/orders \
  -H "Content-Type: application/json" \
  -d '{"orderId":"order-1","itemId":"item-1","quantity":2}'
```

PowerShell:
```powershell
Invoke-RestMethod -Uri http://localhost:8080/orders -Method POST -ContentType "application/json" -Body '{"orderId":"order-1","itemId":"item-1","quantity":2}'
```

**Check inventory state:**

Linux / Mac / CMD:
```
curl -s http://localhost:8081/inventory
```

PowerShell:
```powershell
Invoke-RestMethod -Uri http://localhost:8081/inventory
```

**Place an order that exceeds stock (item-3 has only 5):**

Linux / Mac / CMD:
```
curl -s -X POST http://localhost:8080/orders \
  -H "Content-Type: application/json" \
  -d '{"orderId":"order-2","itemId":"item-3","quantity":999}'
```

PowerShell:
```powershell
Invoke-RestMethod -Uri http://localhost:8080/orders -Method POST -ContentType "application/json" -Body '{"orderId":"order-2","itemId":"item-3","quantity":999}'
```

**Send a duplicate order:**

Linux / Mac / CMD:
```
curl -s -X POST http://localhost:8080/orders \
  -H "Content-Type: application/json" \
  -d '{"orderId":"order-1","itemId":"item-1","quantity":2}'
```

PowerShell:
```powershell
Invoke-RestMethod -Uri http://localhost:8080/orders -Method POST -ContentType "application/json" -Body '{"orderId":"order-1","itemId":"item-1","quantity":2}'
```

Check logs: `Duplicate event, skipping [eventId=...]`

**Validation error:**

Linux / Mac / CMD:
```
curl -s -X POST http://localhost:8080/orders \
  -H "Content-Type: application/json" \
  -d '{"orderId":"","itemId":"item-1","quantity":0}'
```

PowerShell:
```powershell
Invoke-RestMethod -Uri http://localhost:8080/orders -Method POST -ContentType "application/json" -Body '{"orderId":"","itemId":"item-1","quantity":0}'
```

### Distributed Tracing

After sending an order, open Zipkin at http://localhost:9411 → click **Run Query** → click **SHOW** on a trace to see the full request flow:

```
order-api: POST /orders → rabbitmq: send to order.events → inventory-service: receive from order.created.queue
```

Each trace shows the complete journey of an order across both services with timing for every step.

### Stop

```
docker compose down -v
```

## Project Structure

```
order-system-event-driven/
├── docker-compose.yml
├── order-api/
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/
│       ├── main/java/rs/mds/orderapi/
│       │   ├── config/RabbitMqConfig.java          # Exchange + publisher confirms/returns
│       │   ├── constants/MessagingConstants.java
│       │   ├── controller/OrderController.java     # POST /orders endpoint
│       │   ├── dto/
│       │   │   ├── CreateOrderRequest.java         # Validated request payload
│       │   │   └── CreateOrderResponse.java
│       │   ├── event/OrderCreatedEvent.java        # Domain event with eventId
│       │   └── service/
│       │       ├── OrderService.java               # Request → Event mapping
│       │       └── OrderEventPublisher.java         # Publishes to RabbitMQ
│       └── test/java/rs/mds/orderapi/
│           ├── OrderApiIntegrationTest.java         # REST + RabbitMQ integration tests
│           └── service/OrderServiceTest.java        # Unit tests
└── inventory-service/
    ├── Dockerfile
    ├── pom.xml
    └── src/
        ├── main/java/rs/mds/inventory/
        │   ├── config/RabbitMqConfig.java           # Full topology (queues, DLX, DLQ, parking lot)
        │   ├── constants/MessagingConstants.java
        │   ├── consumer/
        │   │   ├── OrderEventConsumer.java          # Main consumer
        │   │   └── DlqConsumer.java                 # DLQ retry + parking lot
        │   ├── controller/InventoryController.java  # GET /inventory diagnostic endpoint
        │   ├── event/OrderCreatedEvent.java
        │   ├── model/InventoryItem.java             # Thread-safe stock model
        │   └── service/InventoryService.java        # Reservation logic + idempotency
        └── test/java/rs/mds/inventory/
            ├── consumer/
            │   ├── DlqConsumerIntegrationTest.java       # DLQ retry integration tests
            │   └── OrderFlowIntegrationTest.java         # End-to-end message flow tests
            └── service/InventoryServiceTest.java         # Unit tests (business logic)
```

## Design Decisions

### Why RabbitMQ?

For a two-service system with moderate throughput, RabbitMQ offers simpler operational setup than Kafka, built-in exchange routing, and a management UI out of the box. Kafka shines at massive scale and log-based replay — overkill for this scope.

### Why Direct Exchange?

We have a clear, fixed routing pattern: `order.created` events go to a single queue. Direct exchange provides exact routing key matching, which is the simplest and most explicit choice. If the system grows to need wildcard routing (e.g., `order.*`), switching to a Topic exchange is a one-line change.

### Why DLQ Listener over TTL-based retry?

A DLQ Listener (`DlqConsumer`) makes retry logic explicit and visible in code. It allows logging each retry attempt, controlling the retry count via message headers, and routing exhausted messages to a parking lot queue. TTL-based retry is simpler but hides the logic in queue configuration and offers less control.

### Why in-memory storage?

Per task specification: "Baza podataka nije neophodna. Čuvanje podataka u memoriji je potpuno prihvatljivo." In production, inventory and idempotency state would be backed by a database.

## Integration Patterns

| Pattern | Implementation |
|:---|:---|
| **Error Handling & Retry** | Three-level defense: `requeue=false` prevents infinite loops → DLX routes to `order.created.dlq` → `DlqConsumer` retries up to 3 times → exhausted messages go to `order.parking-lot.queue` for manual inspection. |
| **Idempotent Consumer** | Each event carries a unique `eventId` (UUID). The `InventoryService` tracks processed IDs in a `ConcurrentHashMap`-backed `Set` to skip duplicates. In production, this would be backed by a database or Redis with TTL. |
| **Publisher Confirms & Returns** | `RabbitMqConfig` implements `ConfirmCallback` and `ReturnsCallback`. The broker confirms receipt of every message; unroutable messages are returned and logged. |
| **Thread-Safe Inventory** | `AtomicInteger` with CAS (Compare-And-Set) loop for lock-free concurrent reservations. |
| **Distributed Tracing** | Micrometer Tracing + Zipkin — each request gets a unique trace ID that propagates through RabbitMQ, visible in Zipkin UI at `:9411`. |
| **Graceful Shutdown** | `server.shutdown=graceful` with 20s timeout. Services finish in-flight messages before stopping. Docker Compose `stop_grace_period: 25s` prevents premature container kill. |
| **Async Acknowledgement** | `202 Accepted` HTTP response — the API acknowledges receipt, not processing completion. |
| **JSON Serialization** | `Jackson2JsonMessageConverter` for human-readable, debuggable messages. |
| **Input Validation** | Jakarta Bean Validation (`@NotBlank`, `@Min`) on the REST layer with structured error responses. |
| **Structured Logging** | SLF4J logging with trace IDs, event IDs and order IDs for traceability across services. |
| **Health Checks** | Spring Boot Actuator on both services. Docker Compose `depends_on` + `healthcheck` ensures RabbitMQ is ready before services start. |

## Running Tests

```bash
# Requires Docker running (for Testcontainers)
cd order-api && mvn test
cd inventory-service && mvn test
```

### order-api tests

- Valid order returns 202 ACCEPTED with orderId and status
- Missing orderId / zero quantity / empty body returns 400
- Valid order actually publishes message to RabbitMQ
- OrderService correctly maps request to event with unique eventId

### inventory-service tests

Unit tests (InventoryServiceTest):
- Successful reservation returns RESERVED and decreases stock
- Multiple reservations decrease stock cumulatively
- Unknown item returns REJECTED
- Insufficient stock returns REJECTED and does not change inventory
- Exact stock can be reserved (edge case)
- Zero stock after full reservation rejects next order
- Duplicate eventId is skipped and does not decrease stock twice

Integration tests (DlqConsumerIntegrationTest):
- Message goes through 3 retries then lands in parking lot
- Message with exhausted retries goes straight to parking lot
- Business rejection (unknown item) does not reach DLQ

Integration tests (OrderFlowIntegrationTest):
- Order message reduces inventory stock via RabbitMQ
- Rejected order does not change stock via RabbitMQ

## Local Development (without Docker)

```bash
# 1. Start RabbitMQ and Zipkin only
docker compose up rabbitmq zipkin

# 2. Start Order API (terminal 1)
cd order-api && mvn spring-boot:run

# 3. Start Inventory Service (terminal 2)
cd inventory-service && mvn spring-boot:run
```

## Pre-Seeded Inventory

| Item ID | Initial Stock |
|:--------|:--------------|
| item-1  | 100           |
| item-2  | 50            |
| item-3  | 5             |

## Assumptions

1. **In-memory state is acceptable** — inventory stock and the idempotency set reset on restart (as per task instructions).
2. **Initial inventory** is seeded in the `InventoryService` constructor: `item-1=100`, `item-2=50`, `item-3=5`.
3. **No authentication/authorization** — out of scope for this task.
4. **Single consumer instance** — the idempotency set is per-instance; a distributed deployment would need a shared store (e.g., Redis).
5. **Deserialization errors** (malformed JSON) are treated as fatal by Spring's `ConditionalRejectingErrorHandler` and go to DLQ without retry.

## Potential Extensions

- **Saga / compensating transactions** — if the system grows to multiple downstream services.
- **Outbox pattern** — for stronger exactly-once guarantees when a database is involved.
- **Exponential backoff** — add delay between retries using RabbitMQ Delayed Message Plugin.
- **Metrics & alerting** — expose retry count and parking lot size as Micrometer metrics.
