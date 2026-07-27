# 可观测性与日志（Observability and Logging）

MyAi 引入四层日志结构（2026-07-27 决策落定）：AI 调用日志（每次调 OpenAI/Ollama/Anthropic 留痕，含 tokens 与 latency）、HTTP 访问日志（每个 `/api/**` 请求留痕到 JSON Lines 文件）、业务审计日志（用户增删改 Key / 对话 / 消息的痕迹，30 天软删窗口后硬删）、结构化系统日志（logback JSON 输出 + MDC 跨请求上下文）。这是 MyAi 从「不可见」走向「可调试 / 可审计」的关键架构变更，落地后**部分可逆**（logback 配置可调、新表可 DROP 但有数据迁移成本）。

> English version: `0004-observability.en.md`.

## 上下文

MyAi 此前只有零散 `log.info/warn/error` 调用（`MessageService` / `ConversationCleanupTask`），CLAUDE.md §6 标注「未实现加密、审计、轮换、配额、生产级多租户隔离」。ADR 0003 落地流式 AI 后，**完全没有调用记录**——用户报「AI 为什么这么回」只能猜。ADR 0004 是补这块缺口，且把零散系统日志升级为结构化输出。

## 核心决策

### 1. 日志范围：四层一起做（D = AI 调用 + HTTP 访问 + 业务审计 + 系统运行）
- 不分阶段、不分轮次。
- 一次性落地 ~13 个新文件 + ~10 个改动，估时 5-7 天。

### 2. 存储后端
- **AI 调用日志** + **业务审计日志** → 同一 H2 文件 `./data/myai.mv.db`，新增 `ai_call_log` / `audit_log` 两表（同事务原子写）。
- **HTTP 访问日志** → JSON Lines 文件 `./logs/access.jsonl`（高频写不卡业务，logback 自带 `RollingFileAppender`）。
- **系统日志** → stdout（dev JSON pretty-print 或 prod 单行 JSON） + `./logs/app.jsonl`。

### 3. AI 调用日志
- 新表 `ai_call_log(id, user_id, conversation_id, message_id, provider, model, status, latency_ms, input_tokens, output_tokens, error_message, created_at)`。
- `MessageService.streamReply` / `regenerate` 改用 `stream().chatResponse()` 而非 `stream().content()`，`ChatResponse.getMetadata().getUsage()` 取 input/output tokens。
- 落库时机：onComplete（成功）或 onError（失败）—— 同一事务。

### 4. 业务审计日志
- 新表 `audit_log(id, user_id NULL, action, target_type, target_id, ip_address NULL, user_agent NULL, created_at, deleted_at NULL)`。
- `user_id` 允许 NULL（系统后台动作留痕）。
- **软删**：`deleted_at` 列；默认查询 `WHERE deleted_at IS NULL`；30 天后由 `LogCleanupTask` 物理删。

### 5. HTTP 访问日志
- 新建 `TraceIdFilter implements Filter`，`FilterRegistrationBean` 注册，`order = SecurityProperties.DEFAULT_FILTER_ORDER - 100`（跑在 Spring Security 之前）。
- 路径白名单：只写 `/api/**`（其他静态资源、H2 console、favicon 不记）。
- 每个请求记：method / path / query_string / status / latency_ms / user_id（缺失记 `anonymous`）/ ip / user_agent。
- 同时承担 **MDC 注入**：trace_id / user_id / conversation_id / request_method / request_path / client_ip。
- 响应头回写 `X-Trace-Id`（接受上游头或生成 UUID v4）。

### 6. 系统日志格式
- logback 重写：`src/main/resources/logback-spring.xml`。
- 全 JSON 输出（`<springProfile>` 切 dev pretty vs prod single-line）。
- 内置 encoder：`ch.qos.logback.classic.encoder.JsonEncoder`（Logback 1.5+ 内置，零额外依赖）。
- 两个文件 appender：`./logs/app.jsonl` 与 `./logs/access.jsonl`，按天切 + `maxHistory` 30。
- `my-ai.logs.retention-days` 配置同时控制 DB 清理与 logback `maxHistory`。

### 7. 业务审计触发：AOP `@Auditable` + `@Around`
- 自定义 `@Auditable(action="KEY_CREATED", targetType="UserApiKey")` 注解。
- `AuditAspect @Around` 拦截：先调原方法，从**返回值类型**取 `targetId`（`UserApiKey` / `Conversation` / `Message` → `.getId()`），void 方法 fallback 到**最后一个 `Long` 类型参数**。
- 与 `@Transactional` 同事务回滚。

### 8. admin bootstrap：纯 env var
- `my-ai.admin.emails: []`（默认空）；多个邮箱用逗号分隔（env var `MYAI_ADMIN_EMAILS="a@x.com,b@x.com"`）。
- `AuthService.register` 按邮箱命中列表设 role=ADMIN；否则 USER。
- **无 fallback**：env var 没配且 user 表非空，注册的新用户全是 USER，没人能调 `/api/logs/**`。

### 9. 查询 API：admin-only 4 端点
- `GET /api/logs/ai-calls?from=&to=&page=&size=&sort=created_at,desc`（owner 自动过滤）
- `GET /api/logs/ai-calls/{id}`
- `GET /api/logs/audit`（同上参数）
- `GET /api/logs/audit/{id}`
- SecurityConfig 加 `/api/logs/**` hasRole("ADMIN")；用户表加 `role VARCHAR(20) NOT NULL DEFAULT 'USER'`。
- `AuthPrincipal` / `JwtService` 加 role 字段。

### 10. 保留期：可配置，默认 30
- `my-ai.logs.retention-days: 30`（env var `MYAI_LOG_RETENTION_DAYS`）。
- `LogCleanupTask @Scheduled(cron="0 4 * * * *")`：清 `ai_call_log.created_at < NOW() - retentionDays` + `audit_log.deleted_at IS NULL AND created_at < ?`。
- logback `maxHistory=${retentionDays}`。

### 11. TraceId 协议
- 上游有 `X-Trace-Id` 头则沿用；没有则生成 UUID v4。
- 全部响应（含 SSE）回写 `X-Trace-Id` 响应头。
- SSE token 事件数据不带 trace_id（避免冗余）。

## 考虑过的选项

### 1.1 日志范围
- **A. 只做系统运行日志** — 拒绝。零业务代码改动但不解决核心可见性。
- **B. 系统 + AI 调用日志** — 不选。HTTP 访问 / 审计留 follow-up，治标不治本。
- **C. B + HTTP 访问日志** — 不选。审计缺位。
- **D. 全部 4 块** — **已选**。一次性投资，分次反而是隐性技术债。

### 2.1 AI + 审计日志存储
- **A. 同一 H2 文件，新表** — **已选**。简单；同事务；H2 console 可查。
- **B. 独立 H2 文件 `./data/myailog.mv.db`** — 拒绝。多数据源复杂；不与业务同事务。
- **C. JSON Lines 文件** — 拒绝。AI 调用需按 userId / conversationId 频繁查询，DB 索引远胜 grep。
- **D. ES / Loki / ClickHouse** — 拒绝。远超本地 demo 范围（CLAUDE.md §6 定位）。

### 3.1 AI token 计数
- **A. 不记 token** — 拒绝。失去核心可观测性（cost / 输入长度）。
- **B. `stream().chatResponse()` + `metadata.usage`** — **已选**。Spring AI 2.x 原生支持；缺失允许 NULL。
- **C. 异步估算** — 拒绝。Spring AI 无现成 API；估算不准。

### 4.1 audit_log 删除策略
- **A. 硬删** — 不选。误删即丢，本地 demo 也允许 30 天反悔。
- **B. 软删（`deleted_at`）+ retentionDays 后硬删** — **已选**。30 天软窗口 = 误操作可手动改 query；之后物理清。
- **C. 永不清** — 拒绝。H2 永远涨。

### 5.1 HTTP 过滤器
- **A. Spring 内置 `AbstractRequestLoggingFilter`** — 拒绝。不支持 SSE 完成时记 status / duration。
- **B. 自定义 `OncePerRequestFilter`** — 不选。等价 C 但晚于 Security 启动（看不到 401/403）。
- **C. Servlet `Filter` + `FilterRegistrationBean`（跑在 Security 之前）** — **已选**。看得到 401/403；user_id 缺失 fallback `anonymous`。

### 6.1 系统日志格式
- **A. 全 JSON** — **已选**。机器可解析；日志聚合栈友好。
- **B. 全 plain text + MDC** — 拒绝。聚合栈难消费。
- **C. profile 区分** — 拒绝。本项目只一个 profile，多维护成本 > 收益。

### 7.1 MDC 字段
- **A. trace_id + user_id** — 拒绝。聊胜于无。
- **B. trace_id / span_id / user_id** — 拒绝。无 OTel 收集器，span 没人看。
- **C. 全套：trace_id / user_id / conversation_id / request_method / request_path / client_ip** — **已选**。任何日志都能直接看到上下文。

### 8.1 审计触发机制
- **A. 显式 `auditLog.record(...)` 调用** — 拒绝。写代码易漏；约 6-8 处。
- **B. Spring AOP `@Auditable` + `@Around`** — **已选**。声明式；不侵入方法体；与 `@Transactional` 同事务。
- **C. `ApplicationEventPublisher` + `@EventListener`** — 拒绝。异步 = 可能丢；事务不对齐。
- **D. Hibernate Envers** — 拒绝。只覆盖实体增删改，不覆盖业务动作。

### 9.1 AOP targetId 提取
- **A. SpEL 表达式** — 拒绝。写起来啰嗦；编译期无校验。
- **B. 约定返回类型 + 最后 Long 参数 fallback** — **已选**。极简注解体；现有 service 方法都已返回实体。
- **C. 显式 `targetId` Long 参数** — 拒绝。重构易破坏；service 方法签名稳定性低。

### 10.1 admin bootstrap
- **A. 第一个注册用户自动是 admin** — 拒绝。多用户 race；谁先注册谁管。
- **B. env var `MYAI_ADMIN_EMAILS` 匹配** — **已选**。幂等；多管理员可配；与现有 `MYAI_JWT_SECRET` 风格一致。
- **C. schema seed admin/admin123** — 拒绝。弱密码反模式。

### 11.1 查询 API 暴露
- **A. 仅 H2 console** — 拒绝。没控制台时查不到。
- **B. owner-only（普通用户看自己）** — 不选。没 admin 视角。
- **C. admin-only + role 系统** — **已选**。完整 RBAC；与 ADR 0004 的合规意图一致。

### 12.1 保留期
- **A. 永不清** — 拒绝。H2 无限涨。
- **B. 固定 30 天** — 拒绝。写死 30 不灵活。
- **C. 可配置 `my-ai.logs.retention-days`，默认 30** — **已选**。与 trash 同模式；env var 可覆盖。

## 后果

### 数据模型变更
- `user` 表加 `role VARCHAR(20) NOT NULL DEFAULT 'USER'`（幂等 `ALTER TABLE ADD COLUMN IF NOT EXISTS`）。
- 新表 `ai_call_log`（12 列 + 1 索引 + FK）。
- 新表 `audit_log`（9 列 + 1 索引 + 1 软删列）。
- `schema.sql` 全部沿用现有 `CREATE TABLE IF NOT EXISTS` + `ALTER TABLE ADD COLUMN IF NOT EXISTS` 幂等模式。

### API 变更（非破坏性）
- 新增 4 个 admin-only 端点（`/api/logs/**`）。
- `/api/auth/register` 响应不变；用户 role 仅 JWT claims 携带 + admin emails 命中时设。
- 现有 11 个对话 / 消息端点不变。
- `MessageService.streamReply` 内部改用 `stream().chatResponse()`——**SSE 事件协议不变**（`event: token / done / error` 数据格式仍 `Map.of("text", ...)`）。

### 依赖新增
- 后端：`spring-boot-starter-aop`（CLAUDE.md 未列；项目引 AOP 需要；查 pom 后决定显式加或 spring-boot-starter-web 已传递）
- 前端：无变化。
- logback `JsonEncoder` 是 Logback 1.5+ 内置；spring-boot-starter-logging 已传递。

### 错误码新增
- 无（4010 已登录态；4030 跨用户；新接口 admin-only 失败走现有 4030 拒绝访问逻辑）。

### 业务码变更
- 现有 5 个业务码不变。新增 `Role` 字段语义不在业务码层（用 Spring Security 的 `AccessDeniedException` 处理）。

### 安全边界
- 引入 RBAC：`USER` / `ADMIN` 两个角色；admin 通过 env var 列表授予，不可注册时自封。
- admin 操作（查 `/api/logs/**`）记到 `audit_log`（自审计）。
- `user_id` MDC 缺失时记 `anonymous`（401 / 403 路径）。
- `audit_log.user_id` NULL 表示系统后台动作。

### 性能 / 资源
- H2 文件增长：~300 字节 / AI 调用 + ~200 字节 / 审计 + ~150 字节 / HTTP 访问（月 10 MB 量级，retentionDays 后清）。
- logback 文件 appender 单线程追加写，无锁竞争。
- AOP aspect 在 `@Transactional` 边界内，零额外 DB 连接。
- `TraceIdFilter` 跑在 Security 之前但不在 Spring DispatcherServlet 之前，所有请求都过一遍；MDC 注入 O(1) 哈希操作。

### UI / 行为变更
- 无前端变更（admin 端点 v1 不做管理 UI，仅 REST；查日志用 H2 console 或 curl）。
- Spring Boot Actuator **不引入**（CLAUDE.md §2 未列；按需可加）。

### 已知风险
1. **Ollama 不一定返回 usage**——`input_tokens` / `output_tokens` 允许 NULL；UI 不展示但 DB 留痕。
2. **AOP `@Auditable` 与 `@Transactional` 同事务**——若 AOP 在 `@Transactional` 外触发，audit log 不回滚——必须确认标注顺序（aspect 内 `proceed()` 应在事务里）。
3. **`ConversationCleanupTask` 删软删对话不审计**——系统后台动作留 audit 但 `user_id=NULL`；否则用户视角下"我删的对话被删了"是预期，不算审计事件。
4. **logback `JsonEncoder` 与 pattern layout 互斥**——logback 1.5+ 要求 encoder 单一职责；不能同时用 `%msg` 和 JsonEncoder。
5. **trace_id 在 SSE 事件流里没有"帧级"标识**——只有响应头一次；客户端读时取一次即可。
6. **`/api/logs/**` 没有分页保护**——`page` + `size` 上限 `size <= 200` 强制（防爆）。

## 状态

✅ **决策落定**（2026-07-27）。实施期开新会话写代码，按本 ADR 落地。预计改动 ~13 新文件 + ~10 修改，估时 5-7 天。

## 关联

- [[0003-conversations-and-messages]] — 上一个 ADR，本 ADR 的 AI 调用日志要嵌入 `MessageService.streamReply` / `regenerate`。
- [[0001-defer-wechat-integration]] / [[0002-wechat-scan-login]] — 正交，不影响。
- CLAUDE.md §4 关键架构约定将在实施期更新，加入"日志 / 审计"约束。
- CLAUDE.md §6 安全边界：引入 RBAC 后"未实现生产级多租户隔离"部分缓解；audit_log 填补"审计"缺口。