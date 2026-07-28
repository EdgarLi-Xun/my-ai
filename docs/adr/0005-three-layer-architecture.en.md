# ADR 0005: Three-layer Architecture, XML Mappers, and Bilingual Comments

> 中文版: [`0005-three-layer-architecture.md`](./0005-three-layer-architecture.md).

**Status:** 🚧 Design (ratified 2026-07-28 via `/grill-with-docs`; awaiting user notification before implementation).

## Context

MyAi (Java 21 / Spring Boot 4.0.7 / MyBatis-Flex 1.11.8 / Spring Security 6 / Vue 3) has ~52 Java source files after ADR 0003 (Conversations & Messages) and ADR 0004 (Observability) landed. From a "classical three-layer architecture" perspective, four pain points remain:

1. **Entity leaks into the response layer** — `User` and `UserApiKey` are returned directly by controllers; other entities have dedicated `web/dto/*Response` classes.
2. **`MessageService` is overweight** — it carries 6 responsibilities: SSE streaming, AI invocation, `ai_call_log` writes, auto-title generation, message persistence, and transaction orchestration.
3. **Cross-cutting concerns share the same layer as business code** — `TraceIdFilter` / `AuditAspect` sit next to `service/`; `observability` is a top-level package.
4. **AI calls don't "flow through" the service layer** — `cn.edgarli.ai` is a top-level package; `MessageService` depends directly on `ChatClientFactory`.

Plus two supplementary requirements raised by the user:
- **Comment granularity down to function / parameter / local variable**, **covering the entire repository**.
- **mapper.xml must also be commented** — verified: the repository currently has **no XML mappers**, so XML must be introduced as part of the refactor.

`/grill-with-docs` resolved all twelve decisions below.

## Decision

### 1. ORM unchanged
- The repository already uses MyBatis-Flex 1.11.8 (`mybatis-flex-spring-boot4-starter` + `@Table` annotations + `BaseMapper<T>`). There is **zero JPA / Hibernate footprint**.
- The user's original "remove JPA" was based on a mistaken premise; this ADR makes no ORM switch.
- `FlexConfig` already configures the XML scan path `classpath:cn/edgarli/mapper/**/*.xml`, so introducing XML mappers needs no Java-side config change.

### 2. Service interface / implementation separation
- Naming: **same-name interface + `Impl` suffix** (`UserService` interface + `UserServiceImpl` implementation).
- **No** `I` prefix (`IUserService`) — Spring community standard, IDE jump-to-definition is smoother.
- Controllers depend on the **interface**; Spring injects the **implementation**.
- All 5 existing services (Auth / UserApiKey / Message / Conversation / AiCallLog) split into interface + implementation.

### 3. `MessageService` split into three pieces
- **`MessageQueryService`** interface — `list` / `get` (read-only).
- **`MessageCommandService`** interface — `streamReply` / `regenerate` / `edit` (writes + streaming).
- **`MessageService`** aggregator interface — `extends MessageQueryService, MessageCommandService`.
- **`MessageServiceImpl`** delegates to the two split implementations (no duplicated logic).
- Controllers inject the single `MessageService` interface and get both query + command capabilities.
- `AuditAspect` and `@Auditable` continue to intercept by method signature; interface split doesn't break interception.

### 4. AI sub-package: into the service layer
- `cn.edgarli.ai` → `cn.edgarli.service.ai` (moved into the service layer).
- New **`AiService` interface + `AiServiceImpl` implementation** — responsible for prompt assembly, token extraction, streaming, and `ChatResponse.getMetadata().getUsage()` handling.
- `ChatClientFactory` and `provider/` remain internal; only `AiServiceImpl` depends on them.
- `MessageCommandServiceImpl` no longer depends on `ChatClientFactory` / `ChatClient`; it depends on `AiService`.

### 5. Infrastructure consolidated under `cn.edgarli.infrastructure.*`
| Sub-package | Classes | Notes |
| --- | --- | --- |
| `security/` | `SecurityConfig` / `JwtService` / `JwtAuthenticationFilter` / `AuthPrincipal` / `RestAuthenticationEntryPoint` / `RestAccessDeniedHandler` | Spring Security config |
| `config/` | `FlexConfig` / `AdminProperties` / `LogProperties` / `TrashProperties` / `FilterConfig` | Configuration beans |
| `task/` | `ConversationCleanupTask` / `LogCleanupTask` | `@Scheduled` tasks |
| `observability/` | `TraceIdFilter` + new `@TraceId` annotation (if any) | Access logs / trace_id |
| `audit/` | `@Auditable` / `AuditAspect` / `AuditLogDo` / `AuditLogMapper` | Business audit |

### 6. Entity / DTO: **DO / BO / VO three layers**
- **DO (Data Object)** — persistence object, maps to a DB table (current `entity/*.java` renamed with `Do` suffix).
- **BO (Business Object)** — business intermediate, cross-mapper composition at the service layer (e.g. `UserWithKeys`, `ConversationWithMessages`).
- **VO (View Object)** — external Response / Request records.
- **Conversion layer** — `cn.edgarli.web.converter` sub-package, one file per entity (`UserConverter`, `UserApiKeyConverter`, etc.); explicit manual DO ⇄ VO conversion (no MapStruct to avoid extra dependencies).

### 7. DO naming: **`Do` suffix**
- `User` → `UserDo`
- `UserApiKey` → `UserApiKeyDo`
- `Conversation` → `ConversationDo`
- `Message` → `MessageDo`
- `AiCallLog` → `AiCallLogDo`
- `AuditLog` → `AuditLogDo`
- Package name `entity` is kept — `@Table` annotation scanning path unchanged.

### 8. XML Mapper introduction
- Currently `src/main/resources/cn/edgarli/mapper/` is empty (only `schema.sql` + `logback-spring.xml` + `rebel.xml`).
- `FlexConfig` already scans `classpath:cn/edgarli/mapper/**/*.xml` — **no Java config change needed**.
- Migration scope (this ADR):
  - **`ConversationMapper.xml`** — `findActiveByUserId` / `findDeletedByUserId` / `findByIdAndUserId` / `findNonOrphanedContextBefore` / `softDelete` / `restore` / `updateTitle` / `hardDeleteOlderThan` / `touchUpdatedAt`
  - **`MessageMapper.xml`** — `findByConversationId` / `findNonOrphanedContext` / `markOrphansAfter` / `markOrphan` / `existsNonOrphanedUserMessage`
- Other mappers (User / UserApiKey / AiCallLog / AuditLog) keep `QueryWrapper.create()` chaining for dynamic queries; simple CRUD still goes through `BaseMapper<T>` defaults.

### 9. Comment granularity + bilingual
- **Method Javadoc** — every `public` / `protected` method; includes `<p>` paragraphs, `@param` / `@return` / `@throws`.
- **`@param name`** — every method parameter.
- **Local variable `//`** — every local variable inside methods (e.g. `String trimmedEmail = trim(email);`).
- **Language**: **strictly bilingual inline** (Chinese + English on the same line).
  - Class-level Javadoc: first line English summary, then Chinese detailed description.
  - Method Javadoc: Chinese body + first-line English summary.
  - `@param userId 用户 ID / user identifier`
  - `@return AI 回复 SSE emitter / SSE emitter for AI reply`
  - `@throws BizException 4035 默认 Key 不可用 / 4035 default key unavailable`
  - Inline `//`: one Chinese line + one English line (e.g. `String trimmedEmail = trim(email); // 去掉前后空白的邮箱 / email trimmed of leading/trailing whitespace`)
- **Coverage**: entire repository — ~50 Java source files + 2 XML mapper files.

## Considered options

### §2.1 Service interface naming
- **A. `I`-prefix interface (`IUserService` + `UserServiceImpl`)** — rejected. Traditional Java/C# style, but Spring community has moved away; IDE navigation is worse than same-name style.
- **B. Same-name interface + `Impl` suffix (`UserService` + `UserServiceImpl`)** — **chosen**. Spring mainstream; same-name makes IDE / compiler jumps most direct.
- **C. Interface and impl with different names (`MessageApi` + `MessageServiceImpl`)** — rejected. Semantic ambiguity; not recommended.

### §3.1 `MessageService` split
- **A. Single interface + private helpers in impl** — rejected. Minimal change but 6 responsibilities still in one service; doesn't address the "too heavy" pain point.
- **B. Two interfaces + aggregator interface (Query + Command + MessageService)** — **chosen**. Clear expression; controller injects one interface and gets both query + command capabilities.
- **C. Two interfaces + controller injects both** — not chosen. More injection points at controllers; harder to compose.
- **D. Three interfaces (Query + Edit + Stream)** — not chosen. Too granular; cross-interface calls become harder.

### §4.1 AI sub-package destination
- **A. AI as infrastructure (`cn.edgarli.infrastructure.ai`)** — rejected. AI calls are both "external resource" and "business"; folding them into infrastructure blurs the business boundary.
- **B. AI as part of service layer (`cn.edgarli.service.ai`)** — **chosen**. Aligns with "AI invocation semantics belong to business".
- **C. AI as part of mapper layer (`cn.edgarli.mapper.ai`)** — rejected. AI is not data access.
- **D. Keep `cn.edgarli.ai` as-is** — rejected. Explicit user pain point.

### §5.1 Infrastructure consolidation
- **A. Unified under `cn.edgarli.infrastructure`** — **chosen**. Consistent naming; clear sub-package split by concern.
- **B. Split `shared/` vs `infrastructure/`** — rejected. No actionable criterion for "misc" vs "standalone facility".
- **C. All under `shared/`** — rejected. One name without sub-package split is a flat anti-pattern.

### §6.1 Entity / DTO layering granularity
- **A. Only add `UserResponseVO` / `UserApiKeyResponseVO`** — rejected. Other entities already have dedicated VOs (`ConversationResponse`, etc.); format would be inconsistent.
- **B. Every entity gets a VO, entity never leaks** — not chosen. Superseded by §6.1 C (more thorough).
- **C. DO / BO / VO three layers** — **chosen**. Every entity gets DO (persistence) / BO (business composition) / VO (response) trio; conversion is explicit; persistence and response are fully decoupled.

### §7.1 DO naming
- **A. Add `Do` suffix (`UserDo`)** — **chosen**. Clear semantics; existing imports can be batch-updated.
- **B. No suffix (keep `User`), only migrate package to `dataobject/`** — rejected. Service consumers can't tell if `User` is DO or BO from the name alone; import inspection needed.
- **C. Different noun + package rename (`cn.edgarli.persistence.UserRecord`)** — rejected. Massive import churn; over-engineered.

### §8.1 XML mapper scope
- **A. Only LogsController AI call / audit queries → XML** — not chosen. User chose broader scope.
- **B. Conversation + Message business queries fully → XML** — **chosen**. Business queries "traditionalized"; annotations remain only for DO mapping.
- **C. All custom queries across all mappers → XML** — not chosen. Dynamic queries on User / UserApiKey / AiCallLog / AuditLog are concise enough with `QueryWrapper`.
- **D. XML only when "complex SQL is truly needed"** — rejected. The judgment criterion is fuzzy.

### §9.1 Comment language
- **A. Chinese only** — rejected (user changed mind).
- **B. English only** — rejected (inconsistent with existing Javadoc).
- **C. Bilingual with two lines per item (one Chinese line + one English line)** — not chosen. Too verbose.
- **D. Strictly bilingual inline (Chinese + English on the same line)** — **chosen**. Format like `@param userId 用户 ID / user identifier` is consistent and information-dense.

## Consequences

### Package structure (after refactor)

```
cn.edgarli
├── MyAiApplication.java
├── web/                                  # Controller + Request/Response (VO)
│   ├── *Controller.java (8 of them)
│   ├── dto/                              # VO: Request / Response record
│   └── converter/                        # DO ⇄ VO converters (one per entity)
├── service/                              # Business services (interface + impl)
│   ├── AuthService + AuthServiceImpl
│   ├── UserService + UserServiceImpl
│   ├── UserApiKeyService + UserApiKeyServiceImpl
│   ├── ConversationService + ConversationServiceImpl
│   ├── MessageQueryService + MessageQueryServiceImpl
│   ├── MessageCommandService + MessageCommandServiceImpl
│   ├── MessageService                    # Aggregator interface (extends Query + Command)
│   ├── MessageServiceImpl                # Delegates to split impls
│   ├── AiCallLogService + AiCallLogServiceImpl
│   └── ai/                               # AI sub-layer
│       ├── AiService + AiServiceImpl
│       └── provider/
├── business/                             # BO business objects
├── mapper/                               # DAO interfaces
│   ├── ConversationMapper.java
│   └── MessageMapper.java
├── entity/                               # DO persistence objects (Do suffix)
├── infrastructure/                       # Infrastructure
│   ├── security/
│   ├── config/
│   ├── task/
│   ├── observability/
│   └── audit/
├── common/                               # Result, BizException, GlobalExceptionHandler
└── resources/cn/edgarli/mapper/          # XML mapper location
    ├── ConversationMapper.xml
    └── MessageMapper.xml
```

### Data model changes
- **None**. DB schema unchanged; 6 entities are renamed to `XxxDo` but table / column names are unchanged.

### API changes
- **None**. All `/api/*` endpoint paths, request bodies, response formats, and business codes are unchanged. Pure code-organization refactor.

### New dependencies
- **None**. Existing deps (Spring Boot 4 / MyBatis-Flex / Spring Security / JJWT / HikariCP / Spring AI / Lombok) are sufficient.

### Error code / business code changes
- **None**.

### Performance / resources
- **No noticeable impact**. Service has one extra interface indirection (one extra method dispatch), negligible.

### Impact scope estimate

| Category | Count |
| --- | --- |
| New Java files | ~38 (6 Impl + 8 Converters + ~6 BO + 1 aggregator MessageServiceImpl + 1 AiServiceImpl + 1 aggregator interface + others) |
| New XML files | 2 (`ConversationMapper.xml` + `MessageMapper.xml`) |
| Modified Java files | ~50 (all service / controller / mapper / entity / cross-cutting imports updated) |
| Comment changes | entire repo: ~50 Java + 2 XML — method Javadoc + `@param` + local `//` (bilingual inline) |

### Verification
1. `mvn -DskipTests package` must pass.
2. Post-start smoke:
   - `POST /api/auth/register` + `POST /api/auth/login` + `GET /api/auth/me`
   - Create conversation + streaming SSE send message (verify SSE protocol unchanged after `AiService` refactor)
   - Create / update / delete Key (verify `@Auditable` still fires after package move)
   - Start with `MYAI_ADMIN_EMAILS=...`, admin calls `/api/logs/ai-calls` + `/api/logs/audit`
3. XML mapper validation: send message → check DB `ai_call_log` written correctly (verify Conversation / Message XML migration preserves behavior).

### Commit split (5 commits)
1. **Stage A** — infrastructure relocation (security/config/task/observability/audit → `infrastructure`), no business change
2. **Stage B** — Service interface/Impl separation + AI sub-package refactor (split MessageService + introduce AiService)
3. **Stage C** — DO/BO/VO layering (6 entities → `Do` suffix; add VO; add converters; add BO)
4. **Stage D** — Conversation + Message XML mapper migration
5. **Stage E** — Whole-repo comment fill-in (method Javadoc + `@param` + local `//`, bilingual inline)

Each stage verified by `mvn -DskipTests package`.

### Known risks
1. **Large mechanical refactor** (~88 files changed / added) — single commit would make review hard; 5-commit split is mandatory.
2. **AOP still fires after interface split** — `AuditAspect @Around @annotation(auditable)` intercepts by method signature; interface split doesn't break it (interface methods and impl methods share signatures).
3. **`MessageServiceImpl` delegation has "double indirection"** — `messageCommandService.streamReply(...)` passes through two layers; performance negligible but debug stacks are one level deeper.
4. **XML mapper coexists with `BaseMapper` defaults** — simple CRUD still on `BaseMapper`, complex queries on XML; maintainers need to know which methods live in Java and which in XML.
5. **Bilingual comment volume** — every local variable bilingual grows code size by ~30-50%; no runtime impact.

### Out of scope
- No MapStruct (external dep, manual converters are sufficient)
- No ORM switch (already MyBatis-Flex)
- No DB schema / API path / business code changes
- No rewrite of service business logic (only interface / impl separation; method bodies unchanged)

## Status

🚧 **Design ratified** (2026-07-28, `/grill-with-docs` 12 decisions). **Awaiting user notification before implementation** — this ADR documents design only; no business code is written yet.

## Related

- [[0003-conversations-and-messages]] — `MessageService` split is based on ADR 0003's `MessageService`; interface split doesn't break §4 PATCH edit semantics.
- [[0004-observability]] — `AuditAspect` path moves from `cn.edgarli.observability` to `cn.edgarli.infrastructure.audit`; `AuditLog` entity renamed to `AuditLogDo`; `@Auditable` interception logic unchanged.
- CLAUDE.md §4 "Key architecture conventions" will be updated during implementation to add "package structure / Service interface separation / three-layer data objects" constraints.
- CLAUDE.md §6 "Security boundaries" — not affected by this ADR.