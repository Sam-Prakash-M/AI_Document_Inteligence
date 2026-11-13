# AGENTS.md — guide for AI coding agents

This is a Spring Boot 4.1 / Java 25 RAG backend. It ingests documents into PostgreSQL + pgvector and answers questions with LLM-generated, source-cited answers over a REST API secured by API keys. LangChain4j provides the model and parser integrations; Redis is used for caching and rate limiting. Human-facing docs are in `README.md`.

## Commands

```bash
./mvnw test                         # all tests; no DB/Redis/LLM needed (Windows: mvnw.cmd)
./mvnw -q -DskipTests compile       # fast compile check
./mvnw spring-boot:run              # needs Postgres+Redis: docker compose up -d postgres redis
docker compose up -d --build        # full stack (needs .env, copy from .env.example)
```

Before you finish any change, run `./mvnw test` and confirm it passes. If the change touches SQL, Flyway, Redis or provider wiring, say that the unit tests don't exercise it and that it needs a check against the compose stack.

## Where things are

```
src/main/java/com/ai/documentintelligence/
  config/AppProperties.java       all app.* settings (immutable records + Bean Validation)
  config/AiModelConfig.java       builds ChatModel / EmbeddingModel per provider (switch on enum)
  config/SecurityConfig.java      the only SecurityFilterChain; registers the two filters
  config/AsyncConfig.java         bounded "ingestionExecutor"
  security/                       ApiKeyRegistry, ApiKeyAuthenticationFilter, RateLimiter(+Filter), ProblemResponseWriter
  document/                       SourceDocument (JPA), repository, DocumentService, IngestionWorker (@Async), controller, dto/
  vector/ChunkRepository.java     ALL pgvector SQL (NamedParameterJdbcTemplate)
  rag/                            RagService (query pipeline), PromptBuilder (prompt + citation parsing), QueryController, dto/
  cache/RagCache.java             Redis embedding + answer cache, corpus-version invalidation
  common/                         GlobalExceptionHandler, NotFoundException, AiProviderException, Hashing
src/main/resources/application.yml                 config + env-var defaults
src/main/resources/db/migration/V1__init_schema.sql  schema (Flyway)
src/test/java/...                                    unit + @WebMvcTest tests; TestProperties builds AppProperties
```

## Invariants: do not break

1. **Tenant isolation.** The `owner` is `ApiClient.name()` from `@AuthenticationPrincipal`. Every read or write on `documents` / `document_chunks` must filter by owner (`findByIdAndOwner`, `WHERE c.owner = :owner`). Never accept the owner from a request body or parameter.
2. **Grounding contract.** If retrieval returns no chunk at or above `minScore`, `RagService` returns `PromptBuilder.NOT_FOUND_ANSWER` and does not call the LLM. Sources are numbered from 1 in retrieval order, and `SourceCitation.index` must match the `[n]` numbers in the prompt. Keep the system prompt rule that source text is untrusted data.
3. **Status transitions use targeted UPDATEs** (`markProcessing`, `markReady`, `markFailed` in `SourceDocumentRepository`). Never call `save(entity)` in the worker. A JPA merge would re-insert a document that was deleted mid-ingestion. Inserting chunks and marking READY share one transaction (`TransactionTemplate` in `IngestionWorker`).
4. **Cache invalidation.** Any change to an owner's corpus (ingest success, delete) must call `RagCache.invalidateOwner(owner)`. Answer-cache keys include the corpus version, so don't add key scans or `KEYS *`.
5. **Redis is optional at runtime.** `RagCache` and `RateLimiter` catch `RuntimeException`, log a warning, and degrade (cache miss, or allow the request). Keep it that way. For the same reason the Redis health indicator is disabled (`management.health.redis.enabled: false`). Postgres, by contrast, is required.
6. **The embedding dimension is fixed in the schema** through the Flyway placeholder `${embedding_dimension}`, and `ChunkRepository.verifyDimension()` checks it at startup. Never edit `V1__init_schema.sql`. Schema changes go in a new `V{n}__description.sql`.
7. **Security filters are not Spring beans.** `ApiKeyAuthenticationFilter` and `RateLimitFilter` are created inside `SecurityConfig`. Annotating them with `@Component` would register them a second time in the servlet chain, outside security.
8. **Errors are RFC 9457 `ProblemDetail`.** Controllers throw exceptions, and `GlobalExceptionHandler` maps them: `NotFoundException`→404, `IllegalArgumentException`→400, `AiProviderException`→502, `TaskRejectedException`→503, anything else→500 with a generic detail (never the exception message). Filters use `ProblemResponseWriter`. Don't return ad-hoc error maps.
9. **Secrets.** Never log API keys or provider keys, and never commit `.env`. `ApiKeyRegistry` keeps only SHA-256 digests.

## Version-specific gotchas

- **Spring Boot 4 uses Jackson 3.** The packages are `tools.jackson.databind.*`, and the injectable bean is `tools.jackson.databind.json.JsonMapper`. Annotations remain `com.fasterxml.jackson.annotation.*`. LangChain4j uses Jackson 2 internally, which is fine because the two don't clash.
- **Boot 4 is modular.** Test slices are in new packages, for example `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest`. Security autoconfig is in `org.springframework.boot.security.autoconfigure.*`. Flyway needs `spring-boot-starter-flyway` plus `flyway-database-postgresql`.
- **LangChain4j 1.x API:** `ChatModel.chat(ChatRequest)` → `ChatResponse.aiMessage().text()`; `EmbeddingModel.embed(String)` / `embedAll(List<TextSegment>)` → `Response<…>.content()`. Versions come from `langchain4j-bom` (`langchain4j.version` in `pom.xml`). Some modules, such as Tika, are `-betaN` versions managed by the BOM, so don't pin them separately.
- **JPA `ddl-auto: none`.** Flyway owns the schema. When you add a column, write a migration and update the entity.
- **pgvector SQL** uses `CAST(:param AS vector)` rather than `::vector` with named parameters, and `VectorFormat.toLiteral(float[])` for values. `<=>` is cosine distance, and score = `1 - distance`. Keep `ORDER BY embedding <=> …` so that the HNSW index is used.
- `UserDetailsServiceAutoConfiguration` is excluded in `application.yml` (there are no users, only API keys). The `@WebMvcTest` repeats that exclusion in its `@TestPropertySource`.

## How to…

**Add a chat or embedding provider:** add the LangChain4j module to `pom.xml` without a version (the BOM supplies it), add an enum constant to `AppProperties.ChatProvider` / `EmbeddingProvider`, add a `case` in `AiModelConfig`, and document it in `.env.example` and the README configuration table.

**Add an endpoint:** create a controller under the relevant package with path `/api/v1/...`, take `@AuthenticationPrincipal ApiClient client` and pass `client.name()` down as the owner. Use `@Valid` record DTOs in a `dto/` subpackage. Add OpenAPI `@Operation` annotations, extend `SecurityWebTest` or add a focused `@WebMvcTest`, and update the README API reference.

**Change the schema:** add `src/main/resources/db/migration/V2__<what>.sql` and update `SourceDocument` or the `ChunkRepository` SQL to match.

**Add a config setting:** add a field to the right record in `AppProperties` (use `@DefaultValue` for optional ones), add it with a comment to `application.yml`, and update `TestProperties.withApiKeys(...)`, because the record constructors change.

## Conventions

- Constructor injection only, no field `@Autowired`. Configuration is read only through `AppProperties`, never `@Value`.
- DTOs are Java records. Entities never leave the service layer; map them with `XxxResponse.from(entity)`.
- Code uses tabs for indentation (Eclipse/Spring style), and comments explain why rather than what.
- Tests are plain JUnit 5 + AssertJ + Mockito where possible, and must not need a running DB, Redis or LLM. Build config with `TestProperties.defaults()`.
- Keep `README.md` (humans) and this file (agents) in sync when behaviour, configuration or endpoints change.
