# Observability and Logging

MyAi introduces four-layer logging (2026-07-27 decision locked): AI call log (every OpenAI/Ollama/Anthropic call recorded with tokens + latency), HTTP access log (every `/api/**` request to JSON Lines file), business audit log (trace of Key / conversation / message CRUD with 30-day soft-delete window then hard delete), structured system log (logback JSON output + MDC cross-request context). This is the key architecture change from "invisible" to "debuggable / auditable"; partial rollback (logback config tunable, new tables droppable but with data migration cost).

> Chinese version: `0004-observability.md`.

## Context

MyAi previously had only scattered `log.info/warn/error` calls (`MessageService` / `ConversationCleanupTask`); CLAUDE.md §6 notes "encryption, audit, rotation, quota, production multi-tenant not implemented". After ADR 0003 landed streaming AI, **there are zero call records** — when a user reports "why did the AI reply like this" we can only guess. ADR 0004 fills this gap and upgrades scattered system logs to structured output.

## Core Decisions

### 1. Scope: all 4 layers in one go (D = AI call + HTTP access + business audit + system runtime)
- No phasing, no rounds.
- One-shot ~13 new files + ~10 modifications, estimate 5-7 days.

### 2. Storage backend
- **AI call log** + **business audit log** → same H2 file `./data/myai.mv.db`, new `ai_call_log` / `audit_log` tables (atomic write in same transaction).
- **HTTP access log** → JSON Lines file `./logs/access.jsonl` (high-frequency writes don't block business; logback's built-in `RollingFileAppender`).
- **System log** → stdout (dev JSON pretty-print or prod single-line JSON) + `./logs/app.jsonl`.

### 3. AI call log
- New table `ai_call_log(id, user_id, conversation_id, message_id, provider, model, status, latency_ms, input_tokens, output_tokens, error_message, created_at)`.
- `MessageService.streamReply` / `regenerate` switched to `stream().chatResponse()` instead of `stream().content()`; `ChatResponse.getMetadata().getUsage()` for input/output tokens.
- Insert timing: onComplete (success) or onError (failure) — same transaction.

### 4. Business audit log
- New table `audit_log(id, user_id NULL, action, target_type, target_id, ip_address NULL, user_agent NULL, created_at, deleted_at NULL)`.
- `user_id` NULL allowed (system background actions leave traces).
- **Soft delete**: `deleted_at` column; default query `WHERE deleted_at IS NULL`; `LogCleanupTask` physically deletes after 30 days.

### 5. HTTP access log
- New `TraceIdFilter implements Filter`, registered via `FilterRegistrationBean`, `order = SecurityProperties.DEFAULT_FILTER_ORDER - 100` (runs before Spring Security).
- Path whitelist: only writes `/api/**` (static resources, H2 console, favicon excluded).
- Per request records: method / path / query_string / status / latency_ms / user_id (missing → `anonymous`) / ip / user_agent.
- Also handles **MDC injection**: trace_id / user_id / conversation_id / request_method / request_path / client_ip.
- Response header `X-Trace-Id` (accept upstream header or generate UUID v4).

### 6. System log format
- logback rewritten: `src/main/resources/logback-spring.xml`.
- All JSON output (`<springProfile>` switches dev pretty vs prod single-line).
- Built-in encoder: `ch.qos.logback.classic.encoder.JsonEncoder` (Logback 1.5+ built-in, zero extra deps).
- Two file appenders: `./logs/app.jsonl` and `./logs/access.jsonl`, daily rotation + `maxHistory` 30.
- `my-ai.logs.retention-days` controls both DB cleanup and logback `maxHistory`.

### 7. Business audit trigger: AOP `@Auditable` + `@Around`
- Custom `@Auditable(action="KEY_CREATED", targetType="UserApiKey")` annotation.
- `AuditAspect @Around` intercepts: calls original method first, extracts `targetId` from **return value type** (`UserApiKey` / `Conversation` / `Message` → `.getId()`); void methods fall back to **last `Long` typed parameter**.
- Same transaction as `@Transactional` for atomic rollback.

### 8. admin bootstrap: pure env var
- `my-ai.admin.emails: []` (default empty); multiple emails comma-separated (env var `MYAI_ADMIN_EMAILS="a@x.com,b@x.com"`).
- `AuthService.register` checks if email matches list → role=ADMIN; otherwise USER.
- **No fallback**: if env var unset and user table non-empty, new registrations are all USER, no one can hit `/api/logs/**`.

### 9. Query API: admin-only 4 endpoints
- `GET /api/logs/ai-calls?from=&to=&page=&size=&sort=created_at,desc` (owner filter automatic)
- `GET /api/logs/ai-calls/{id}`
- `GET /api/logs/audit` (same params)
- `GET /api/logs/audit/{id}`
- `SecurityConfig` adds `/api/logs/**` hasRole("ADMIN"); `user` table gets `role VARCHAR(20) NOT NULL DEFAULT 'USER'`.
- `AuthPrincipal` / `JwtService` carry role.

### 10. Retention: configurable, default 30
- `my-ai.logs.retention-days: 30` (env var `MYAI_LOG_RETENTION_DAYS`).
- `LogCleanupTask @Scheduled(cron="0 4 * * * *")`: clear `ai_call_log.created_at < NOW() - retentionDays` + `audit_log.deleted_at IS NULL AND created_at < ?`.
- logback `maxHistory=${retentionDays}`.

### 11. TraceId protocol
- Upstream `X-Trace-Id` header → reuse; otherwise generate UUID v4.
- All responses (including SSE) write back `X-Trace-Id` response header.
- SSE token event data does NOT include trace_id (avoid redundancy).

## Considered Options

### 1.1 Scope
- **A. System log only** — rejected. Zero business code change but doesn't solve core visibility.
- **B. System + AI call log** — not chosen. HTTP access / audit left as follow-up, treats symptoms.
- **C. B + HTTP access log** — not chosen. Audit missing.
- **D. All 4** — **chosen**. One-time investment; splitting is hidden tech debt.

### 2.1 AI + audit log storage
- **A. Same H2 file, new tables** — **chosen**. Simple; same transaction; H2 console queryable.
- **B. Independent H2 file `./data/myailog.mv.db`** — rejected. Multi-data-source complex; not same transaction.
- **C. JSON Lines files** — rejected. AI call needs frequent userId/conversationId queries; DB index far better than grep.
- **D. ES / Loki / ClickHouse** — rejected. Way beyond local demo scope (CLAUDE.md §6 positioning).

### 3.1 AI token counting
- **A. No tokens** — rejected. Lose core observability (cost / input length).
- **B. `stream().chatResponse()` + `metadata.usage`** — **chosen**. Spring AI 2.x native support; NULL allowed when missing.
- **C. Async estimation** — rejected. Spring AI has no API; estimates inaccurate.

### 4.1 audit_log deletion
- **A. Hard delete** — not chosen. Accidental delete = lost; local demo also allows 30-day undo.
- **B. Soft delete (`deleted_at`) + retentionDays hard delete** — **chosen**. 30-day soft window = manual query fix possible; then physical clear.
- **C. Never clear** — rejected. H2 grows forever.

### 5.1 HTTP filter
- **A. Spring built-in `AbstractRequestLoggingFilter`** — rejected. Doesn't support recording status / duration at SSE completion.
- **B. Custom `OncePerRequestFilter`** — not chosen. Equivalent to C but starts after Security (can't see 401/403).
- **C. Servlet `Filter` + `FilterRegistrationBean` (before Security)** — **chosen**. Sees 401/403; missing user_id → `anonymous`.

### 6.1 System log format
- **A. All JSON** — **chosen**. Machine-parseable; aggregation stack friendly.
- **B. All plain text + MDC** — rejected. Aggregation stack hard to consume.
- **C. Profile differentiated** — rejected. Project uses single profile; maintenance cost > benefit.

### 7.1 MDC fields
- **A. trace_id + user_id** — rejected. Better than nothing.
- **B. trace_id / span_id / user_id** — rejected. No OTel collector, span unwatched.
- **C. Full: trace_id / user_id / conversation_id / request_method / request_path / client_ip** — **chosen**. Any log can directly see context.

### 8.1 Audit trigger
- **A. Explicit `auditLog.record(...)` calls** — rejected. Easy to forget in new code; ~6-8 places.
- **B. Spring AOP `@Auditable` + `@Around`** — **chosen**. Declarative; doesn't invade method body; same transaction as `@Transactional`.
- **C. `ApplicationEventPublisher` + `@EventListener`** — rejected. Async = possible loss; transaction misalignment.
- **D. Hibernate Envers** — rejected. Only covers entity CRUD, not business actions.

### 9.1 AOP targetId extraction
- **A. SpEL expression** — rejected. Verbose; no compile-time check.
- **B. Convention return type + last Long parameter fallback** — **chosen**. Minimal annotation; existing service methods already return entities.
- **C. Explicit `targetId` Long parameter** — rejected. Refactoring-fragile; service method signature stability low.

### 10.1 admin bootstrap
- **A. First registered user auto-admin** — rejected. Multi-user race; first to register rules all.
- **B. env var `MYAI_ADMIN_EMAILS` match** — **chosen**. Idempotent; multi-admin configurable; consistent with existing `MYAI_JWT_SECRET` style.
- **C. schema seed admin/admin123** — rejected. Weak password anti-pattern.

### 11.1 Query API exposure
- **A. H2 console only** — rejected. Unreachable without console.
- **B. Owner-only (users see own logs)** — not chosen. No admin view.
- **C. admin-only + role system** — **chosen**. Full RBAC; matches ADR 0004's compliance intent.

### 12.1 Retention
- **A. Never clear** — rejected. H2 grows forever.
- **B. Fixed 30 days** — rejected. Hardcoded.
- **C. Configurable `my-ai.logs.retention-days`, default 30** — **chosen**. Same pattern as trash; env var override.

## Consequences

### Data model changes
- `user` table adds `role VARCHAR(20) NOT NULL DEFAULT 'USER'` (idempotent `ALTER TABLE ADD COLUMN IF NOT EXISTS`).
- New table `ai_call_log` (12 cols + 1 index + FK).
- New table `audit_log` (9 cols + 1 index + 1 soft-delete col).
- `schema.sql` all use existing `CREATE TABLE IF NOT EXISTS` + `ALTER TABLE ADD COLUMN IF NOT EXISTS` idempotent pattern.

### API changes (non-breaking)
- New 4 admin-only endpoints (`/api/logs/**`).
- `/api/auth/register` response unchanged; user role only in JWT claims + admin emails match.
- Existing 11 conversation / message endpoints unchanged.
- `MessageService.streamReply` internally switches to `stream().chatResponse()` — **SSE event protocol unchanged** (`event: token / done / error` data still `Map.of("text", ...)`).

### New dependencies
- Backend: `spring-boot-starter-aop` (CLAUDE.md doesn't list; AOP needs it; check pom to decide explicit add or transitive via spring-boot-starter-web)
- Frontend: no change.
- logback `JsonEncoder` is Logback 1.5+ built-in; transitive via spring-boot-starter-logging.

### New error codes
- None (4010 for unauthenticated; 4030 for cross-user; new admin endpoints use existing 4030 via Spring Security `AccessDeniedException`).

### Business code changes
- Existing 5 codes unchanged. New `Role` field semantics not at business-code level (use Spring Security `AccessDeniedException`).

### Security boundary
- RBAC introduced: `USER` / `ADMIN`; admin granted via env var list, not self-registration.
- admin actions (querying `/api/logs/**`) recorded in `audit_log` (self-audit).
- `user_id` MDC missing → log as `anonymous` (401 / 403 paths).
- `audit_log.user_id` NULL = system background action.

### Performance / resources
- H2 growth: ~300 B / AI call + ~200 B / audit + ~150 B / HTTP access (~10 MB/month, cleared after retentionDays).
- logback file appender single-thread append write, no lock contention.
- AOP aspect inside `@Transactional` boundary, no extra DB connection.
- `TraceIdFilter` before Security but after DispatcherServlet registration; all requests pass once; MDC injection O(1) hash.

### UI / behavior changes
- No frontend change (admin endpoints v1 no UI; query via H2 console or curl).
- Spring Boot Actuator **not introduced** (CLAUDE.md §2 doesn't list; on-demand if needed).

### Known risks
1. **Ollama may not return usage** — `input_tokens` / `output_tokens` allow NULL.
2. **AOP `@Auditable` + `@Transactional` ordering** — aspect's `proceed()` must run inside transaction or audit log won't roll back.
3. **`ConversationCleanupTask` hard-delete soft-deleted conversations** — system action leaves audit (`user_id=NULL`); not a user-facing audit event.
4. **logback `JsonEncoder` vs pattern layout mutually exclusive** — Logback 1.5+ requires single-purpose encoder.
5. **TraceId not at SSE event frame level** — only response header; client reads once.
6. **`/api/logs/**` no pagination cap** — `size <= 200` enforced (anti-DoS).

## Status

✅ **Decisions locked** (2026-07-27). Implementation in new conversation follows this ADR; ~13 new + ~10 modified files, 5-7 days estimate.

## Related

- [[0003-conversations-and-messages]] — Previous ADR; AI call log embeds into `MessageService.streamReply` / `regenerate`.
- [[0001-defer-wechat-integration]] / [[0002-wechat-scan-login]] — Orthogonal.
- CLAUDE.md §4 architectural conventions will be updated at implementation; add "logging / audit" rules.
- CLAUDE.md §6 security boundary: RBAC partially mitigates "production multi-tenant not implemented"; audit_log fills "audit" gap.