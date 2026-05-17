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
                       │  ├── order.created.queue ──► consumer
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
- Maven
- Docker Compose
- Lombok

## Quick Start

### Prerequisites

- Docker & Docker Compose

### Run

```bash
docker compose up --build
```

Wait ~30 seconds for all services to start.

Available UIs:
- RabbitMQ Management: [http://localhost:15672](http://localhost:15672) (guest/guest)
- Zipkin Tracing: [http://localhost:9411](http://localhost:9411)

### Test

**Place an order (sufficient stock):**
```bash
curl -s -X POST http://localhost:8080/orders \
  -H "Content-Type: application/json" \
  -d '{"orderId":"order-1","itemId":"item-1","quantity":2}'
```

**Check inventory state:**
```bash
curl -s http://localhost:8081/inventory
```

**Place an order that exceeds stock (item-3 has only 5):**
```bash
curl -s -X POST http://localhost:8080/orders \
  -H "Content-Type: application/json" \
  -d '{"orderId":"order-2","itemId":"item-3","quantity":999}'
```

**Validation error:**
```bash
curl -s -X POST http://localhost:8080/orders \
  -H "Content-Type: application/json" \
  -d '{"orderId":"","itemId":"item-1","quantity":0}'
```

### Distributed Tracing

After sending an order, open Zipkin at [http://localhost:9411](http://localhost:9411) → click **Run Query** → click **SHOW** on a trace to see the full request flow:

```
order-api: POST /orders → rabbitmq: send to order.events → inventory-service: receive from order.created.queue
```

Each trace shows the complete journey of an order across both services with timing for every step.

### Dead Letter Queue

Failed messages are automatically routed to `order.created.dlq`. To verify, check the RabbitMQ Management UI → Queues → `order.created.dlq`.

### Stop

```bash
docker compose down -v
```

## Project Structure

```
order-system-event-driven/
├── docker-compose.yml
├── order-api/
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/java/rs/mds/orderapi/
│       ├── config/RabbitMqConfig.java
│       ├── constants/AppConstants.java
│       ├── controller/OrderController.java
│       ├── dto/
│       │   ├── CreateOrderRequest.java
│       │   └── CreateOrderResponse.java
│       ├── event/OrderCreatedEvent.java
│       └── service/
│           ├── OrderService.java
│           └── OrderEventPublisher.java
└── inventory-service/
    ├── Dockerfile
    ├── pom.xml
    └── src/main/java/rs/mds/inventory/
        ├── config/RabbitMqConfig.java
        ├── constants/MessagingConstants.java
        ├── consumer/OrderEventConsumer.java
        ├── controller/InventoryController.java
        ├── event/OrderCreatedEvent.java
        ├── model/InventoryItem.java
        └── service/InventoryService.java
```

## Design Decisions

### Why RabbitMQ?

For a two-service system with moderate throughput, RabbitMQ offers simpler operational setup than Kafka, built-in exchange routing, and a management UI out of the box. Kafka shines at massive scale and log-based replay — overkill for this scope.

### Why Direct Exchange?

We have a clear, fixed routing pattern: `order.created` events go to a single queue. Direct exchange provides exact routing key matching, which is the simplest and most explicit choice. If the system grows to need wildcard routing (e.g., `order.*`), switching to a Topic exchange is a one-line change.

### Integration Patterns

| Pattern | Implementation |
|---|---|
| **Idempotent Consumer** | Each event carries a unique `eventId` (UUID). The `InventoryService` tracks processed IDs in a `ConcurrentHashMap`-backed `Set` to skip duplicates. |
| **Thread-Safe Inventory** | `AtomicInteger` with CAS (Compare-And-Set) loop for lock-free concurrent reservations. |
| **Dead Letter Queue** | Failed messages are routed to `order.created.dlq` via `order.dlx` exchange for inspection and replay. |
| **Distributed Tracing** | Micrometer Tracing + Zipkin — each request gets a unique trace ID that propagates through RabbitMQ, visible in Zipkin UI at `:9411`. |
| **Async Acknowledgement** | `202 Accepted` HTTP response — the API acknowledges receipt, not processing completion. |
| **JSON Serialization** | `Jackson2JsonMessageConverter` for human-readable, debuggable messages. |
| **Input Validation** | Jakarta Bean Validation (`@NotBlank`, `@Min`) on the REST layer with structured error responses. |
| **Structured Logging** | SLF4J logging with event IDs and order IDs for traceability across services. |
| **Health Checks** | Spring Boot Actuator on both services. |
| **Container Orchestration** | Docker Compose with `depends_on` + `healthcheck` to ensure RabbitMQ is ready before services start. |

### Assumptions

1. **In-memory state is acceptable** — inventory stock and the idempotency set reset on restart (as per task instructions).
2. **Initial inventory** is seeded in the `InventoryService` constructor: `item-1=100`, `item-2=50`, `item-3=5`.
3. **No authentication/authorization** — out of scope for this task.
4. **Single consumer instance** — the idempotency set is per-instance; a distributed deployment would need a shared store (e.g., Redis).

### Potential Extensions

- **Consumer retry with backoff** — automatic retry on transient failures before rejecting to DLQ.
- **Saga / compensating transactions** — if the system grows to multiple downstream services.
- **Outbox pattern** — for stronger exactly-once guarantees when a database is involved.
- **Global Exception Handler** — structured JSON error responses on the REST layer.
