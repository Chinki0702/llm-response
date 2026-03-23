# LLM Response Service

A Spring Boot service that accepts user messages via a REST API, generates AI responses using a mocked LLM, and stores full conversation history in a database.

## How to Run

### Prerequisites
- Java 17+
- Maven 3.8+

### Build & Run

```bash
# Build the project
mvn clean package

# Run the application
mvn spring-boot:run
```

The service starts on **http://localhost:8080**.

### Quick Test with cURL

**Send a message (new conversation):**
```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: req-001" \
  -d '{"userId": "user1", "message": "Hello, AI!"}'
```

**Continue a conversation:** (use the `conversationId` from the previous response)
```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"userId": "user1", "conversationId": "<UUID>", "message": "Tell me more"}'
```

**Get conversation history:**
```bash
curl http://localhost:8080/api/conversations/<UUID>
```

**List user conversations:**
```bash
curl http://localhost:8080/api/conversations?userId=user1
```

### H2 Console

Available at http://localhost:8080/h2-console during development.
- JDBC URL: `jdbc:h2:mem:llmdb`
- Username: `sa`
- Password: *(empty)*

### Run Tests

```bash
mvn test
```

---

## API Reference

### POST /api/chat

Send a user message and receive an AI response.

| Header | Required | Description |
|---|---|---|
| `Idempotency-Key` | No | Prevents duplicate processing of the same request |

**Request Body:**
```json
{
  "userId": "user1",
  "conversationId": null,
  "message": "Hello, AI!"
}
```

- `userId` (required): Identifies the user.
- `conversationId` (optional): UUID of an existing conversation. Omit or set to `null` to start a new conversation.
- `message` (required): The user's message (max 4000 chars).

**Response (200):**
```json
{
  "conversationId": "...",
  "messageId": "...",
  "reply": "AI-generated response text",
  "timestamp": "2026-03-23T10:15:30Z"
}
```

### GET /api/conversations/{conversationId}

Returns a conversation with its full message history.

### GET /api/conversations?userId={userId}

Returns all conversations for the given user, ordered by creation time (newest first).

---

## Design Decisions

### Architecture

- **Layered architecture**: Controller → Service → Repository. Each layer has a single responsibility.
- **Entity model**: `Conversation` (1:N) → `Message`. This naturally supports multi-user, multi-conversation scenarios.
- **DTOs**: Request/response objects are Java records, keeping the API contract separate from the persistence model.

### Mocked LLM

- `LlmClient` is an interface, making it trivial to swap the mock for a real LLM SDK (OpenAI, etc.) without changing any service code.
- `MockLlmClient` simulates realistic latency (200–800ms) and supports a configurable failure rate for testing resilience.
- The mock selects a canned response deterministically based on the prompt hash — same prompt always yields the same response, which aids testing and debugging.

### Reliability

- **Retry**: `ResilientLlmService` uses Spring Retry with up to 3 attempts and exponential backoff (1s, 2s, 4s). Only `LlmException` triggers retries.
- **Timeout**: `MockLlmClient` wraps generation in a `Future` with a configurable timeout (default 5s). A timed-out call is cancelled and throws `LlmException`, which feeds into the retry mechanism.
- **Graceful degradation**: After all retries are exhausted, the service returns HTTP 503 with a clear error message.

### Idempotency

- Clients can send an `Idempotency-Key` header with their request.
- On first receipt, the service processes the request normally and caches the serialized response against the key in the `idempotency_keys` table.
- On duplicate receipt (same key), the cached response is returned immediately — no LLM call, no duplicate messages.
- This design is safe for retries from unreliable networks.

### Persistence

- **H2 in-memory database** for zero-setup development. Easy to switch to PostgreSQL/MySQL by changing `application.properties` and adding the appropriate JDBC driver.
- **JPA with Hibernate** auto-DDL for schema management during development.
- Messages are ordered by timestamp within each conversation.

### Logging

- SLF4J + Logback (Spring Boot default).
- Key events logged: incoming requests, LLM calls and responses, retries, timeouts, idempotency cache hits, errors.
- Log levels: `INFO` for normal operations, `DEBUG` for detailed flow, `ERROR` for failures.

---

## Assumptions

1. **In-memory database is acceptable** for this exercise. In production, a persistent store (PostgreSQL, MySQL) would be used.
2. **Authentication/authorization is out of scope.** The `userId` is trusted as provided. In production, this would come from a JWT or session.
3. **The mocked LLM is synchronous.** A real integration might use async/streaming, but the interface allows that to be swapped in.
4. **Idempotency keys have no expiration.** In production, a TTL-based cleanup strategy would be added.
5. **No pagination** on the conversations list endpoint. Suitable for the exercise scope; would add `Pageable` support for production.
6. **Single-instance deployment.** Idempotency relies on the database, which works correctly for single-instance and can scale to multi-instance with a shared database.

---

## Project Structure

```
src/main/java/com/example/llmresponse/
├── LlmResponseApplication.java          # Entry point, enables retry
├── controller/
│   ├── ChatController.java              # REST endpoints
│   └── GlobalExceptionHandler.java      # Centralized error handling
├── dto/
│   ├── ChatRequest.java                 # Inbound request
│   ├── ChatResponse.java                # Outbound response
│   ├── ConversationDto.java             # Conversation view
│   └── MessageDto.java                  # Message view
├── entity/
│   ├── Conversation.java                # JPA entity
│   ├── IdempotencyRecord.java           # Dedup tracking
│   └── Message.java                     # JPA entity
├── repository/
│   ├── ConversationRepository.java      # Data access
│   ├── IdempotencyRepository.java       # Dedup data access
│   └── MessageRepository.java           # Data access
└── service/
    ├── ConversationNotFoundException.java
    ├── ConversationService.java          # Core business logic
    ├── LlmClient.java                   # LLM abstraction
    ├── LlmException.java                # LLM error type
    ├── MockLlmClient.java               # Simulated LLM
    └── ResilientLlmService.java          # Retry wrapper
```
