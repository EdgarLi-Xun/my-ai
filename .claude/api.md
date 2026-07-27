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
| `4040` | 资源不存在 | `BizException.notFound`、`NoResourceFoundException`（接口路径不存在也走这里） |
| `4090` | 业务冲突 | 用户没有可用默认 Key（`/api/chat` 调用前）；邮箱已被注册 |
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
- 成功返回 `AuthResponse`：`{ userId, name, email, token }`。

### POST `/api/auth/login`

```json
{ "email": "alice@example.com", "password": "123456" }
```

- 邮箱或密码错误 → 4010 邮箱或密码错误（不做区分）。
- 成功返回 `AuthResponse`。

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

请求体（`UserApiKeyRequest`）：

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

兼容旧调用。请求体（`ChatRequest`）：

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

成功返回 `ChatResponse` + `Deprecation: true` 响应头：

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

## 5. 对话与消息 `/api/conversations` `/api/messages`（规划，未实现 — 2026-07-27）

> 设计定稿见 ADR `docs/adr/0003-conversations-and-messages.md`。本节是设计快照，**代码中尚无任何端点**。

### 5.1 对话（Conversation）

| 方法 | 路径 | 作用 | 备注 |
| --- | --- | --- | --- |
| POST | `/api/conversations` | 创建空 conversation | title = "新对话 N"（N = 当前用户未删数 + 1） |
| GET | `/api/conversations?include_deleted=false` | 列当前用户的 conversations | 侧栏用，按 `updated_at DESC` |
| PATCH | `/api/conversations/{id}` | 改 title | 设 `title_manually_set = TRUE` |
| DELETE | `/api/conversations/{id}` | 软删 | `deleted_at = NOW()` |
| POST | `/api/conversations/{id}/restore` | 恢复 | `deleted_at = NULL` |
| DELETE | `/api/conversations/{id}/permanent` | 硬删 | CASCADE message |

### 5.2 消息（Message）

| 方法 | 路径 | 作用 | 备注 |
| --- | --- | --- | --- |
| GET | `/api/conversations/{id}/messages?include_orphaned=false` | 列当前对话的未作废消息 | 按 `created_at` |
| POST | `/api/conversations/{id}/messages` | 发新消息 | **流式 SSE 返回** AI 回复 |
| PATCH | `/api/messages/{id}` | 改 USER 消息内容 | 把该消息之后所有消息标 `is_orphaned = TRUE` |
| POST | `/api/messages/{id}/regenerate` | 重新生成 ASSISTANT 消息 | 基于历史重新调 AI |

### 5.3 错误码新增

| code | 含义 |
| --- | --- |
| 4030 | 默认 Key 不可用（NULL / disabled / 配置无效），UI 引导到 Key 管理 |
| 4031 | 对话不存在 / 已删 |
| 4032 | 消息不存在 / 不属于当前用户 |
| 4033 | 编辑消息时该消息不是 USER 角色 |
| 4034 | 重新生成时该消息不是 ASSISTANT 角色 |

### 5.4 行为约束

- **AI 上下文**：调 AI 前 fetch `WHERE conversation_id = ? AND is_orphaned = FALSE` 全部消息拼成 prompt；不同对话互不干扰。
- **不绑 Key**：`conversation` 表不存 `key_id`，每次调 AI 用 `User.default_key_id`（"假连贯"已知接受的代价）。
- **SYSTEM 角色**：`message.role` 约束 `'USER' | 'ASSISTANT' | 'SYSTEM'`；v1 后端不主动注入 SYSTEM 消息，列是给将来用的。
- **流式 + 续传**：`POST /api/conversations/{id}/messages` 返回 `text/event-stream`；用户点"停止" → `AbortController` 关闭流 → 已生成 token 全部丢弃。续传实现细节留到实施期。
- **多 tab 同步**：浏览器 `BroadcastChannel('my-ai-conversations')` 广播事件，纯客户端跨 tab 通信。
- **软删清理**：Spring `@Scheduled(cron = "0 3 * * * *")` 每天扫 `deleted_at < NOW() - 30 days` 的对话 hard delete；retention 走 `@ConfigurationProperties: my-ai.trash.retention-days`，默认 30。
- **Markdown 渲染**：前端 `marked` + `highlight.js` + `KaTeX` + `DOMPurify`；`message.content` 存纯文本 Markdown 源，后端不做 HTML 转换。
