# API 参考

> 来源：`src/main/java/cn/edgarli/web/*Controller.java` 与 `common/Result.java`。
> 与 README.md / 「数据模型」叙述不一致时，以本文档（直接来自源码）为准。

## 0. 通用约定

### 响应外壳

所有接口（成功 / 失败）统一返回 `Result<T>`，HTTP 状态码恒为 200。错误通过业务码表达：

```json
{ "code": 0, "message": "success", "data": <T> }
```

### 错误码

| code | 含义 | 触发场景 |
| --- | --- | --- |
| `0` | 成功 | 全部 2xx 业务 |
| `4000` | 请求参数错误 | `BizException.badRequest`、`HttpMessageNotReadableException`、`MethodArgumentTypeMismatchException`、`MissingServletRequestParameterException`、`HttpRequestMethodNotSupportedException` |
| `4010` | 未登录 | 未带 JWT 或 JWT 过期/无效；由 `RestAuthenticationEntryPoint` 或 `AuthenticationException` 返回 |
| `4030` | 无权访问 | 已登录但试图操作其他用户的资源；由 `RestAccessDeniedHandler` 或 `BizException.forbidden` 返回 |
| `4035` | 默认 Key 不可用 | ADR 0003；由 `BizException.defaultKeyUnavailable` 返回。详见 §5.3 |
| `4040` | 资源不存在 | `BizException.notFound`、`NoResourceFoundException`（接口路径不存在也走这里） |
| `4090` | 业务冲突 | 邮箱已被注册等；**注意**：原 `/api/chat` 旧实现曾用 4090 表达"无默认 Key"，已迁移到 4035（见 §4） |
| `5020` | 上游错误 | 预留（`BizException.upstream`，当前未触发） |
| `5000` | 服务异常 | 其他未捕获异常（`GlobalExceptionHandler` 兜底） |

### 认证

除 `/api/auth/*`、`GET /api/providers`、静态资源和 `/h2-console/**` 外，所有接口必须在请求头携带 JWT：

```
Authorization: Bearer <token>
```

- 注册 / 登录成功后获得 `token`（HS256，默认 7 天有效）。
- 4010 返回不等于前端被动断开——前端在 `api()` 包装函数里检测到 `code === 4010` 后自动清空 token 并弹出登录卡片。

> 规划（未实现）：微信扫码登录的后端回调接口（GET，302 回前端、token 走 URL fragment）见 ADR `docs/adr/0002-wechat-scan-login.md`；实现后需在本节补充端点并把回调路径加入 `SecurityConfig` 白名单。
>
> 规划（未实现）：`POST /api/chat` 会被替换为 11 个新端点（`/api/conversations/*`、`/api/messages/*`），实现后本节 §4 整段会删除并改写到 §5；详见 ADR `docs/adr/0003-conversations-and-messages.md`。

### Key 脱敏

`/api/users/{userId}/keys*` 系列响应里 `apiKey` 不会出现，只暴露两个字段：

- `maskedApiKey`：`null` 或 `****`（≤4 字符时）或 `****` + 原值末尾 4 字符。
- `hasApiKey`：`true` / `false`。

更新时 `apiKey` 留空 = 保留原值（见 `UserApiKeyService.mergeAndValidate`）。

---

## 0.5 认证 `/api/auth`

### POST `/api/auth/register`

注册新用户。

```json
{ "name": "Alice", "email": "alice@example.com", "password": "123456" }
```

- `name` / `email` / `password` 均必填；`password` 至少 6 位 → 4000。
- `email` 已存在 → 4090 该邮箱已被注册。
- 密码以 BCrypt 散列存储，不会明文落库。
- 成功返回 `AuthVo`：`{ userId, name, email, token }`。

### POST `/api/auth/login`

```json
{ "email": "alice@example.com", "password": "123456" }
```

- 邮箱或密码错误 → 4010 邮箱或密码错误（不做区分）。
- 成功返回 `AuthVo`。

### GET `/api/auth/me`

需登录。返回当前用户的完整 `User` 信息（不含 `passwordHash`）。

---

## 1. 用户 `/api/users`（需登录）

### GET `/api/users`

列出当前用户（已登录后仅返回自己，不再是全量列表）。→ 需登录。

### GET `/api/users/{id}`

获取单个用户。只能查自己，`id` 不等于登录 `userId` → 4030。→ 需登录。

### POST `/api/users`

创建用户（保留接口；推荐用 `/api/auth/register`）。→ 需登录。

```json
{ "name": "Alice", "email": "alice@example.com" }
```

- `name` 必填且 trim 后非空，否则 4000。
- `email` 可空，trim 后非空才写入。
- `defaultKeyId` 创建时为 `null`。

### DELETE `/api/users/{id}`

删除用户（只能删自己），并级联删除其全部 Key。→ 需登录，id 不匹配 → 4030。

---

## 2. 用户 Key `/api/users/{userId}/keys`

> 由 `UserApiKeyController` 暴露。`userId` 不存在时统一返回 4040；`keyId` 与 `userId` 不匹配时也返回 4040（`UserApiKeyService.requireKey`）。

### GET `/api/users/{userId}/keys`

列出该用户的全部 Key（按 id 升序）。

### GET `/api/users/{userId}/keys/{keyId}`

返回单条 Key（含脱敏 `maskedApiKey`、`hasApiKey`、`defaultKey` 标志）。

### POST `/api/users/{userId}/keys`

新增 Key 配置。

请求体（`UserApiKeyDto`）：

| 字段 | 必填 | 行为 |
| --- | --- | --- |
| `name` | ✅ | 必填字符串 |
| `provider` | ✅ | 必须在 `application.yml` 的 `my-ai.providers` 中存在（大小写不敏感），否则 4000 `不支持的 provider` |
| `apiKey` | 看 provider | 当 `requiresKey=true` 且启用该 Key 时必须非空（4000） |
| `baseUrl` | ❌ | 空 → 用 provider 的 `defaultBaseUrl`；非空必须是合法 http(s) URL，否则 4000 |
| `modelName` | ❌ | 空 → 用 provider 的 `defaultModel` |
| `enabled` | ❌ | 创建时缺省视为 `true` |

副作用：当用户当前 `defaultKeyId` 为 `null` 且新 Key `enabled=true` 时，自动把 `defaultKeyId` 设为新 Key 的 id。

### PUT `/api/users/{userId}/keys/{keyId}`

更新 Key。请求体同上，但 `apiKey` 留空 = 保留原值。

副作用：如果更新的正是默认 Key 且 `enabled` 被改为 `false`，则把 `defaultKeyId` 置 `null`，但不会自动切换到其他 Key。

### DELETE `/api/users/{userId}/keys/{keyId}`

删除 Key。如果删除的是默认 Key，把 `defaultKeyId` 置 `null`。

### PUT `/api/users/{userId}/keys/{keyId}/default`

设为默认 Key。前提：

- 该 Key 存在且属于该用户；
- `enabled=true`；
- 通过 `validateConfiguration`（即所需 provider 的 `apiKey` 非空）。

不满足 → 4000。

---

## 3. 厂家池 `/api/providers`

### GET `/api/providers`

只读，公开接口（无需登录）。

每项字段（`ProviderSpec`）：

| 字段 | 含义 |
| --- | --- |
| `name` | 厂家键（小写），如 `openai`、`ollama` |
| `displayName` | 厂家展示名 |
| `protocol` | `OPENAI_COMPATIBLE` 或 `OLLAMA` |
| `defaultBaseUrl` | 用户未填时的默认 base URL |
| `defaultModel` | 用户未填时的默认模型 |
| `requiresKey` | 是否必须填写 API Key |

---

## 4. 聊天 `/api/chat`（deprecated — 2026-07-27）

> ⚠️ **已废弃**。响应头带 `Deprecation: true` + `Warning: 299`。新代码请用 §5
> 的 `POST /api/conversations/{id}/messages`（流式 SSE）。本节保留是为兼容老 curl
> 脚本与外部调用，下架时间未定。

### POST `/api/chat`

兼容旧调用。请求体（`ChatDto`）：

```json
{
  "userId": 1,
  "messages": [
    { "role": "system",    "content": "..." },
    { "role": "user",      "content": "你好" },
    { "role": "assistant", "content": "..." }
  ]
}
```

- `userId` 必填。后端不接受 `provider` / `keyId` / API Key。
- `messages` 必须非空且每条 `content` 非空 → 否则 4000。
- `role` 仅识别 `system` / `assistant` / 其他（默认 `user`）。
- 默认 Key 不可用 → **4035**（已从原 4090 迁移）。
- 调用方跨用户 → 4030（沿用）。

成功返回 `ChatVo` + `Deprecation: true` 响应头：

```json
{ "code": 0, "message": "success", "data": { "reply": "..." } }
```

服务端流程（`ChatController` → `ChatService.chat`）：与历史实现一致；**不**写 message 表，
**不**创建 conversation，是真正的无状态 alias。

---

## 5. 对话与消息 `/api/conversations` `/api/messages`（已实现 — 2026-07-27）

> 设计定稿见 ADR `docs/adr/0003-conversations-and-messages.md`。本节是实现版。
> SSE 端点（§5.2 的 `POST messages` 与 `POST /regenerate`）返回 `text/event-stream`，
> 不走 `Result<>` 外壳；其余端点都走 `Result<T>`。

### 5.1 对话（Conversation）

| 方法 | 路径 | 作用 | 备注 |
| --- | --- | --- | --- |
| POST | `/api/conversations` | 创建空 conversation | title = "新对话"，`title_manually_set=false` |
| GET | `/api/conversations?include_deleted=false` | 列当前用户的 conversations | 默认 `updated_at DESC` |
| GET | `/api/conversations?include_deleted=true` | 列 trash 区 | `deleted_at DESC` |
| PATCH | `/api/conversations/{id}` | 改 title | 强制设 `title_manually_set=TRUE` |
| DELETE | `/api/conversations/{id}` | 软删 | `deleted_at = NOW()` |
| POST | `/api/conversations/{id}/restore` | 恢复 | `deleted_at = NULL` |
| DELETE | `/api/conversations/{id}/permanent` | 硬删 | CASCADE message |

### 5.2 消息（Message）

| 方法 | 路径 | 作用 | 备注 |
| --- | --- | --- | --- |
| GET | `/api/conversations/{id}/messages?include_orphaned=false` | 列当前对话的未作废消息 | 按 `created_at` |
| POST | `/api/conversations/{id}/messages` | 发新消息 | **流式 SSE** 返回 AI 回复 |
| PATCH | `/api/messages/{id}` | 改 USER 消息内容 | 把该消息及之后所有消息标 `is_orphaned = TRUE`，**不**自动重跑 AI |
| POST | `/api/messages/{id}/regenerate` | 重新生成 ASSISTANT 消息 | 基于历史重新调 AI，旧回复标 orphan |

### 5.3 错误码（ADR 0003 实现版）

| code | 含义 |
| --- | --- |
| 4000 | 请求参数错误 |
| 4010 | 未登录 |
| 4030 | 跨用户操作被拒 |
| 4031 | 对话不存在 / 已删（`BizException.conversationNotFound`） |
| 4032 | 消息不存在 / 不属于当前用户（`BizException.messageNotFound`） |
| 4033 | 编辑消息时该消息不是 USER 角色（`BizException.messageNotUser`） |
| 4034 | 重新生成时该消息不是 ASSISTANT 角色（`BizException.messageNotAssistant`） |
| 4035 | 默认 Key 不可用（NULL / disabled / 配置无效），ADR 0003 |
| 4040 | 资源不存在 |
| 4090 | 业务冲突 |
| 5000 | 服务异常 |
| 5020 | 上游错误（预留） |

> 与 ADR §5.3 原文差异：原计划用 4030 表示"默认 Key 不可用"，但 4030 已被 `FORBIDDEN`
> 占用（`GlobalExceptionHandler.AccessDeniedException` + `UserController.requireOwner` +
> `UserApiKeyController.requireOwner` + `ChatController` deprecated alias）。实施期改用 **4035**。

### 5.4 行为约束

- **AI 上下文**：调 AI 前 fetch `WHERE conversation_id = ? AND is_orphaned = FALSE` 全部消息拼成 prompt；不同对话互不干扰。
- **不绑 Key**：`conversation` 表不存 `key_id`，每次调 AI 用 `User.default_key_id`（"假连贯"已知接受的代价）。
- **SYSTEM 角色**：`message.role` 约束 `'USER' | 'ASSISTANT' | 'SYSTEM'`；v1 后端不主动注入 SYSTEM 消息，列是给将来用的。
- **流式 + 续传**：`POST /api/conversations/{id}/messages` 返回 `text/event-stream`；用户点"停止" → `AbortController` 关闭流 → 已生成 token 全部丢弃。v1 不做续传（ADR §5.1 = C，PLAN §12 决定"实施期定"）；断网 fallback：UI 显示已落库消息 + "重新发送最后 USER"按钮。
- **多 tab 同步**：浏览器 `BroadcastChannel('my-ai-conversations')` 广播事件，纯客户端跨 tab 通信。
- **软删清理**：Spring `@Scheduled(cron = "0 3 * * * *", zone = "Asia/Shanghai")` 每天扫 `deleted_at < NOW() - retention_days` 的对话 hard delete（CASCADE message）；retention 走 `@ConfigurationProperties: my-ai.trash.retention-days`，默认 30。
- **Markdown 渲染**：前端 `markdown-it` + `markdown-it-highlightjs` + `@vscode/markdown-it-katex` + `DOMPurify`；`message.content` 存纯文本 Markdown 源，后端不做 HTML 转换。
- **PATCH edit 语义**：标 orphan，**不**自动重跑 AI；用户需主动"发送新消息"或点 ASSISTANT 的"重新生成"才让 AI 看到新内容（与 ADR §4 第二句偏离，落地补丁）。

---

## 6. 日志查询 `/api/logs`（已实现 — 2026-07-28，仅 admin）

> 设计定稿见 ADR `docs/adr/0004-observability.md`。本节是实现版。
> 所有端点都需 admin 角色（`User.role = 'ADMIN'`，由 env var `MYAI_ADMIN_EMAILS` 匹配授予）。
> 非 admin 调 → 4030；未登录 → 4010。响应走 `Result<>`。

### 6.1 端点

| 方法 | 路径 | 作用 | 备注 |
| --- | --- | --- | --- |
| GET | `/api/logs/ai-calls?from=&to=&page=&size=` | 列 AI 调用日志 | 默认 `created_at DESC`；`size` 上限 200 |
| GET | `/api/logs/ai-calls/{id}` | 单条 AI 调用日志 | 不存在 → 4040 |
| GET | `/api/logs/audit?from=&to=&page=&size=` | 列审计日志 | 默认 `WHERE deleted_at IS NULL` + `created_at DESC`；`size` 上限 200 |
| GET | `/api/logs/audit/{id}` | 单条审计日志 | 不存在 → 4040 |

### 6.2 数据来源

- `ai_call_log`：由 `MessageService.streamReply / regenerate` 在 onComplete / onError 写入。含 provider / model / status（SUCCESS / FAILURE）/ latency_ms / input_tokens / output_tokens（nullable）/ error_message / trace_id / user_id / conversation_id / message_id。
- `audit_log`：由 `AuditAspect @Around @Auditable` 在 service 方法成功后写入。action 见 `cn.edgarli.observability.Auditable.action`（如 `USER_API_KEY_CREATE` / `CONVERSATION_SOFT_DELETE`）；target_id 自动从返回值 id（先 `getId()`，再 `id()` record）提取，fallback 最后 Long 参数。

### 6.3 行为约束

- **保留期**：`my-ai.logs.retention-days` 默认 30（env var `MYAI_LOGS_RETENTION_DAYS`）；`LogCleanupTask @Scheduled(cron="0 4 * * * *", zone="Asia/Shanghai")` 每天凌晨跑。ai_call_log 直接物理删 `created_at < now-retentionDays`；audit_log 先软删 `deleted_at = now`（created_at < cutoff 且 deleted_at IS NULL），再物理删 `deleted_at < now-retentionDays`。
- **访问日志**：HTTP 访问日志写到 `./logs/access.jsonl`（独立 logger `myai.access`，additivity=false，RollingFileAppender）；由 `TraceIdFilter` 在 finally 阶段输出。响应头 `X-Trace-Id` 始终回写。
- **系统日志**：logback-spring.xml 全 JSON（Logback 1.5+ 内置 `JsonEncoder`）写到 stdout + `./logs/app.jsonl`；MDC 字段 trace_id / user_id / request_method / request_path / client_ip / conversation_id / message_id。
- **admin bootstrap**：`my-ai.admin.emails` 绑定 env var `MYAI_ADMIN_EMAILS`（逗号分隔），register / login 时按邮箱匹配设 role=ADMIN。无 fallback — 没配则系统无管理员。

---

> ADR 0003 的 §5.1-5.4（规划版本，含 4030 默认 Key 不可用、`marked` 渲染等）已过时；以本文件 §5（已实现）与 ADR 修订为准。

## 7. SDK 调用契约（ADR 0006，2026-08-14 落地）

> 跨 web + uni-app x App 共享的 TypeScript SDK 位于 `../myAi-sdk/`（独立 git 仓库）。本节列出 SDK 暴露的关键导出与对应后端端点，开发者新增功能时**先在 SDK 内补齐接口，再在两端消费**——不要在 web / App 任一端单独写 `fetch` 调用。

### 7.1 模块总览

| 模块 | 主要导出 | 对应后端范围 |
| --- | --- | --- |
| `types` | `UserVo` / `AuthVo` / `UserApiKeyVo` / `ConversationVo` / `MessageVo` / `ProviderVo` / `AiCallLogVo` / `AuditLogVo` + `Result<T>` + `Role` / `MessageRole` / `ProviderProtocol` | §1 / §2 / §3 / §5 / §6 全量 |
| `errors` | `BizCode`（12 常量）+ `SdkError` + 12 类型化子类（`UnauthorizedError` / `ForbiddenError` / `ConversationNotFoundError` / `DefaultKeyUnavailableError` 等）+ `NetworkError` + `errorFromCode` + `unwrap` / `unwrapOrNull` / `unwrapVoid` | §0 错误码表 |
| `utils` | `validateBackendUrl(url, opts)` + `sleep` + `retry` + `shortTraceId` + `toQueryString` | §0（ping 校验）+ 通用 |
| `storage` | `StorageAdapter` 接口 + `LocalStorageAdapter` / `UniStorageAdapter` / `InMemoryAdapter` + `SdkStorage` + `StorageKey`（3 个键：Token / BackendUrl / ActiveConversationId） | 客户端持久化（不在后端） |
| `auth` | `AuthProvider` 接口 + `AuthService`（login / register / logout / getCurrentUser / isAuthenticated / setOnUnauthorized + notifyUnauthorized） | §0.5 |
| `api` | `HttpClient` 接口 + `FetchHttpClient` + `ProviderApi` / `UserApiKeyApi` / `ConversationApi` / `MessageApi` / `LogsApi` | §3 / §2 / §5 / §6 |
| `streaming` | `SseParser` + `frameToEvent` + `createStreamingResponse` + `streamConversationMessage` + `streamRegenerate` + `StreamingResponse` | §5 SSE 端点（`POST /api/conversations/{id}/messages` + `POST /api/messages/{id}/regenerate`） |
| `media` / `push` | （v2 占位，仅空桶）/ (empty barrel for v2) | §0（未实现项） |

### 7.2 关键调用模式

**HTTP 客户端**：

```typescript
import {
  FetchHttpClient, AuthService, createStorage, LocalStorageAdapter,
  ProviderApi, UserApiKeyApi, ConversationApi, MessageApi, LogsApi,
  unwrap, errorFromCode,
} from '@myai/sdk'

const storage = createStorage(new LocalStorageAdapter())
const http = new FetchHttpClient({
  baseUrl: '',                          // web 同源；App 端 = 用户填的 backendUrl
  getToken: () => storage.getToken(),
  onUnauthorized: () => {               // 4010 → 清 token + 触发上层 handler
    storage.clearToken()
    storage.clearActiveConversationId()
  },
})
const auth = new AuthService({ http, storage })
const userApiKeyApi = new UserApiKeyApi(http)
// …其他 4 个 API 类同模式
```

**登录与 4010 自愈**：

```typescript
try {
  const { token, userId, name, email } = await auth.login({
    email, password,
  })
  // token 已自动写入 storage；后续 http.request 自动注入 Authorization: Bearer
} catch (e) {
  // e 是 SdkError 子类；err.code 是 BizCode 常量（如 4000 / 4010 / 4035）
  if (e.code === 4010) { /* token 无效 */ }
}
```

**SSE 流式**：

```typescript
import { streamConversationMessage, streamRegenerate } from '@myai/sdk'

// 发消息：POST /api/conversations/{conversationId}/messages
const stream = streamConversationMessage({
  baseUrl: '', conversationId: 7, content: 'hi',
  getToken: () => storage.getToken(),
  signal: abortController.signal,
})
for await (const ev of stream.events()) {
  if (ev.type === 'token') append(ev.text)
  else if (ev.type === 'done') { reload(); break }
  else if (ev.type === 'error') { showError(ev.code, ev.message); break }
}
// stream.abort() 取消；stream.done Promise resolve/reject
```

**后端 URL 校验（App 端首启 / 改 URL）**：

```typescript
import { validateBackendUrl } from '@myai/sdk'

const r = await validateBackendUrl('https://api.example.com', { timeoutMs: 5000 })
if (!r.ok) showError(r.error)
// 仅校验 http/https 协议 + GET /api/providers 返回 200；不拦截内网 IP（见 CLAUDE.md §4-25）
```

### 7.3 端点 ↔ API 类对应表

| 后端端点 | SDK API 方法 | 路径形态 |
| --- | --- | --- |
| §0.5 `POST /api/auth/register` | `auth.register(dto)` | `/api/auth/register` |
| §0.5 `POST /api/auth/login` | `auth.login(dto)` | `/api/auth/login` |
| §0.5 `GET /api/auth/me` | `auth.getCurrentUser()` | `/api/auth/me` |
| §3 `GET /api/providers` | `providerApi.list()` | `/api/providers` |
| §2 `GET/POST/PUT/DELETE /api/users/{userId}/keys[/{keyId}[/default]]` | `userApiKeyApi.list(userId)` / `.get(uid, kid)` / `.create(uid, dto)` / `.update(uid, kid, dto)` / `.delete(uid, kid)` / `.setDefault(uid, kid)` | 同上 |
| §5 `POST /api/conversations` | `conversationApi.create()` | `/api/conversations` |
| §5 `GET /api/conversations?include_deleted=…` | `conversationApi.list({ includeDeleted })` | 同上 |
| §5 `PATCH/DELETE/POST /api/conversations/{id}[/restore\|/permanent]` | `conversationApi.update/update(id, dto)` / `.softDelete(id)` / `.restore(id)` / `.hardDelete(id)` | 同上 |
| §5 `GET /api/conversations/{cid}/messages?include_orphaned=…` | `messageApi.list(cid, { includeOrphaned })` | 同上 |
| §5 `PATCH /api/messages/{id}` | `messageApi.update(id, dto)` | `/api/messages/{id}` |
| §5 `POST /api/conversations/{id}/messages`（SSE） | `streamConversationMessage({ conversationId, content, ... })` | 同上 |
| §5 `POST /api/messages/{id}/regenerate`（SSE） | `streamRegenerate({ messageId, ... })` | 同上 |
| §6 `GET /api/logs/ai-calls[/{id}]` | `logsApi.listAiCalls(opts)` / `.getAiCall(id)` | 走 `requestRaw`（无 Result 外壳） |
| §6 `GET /api/logs/audit[/{id}]` | `logsApi.listAuditLogs(opts)` / `.getAuditLog(id)` | 同上 |

### 7.4 错误处理契约

- 业务错误 → SDK 抛对应 SdkError 子类（`code` 字段为 BizCode 常量，`message` 为后端 message）
- 网络错误 → SDK 抛 `NetworkError`（`code = 0`）
- SSE 业务错误 → SDK 仍走 `BizException` 流：`for await` 拿到 `{ type: 'error', code, message }`；不要把 SSE error 当作 Promise reject 处理

### 7.5 已知缺口

- v2 占位 `media` / `push` 暂未实现（图片上传 + 推送通知，留待 v2 触发）
- 后端 `/api/app/**` 命名空间未启用（ADR 0006 Q4 推迟）；当前 SDK 消费现有 `/api/**`
- 内网 IP 黑名单未落地（CLAUDE.md §4-25）；部署到不可信用户前必须补齐
