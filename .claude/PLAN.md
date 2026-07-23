# 项目 Plan 追踪

> 替代旧的 `.claude/对话记录.md` 机制。
> 每次会话开始时，先核对本文件，把本次会话对应的 plan 节点更新为 `🚧 进行中`；结束或确认后，改为 `✅ 已完成（YYYY-MM-DD）`，并补一句结果摘要。
> 维护者：Claude；调用入口：用户级 `~/.claude/CLAUDE.md` + 项目级 `.claude/CLAUDE.md` 都已同步约定。

## 格式约定

| 状态 | 标记 | 含义 |
| --- | --- | --- |
| 已完成 | ✅ 已完成（YYYY-MM-DD） | 已交付并通过验证 |
| 进行中 | 🚧 进行中（始于 YYYY-MM-DD） | 本次或跨会话正在推进 |
| 待办 | 📋 待办 | 已被记录但未启动 |
| 阻塞 | ⛔ 阻塞（原因） | 因外部条件卡住，等待用户/环境 |

## 已完成（归档）

### 第 1 次对话（2026-07-22）— ✅ 已完成
建立对话记录约定：按轮次完整记录，每次会话追加到 `.claude/对话记录.md`。
（已于 2026-07-23 由本文件替代，原 `.claude/对话记录.md` 标记为归档，不再追加。）

### 第 2 次对话（2026-07-22）— ✅ 已完成
澄清"agents.md"歧义后创建项目级 `CLAUDE.md`：工作准则（编码前思考 / 简洁优先 / 精准修改 / 目标驱动）+ 项目要点 + 用户偏好（中文 + 对话日志追加）。

### 第 3 次对话（2026-07-22）— ✅ 已完成
多用户/多 Key 数据模型落地：`user` 一对多 `user_api_key`，每用户设置默认 Key，每条 Key 独立保存（名称、provider、apiKey、baseUrl、modelName、enabled）。按数据层 / CRUD / 动态 ChatClient / 前端 / 启动验证 5 段实施，端到端跑通。

### 第 4 次对话（2026-07-22）— ✅ 已完成
Result 统一化（HTTP 200 + `{code, message, data}`，code 0/4000/4040/4090/5000/5020）；MyBatis-Flex 落地（`mybatis-flex-spring-boot4-starter:1.11.8`）；H2 数据源 `jdbc-url` + HikariCP 4.0.3 启动跑通；H2 锁问题诊断后清理残留 java 进程；反思"反复试错"违规，按事实重写 `.claude/CLAUDE.md`。

### 第 5 次对话（2026-07-23）— ✅ 已完成
延续 H2 锁处置 + 全局规则反思：承认违反"每次会话自动记录"约定，把第 5 次对话按轮次补全到 `.claude/对话记录.md`。

### 第 6 次对话（2026-07-23）— ✅ 已完成
动态 Provider 池：抽取 6 家 Provider（openai / deepseek / moonshot / zhipu / minimax / ollama）至 `application.yml`，删除旧 `AiProvider` 枚举，新建 `ProviderSpec / ProviderProtocol / ProviderCatalog`，`ChatClientFactory` 按 `protocol` 分支；`GET /api/providers` + 前端动态下拉；后端编译 + 前端 `npm run build` 通过。

### 第 7 次对话（2026-07-23）— ✅ 已完成
机制切换：把"对话日志追加"改为"plan 持久化"。
- 新建 `.claude/PLAN.md`，用 plan 格式（已完成 / 进行中 / 待办 / 修改日志）追踪项目进度；
- 前 6 次对话在 PLAN.md 中归档为 ✅ 已完成；
- 旧的 `.claude/对话记录.md` 顶部加 `[ARCHIVED 2026-07-23]` 标记，仅保留原文不再追加；
- 同步更新用户级 `~/.claude/CLAUDE.md` 与项目级 `.claude/CLAUDE.md` 中的约定；
- 同步更新项目记忆 `MEMORY.md` 与 `conversation-log.md` 指向新机制。

---

## 当前活跃

### 🚧 动态 Provider 池落地收尾（始于 2026-07-23）
> 来源：第 6 次对话 + `.claude/plans/imperative-beaming-hippo.md`

**目标**
- `application.yml` 维护一份 `my-ai.providers` 列表，新增厂商只改 YAML，不必改 Java；
- `user_api_key.provider` 接受 YAML 中任一 `name`；
- OpenAI 兼容厂统一走 `spring-ai-openai` 客户端，Ollama 仍走 `spring-ai-ollama`；
- `Result` 与"按 userId 默认 Key 聊天"行为不变。

**已完成的子步骤**
- ✅ 抽取 `ProviderSpec` / `ProviderProtocol` / `ProviderCatalog`
- ✅ 重写 `ChatClientFactory`，按 `protocol` 分支
- ✅ 重写 `UserApiKeyService` 校验与默认 baseUrl/model 填充
- ✅ 新增 `ProviderController#list` → `GET /api/providers`
- ✅ 前端 `App.vue` 改用 `/api/providers` 动态下拉 + 默认值
- ✅ `mvn clean package -DskipTests` 编译通过
- ✅ `npm run build` 前端构建通过

**验证项（按计划均待用户在 IDE 内 Run/Debug 后 curl）**
- ⏳ `curl http://localhost:8080/api/providers` 返回 6 家 Provider
- ⏳ 同一用户保存 deepseek + minimax 两条 Key → 列表 2 条 + 不泄露明文
- ⏳ `PUT /api/users/{id}/keys/{minimaxId}/default` 切换默认 → 聊天改用 minimax
- ⏳ 故意把 minimax baseUrl 写错 → 前端拿到 `code=5020`
- ⏳ Ollama 错误 baseUrl → `code=5020`
- ⏳ 切换用户后前端下拉保留正确顺序

> ⛔ 阻塞（原因：用户控制 IDE 启动节奏；H2 文件锁/端口占用只能由用户授权处置，Claude 不再自动停止 IDE 进程）。
> 突破条件：用户在 IntelliJ 内 Run `cn.edgarli.MyAiApplication`，启动后向 Claude 反馈 `Started MyAiApplication` 日志或一个具体验证项的 curl 结果。

**关键文件**
- 新建：`src/main/java/cn/edgarli/ai/provider/ProviderSpec.java`、`ProviderProtocol.java`、`ProviderCatalog.java`
- 改写：`src/main/java/cn/edgarli/ai/AiProvider.java`、`src/main/java/cn/edgarli/ai/ChatClientFactory.java`、`src/main/java/cn/edgarli/service/UserApiKeyService.java`
- 新建：`src/main/java/cn/edgarli/web/ProviderController.java`
- 修改：`src/main/resources/application.yml`（加 `my-ai.providers` 段）
- 修改：`frontend/src/App.vue`（provider 下拉 + 默认 baseUrl/model）
- 文档：`.claude/CLAUDE.md`、`README.md`、`.claude/PLAN.md`

---

## 待办（未来）

（暂无 — 待下次会话确认后写入）

---

## 修改日志

- 2026-07-23：从 `.claude/对话记录.md` 改造为 `.claude/PLAN.md`（plan 持久化）；前 6 次对话归档为 ✅ 已完成；新增「动态 Provider 池落地收尾」为 🚧 进行中节点。
- 2026-07-23：第 7 次会话闭环 —— 把「plan 持久化改造」自身登记为 ✅ 已完成（第 7 次对话节点），记忆 `MEMORY.md` 已切换指向新机制。
- 2026-07-23：观测到对话记录已切换到 `.claude/PLAN.md`（plan 持久化）。将"动态 Provider 池落地收尾"由 ⏳ 改为 ⛔ 阻塞，等待用户在 IDE 启动后回调 Claude；Claude 不再主动重启/停止 IDE 进程。
- 2026-07-23：在 IDE 启动时报 `ProviderCatalog` bean 缺失，因为 `@ConfigurationProperties` 在 Spring Boot 4 下需要 `@Component` 才会注册为 bean；已为 `ProviderCatalog` 加 `@Component`。
- 2026-07-23：修复前端 `Cannot read properties of undefined (reading 'trim')`：来源是 Provider 池尚未加载完成时初始化 `newKeyForm()` 把 `provider` 设为对象而非字符串，导致 select 双向绑定的更新触发 `applyProviderDefaults` 时取到 undefined。已对 `newKeyForm` / `startEditKey` / `applyProviderDefaults` 加空值兜底，并重建 `npm run build`。