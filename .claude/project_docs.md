# 项目文档

> 资料来源：仓库现有 `pom.xml`、`application.yml`、`schema.sql`、Java / Vue 源码、`README.md`。无法核实处见文末「已知缺口」。本文档与代码冲突时按 `CLAUDE.md` 第 1 节处置（以代码为准，但保留差异提示）。

## 1. 项目概览

**MyAi**：本地多用户聊天应用。每个用户可以维护多条 AI 厂家配置（OpenAI 兼容协议 / Ollama），并指定一条默认配置；聊天接口只接收 `userId`，由后端读取数据库里的默认 Key 调用模型。

定位：本地示例工具。没有认证 / 授权 / 加密 / 审计 / 配额 / 多租户隔离，详见第 7 节安全边界。

## 2. 技术栈

| 层 | 技术 |
| --- | --- |
| 后端 | Java 21、Spring Boot 4.0.7、Spring AI 2.0.0（`spring-ai-openai`、`spring-ai-ollama`） |
| 数据 | **MyBatis-Flex 1.11.8**（`mybatis-flex-spring-boot4-starter`）、HikariCP 4.0.3、H2 文件数据库 |
| 前端 | Vue 3.4、Vite 5.4、原生 `fetch` |
| 构建 | Maven、npm |

> README 里曾写 "MyBatis 3.0.4"，与实际不符；本节以 `pom.xml` 为准。

## 3. 目录与模块

```
.
├── pom.xml
├── frontend/                        # Vite + Vue 3 子项目
│   ├── index.html
│   ├── package.json                 # vue ^3.4, vite ^5.4
│   ├── vite.config.js               # /api 代理到 8031
│   └── src/{App.vue, main.js, style.css}
├── src/
│   ├── main/
│   │   ├── java/cn/edgarli/
│   │   │   ├── MyAiApplication.java
│   │   │   ├── ai/                 # AI 模型客户端层
│   │   │   ├── common/             # Result / BizException / ExceptionHandler
│   │   │   ├── config/             # FlexConfig（MyBatis-Flex + SB4 兼容层）
│   │   │   ├── entity/             # User、UserApiKey
│   │   │   ├── mapper/             # MyBatis-Flex BaseMapper 默认方法
│   │   │   ├── service/            # UserApiKeyService
│   │   │   └── web/                # REST 控制器
│   │   └── resources/
│   │       ├── application.yml     # 含 my-ai.providers 厂家池
│   │       ├── schema.sql          # 幂等 DDL，每次启动执行
│   │       ├── cn/edgarli/mapper/  # FlexConfig 仍按该通配扫描（当前为空）
│   │       ├── mapper/             # 同上（备用扫描路径，未使用）
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

1. **AI 厂家配置即代码的唯一源是 `application.yml::my-ai.providers`**。`ProviderCatalog` 用 `@ConfigurationProperties("my-ai.providers")` 绑定。新增厂家只改 yml，Java 侧零改动。
2. **ChatClient 按请求动态构建，不缓存**。`ChatClientFactory` 收到 `UserApiKey` 后立即构建 `OpenAiChatModel` / `OllamaChatModel` + `ChatClient`，每次调用都新建——保证 Key / BaseURL / 模型变更下一次请求立刻生效。
3. **`POST /api/chat` 不接受 `provider` / `keyId` / API Key**，仅接受 `userId` + `messages`。模型选择由用户默认 Key 决定。
4. **默认 Key 规则集中在 `UserApiKeyService`**：
   - 用户首个启用 Key → 自动设为默认（创建时）。
   - 默认 Key 被禁用 / 删除 → 同步把 `defaultKeyId` 置 `null`，**不**自动切换。
   - 设为默认前要 `enabled=true` 且通过 `validateConfiguration`（`requiresKey=true` 的 provider 必须有非空 `apiKey`）；`baseUrl` 必须是合法 http(s)。
5. **错误流全部走 `BizException`**。`GlobalExceptionHandler` 把 `BizException`、JSON / 参数错误、`NoResourceFoundException`、未捕获异常分别映射到 4000 / 4040 / 4090 / 5020 / 5000；HTTP 状态统一 200。
6. **Key 明文存盘但不外发**：`UserApiKey.apiKey` 加 `@ToString.Exclude`；响应只暴露 `maskedApiKey`、`hasApiKey`；编辑时 `apiKey` 留空 = 保留原值。
7. **密码认证（Spring Security 6 + JWT）**：`POST /api/auth/register` / `POST /api/auth/login` 公开；密码 BCrypt 散列存 `user.password_hash`。JWT HS256，`my-ai.jwt.secret` 配置驱动（≥32 字节）。登录后前端 `Authorization: Bearer <token>` 访问其余 `/api/*`。每个接口校验 `userId` 与 JWT 中主体一致，否则 4030。

## 7. 安全边界（与 README 一致）

- 无认证 / 授权：能访问服务的人即可管理用户与 Key。
- API Key 以明文存在本机 H2 文件；**不会**通过 API 响应、`toString()`、MyBatis 参数日志输出。
- 自定义 `baseUrl` 会让后端对用户填写的地址发起出站请求；当前应用若对外开放，存在 SSRF 风险。
- 未实现：Key 加密、审计、轮换、配额、生产级多租户隔离。

## 8. 已知缺口（与 README 偏差）

| 项 | 描述 | 来源 |
| --- | --- | --- |
| 端口 | `application.yml` 是 `8031`；README 与 `MyAiApplication.startupInfo()` 横幅仍写 8080 | pom.xml 已确认 |
| ORM | README 写 "MyBatis 3.0.4"，实际为 MyBatis-Flex 1.11.8；`MyBatisConfig` 类不存在，兼容层实际为 `FlexConfig` | pom.xml + 代码 |
| 测试 | `src/test/java` 当前为空；`mvn test` 命令可执行但无用例 | 文件系统 |
| Provider enum | 当前没有显式枚举限制 `provider` 取值，靠 yml 注册；前端下拉直接来源 `/api/providers` | 代码 |
