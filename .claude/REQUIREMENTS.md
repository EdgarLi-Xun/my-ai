# 需求

> 仓库没有形式化的需求文档（requirements / spec 文件）。本文件从 `README.md` 和实际源码反推需求，仅作为后续会话参考。每条需求都标注证据文件 / 代码位置，可疑或推断项列入文末「推测 / 已知缺口」。

## 1. 功能需求

### 1.0 用户认证（2026-07-23 新增）
- 用户通过邮箱 + 密码注册/登录。
  - 证据：`POST /api/auth/register`、`POST /api/auth/login`（`AuthController`）。
- 密码以 BCrypt 散列存储，不对外返回。
  - 证据：`User.passwordHash` 字段加 `@JsonIgnore` / `@ToString.Exclude`；`AuthService` 使用 `PasswordEncoder`。
- JWT 通过 `Authorization: Bearer` 传递，7 天有效。
  - 证据：`JwtService`（`my-ai.jwt.expiration: 604800`）、`JwtAuthenticationFilter`。
- 未登录 → 4010；已登录但操作他人资源 → 4030。
  - 证据：`BizException.UNAUTHORIZED` / `FORBIDDEN`、`GlobalExceptionHandler`、`RestAuthenticationEntryPoint` / `RestAccessDeniedHandler`。
- 仅 `/api/auth/*` 和 `GET /api/providers` 公开，其余 `/api/*` 全部需登录。
  - 证据：`SecurityConfig.filterChain`。
- 来源：2026-07-23 用户需求 + 代码。

### 1.0.1 微信扫码登录（已设计，未实现 — 2026-07-24）
- 设计定稿见 ADR `docs/adr/0002-wechat-scan-login.md`：扫码登录与密码登录并存；未绑定微信首次扫码自动注册（`email` / `password_hash` 为 NULL）；`user` 表加 `wechat_open_id`（唯一）/ `wechat_union_id`；已登录用户可在设置页扫码绑定。
- 落地前提：微信开放平台组织资质认证（个人不可办，300 元/年）+ ICP 备案域名；穿透调试域名无法备案，实现前需复核。
- 来源：2026-07-24 `/grill-me` 会话决策；**代码中尚无任何实现**，证据文件尚不存在。

### 1.1 多用户管理
- 用户可创建、查询、删除。
  - 证据：`UserController` 提供 `GET /api/users`、`GET /api/users/{id}`、`POST /api/users`、`DELETE /api/users/{id}`。
- 用户级联删除：删除用户同时删除其全部 Key。
  - 证据：`schema.sql` 上 `user_api_key.user_id` 是 `ON DELETE CASCADE`；`UserController.delete` 不需要显式删子表。
- 来源：README 功能清单（"多用户管理"） + 代码。

### 1.2 单用户多条 Key 配置
- 单条 Key 保存：`name, provider, api_key, base_url, model_name, enabled`。
  - 证据：`UserApiKey` 实体字段、`UserApiKeyRequest` 请求体字段。
- Key 可被创建、查询、修改、删除、设为默认。
  - 证据：`UserApiKeyController` 6 个端点。
- 编辑时 `apiKey` 留空 = 保留原值。
  - 证据：`UserApiKeyService.mergeAndValidate`：`creating || requestedKey != null` 才覆盖。
- 来源：README "单用户多 Key" + 代码。

### 1.3 每用户一个默认 Key
- 新建 Key 时若用户尚无默认项且该 Key 启用，自动设为默认。
  - 证据：`UserApiKeyService.create` 中 `if (user.getDefaultKeyId() == null && Boolean.TRUE.equals(key.getEnabled()))`。
- 默认 Key 被禁用 / 删除时，`default_key_id` 置 `null`，**不**自动切换。
  - 证据：`UserApiKeyService.update` / `delete` / `setDefault` 的相关分支；`schema.sql` 上 `user.default_key_id` 是 `ON DELETE SET NULL`。
- 设为默认前必须 `enabled=true` 且配置完整（启用且 `requiresKey=true` 的 provider 必须有非空 `apiKey`，`baseUrl` 合法 http(s)）。
  - 证据：`UserApiKeyService.setDefault` 调用 `validateConfiguration`；`validateUrl`。
- 来源：README "默认 Key" 规则段 + 代码。

### 1.4 聊天走用户默认 Key
- 聊天接口不暴露 `provider` / `keyId` / API Key；服务端按 `userId` 取默认 Key 调用。
  - 证据：`/api/chat` 的 `ChatController` 仅 `request.userId()` + `request.messages()`；`ChatService.chat` 使用 `keyService.getDefaultForChat(userId)`。
- 默认 Key 不存在或被禁用 → 4090 用户没有可用的默认 Key。
  - 证据：`UserApiKeyService.getDefaultForChat`。
- 用户不存在 → 4040；请求体 / 消息不合法 → 4000。
  - 证据：`UserApiKeyService.requireUser` + `GlobalExceptionHandler`。
- 来源：README "聊天" 段 + 代码。

### 1.5 多厂家 / 多协议
- 同时支持 OpenAI 兼容协议（OpenAI / DeepSeek / Moonshot / 智谱 / MiniMax 等）和 Ollama。
  - 证据：`application.yml::my-ai.providers` 注册 6 厂家；`ChatClientFactory` 按 `ProviderProtocol` 分流。
- 新增厂家无需修改 Java 代码，只改 yml。
  - 证据：`ProviderCatalog` 用 `@ConfigurationProperties` 绑定。
- 来源：README "技术栈" + 代码。

### 1.6 动态客户端
- 修改默认 Key 后下一次聊天立即生效（不必重启）。
  - 证据：`ChatClientFactory` 每次调用都新建 `OpenAiChatModel` / `OllamaChatModel`（不缓存）。
- 来源：README "OpenAI 与 Ollama 动态 ChatClient"。

### 1.7 Key 脱敏
- 任何对外返回都不包含明文 `apiKey`，只暴露 `maskedApiKey` / `hasApiKey`。
  - 证据：`UserApiKeyResponse` 字段 + `UserApiKeyService.mask()`。
- 长度 ≤ 4 时只返回 `****`。
  - 证据：`UserApiKeyService.mask`。
- 来源：README "Key 响应脱敏" + 代码。

## 2. 非功能需求 / 约束

### 2.1 数据持久化
- 嵌入式 H2 文件模式，路径 `./data/myai.mv.db`。
  - 证据：`application.yml::spring.datasource.jdbc-url`。
- `schema.sql` 每次启动以幂等 DDL 执行，向后兼容旧四列 `user` 表。
  - 证据：`schema.sql` 全部 `IF NOT EXISTS`、`ADD COLUMN IF NOT EXISTS`、`ADD CONSTRAINT IF NOT EXISTS`；`spring.sql.init.mode=always`。
- 来源：README "数据模型 / 快速开始" + 代码。

### 2.2 跨版本兼容
- 旧库自动补 `default_key_id` 列与外键。
  - 证据：`schema.sql::ALTER TABLE user ADD COLUMN IF NOT EXISTS default_key_id BIGINT;` + `ADD CONSTRAINT IF NOT EXISTS fk_user_default_key ...`。
- 来源：README "H2 文件数据库，旧四列 user 表启动时自动补充新结构"。

### 2.3 部署形态
- 单实例本地应用，通过 Spring Boot 内嵌容器启动。
  - 证据：`MyAiApplication.main`；无独立 servlet 容器配置。
- 前端构建产物由后端 `src/main/resources/static/` 直接托管。
  - 证据：`README` "构建前端" 段；`.gitignore` 排除 `static/assets/**` 与 `static/index.html`。

### 2.4 错误响应格式
- 所有接口（成功 / 失败）HTTP 200，业务码 `0` 表示成功；4xxx 表示业务错误。
  - 证据：`Result<T>`（`code: int, message: String, data: T`），`GlobalExceptionHandler` 仅返回 `Result.failure`。
- 来源：代码（README 没有显式描述，但前端默认按此约定解析）。

## 3. 推测 / 已知缺口

| 项 | 类型 | 说明 |
| --- | --- | --- |
| 认证 / 授权 | 显式无 | README 明示 "没有认证与授权"；当前所有 `/api/*` 端点均无身份校验 |
| Key 加密 | 显式无 | README 明示 "API Key 明文保存在本机 H2"；未看到任何加密层 |
| 多租户隔离 | 显式无 | README 明示 "未实现 ... 生产级多租户隔离" |
| Key 轮换 / 配额 / 审计 | 显式无 | README 明示缺失 |
| 模型客户端缓存 | 推测无 | 代码确认每次请求新建；设计上无任何缓存层 |
| 流式输出 / SSE | 推测无 | `/api/chat` 调用 `.call().content()` 同步返回；没有任何 streaming 配置 |
| 国际化 | 推测无 | 前端仅观察到中文文案，未发现 i18n 资源文件 |
| 单元 / 集成测试 | 缺口 | `src/test/java/` 为空，`mvn test` 无用例 |
| 端到端 / UI 测试 | 缺口 | `frontend/` 也未发现测试文件 |
| 限流 / CORS 配置 | 缺口 | 未发现显式限流或 CORS Bean；本地访问默认同源 |
| 正式 API 文档 / OpenAPI | 缺口 | 当前仅有本仓库 `.claude/api.md` 与 README，未引入 springdoc / swagger |
| README 与代码的偏差 | 已知 | ORM 实际为 MyBatis-Flex；端口实际为 8031；兼容类实际为 `FlexConfig`——以代码为准 |

## 4. 变更追踪

- `2026-07-23`：初次从 README + 源码反推成文（与项目级 `CLAUDE.md` 同步生成）。
- `2026-07-24`：新增 1.0.1「微信扫码登录（已设计，未实现）」，对应 ADR 0002。
