# ADR 0003: Conversations and Messages Architecture

> 中文版: [`0003-conversations-and-messages.md`](./0003-conversations-and-messages.md).

**Status:** 🚧 Design (ratified 2026-07-27).

## Context

MyAi's original `POST /api/chat` was stateless: each call submitted a single message to the AI, the AI saw no history, and the user had no record. The user's request "record chat history and open different chat windows" forces a transition to a **stateful conversation model**: two new tables (`conversation`, `message`) that consolidate AI context, UI state, and user behavior boundaries under the single domain object of "conversation". The replacement of `/api/chat` is a breaking change that cannot be cleanly reversed once shipped.

## Decision

### 1. Data model
- **`conversation`**: `id, user_id, title, title_manually_set, created_at, updated_at, deleted_at`. `user_id` ON DELETE CASCADE. **No** `key_id` (see §2).
- **`message`**: `id, conversation_id, role, content, is_orphaned, created_at`. `conversation_id` ON DELETE CASCADE. `role` CHECK constrained to `'USER' | 'ASSISTANT' | 'SYSTEM'`.
- **`content` is plain-text Markdown source**; rendering happens in the frontend only (`marked` + `highlight.js` + `KaTeX` + `DOMPurify`). The backend never produces HTML.

### 2. Conversation does not lock a Key
- `conversation` table has no `key_id` column. Every message-send consults `User.default_key_id` and dispatches to that `UserApiKey`.
- The "false continuity" cost is accepted: if the user changes their default key mid-conversation, earlier and later messages may come from different models/providers.
- Rationale: keys are a user preference; conversations are a working unit. Decoupling them lets multi-key workflows (parallel "GPT-4 brainstorm" and "Claude code review" conversations) avoid schema overhead.

### 3. AI context = all non-orphaned messages within the conversation
- Before each AI call, fetch `WHERE conversation_id = ? AND is_orphaned = FALSE ORDER BY created_at` and assemble into the prompt.
- Conversations are isolated: conversation A's messages never leak into conversation B's prompt.

### 4. Edit and regenerate via `is_orphaned` soft-mark
- User edits a USER message → all later messages get `is_orphaned = TRUE` → AI re-runs with the new content + the conversation's pre-edit non-orphaned history.
- User clicks "regenerate" on an ASSISTANT message → AI re-runs with the same history, the new reply overwrites the old (old gets `is_orphaned = TRUE`).
- Orphaned messages are **not** physically deleted; they remain in the DB so users can regenerate back to an earlier branch.

### 5. Streaming output + reconnect (SSE)
- `POST /api/conversations/{id}/messages` returns `text/event-stream`.
- Client consumes with `fetch + ReadableStream`, rendering a typewriter animation.
- A complete `message.content` is persisted **after** the full token stream finishes.
- "Stop" button → `AbortController.abort()` → server detects client disconnect → cancels `ChatClient.stream()` → all generated tokens are discarded.
- Reconnect strategy deferred to implementation (candidates: server-side buffer replay / client-side cache / new AI call skipping already-shown tokens).

### 6. Multi-tab real-time sync via `BroadcastChannel`
- Browser-native `BroadcastChannel('my-ai-conversations')` broadcasts cross-tab events: `message:created`, `message:updated`, `message:orphaned`, `conversation:created`, `conversation:updated`, `conversation:deleted`, etc.
- No backend SSE channel needed; pure client-side cross-tab communication.
- Fallback: on `visibilitychange` and conversation switch, refetch from server.

### 7. Default Key unavailable → 4030 + UI引导
- Server detects `User.default_key_id IS NULL` / pointing to disabled Key / Key misconfigured → throws `BizException(code=4030)`.
- Client recognises 4030 → toast + "Go to settings" button that opens the Key manager.
- Preserves CLAUDE.md §4.6's existing rule "default Key management lives in `UserApiKeyService`" — no auto-fallback to the first enabled Key.

### 8. Soft delete + 30-day auto-cleanup
- `DELETE /api/conversations/{id}` → `deleted_at = NOW()`. No physical delete.
- `POST /api/conversations/{id}/restore` → `deleted_at = NULL`.
- Spring `@Scheduled(cron = "0 3 * * * *")` runs daily at 03:00, scanning `deleted_at < NOW() - 30 days`, hard-deleting those rows (CASCADE messages).
- Retention configurable via `@ConfigurationProperties`: `my-ai.trash.retention-days` (default 30).
- UI: a collapsed "Deleted (N)" section at the bottom of the sidebar, with [Restore] and [Delete permanently] buttons.

### 9. Context window management = transparent error pass-through
- Backend does **not** implement token truncation, summarisation, or sliding window.
- When the AI provider returns "context window exceeded", `BizException` wraps it with a clear message: "Conversation too long, please delete some history and retry".
- **Trigger for upgrade**: any user actively reports "conversation too long". Until then, no proactive truncation / summarisation.

## Considered options

### §2.1 Conversation-Key binding
- **A. Lock `key_id` at conversation creation** — rejected. Conceptually clean ("this conversation IS the GPT-4 one") but pointless for single-key users and only saves the multi-key user a 2-step "change default then new conversation" dance. Schema overhead not worth it.
- **B. Each message picks its own Key** — rejected. Switching models mid-thread breaks the AI's voice continuity and confuses the human reader. Mutually exclusive with §3 (AI sees all history).
- **C. No stored key, use `User.default_key_id` per call** — **chosen**. Simplest schema, most flexible.

### §4.1 Regeneration strategy
- **A. Append-only** — rejected. ChatGPT / Claude both have edit + regenerate; absence is a UX gap.
- **B. Edit USER but no regenerate** — rejected. Editing becomes a no-op since the AI never re-runs.
- **C. Edit + regenerate via `is_orphaned` soft-mark** — **chosen**. Mainstream behavior, soft mark avoids losing history.

### §5.1 Output mode
- **A. Non-streaming, wait for full response** — rejected. First-token latency and long-response UX suffer.
- **B. Streaming, no reconnect** — rejected. Network drop on a long stream = lost work.
- **C. Streaming + reconnect** — **chosen**. Reconnect mechanism to be decided in implementation.

### §6.1 Multi-tab sync
- **A. Independent tabs, refresh to see latest** — rejected. With streaming, "the other tab doesn't see what I just sent" is confusing.
- **B. `BroadcastChannel`** — **chosen**. Zero deps, pure client-side, simple event list.
- **C. WebSocket / SSE server-push** — rejected. All tabs are same-origin clients; routing through the server is wasteful.
- **D. Block multi-tab same-conversation (Web Locks API)** — rejected. "Two tabs of the same conversation while cross-referencing" is a common workflow.

### §7.1 Default Key missing
- **A. Error + UI引导 to Key settings** — **chosen**. Preserves existing convention (CLAUDE.md §4.6).
- **B. Auto-fallback to first enabled Key** — rejected. Breaks existing convention; makes "which Key is in use" opaque.
- **C. Silent / generic error** — rejected. "User hasn't configured a Key" must be distinguishable from "AI service is down".

### §8.1 Delete semantics
- **A. Hard delete, irreversible** — rejected. Must offer a recovery path.
- **B. Soft delete, permanent** — rejected. H2 file grows unbounded.
- **C. Soft delete + 30-day auto hard delete** — **chosen**. Recovery window + automatic cleanup.
- **D. Soft delete, clear messages but keep shell** — rejected. Empty shell confuses the user.

### §9.1 Context window
- **A. Pass through, AI provider errors surface** — **chosen**. §3's "do A, optimise when broken" promise, now fulfilled.
- **B. Server truncates to last N** — rejected (for now). Fixed N is arbitrary; per-model token limits add configuration burden.
- **C. Sliding window + old-message summary** — rejected (for now). Adds AI calls, summary storage, summary update policy — order of magnitude more complex.

## Consequences

### Data model changes
- New `conversation` table (9 columns) and `message` table (5 columns + 2 constraints).
- `schema.sql` uses `CREATE TABLE IF NOT EXISTS` + `ALTER TABLE ADD COLUMN IF NOT EXISTS` for backward compatibility with existing H2 files (per CLAUDE.md §4 "idempotent DDL" pattern).

### API changes (breaking)
- `POST /api/chat` removed.
- 11 new endpoints (see design summary §2), all auth-required.
- `ChatController` splits into `ConversationController` + `MessageController`.

### New dependencies
- Backend: `spring-ai-openai` / `spring-ai-ollama` / `spring-ai-anthropic` `stream()` APIs (already in pom, no new deps).
- Frontend: `marked` or `markdown-it`, `highlight.js`, `KaTeX`, `DOMPurify`. ~400KB min+gz total.

### New error codes
- 4030 (default Key unavailable), 4031 (conversation not found / deleted), 4032 (message not found / wrong user), 4033 (edit target not USER), 4034 (regenerate target not ASSISTANT).

### UI changes
- `App.vue` evolves from "four-panel single SFC" to "left sidebar + main area + top menu / bottom drawer". Still single-file; no vue-router.
- User / Key panels move from "independent tab" to "modal / drawer"; backend API unchanged.

### Performance / resources
- H2 file growth: one row per conversation + one per message; soft-deleted cleaned after 30 days. Bounded under normal use.
- AI provider call rate: 1 AI call per USER message (streaming). Without reconnect, a dropped stream = 1 manual resend.

### Known risks
- §5.1 reconnect: implementation-phase decision; v1 may degrade to "new AI call + client skip already-shown tokens", wasting AI tokens.
- §2 "false continuity": changing default Key mid-conversation means earlier and later messages may come from different models. Accepted cost.
- §1 (image rendering under Q19=D): AI providers rarely return image URLs; v1 image rendering only serves "user pastes a link", no image upload.

## Status

🚧 **In design** (ratified 2026-07-27). Implementation requires a new conversation to land the schema / entity / service / controller / frontend / scheduled task per this ADR. Do **not** begin coding based on this document alone.

## Related

- [[0001-defer-wechat-integration]] — orthogonal (deferred WeChat chat channel).
- [[0002-wechat-scan-login]] — orthogonal (WeChat scan login).
- CLAUDE.md §4 "Key architecture conventions" will be updated in the implementation phase to add two new constraints covering "conversation" and "message".
