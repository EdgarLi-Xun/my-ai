# WeChat Scan-to-Login (Design Decision)

MyAi's web admin UI will add **WeChat QR scan login**, coexisting with the existing email + password login (design finalized 2026-07-24). This is an authentication decision, unrelated to the deferred "WeChat as chat channel" decision in [0001](0001-defer-wechat-integration.md) — 0001 governs where a bot sends/receives messages; this document governs how users log in. This round produces design only, no code. Implementation is gated on WeChat Open Platform qualification (see "Prerequisites").

> 中文版：`0002-wechat-scan-login.md`。

## Prerequisites (must hold before implementation)

- **Organization qualification**: Open Platform "website app WeChat login" is only available to organization accounts (enterprise / sole proprietorship, etc.); individual accounts cannot be certified. Fee: 300 CNY/year (verified 2026-07).
- **ICP-filed domain**: the OAuth redirect domain must be an ICP-filed domain. The app currently runs as a local single machine (port 8031); we plan to debug via tunneling, but **tunnel domains cannot be ICP-filed** and WeChat may reject them outright — must be re-validated at implementation time; worst case requires a public deployment.
- **AppSecret management**: injected via environment variables, never committed. `application.yml` gains a `my-ai.wechat` section (`app-id` / `redirect-uri` / `enabled`, default `false`); the frontend hides the QR entry when disabled.

## Core Flow

1. Login page embeds the official WeChat QR code (Open Platform JS SDK; no full-page redirect).
2. After scan + authorization, WeChat 302s to the **backend callback** with `code` + `state` (`redirect_uri` points at the backend).
3. Backend validates the one-time `state` (CSRF), then exchanges `code` + AppSecret for `access_token` / `openId` / `unionId`.
4. Looks up `user` by `unionId` (preferred) then `openId`:
   - bound → issue JWT directly;
   - unbound → **auto-register** (`name` = WeChat nickname fallback; `email` / `password_hash` = `NULL` — the existing `AuthService.login` already handles a NULL hash, so such users simply cannot log in by password).
5. Backend 302s back to the frontend with the JWT in the **URL fragment** (`#token=...`), keeping it out of server logs.

## Key Decisions

- **Coexist, don't replace**: password login stays; WeChat scan is an additional identity source; no forced migration of existing users.
- **Auto-register**: an unbound WeChat identity creates an account on first scan — no "bind first" or whitelist (UX first; consistent with `/api/auth/register` semantics in this multi-user app).
- **Columns on `user`**: add `wechat_open_id` (unique) and `wechat_union_id` via idempotent `ALTER TABLE` in `schema.sql`; no separate identity table (only one external identity source today — no abstraction for hypothetical futures).
- **Binding for existing accounts**: a logged-in user scans a QR on the settings page (binding flow uses a distinct `state` marker; the callback writes the openId onto the current account instead of auto-registering).
- **state / code security**: `state` is backend-issued, one-time, strictly validated; `code` is one-time per WeChat — never cached or replayed by the backend.

## Considered Options

- **Full-page redirect authorization** — rejected. Simpler, but breaks page state; embedded QR is the mainstream, better-UX choice.
- **`redirect_uri` on a frontend route, frontend exchanges code via backend API** — rejected. Symmetric with `/api/auth/login` and keeps the token out of the URL, but the user chose the backend-callback flow (one fewer round trip); the fragment-based token handoff mitigates URL leakage.
- **Separate `user_identity` table** — rejected. One external identity source today; migrate if a second one ever appears.
- **Scan-then-bind-existing-account flow (overturning auto-register)** — rejected. Extra password step hurts new-user UX; the binding need is covered by the logged-in settings-page scan.

## Consequences

- `user` schema change: `+ wechat_open_id` (unique index), `+ wechat_union_id`.
- New public endpoints (design): QR state issuance, WeChat callback (GET, 302), logged-in binding callback; `SecurityConfig` whitelist must permit the callback paths.
- A new user category with neither password nor email exists; any future logic assuming non-null email (e.g., password reset) must handle it.
- Security boundary loosens: tunneling exposes a callback path to the public internet for debugging; a real public deployment requires re-evaluating JWT storage and token refresh (tokens currently live in session memory only).
- No WeChat SDK: the OAuth dance is two HTTPS calls (code→token, userinfo), servable by the existing HTTP client.

## References (verified 2026-07)

- [JustAuth — WeChat Open Platform login guide](https://www.justauth.cn/guide/oauth/wechat_open/)
- [WeChat Open Platform official docs](https://developers.weixin.qq.com/doc/oplatform/en/Third-party_Platforms/2.0/operation/open/create.html)
- [Developer qualification application guide](https://blog.lusyoe.com/article/wechat-login-developer-qualification-guide.html)
