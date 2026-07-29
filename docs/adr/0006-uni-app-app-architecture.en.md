# ADR 0006: uni-app App Architecture with Configurable Backend Address

> 中文版: [`0006-uni-app-app-architecture.md`](./0006-uni-app-app-architecture.md).

**Status:** 🚧 Design (ratified 2026-07-29 via `/grill-with-docs`; awaiting user notification before implementation).

## Context

MyAi (Java 21 / Spring Boot 4.0.7 / MyBatis-Flex 1.11.8 / Vue 3.4 + Vite 5.4) currently ships as a single-page web frontend (1040-line `App.vue` + `lib/{sse,markdown}.js`) talking to a Spring Boot backend that has reached "strict three-layer architecture + observability + RBAC + JWT + SSE streaming" maturity via ADR 0003 / 0004 / 0005. The user has raised a new requirement:

1. Mirror existing web functionality to mobile apps (mobile usage is a forthcoming high-frequency scenario)
2. **App must support user-supplied backend address** — users may self-host multiple instances (home / office / staging), so the App cannot hard-code a single URL

Factual constraints:

- **HarmonyOS NEXT applications / atomic services can only be produced by `uni-app x`** (uts → ArkTS). Traditional uni-app (Vue + JS) does not support HarmonyOS — this is a hard DCloud line, not a configuration choice.
- Backend ADR 0005 (three-layer) + ADR 0004 (observability) + ADR 0003 (SSE streaming) + ADR 0002 (WeChat scan login design — drafted but not implemented) are all already multi-client-ready; **reuse value is high**.
- `frontend/src/lib/{sse,markdown}.js` is platform-agnostic and can be absorbed into a shared SDK.
- CLAUDE.md §6 locks the product position as a "local single-machine app" — not exposed to the public internet; App should similarly not be submitted to app stores.
- CLAUDE.md §6 warns: "Custom `baseUrl` lets the backend make outbound requests to user-supplied addresses (SSRF risk)." — but that warning is about `UserApiKey.baseUrl` (backend → AI provider). **App-supplied backend URL is the inverse trust direction** (App trusts the user to choose a backend), **not SSRF** — but URL validation is still required.

`/grill-with-docs` resolved all thirteen decisions below (Q1 adjusted to "A + HarmonyOS"; Q13 added for backend URL configuration).

## Decision

### 1. Target platforms = Android + iOS + HarmonyOS NEXT + H5 (fallback)

| Platform | Necessity | Notes |
| --- | --- | --- |
| Android | One of the user's primary scenarios | Custom-base `.apk` direct install |
| iOS | One of the user's primary scenarios | Apple Developer account ($99/yr) for real-device debug |
| HarmonyOS NEXT | Explicitly requested by user | AppGallery Connect qualification + real-device UDID registration |
| H5 | Fallback for legacy links / mobile browser | `uni-app x` builds H5 to be deployed into existing `src/main/resources/static/` |

### 2. Framework = uni-app x (Vue 3 + Vite + **uts**)

- **Not traditional uni-app (Vue + JS)** — does not support HarmonyOS.
- **Business logic in uts** (TypeScript superset, compiles to ArkTS on HarmonyOS, Swift/Kotlin on iOS/Android).
- **UI in Vue 3 SFC** (`<template>` + `<script setup lang="uts">`).
- Build tooling: Vite (built into HBuilderX 4.x+; or CLI standalone project).
- Do not pull TypeScript-era traditional uni-app plugin ecosystem (some plugins only support the traditional version).

### 3. Repo strategy = dual-repo parallel (web and App physically isolated)

```
D:/MyWork/myAi/                       # Existing web + backend (untouched)
D:/MyWork/myAi-sdk/                   # NEW: shared SDK (standalone npm package)
D:/MyWork/myAi-app/                   # NEW: uni-app x App repo
```

- **Dual repo** = git-physical isolation, independent version numbers, independent release cadence.
- **Shared SDK** = cross-repo decoupling point; platform-agnostic code is written once.
- **Backend stays in the existing MyAi repo** under `src/main/java/` — App talks to it via HTTP/SSE; **backend code is NOT part of the dual-repo scope.**

### 4. Backend strategy = completely reuse existing Spring Boot + reserve `/api/app/**` namespace

- **Zero backend forking** — all existing endpoints (`/api/auth/*` / `/api/chat` / `/api/conversations/*` / `/api/users/*/keys/*` / `/api/logs/*` / `/api/providers`) are called directly by App.
- **MVP actually needs no new `/api/app/**` endpoints** — every feature is shared with web via existing endpoints.
- **`/api/app/**` namespace is reserved** for v2 (push-token registration, HarmonyOS atomic-service enrollment, etc.); MVP does not add any.
- No new backend stack (still MyBatis-Flex + Spring Security + Spring AI).

### 5. Code sharing = shared SDK package (TypeScript implementation, both ends consume)

| Repo | How SDK is consumed |
| --- | --- |
| `myAi-app` (uni-app x) | `import { ... } from '@myai/sdk'` (uts consumes TS package via interop — supported by HBuilderX) |
| `myAi` (web Vue 3 + Vite) | `import { ... } from '@myai/sdk'` (native TS; only need to rewrite existing import paths in `App.vue`) |

**SDK content boundary**:

```
myAi-sdk/
├── src/
│   ├── api/             # API clients (chat / conversations / keys / auth)
│   ├── streaming/       # StreamingResponse abstraction (H5 EventSource / App fetch+ReadableStream)
│   ├── auth/            # AuthProvider interface + EmailPasswordAuthProvider (MVP impl)
│   ├── storage/         # Token / backend URL / user-preference persistence (wraps uni.storage)
│   ├── errors/          # Error code → user-facing message mapping
│   ├── media/           # MediaUploadProvider interface (placeholder, v2 impl)
│   ├── push/            # PushProvider interface (placeholder, v2 impl)
│   ├── utils/           # Common utilities (time, URL validation, UUID, etc.)
│   └── types/           # Type definitions (matches backend Dto/Vo schema)
├── tests/               # vitest unit tests
├── package.json
└── tsconfig.json
```

**SDK internal conventions**:
- Pure TypeScript (no `uni.*` API imports); App-side injects `storage` / `http` implementations via DI.
- No UI components.
- No business rules (business lives in App-side code).

### 6. App-side authentication = MVP = email/password + JWT (SDK builds `AuthProvider` interface skeleton on day one)

```typescript
// SDK skeleton (build on day one, fill implementations in v2)
interface AuthProvider {
  login(credentials): Promise<AuthResult>
  register(credentials): Promise<AuthResult>
  logout(): Promise<void>
  getCurrentUser(): Promise<User | null>
  refreshSession(): Promise<AuthResult>
}

class EmailPasswordAuthProvider implements AuthProvider { ... }  // MVP impl
class HuaweiOAuthProvider implements AuthProvider { ... }        // placeholder, v2
class WeChatOAuthProvider implements AuthProvider { ... }        // placeholder, v2 (triggers ADR 0002)
class AppleIDAuthProvider implements AuthProvider { ... }        // placeholder, v2
```

- **MVP backend zero changes** — reuses existing `/api/auth/{register, login, me}`.
- **Token stored in `uni.storage`** — acceptable under the local single-machine app premise (CLAUDE.md §6).
- **iOS submission requires Apple ID** — MVP not submitted to store, defer AppleIDAuthProvider to v2.
- **HarmonyOS submission requires Huawei ID** — MVP not submitted to store, defer HuaweiOAuthProvider to v2.

### 7. API Key security = fully reuse existing pattern (user input → backend → frontend only holds masked value)

- App-side key input box submits to backend `/api/users/{id}/keys`.
- Frontend never holds plaintext key (response only returns `****abcd` masked value).
- App-side does not cache locally (consistent with web).
- SDK provides `KeyInputHintProvider` (clipboard auto-fill / screenshot protection / cloud-backup prevention) — **all disabled by default**, v2 configurability.

> **SSRF misreading clarification**: The CLAUDE.md §6 warning about "custom baseUrl SSRF risk" is about `UserApiKey.baseUrl` (backend induced to call internal network). App-supplied backend URL is the inverse trust direction (App trusts the user's backend choice), **does not constitute SSRF**. The two are not to be confused.

### 8. SSE streaming response = keep backend SSE, frontend `fetch + ReadableStream` adapts

- **Backend zero changes** — preserves ADR 0003 §4 SSE event boundaries + ADR 0004 `ai_call_log` latency calculation.
- SDK writes a `StreamingResponse` abstraction, exposing unified `onToken / onDone / onError` callbacks to business code.
- **H5 end**: `EventSource` (identical to existing `frontend/src/lib/sse.js`, absorbed into SDK).
- **App end**: `uni.request({ streaming: true })` + `body.getReader()` (stable on H5 / iOS / Android; HarmonyOS needs PoC verification).
- **HarmonyOS PoC failure fallback**: backend chunked + JSON Lines (`Content-Type: application/x-ndjson`) — **only changes backend streaming implementation, leaves SSE event semantics unchanged**.

```typescript
// SDK abstraction
class StreamingResponse {
  onToken: (text: string) => void
  onDone: (usage?: TokenUsage) => void
  onError: (err: Error) => void
  close(): void
}
async function streamChat(params: ChatParams): Promise<StreamingResponse> {
  // H5: new EventSource(...)
  // App: uni.request({ streaming: true, ... })
}
```

### 9. UI component library = uni-ui (DCloud official)

- Best cross-end coverage (including HarmonyOS NEXT).
- Most stable maintenance (priority adaptation on uni-app upgrades).
- Minimalist style; custom CSS variables align with web brand colors.
- **Business components are NOT in the SDK** (web and App forms differ too much); UI library only covers infrastructure components (button, list, input, tabbar, loading, pull-refresh).
- **Business components (message bubble, key management form, conversation list item) are hand-written in App-side code.**

### 10. MVP native capabilities = 1:1 mirror, all native capabilities deferred to v2

| Native capability | MVP status | v2 integration |
| --- | --- | --- |
| Push notifications | Deferred | SDK reserves `PushProvider` interface; v2 adds iOS APNs / Android FCM / HarmonyOS Push Kit |
| WeChat scan login | Deferred | Triggers ADR 0002 implementation |
| Huawei ID login | Deferred | `HuaweiOAuthProvider` v2 implementation |
| Apple ID login | Deferred | Required for iOS submission |
| Image upload + conversation | Deferred | `MediaUploadProvider.uploadImage` + backend multipart endpoint; ~2 weeks (scenarios 1+3) |
| Image generation | Permanently assessed separately | Independent product capability from chat App |
| Device fingerprint / risk control | Not done | Local single-machine app premise |

**MVP experience boundaries**:
- User receives no notifications when offline (must actively open App to fetch).
- Text-only conversation only.
- Login via email/password only.

### 11. Build and release = custom base + local distribution + local offline packaging

| Platform | Build method | Distribution |
| --- | --- | --- |
| Android | Local offline packaging (Android SDK) | debug `.apk` direct install; release keystore self-signed |
| iOS | Local offline packaging (Xcode) | Apple Developer account ($99/yr) required; HBuilderX built-in iOS simulator needs no account |
| HarmonyOS | Local offline packaging (DevEco Studio) | Huawei Developer account + real-device UDID registration required |
| H5 | `npm run build:h5` | Local nginx or CDN |

- **No app-store submissions** — consistent with CLAUDE.md §6 "local single-machine app" position.
- **No DCloud cloud packaging** — code security (API key business logic / auth logic does not upload).
- iOS / HarmonyOS developer accounts are implementation-phase sub-questions (not locked in this ADR).

### 12. Test strategy = SDK unit tests (vitest) + App business-code manual tests

**SDK unit test coverage** (vitest):
- API client (mock backend, verify request path / method / body / headers)
- StreamingResponse parser (SSE event → callback)
- Error code mapping (`BizException` code → user-facing message)
- Token persistence / restoration
- AuthProvider interface contract (mock impl + EmailPasswordAuthProvider real path)
- Backend URL validation (`validateBackendUrl` URL format + `/api/providers` ping)

**App manual test checklist**:
- Login / register / logout
- Key CRUD (add / edit / delete / set-default / enable / disable)
- Create conversation / switch conversation / delete conversation
- Send message → streaming receive
- Edit message → trigger regenerate
- Logout → token cleanup
- **First launch without URL → config-page flow**
- **Settings page edit URL → validation flow**

**Cross-platform verification**: H5 + iOS + Android + HarmonyOS each runs the manual checklist (first version).

### 13. App backend address configurability (new user constraint) = hardcoded default + editable

**SDK design**:

```typescript
// SDK interface
interface BackendConfig {
  getBaseUrl(): string                      // read current URL
  setBaseUrl(url: string): Promise<void>    // change URL, auto-persist + validate
  validateBackendUrl(url: string): Promise<ValidationResult>  // ping /api/providers
  reset(): Promise<void>                    // clear config (for first launch)
}

// Persistence
uni.setStorageSync('myai.backend_url', 'https://api.example.com')

// Validation
async function validateBackendUrl(url) {
  try {
    const resp = await fetch(`${url}/api/providers`)
    return { ok: resp.ok, error: resp.ok ? null : `HTTP ${resp.status}` }
  } catch (e) {
    return { ok: false, error: e.message }
  }
}
```

**App launch flow**:

```
Launch
  ↓
Read myai.backend_url from uni.storage
  ↓
Exists? ── No ─→ Enter "Configure Backend Address" page (forced)
  ↓ Yes
Call validateBackendUrl(url)
  ↓
Reachable? ── No ─→ Enter "Configure Backend Address" page (with error + current URL)
  ↓ Yes
Read myai.token
  ↓
Exists? ── No ─→ Enter Login page
  ↓ Yes
Enter main UI (conversation list / key management)
```

**Build-time default value**:
- `myAi-app/.env.production`: `VITE_DEFAULT_BACKEND_URL=https://api.example.com`
- Replace with own instance before team distribution; leave empty for generic distribution (forced config on first launch).

**Security notes**:
- URL must start with `http://` or `https://`; format validated.
- Warning prompt: "When using a public backend, please confirm it is a service you trust."
- Token bound to URL by storage (key includes URL hash), **avoid cross-backend token leakage**:
  ```typescript
  // Not recommended: uni.storage stores myai.token (any backend URL can use it)
  // Recommended: uni.storage stores myai.token.<urlHash> (only current URL can use it)
  ```

**CLAUDE.md §6 SSRF misreading clarification**: The original clause refers to `UserApiKey.baseUrl` (backend induced to call internal network), **completely opposite** from App-supplied backend URL — App actively trusts the user's backend choice, **does not constitute SSRF**. This ADR does not change the existing CLAUDE.md §6 security conclusion.

## Considered Options

### §1.1 Target platform scope
- **A. H5 only** (not really uni-app) — Rejected. Just modify existing Vue project; no uni-app needed.
- **B. WeChat mini-program only** — Rejected. Mini-programs have no `localStorage`, SSE needs alternative; user's scenario isn't in WeChat ecosystem.
- **C. Full-stack (Android + iOS + HarmonyOS + mini-program + H5)** — Not selected. Highest maintenance cost.
- **D. Android + iOS + HarmonyOS + H5 (fallback)** — **Selected** (user adjustment: "A + HarmonyOS"). Covers primary scenarios + legacy link compatibility.

### §2.1 Framework form
- **A. Use uni-app x from the start (Vue 3 + Vite + uts)** — **Selected**. HarmonyOS NEXT forces it; avoids "write once then rewrite."
- **B. Use traditional uni-app first for Android+iOS+H5, defer HarmonyOS** — Not selected. Means uts migration = second development.

### §3.1 Repo strategy
- **A. Single-repo monorepo** (`myAi/{web, app, sdk, backend}`) — Not selected. Packages coupled, git history muddled.
- **B. Add `app-frontend/` subdirectory to existing repo** — Rejected. Contradicts "independent evolution" intent; git operations complex.
- **C. Dual repo + shared SDK independent repo** — **Selected**. Physical isolation; SDK decoupled.

### §4.1 Backend strategy
- **A. Completely reuse existing Spring Boot** — **Selected**. Zero backend forking; preserves all ADR 0002/0003/0004/0005 outcomes.
- **B. New `app-api` submodule in existing repo** — Not selected. Same-repo different-modules increases coupling.
- **C. Completely new standalone backend** — Rejected. Rebuild means overturning existing ADRs; workload too large.

### §5.1 Code sharing
- **A. Zero sharing** — Rejected. DTO changes need manual sync on both ends; type drift almost inevitable.
- **B. Share type definitions** (OpenAPI → both ends generate) — Not selected. Zero business-code sharing but generator maintenance cost high.
- **C. Share SDK package** (types + API clients + utilities) — **Selected**. Platform-agnostic code naturally written once; TS impl + uts interop.
- **D. Git submodule** — Rejected. IDE indexing / dependency install unfriendly.

### §6.1 App-side authentication
- **A. MVP = email/password + JWT** — **Selected**. Backend zero changes; fastest to MVP; MVP not on store so no Apple ID / Huawei ID needed.
- **B. WeChat scan login** (triggers ADR 0002) — Deferred to v2.
- **C. WeChat mobile OAuth** — Rejected. Only for in-WeChat H5 / mini-program, not for App.
- **D. Multi-account system** (email + WeChat + Apple + Huawei) — Not selected for MVP. Too complex.

### §7.1 API Key security
- **A. Fully reuse existing pattern** (user input → backend → frontend only holds masked value) — **Selected**. Backend zero changes; zero extra security responsibility on App side.
- **B. App-side extra plaintext cache in `uni.storage`** — Rejected. iOS review risk; current risk locked to local single-machine, no need.
- **C. Side encryption + HarmonyOS KeyStore / iOS Keychain / Android Keystore** — Rejected. Over-engineering.
- **D. Completely forbid App-side Key management** — Not selected. Experience fragmentation.

### §8.1 SSE streaming
- **A. Keep SSE, frontend fetch + ReadableStream adapts** — **Selected**. Backend zero changes; SDK abstraction unified.
- **B. Backend switches to WebSocket** — Rejected. AI streaming only needs half-duplex; modifying ADR 0003 §4 costly.
- **C. Backend switches to chunked + JSON Lines** — As HarmonyOS PoC failure fallback.
- **D. Switch to polling** — Rejected. Bad UX.

### §9.1 UI component library
- **A. uni-ui (DCloud official)** — **Selected**. Best cross-end coverage; most stable maintenance.
- **B. uView Plus** — Not selected. Community-maintained; long-term sustainability weaker than official.
- **C. NutUI-UNI** — Not selected. HarmonyOS support lags behind official library.
- **D. All hand-written** — Rejected. Too many App components; ~2-3 weeks workload.
- **E. Commercial UI library** — Not selected for MVP.

### §10.1 MVP native capability scope
- **A. MVP = 1:1 mirror** — **Selected**. All native capabilities deferred to v2.
- **B. A + push notifications** — Not selected for MVP. Push service selection complex.
- **C. A + WeChat scan login** — Not selected for MVP. Deferred to separate v2 PR.
- **D. A + image upload conversation** — Not selected for MVP. ~2 weeks workload would slow core validation.
- **E. Everything** — Rejected. Workload huge.

### §11.1 Build and release
- **A. Custom base + local distribution** — **Selected**. No review; consistent with CLAUDE.md §6 lock.
- **B. iOS + HarmonyOS app market + Android APK direct install** (hybrid) — Not selected for MVP. Needs Apple ID / Huawei account, conflicts with MVP auth decision.
- **C. Full-stack formal app stores** — Rejected. Workload huge.
- **D. HBuilderX cloud packaging vs local offline packaging** — **Selected: local offline** (paired with A). Code does not upload.

### §12.1 Test strategy
- **A. Pure manual testing** — Not selected. SDK change risk high.
- **B. SDK unit tests + App manual tests** — **Selected**. SDK is core, must automate; App UI testing ROI low.
- **C. B + critical-flow E2E** — Not selected for MVP. Medium maintenance cost.
- **D. Full CI/CD + real-device cloud testing** — Rejected. Excessive for MVP.

### §13.1 App backend address strategy
- **A. Hardcoded default + editable** — **Selected** (after user clarification). Build-time default URL; user can change and validate; fits self-hosting + team distribution.
- **B. Empty default + first-launch forced config** — Not selected. Generic distribution package scenario possible but MVP not distributing.
- **C. Hardcoded default + multi-URL profile** — Not selected for MVP. v2 feature.

## Consequences

### New repos

| Repo | Path | Scope |
| --- | --- | --- |
| `myAi-sdk` | `D:/MyWork/myAi-sdk/` | Shared SDK (standalone npm package, TS impl) |
| `myAi-app` | `D:/MyWork/myAi-app/` | uni-app x App repo |

### Existing repo changes

- **Web side**: `App.vue` and other frontend code continues maintenance in `frontend/`; can gradually import SDK to replace inline code (not required).
- **Backend**: zero code changes for MVP; reserve `/api/app/**` namespace.
- **CLAUDE.md**: implementation-phase additions — §2 (project facts — App end) + §4 (key architectural conventions — App section) + §6 (security boundary — URL config + SSRF misreading clarification).

### Data model changes

- **None**. DB schema unchanged; existing 6 tables unchanged.

### API changes

- **No new endpoints for MVP**. All App-side needs reuse existing `/api/*`.
- **Reserved** `/api/app/**` namespace for v2.

### Dependency additions

| Repo | Dependency | Purpose |
| --- | --- | --- |
| `myAi-sdk` | TypeScript 5.x + vitest | SDK compile + unit tests |
| `myAi-sdk` | (no runtime deps) | SDK stays lightweight; `uni.storage` / `fetch` injected by App side |
| `myAi-app` | uni-app x (Vue 3 + Vite + uts) | App framework |
| `myAi-app` | uni-ui | UI component library |
| `myAi-app` | `@myai/sdk` (local path or npm) | Shared SDK |
| Backend (MyAi) | **None new** | MVP does not touch backend deps |

### Error code / business code changes

- **None**. Existing `BizException` codes all reused.

### Performance / resources

- SDK adds one layer of indirection — negligible.
- uni-app x build artifact size: ~5-8 MB / platform (includes uni-ui + business code).
- H5 artifact: ~1.5-2 MB (comparable to existing web).

### Impact scope estimate

| Category | Quantity / workload |
| --- | --- |
| New repos | 2 (`myAi-sdk` + `myAi-app`) |
| SDK source files | ~20 (API clients + types + streaming + AuthProvider + storage + errors + utils + tests) |
| App-side business components | ~15 (login / key management / conversation list / message bubble / config page / settings page etc.) |
| Backend changes | **0** (MVP does not touch backend) |
| Doc changes | This ADR + PLAN + CLAUDE.md §2/§4/§6 |

### Verification

1. **SDK unit tests**: `pnpm test` (vitest) must pass — SDK is the shared core between App and web.
2. **App cross-end build**:
   - `npm run build:h5` (H5 artifact runnable)
   - HBuilderX / CLI builds Android `.apk` / iOS `.ipa` / HarmonyOS `.app` (no store submission needed)
3. **First-launch flow**: clear `uni.storage` → restart App → should enter "Configure Backend Address" page.
4. **URL validation flow**: fill wrong URL → should show "Cannot connect to {url}".
5. **Login flow**: fill correct URL → config page passes → enter login page → register / login success → enter main UI.
6. **Core features**: login → create conversation → send message → streaming receive; Key CRUD; logout.
7. **Cross-platform consistency**: H5 + Android + iOS + HarmonyOS each runs core features.

### Landing commit splits (suggestion)

**Phase 1: SDK skeleton** (standalone repo `myAi-sdk/`)
1. Repo init + tsconfig + vitest config
2. Type definitions (align with backend Dto/Vo schema)
3. `BackendConfig` (URL persistence + validation)
4. `AuthProvider` interface + `EmailPasswordAuthProvider` impl
5. API clients (auth / users / keys / conversations / messages)
6. `StreamingResponse` abstraction (H5 / App branches)
7. Error code mapping
8. Unit tests (vitest)

**Phase 2: App repo** (standalone repo `myAi-app/`)
1. uni-app x project init + uni-ui intro
2. `pages/index/` first-launch logic (read URL → config page or main UI)
3. `pages/config-backend/` config page
4. `pages/login/` login page
5. `pages/conversations/` conversation list
6. `pages/conversation/:id` conversation detail + streaming messages
7. `pages/keys/` key management
8. `pages/settings/` settings page (with URL config)
9. Cross-end build verification

**Phase 3: Existing web-side SDK integration** (optional, non-blocking)
- `myAi/frontend/src/App.vue` switches to `import { ... } from '@myai/sdk'`
- Existing `lib/sse.js` / `lib/markdown.js` removed (SDK covers)

### Known risks

1. **uni-app x ecosystem thin** — some plugins only support traditional uni-app; MVP stage using only uni-ui official library avoids this.
2. **HarmonyOS streaming-response PoC unverified** — if `uni.request({ streaming: true })` does not expose chunk boundaries on HarmonyOS real device, must downgrade to §8 option C (chunked + JSON Lines).
3. **Dual-repo version sync** — every SDK change requires App + web to bump version; use semver + `package.json` version lock.
4. **iOS / HarmonyOS developer accounts not yet applied** — implementation-phase sub-question; real-device debug requires prior application.
5. **App package name / Bundle ID undecided** — implementation-phase sub-question.
6. **CLAUDE.md §6 SSRF misreading** — implementation-phase revision must clarify the difference between `UserApiKey.baseUrl` SSRF and `App.backendUrl` reverse-trust.
7. **App backend URL + token binding storage** — avoid cross-backend token leakage; token key format `myai.token.<urlHash>`.

### Out of scope (never do in MVP)

- Push notifications (iOS APNs / Android FCM / HarmonyOS Push Kit)
- WeChat scan login (ADR 0002 deferred to v2)
- Huawei ID / Apple ID login (when submitting to store)
- Image upload conversation (scenarios 1+3 deferred to v2, ~2 weeks workload)
- Image generation (independent product capability, permanently assessed separately)
- App-store submission (iOS App Store / Huawei App Market / domestic Android stores)
- Device fingerprint / risk control
- Multi-backend URL profile (v2)

## Status

🚧 **Design ratified** (2026-07-29 grill-with-docs completed 13 decisions). **Awaiting user notification before implementation** — this ADR writes no App / SDK business code, only locks design.

## Related

- [[0005-three-layer-architecture]] — Backend three-layer architecture is the contract foundation for SDK API client calls; SDK type definitions align with web-layer Dto/Vo schema.
- [[0004-observability]] — App-side SSE calls automatically gain `ai_call_log` writes (latency / tokens metering); `X-Trace-Id` response header continues to return.
- [[0003-conversations-and-messages]] — SSE event protocol (`event: token` / `event: done` / `event: error`) is the contract foundation of SDK `StreamingResponse` abstraction.
- [[0002-wechat-scan-login]] — Deferred to v2 trigger implementation; SDK `WeChatOAuthProvider` placeholder.
- [[0001-defer-wechat-integration]] — No conflict; App-side auth uses email/password + JWT.
- CLAUDE.md §2 / §4 / §6 implementation-phase updates: add App-side project facts + backend URL config security boundary + SSRF misreading clarification.

---

## Implementation checklist (awaiting user notification before starting)

### Phase 1: SDK skeleton (`myAi-sdk/` new repo)
- [ ] Repo init (`package.json` + `tsconfig.json` + `vitest.config.ts` + `.gitignore`)
- [ ] Type definitions (one-to-one with `cn.edgarli.web.dto.*Dto` / `web.vo.*Vo`)
- [ ] `BackendConfig` (getBaseUrl / setBaseUrl / validateBackendUrl / reset + `uni.storage` persistence)
- [ ] `AuthProvider` interface + `EmailPasswordAuthProvider` impl
- [ ] API clients (auth / users / keys / conversations / messages)
- [ ] `StreamingResponse` abstraction (H5 `EventSource` branch + App `fetch + ReadableStream` branch)
- [ ] Error code → user-facing message mapping
- [ ] vitest unit tests (API / Streaming / BackendConfig / AuthProvider all covered)
- [ ] `pnpm test` all green + `pnpm build` artifact importable by App and web

### Phase 2: App repo (`myAi-app/` new repo)
- [ ] uni-app x project init (CLI template) + uni-ui intro
- [ ] `pages/index/` first-launch logic (read URL → route decision)
- [ ] `pages/config-backend/` config page (input + validate + save)
- [ ] `pages/login/` login page (email/password)
- [ ] `pages/conversations/` conversation list
- [ ] `pages/conversation/:id` conversation detail + streaming message bubble
- [ ] `pages/keys/` key management (add/edit/delete / enable/disable / set-default)
- [ ] `pages/settings/` settings page (with URL config + logout)
- [ ] Cross-end build verification (H5 / Android / iOS / HarmonyOS each produces runnable artifact)

### Phase 3: Existing web-side SDK integration (optional, non-blocking)
- [ ] `myAi/frontend/src/App.vue` switches to `import { ... } from '@myai/sdk'`
- [ ] `frontend/src/lib/{sse,markdown}.js` removed (SDK covers)
- [ ] `mvn -DskipTests package` + `npm run build` verification

### Phase 4: Doc wrap-up
- [ ] CLAUDE.md §2 adds "App-side" project facts
- [ ] CLAUDE.md §4 adds "App-side" key architectural conventions
- [ ] CLAUDE.md §6 clarifies SSRF misreading (`UserApiKey.baseUrl` vs `App.backendUrl`)
- [ ] `.claude/api.md` §6 marks SDK call contract (if supplements)
- [ ] `.claude/REQUIREMENTS.md` 1.10 adds App-side requirements section

### Verification
- [ ] `pnpm --filter @myai/sdk test` all green
- [ ] `pnpm --filter @myai/sdk build` artifact importable by App / web
- [ ] `myAi-app` H5 artifact runnable via local nginx, core flow passes
- [ ] `myAi-app` Android `.apk` real-device / emulator runs core flow
- [ ] `myAi-app` iOS `.ipa` simulator runs core flow
- [ ] `myAi-app` HarmonyOS `.app` simulator runs core flow
- [ ] `myAi` web-side `mvn -DskipTests package` passes