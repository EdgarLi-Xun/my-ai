# MyAi

基于 **Spring Boot 4 + Spring AI 2 + Vue 3** 的本地多用户聊天应用。每个用户可以维护多条 OpenAI/Ollama Key 配置，并指定一条默认配置；聊天请求只提交 `userId`，后端从数据库读取该用户的默认 Key。

## 功能

- 多用户管理
- 单用户多 Key：每条配置独立保存名称、provider、API Key、Base URL、模型和启用状态
- 每个用户设置一个默认 Key，聊天时自动使用
- OpenAI 与 Ollama 动态 `ChatClient`，修改默认配置后下一次请求立即生效
- Key 响应脱敏，编辑时留空表示保留原 Key
- H2 文件数据库，旧四列 `user` 表启动时自动补充新结构
- Vue 单页聊天与用户/Key 管理界面

## 技术栈

| 层 | 技术 |
| --- | --- |
| 后端 | Java 21、Spring Boot 4.0.7、Spring AI 2.0.0 |
| 数据 | MyBatis 3.0.4、H2 2.4 文件数据库 |
| 前端 | Vue 3.4、Vite 5.4、原生 `fetch` |
| 构建 | Maven、npm |

> `MyBatisConfig` 是 MyBatis Starter 3.0.4 适配 Spring Boot 4 的必要兼容配置，不应删除。

## 快速开始

### 环境

- Java 21
- Maven 3.8+
- Node.js 18+ 与 npm 9+
- 可选：本机 [Ollama](https://ollama.com) 和已下载的模型
- 可选：OpenAI 或 OpenAI-compatible API Key

### 构建前端

```bash
cd frontend
npm install
npm run build
cd ..
```

前端生产产物写入 `src/main/resources/static/`。Maven 不会自动运行前端构建。

### 测试并启动后端

```bash
mvn clean test
mvn spring-boot:run
```

打开：

- 应用：<http://localhost:8080/>
- H2 控制台：<http://localhost:8080/h2-console>

数据库默认保存在 `./data/myai.mv.db`。

前端开发模式：

```bash
cd frontend
npm run dev
```

打开 <http://localhost:5173/>，Vite 会把 `/api` 代理到 8080。

## 数据模型

```text
user
├── id
├── name
├── email
├── default_key_id ───────────────┐
└── create_time                   │
                                  │
user_api_key                      │
├── id ◄──────────────────────────┘
├── user_id -> user.id
├── name
├── provider (openai / ollama)
├── api_key
├── base_url
├── model_name
├── enabled
└── create_time
```

规则：

- 用户尚无默认项时，新增的首个启用 Key 自动设为默认。
- 禁用或删除默认 Key 后，默认项会被清空，不会自动切换到其他 Key。
- 只有启用且配置完整的 Key 可以设为默认。
- 删除用户会级联删除其全部 Key。

## API

### 用户

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/users` | 用户列表 |
| `GET` | `/api/users/{id}` | 用户详情 |
| `POST` | `/api/users` | 创建用户 |
| `DELETE` | `/api/users/{id}` | 删除用户及其 Key |

创建用户：

```bash
curl -X POST http://localhost:8080/api/users \
  -H "Content-Type: application/json" \
  -d '{"name":"Alice","email":"alice@example.com"}'
```

### Key 配置

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/users/{userId}/keys` | 列出该用户的 Key（脱敏） |
| `GET` | `/api/users/{userId}/keys/{keyId}` | Key 详情（脱敏） |
| `POST` | `/api/users/{userId}/keys` | 新增 Key |
| `PUT` | `/api/users/{userId}/keys/{keyId}` | 更新 Key；空 `apiKey` 保留原值 |
| `DELETE` | `/api/users/{userId}/keys/{keyId}` | 删除 Key |
| `PUT` | `/api/users/{userId}/keys/{keyId}/default` | 设为默认 Key |

新增 Ollama 配置：

```bash
curl -X POST http://localhost:8080/api/users/1/keys \
  -H "Content-Type: application/json" \
  -d '{
    "name":"本机 Ollama",
    "provider":"ollama",
    "apiKey":"",
    "baseUrl":"http://localhost:11434",
    "modelName":"qwen2.5:7b",
    "enabled":true
  }'
```

新增 OpenAI 配置：

```bash
curl -X POST http://localhost:8080/api/users/1/keys \
  -H "Content-Type: application/json" \
  -d '{
    "name":"OpenAI",
    "provider":"openai",
    "apiKey":"sk-your-key",
    "baseUrl":"https://api.openai.com",
    "modelName":"gpt-4o-mini",
    "enabled":true
  }'
```

Key 响应不会包含 `apiKey` 字段，只包含类似 `"maskedApiKey":"****abcd"` 和 `"hasApiKey":true`。

### 聊天

仅支持 `POST /api/chat`：

```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{
    "userId":1,
    "messages":[
      {"role":"system","content":"你是一个简洁的助手"},
      {"role":"user","content":"你好"}
    ]
  }'
```

请求不能指定 `provider`、`keyId` 或 API Key。后端使用 `userId` 对应的默认 Key。用户不存在返回 404；没有可用默认 Key 返回 409；请求配置非法返回 400。

## 配置

`src/main/resources/application.yml` 只配置数据库、SQL 初始化、端口和日志。OpenAI/Ollama 的全局模型自动配置已关闭，模型客户端完全按数据库中的用户 Key 动态创建。

`schema.sql` 每次启动都会执行，使用幂等 DDL 兼容旧数据库。

## 安全边界

该项目当前定位为本地示例应用：

- 没有认证与授权；任何能访问服务的人都能管理用户和 Key。
- API Key 为满足调用需求以明文保存在本机 H2 文件中，但不会通过 API、实体 `toString()` 或 MyBatis 参数日志输出。
- 自定义 Base URL 会让后端访问用户填写的地址；不要向不可信用户开放该能力。
- 未实现 Key 加密、审计、轮换、配额与生产级多租户隔离。

因此不要把当前版本直接暴露到公网或作为生产密钥管理系统使用。

## 构建验证

```bash
cd frontend && npm run build
cd ..
mvn clean test
mvn clean package
```

实际聊天还需要对应的 Ollama 服务/模型或有效的 OpenAI-compatible Key。
