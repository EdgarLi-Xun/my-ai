# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

> 作用范围：本仓库。用户级通用偏好（沟通方式、修改原则、仓库安全、验证原则、项目规则处理）见 `~/.claude/CLAUDE.md`，不要在本文件重复维护。冲突时按用户级与本节优先级处置。

## 1. 冲突处理与本仓库范围

- 仓库内代码、配置、测试、构建脚本是项目事实来源；文档与实际实现不一致时先指出差异，不擅自假定文档或代码正确。
- 子目录中更具体的 `CLAUDE.md` 仅约束其所在目录及子目录。
- 当前任务的明确用户要求优先于本文件中的通用项目规则。
- 仅当歧义会影响公共 API、数据模型、外部行为、安全边界或不可逆操作时请求澄清；低风险局部歧义采用最简单、最保守的合理假设继续，并在结果中说明。

## 2. 项目事实

### 技术栈（与 pom.xml 对齐）

- 后端：Java 21、Spring Boot 4.0.7、Spring AI 2.0.0（`spring-ai-openai` + `spring-ai-ollama` + `spring-ai-anthropic`）、MyBatis-Flex 1.11.8（`mybatis-flex-spring-boot4-starter`）、Spring Security 6 + JJWT 0.12.6、HikariCP 4.0.3。
- 前端：Vue 3.4 + Vite 5.4，原生 `fetch`，无 UI 框架。构建产物写入 `src/main/resources/static/`。
- 数据库：H2 2.x 文件模式 `./data/myai.mv.db`；`schema.sql` 每次启动以幂等 DDL 执行（`CREATE TABLE IF NOT EXISTS` / `ALTER TABLE ADD COLUMN IF NOT EXISTS` / `ADD CONSTRAINT IF NOT EXISTS`）。
- 端口：`application.yml` 中 `server.port: 8031`；`MyAiApplication` 启动横幅仍打印 `8080`（横幅文字已过期，端口以 yml 为准）。

### 与 README 的差异（必须留意）

| 点 | README | 实际仓库 |
| --- | --- | --- |
| 数据访问框架 | "MyBatis 3.0.4" | MyBatis-Flex 1.11.8 |
| 服务端口 | `localhost:8080` | `application.yml` 中是 8031（横幅文字已过期） |
| MyBatis 兼容注解 | `MyBatisConfig` | 实际类名是 `cn.edgarli.config.FlexConfig` |
| 协议支持 | OpenAI / Ollama | 实际含 Anthropic（`ProviderProtocol` 枚举 + `ChatClientFactory` 三分支） |
| 认证 | 无认证 | Spring Security 6 + JWT（`/api/auth/{register,login,me}` + Bearer） |

### 关键源码目录

- 后端包：`cn.edgarli.{ai, common, config, entity, mapper, security, service, web}`。
- 资源：`src/main/resources/{application.yml, application-my.yml, schema.sql}`。
- 前端源：`frontend/src/{App.vue, main.js, style.css}`；构建产物：`src/main/resources/static/{index.html, assets/}`。
- `src/main/resources/cn/edgarli/mapper` 当前为空（`FlexConfig` 按通配扫描，删除该配置会导致启动失败）。

## 3. 标准命令

> 在仓库根目录执行。前端生产产物必须先构建到 `src/main/resources/static/`，否则 `mvn spring-boot:run` 启动后访问 `/` 会缺资源。

```bash
# 安装前端依赖
cd frontend && npm install && cd ..

# 构建前端（产物写入 src/main/resources/static/）
cd frontend && npm run build && cd ..

# 后端运行（默认 profile → 8031）
mvn spring-boot:run
# 切换到 application-my.yml（端口 8032）
mvn spring-boot:run -Dspring-boot.run.profiles=my

# 前端开发模式（Vite 把 /api 代理到 8031）
cd frontend && npm run dev

# 后端测试（当前 src/test/java 为空，命令可执行但无用例）
mvn test
# 后端全量编译 / 跳过空测试套件
mvn clean package
mvn -DskipTests package
```

Maven 不会自动触发 `npm run build`；改前端源码后必须手动构建。`data/`、`frontend/node_modules/`、`src/main/resources/static/assets/**`、`src/main/resources/static/index.html` 已在 `.gitignore`，提交前端产物改动通常意味着覆盖已构建文件，不要把这类产物回填到仓库。

入口：

- 应用首页：<http://localhost:8031/>
- H2 控制台：<http://localhost:8031/h2-console>

## 4. 关键架构约定

只记录改动会影响外部行为或启动失败的约束；其余实现细节看代码。

1. **AI 厂家配置唯一源是 `application.yml` 的 `my-ai.providers`**。新增厂家只在 yml 加一段；Java 侧零改动。`ProviderCatalog.require(name)` 是访问入口。Provider 名（字符串）由 yml 池维护，与协议枚举解耦。
2. **`ProviderProtocol` 三分支**：`OPENAI_COMPATIBLE` / `OLLAMA` / `ANTHROPIC`。`ChatClientFactory.getClient(key)` 按 `UserApiKey.protocol` 派发；Key 上未填则回落 `application.yml` 中该 provider 的默认协议。
3. **`ChatClientFactory` 每次新建客户端，不缓存**。`OpenAiChatModel` / `OllamaChatModel` / `AnthropicChatModel` 都按当前 `UserApiKey`（含最新 `apiKey` / `baseUrl` / `modelName`）构造，所以改默认配置后下一次请求立即生效，进程内无需刷新。
4. **`POST /api/chat` 不接受 provider / keyId / API Key**。后端按 `userId` 取 `User.defaultKeyId` 对应已启用、配置完整的 Key。`ChatController` 在派发前强制 `UserController.currentUserId() == request.userId()`，否则抛 `BizException.forbidden`（4030）。
5. **当前用户取值 helper**：`UserController.currentUserId()` 读 `SecurityContextHolder` 的 `AuthPrincipal.userId()`；需要把请求体/路径 userId 与登录主体对齐时统一用此 helper，不要直接读 `SecurityContextHolder`。
6. **默认 Key 规则集中在 `UserApiKeyService`**：用户首个启用 Key 自动设为默认；默认 Key 被禁用/删除时 `default_key_id` 置 `NULL`，不自动切换；设为默认前必须 `enabled=true` 且通过 `validateConfiguration`；删除用户通过 `ON DELETE CASCADE` 级联删除其全部 Key。
7. **错误流全部走 `BizException`**：`GlobalExceptionHandler` 返回 `Result.failure(code, message)`；JSON 解析/参数错误统一收口为 4000；未捕获异常 → 5000。HTTP 状态码本身保持 200。
8. **认证走 Spring Security 6 + JWT**：`/api/auth/{register, login, me}` 公开；`GET /api/providers` 公开；`/h2-console/**`、`/static/**`、`/`、`/error` 公开；其余 `/api/*` 全部需登录。`JwtAuthenticationFilter` 从 `Authorization: Bearer` 解析 `userId` 并注入 `AuthPrincipal`。密码 BCrypt 散列；`User.passwordHash` 加 `@JsonIgnore` / `@ToString.Exclude` 不外露。
9. **Key 明文落地但不外发**：`UserApiKey.apiKey` 用 `@ToString.Exclude` 屏蔽日志；响应体走 `mask()` 只回 `****abcd` 和 `hasApiKey`；编辑时空字符串明确表示保留原值。
10. **FlexConfig 不能删**：显式声明 `HikariDataSource` 并交给 MyBatis-Flex 的 `FlexSqlSessionFactoryBean`，兼容 Spring Boot 4 自动数据源装配；同时保证 `MapUnderscoreToCamelCase=true`（Flex 1.x 强制）。删除或注释该类会导致启动失败 / 下划线字段无法映射。

### 数据模型（与 schema.sql 对齐）

```
user(id, name, email, password_hash,
     default_key_id → user_api_key.id ON DELETE SET NULL,
     create_time)

user_api_key(id, user_id → user.id ON DELETE CASCADE,
             name, provider, api_key, protocol,
             base_url, model_name, enabled,
             create_time)
idx_user_api_key_user_id on (user_id)
```

`user_api_key.protocol` 列由 `ALTER TABLE ADD COLUMN IF NOT EXISTS protocol VARCHAR(30)` 增量补齐，是可选覆盖项；`password_hash` 与 `default_key_id` 同样按需 `ADD COLUMN IF NOT EXISTS` 兼容旧四列 `user` 表。

## 5. 前端架构

- 单 SFC `App.vue` 承担登录/注册、用户、Key、聊天四个面板；新增 `auth-overlay` + `token` state 取代旧版"无认证直入"。
- JWT token 只存在组件内存中，刷新页面即需重新登录（仅本地开发会话级，未持久化到 localStorage）。
- 所有 API 调用经 `App.vue` 的 `fetch` 封装；`Authorization: Bearer <token>` 由组件统一注入；`/api/auth/me` 启动时用于恢复当前用户。
- Vite 配置代理 `/api` 到后端 8031（参见 `frontend/vite.config.js`）。

> 修改 `frontend/` 后必须 `npm run build` 再继续，否则后端 `target/` 里的旧产物仍是上次结果。

## 6. 安全边界（开发时遵守）

- **认证已加**：JWT 鉴权已上线，但仍是单机本地应用，Token 无刷新机制、明文存于会话内存，不要把该版本直接暴露到公网。
- **API Key 明文** 存于本机 H2 文件；**不会**通过 API、`toString()`、MyBatis 参数日志输出。
- **自定义 baseUrl** 会让后端对用户填写的地址发起出站请求（SSRF 风险）。不要把该能力暴露给不可信用户。
- 未实现加密、审计、轮换、配额、生产级多租户隔离。

## 7. 验证原则（仓库特有补充）

> 与 `~/.claude/CLAUDE.md` 互补。仅列本仓库特有的验证要点。

- 当前 `src/test/java` 为空，无现成测试基线。验证方式以 `mvn package`（编译通过）+ 手动 curl / 前端联调为准。
- 验证受保护接口：先 `POST /api/auth/register` 或 `/login` 取 token，再用 `Authorization: Bearer <token>` 调 `/api/chat`、`/api/users/{id}/keys/**`、`/api/auth/me` 等。
- 实际聊天还需要对应的 Ollama 服务/模型或有效的 OpenAI / Anthropic-compatible Key。
- 未运行或无法运行的检查必须明确说明，不声称"全部通过"。

## 8. `.claude/PLAN.md` 持久化

> 仅对会修改仓库内容的多步骤任务维护。纯咨询、解释、代码 review、搜索或未产生仓库修改的任务不更新 PLAN。

- 多文件任务**编码前**必须先更新 `.claude/PLAN.md`（文件不存在时直接创建），标记 `🚧 进行中`，列出目标、关键决策和子步骤清单。
- 完成任务时，标记 `✅ 已完成（YYYY-MM-DD）` 并补充一句可验证的结果摘要。
- 部分完成或受阻时，标记 `⚠️ 受阻` 并记录已完成内容、阻塞原因和下一步。
- 找不到唯一对应节点时不猜测、不错误修改其他节点；在结果中说明未更新原因。
- PLAN 只记录任务状态、关键决策和结果摘要，不记录完整对话、秘密信息或大段日志。

## 9. 交付说明

完成任务后用四段简洁结构：

1. **完成内容**：改了什么，为何这样改。
2. **验证结果**：实际执行的测试、检查或手动验证，结果如何。
3. **文件范围**：列出主要修改文件；无关文件不应出现变更。
4. **剩余事项**：仅列真实风险、未验证项或需用户决策的事项。

不用含糊表述替代验证结果；不把建议项说成已完成项。

## 10. 项目知识文档

`.claude/` 下的会话级文档：

- `api.md`、`project_docs.md`、`REQUIREMENTS.md` — 供后续会话快速读取。
- `PLAN.md` — 多步骤任务的会话级痕迹。

仓库根的架构决策记录：

- `docs/adr/` — 存放 ADR（Architecture Decision Record）。命名 `NNNN-slug.md`（四位序号 + kebab-case slug）。`NNNN` 按 `docs/adr/` 内最高编号 + 1 取；目录本身只在第一份 ADR 需要时创建。
- ADR 正文遵循 `domain-modeling` skill 的 ADR-FORMAT：1–3 句说明"上下文 + 决策 + 理由"，可选 Status / Considered Options / Consequences。仅在三项条件（难反转 / 没有上下文会令人困惑 / 真实权衡）都满足时落 ADR。
- 同主题多语言版：保留 `0001-slug.md`（默认）+ `0001-slug.<lang>.md`（其它语言），并在文首互相 link。

`.claude/` 下的会话级文档维护约束：

- 任一缺失时按本节一次性补齐；仓库结构/接口/需求发生实质变更时按需刷新，并在文末记录变更摘要与日期。
- 内容必须从仓库现有文件提炼汇总，不得凭空编造；无法核实处标 `TODO` 并列在文末「已知缺口」。
- 不写入密钥、Token、个人信息或敏感日志；维护约束与 `~/.claude/CLAUDE.md` 一致。