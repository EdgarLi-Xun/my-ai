# 项目文档

> 资料来源：仓库现有 `pom.xml`、`application.yml`、`schema.sql`、Java / Vue 源码、`README.md`。无法核实处见文末「已知缺口」。本文档与代码冲突时按 `CLAUDE.md` 第 1 节处置（以代码为准，但保留差异提示）。

## 1. 项目概览

**MyAi**：本地多用户聊天应用。每个用户可以维护多条 AI 厂家配置（OpenAI 兼容协议 / Ollama / Anthropic），并指定一条默认配置；聊天接口只接收 `userId`，由后端读取数据库里的默认 Key 调用模型。

定位：本地示例工具。已加 JWT 认证 + RBAC + 审计 + 可观测性日志；未做加密、轮换、配额、生产级多租户隔离，详见第 7 节安全边界。

## 2. 技术栈

| 层 | 技术 |
| --- | --- |
| 后端 | Java 21、Spring Boot 4.0.7、Spring AI 2.0.0（`spring-ai-openai` + `spring-ai-ollama` + `spring-ai-anthropic`） |
| 数据 | **MyBatis-Flex 1.11.8**（`mybatis-flex-spring-boot4-starter`）、HikariCP 4.0.3、H2 文件数据库 |
| 安全 | Spring Security 6 + JJWT 0.12.6 |
| 前端 | Vue 3.4、Vite 5.4、原生 `fetch` |
| 构建 | Maven、npm |

> README 里曾写 "MyBatis 3.0.4"，与实际不符；本节以 `pom.xml` 为准。

## 3. 目录与模块（ADR 0005 三层架构）

```
.
├── pom.xml
├── docs/adr/                       # 5 对 ADR（含 0005-three-layer-architecture 中英）
├── frontend/                       # Vite + Vue 3 子项目
│   ├── index.html
│   ├── package.json                # vue ^3.4, vite ^5.4
│   ├── vite.config.js              # /api 代理到 8031
│   └── src/{App.vue, main.js, style.css}
├── src/
│   ├── main/
│   │   ├── java/cn/edgarli/
│   │   │   ├── MyAiApplication.java
│   │   │   ├── common/             # BizException / Result / GlobalExceptionHandler
│   │   │   ├── entity/             # 6 entity：User / UserApiKey / Conversation / Message / AiCallLog / AuditLog
│   │   │   ├── infrastructure/     # 横切：security / config / observability / task / audit
│   │   │   ├── mapper/             # 6 BaseMapper 接口（Conversation + Message 业务查询迁 XML）
│   │   │   ├── service/            # 8 interface（Auth/User/UserApiKey/Conversation/AiCallLog/Message/MessageQuery/MessageCommand）
│   │   │   ├── service/impl/       # 8 *Impl + MessageSupport helper
│   │   │   ├── service/ai/         # AiService / AiServiceImpl / ChatClientFactory / ChatService / ChatMessage
│   │   │   ├── service/ai/provider/ # ProviderCatalog / ProviderProtocol / ProviderSpec
│   │   │   └── web/                # 8 controller + dto/ (7 Dto) + vo/ (8 Vo) + converter/ (3 手转)
│   │   └── resources/
│   │       ├── application.yml     # 含 my-ai.providers 厂家池 + my-ai.jwt + my-ai.logs.retention-days
│   │       ├── application-my.yml  # 个人 profile（端口 8032）
│   │       ├── schema.sql          # 幂等 DDL，每次启动执行
│   │       ├── cn/edgarli/mapper/  # ConversationMapper.xml + MessageMapper.xml
│   │       └── static/             # 前端构建产物（git 忽略）
│   └── test/java/                  # 当前为空
└── data/                           # H2 文件输出（git 忽略）
```

## 4. 运行与构建

### 4.1 本地运行（关键路径以 `application.yml` 为准）

- 服务端口 `8031`（README 里写的 8080 已过期；启动横幅文本也仍写着 8080，以 `application.yml` 为准）。
- H2 文件：`./data/myai.mv.db`。
- H2 控制台：<http://localhost:8031/h2-console>。

### 4.2 命令

```bash
# 安装前端依赖
cd frontend && npm install

# 构建前端（产物写入 src/main/resources/static/）
cd frontend && npm run build

# 后端
mvn clean test           # 当前无测试用例
mvn spring-boot:run      # 本地启动
mvn -DskipTests package  # 跳过测试打包
```

> Maven 不会自动构建前端，必须先 `npm run build` 再启动后端，否则访问 `/` 会缺失静态资源。

### 4.3 前端开发模式

```bash
cd frontend && npm run dev   # http://localhost:5173 ，/api 代理到 8031
```

## 5. 数据模型

`schema.sql` 每次启动以幂等 DDL 执行（`CREATE TABLE IF NOT EXISTS` / `ADD COLUMN IF NOT EXISTS` / `ADD CONSTRAINT IF NOT EXISTS`），保证新旧数据库都能用同一份 SQL 启动。

```
user(id PK, name, email, default_key_id -> user_api_key.id ON DELETE SET NULL, create_time)
user_api_key(
  id PK,
  user_id -> user.id ON DELETE CASCADE,
  name, provider, api_key,
  base_url, model_name, enabled,
  create_time
)
idx_user_api_key_user_id on (user_id)
```

`api_key` 长度 `VARCHAR(2048)`，`base_url` `VARCHAR(500)`，`model_name` `VARCHAR(200)`，`provider` `VARCHAR(20)`（保留给较短的厂家键）。

## 6. 关键架构约定

1. **严格三层 + 横切（ADR 0005）**：依赖方向 **web → service → mapper**，反向禁止。横切进 `cn.edgarli.infrastructure.*`。`web` 不直接调 mapper；AI 调用入口统一走 `service.ai.AiService`。
2. **AI 厂家配置即代码的唯一源是 `application.yml::my-ai.providers`**。`ProviderCatalog` 用 `@ConfigurationProperties("my-ai.providers")` 绑定。新增厂家只改 yml，Java 侧零改动。
3. **`ProviderProtocol` 三分支**：`OPENAI_COMPATIBLE` / `OLLAMA` / `ANTHROPIC`。`ChatClientFactory.getClient(key)` 按 `UserApiKey.protocol` 派发，未填回落 provider 默认协议。
4. **ChatClient 按请求动态构建，不缓存**。`ChatClientFactory` 每次新建 `OpenAiChatModel` / `OllamaChatModel` / `AnthropicChatModel`，Key / BaseURL / 模型变更下一次请求立刻生效。
5. **DTO/Vo 双层（ADR 0005 §6）**：请求体走 `web/dto.*Dto`（7 个），响应体走 `web/vo.*Vo`（8 个），持久化对象走 `entity.*`（不加 `Do` 后缀）。controller 不直接返回 entity，必须经 `web.converter.*Converter` 转 VO。
6. **Service 接口/Impl 分离（ADR 0005 §2）**：`service.*Service` 接口 + `service.impl.*ServiceImpl` 实现。`MessageService` 是组合接口（继承 `MessageQueryService` + `MessageCommandService`）。
7. **XML mapper（ADR 0005 §8）**：仅 `ConversationMapper` + `MessageMapper` 业务查询走 XML（接口方法 `@Param` 标注），其余 mapper 仍走 `@Select` 注解或 `QueryWrapper`。
8. **`POST /api/chat` 不接受 `provider` / `keyId` / API Key**，仅接受 `userId` + `messages`。模型选择由用户默认 Key 决定。响应头带 `Deprecation: true` + `Warning: 299`；新代码必须用 `POST /api/conversations/{id}/messages`（SSE 流式）。
9. **默认 Key 规则集中在 `UserApiKeyService`**：用户首个启用 Key → 自动设为默认；默认 Key 被禁用 / 删除 → 同步把 `defaultKeyId` 置 `null`，**不**自动切换；设为默认前要 `enabled=true` 且通过 `validateConfiguration`。
10. **错误流全部走 `BizException`**。`GlobalExceptionHandler` 把 `BizException`、JSON / 参数错误、`NoResourceFoundException`、未捕获异常分别映射到 4000 / 4040 / 4090 / 5020 / 5000；HTTP 状态统一 200。
11. **认证（Spring Security 6 + JWT）**：`/api/auth/{register, login, me}` 公开；`GET /api/providers` 公开；其余 `/api/*` 需登录。密码 BCrypt 散列存 `user.password_hash`。JWT HS256，`my-ai.jwt.secret` 配置驱动。前端 `Authorization: Bearer <token>` 访问；每个接口校验 `userId` 与 JWT 中主体一致，否则 4030。
12. **RBAC（ADR 0004 §5）**：`User.role` = USER / ADMIN；`/api/logs/**` hasRole("ADMIN")；admin 名单由 `AdminProperties.isAdmin(email)` 判定，绑定 env var `MYAI_ADMIN_EMAILS`。无 fallback — env var 没配则无管理员，日志 API 任何人都调不通。
13. **可观测性四层日志（ADR 0004）**：
    - **ai_call_log**：`MessageService.streamReply / regenerate` 用 `stream().chatResponse()`，从 Spring AI `ChatResponse.getMetadata().getUsage()` 取 input/output tokens；onComplete 写 SUCCESS 行，onError 写 FAILURE 行（含 latency_ms + error_message + trace_id）。
    - **audit_log**：`AuditAspect @Around @Auditable` 拦截 service 方法；proceed() 成功后写一行。target_id 提取先试返回值 `getId()`，再试 `id()`，最后 fallback 最后 Long 参数。
    - **HTTP 访问日志**：`TraceIdFilter` 跑在 Spring Security 之前，路径白名单 `/api/**`；通过 logger `myai.access` 写到 `./logs/access.jsonl`；响应头回写 `X-Trace-Id`。
    - **系统日志**：logback-spring.xml 全 JSON，stdout + `./logs/app.jsonl`。
    - **保留期**：`LogCleanupTask @Scheduled(cron="0 4 * * * *", zone="Asia/Shanghai")` 每天清理；ai_call_log 直接物理删，audit_log 先软删再物理删。
14. **Key 明文存盘但不外发**：`UserApiKey.apiKey` 加 `@ToString.Exclude`；响应只暴露 `maskedApiKey`、`hasApiKey`；编辑时 `apiKey` 留空 = 保留原值。
15. **双语注释（ADR 0005 §9）**：所有 Java 源文件 + 2 个 XML mapper 的方法 Javadoc、`@param` / `@return` / `@throws`、impl 内的非平凡局部变量 `//`，都按"中文 + 英文同行"格式。entity / DTO / VO 字段同样双语。

## 7. 安全边界（与 README 一致）

- **认证已加**：JWT 鉴权已上线，但仍是单机本地应用，Token 无刷新机制、明文存于浏览器 `localStorage`（键 `myai.token`）—— 同源 JS 可读、非 HttpOnly cookie、非会话级；活跃会话 id 也存 `localStorage`（键 `myai.activeConversationId`）。不要把该版本直接暴露到公网。
- 4010 / 主动登出会清掉 `myai.token` 与 `myai.activeConversationId`。
- **API Key 明文** 存于本机 H2 文件；**不会**通过 API、`toString()`、MyBatis 参数日志输出。
- **自定义 baseUrl** 会让后端对用户填写的地址发起出站请求（SSRF 风险）。不要把该能力暴露给不可信用户。
- 未实现加密、轮换、配额、生产级多租户隔离。**审计已通过 ADR 0004 落地**（`audit_log` 记录 Key / 对话 增删改；仅 admin 可查）。
- **管理员边界**：admin 角色只能由 env var `MYAI_ADMIN_EMAILS` 授予，注册 / 登录时按邮箱匹配；不可通过 API 自封。日志查询端点 `/api/logs/**` 仅 admin 可访问，普通用户调会得 403。

## 8. 已知缺口（与 README 偏差）

| 项 | 描述 | 来源 |
| --- | --- | --- |
| 端口 | `application.yml` 是 `8031`；README 仍写 8080；`MyAiApplication.startupInfo()` 横幅已对齐到 8031（2026-07-29） | pom.xml 已确认 |
| ORM | README 写 "MyBatis 3.0.4"，实际为 MyBatis-Flex 1.11.8；`MyBatisConfig` 类不存在，兼容层为 `cn.edgarli.infrastructure.config.FlexConfig` | pom.xml + 代码 |
| 测试 | `src/test/java` 当前为空；`mvn test` 命令可执行但无用例 | 文件系统 |
| Provider enum | 当前没有显式枚举限制 `provider` 取值，靠 yml 注册；前端下拉直接来源 `/api/providers` | 代码 |
| 三层架构 + Dto/Vo 命名 | README 没体现 ADR 0005 三层 / Dto/Vo 后缀 / 双语注释；本仓库以 ADR + 代码为准 | ADR 0005 |
| Runtime 验证 | 全程未做 `mvn spring-boot:run` + curl 烟测；仅以 `mvn -DskipTests package` BUILD SUCCESS 为交付信号 | 项目规则 §7 |
