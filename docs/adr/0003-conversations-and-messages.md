# 对话与消息架构（Conversations and Messages）

MyAi 引入**对话**作为聊天的容器（2026-07-27 设计定稿）。一个对话是 1..N 条消息的有序集合，消息的角色为用户 / 助手 / 系统三者之一；AI 在同对话内看得见全部未作废消息，UI 侧栏按 `updated_at` 倒序展示对话，软删的对话 30 天后自动清理。这是 MyAi 从「无状态一问一答」到「有记忆的聊天产品」的转折性架构变更，落地后**不可逆**（既有 `/api/chat` 端点会被替换）。

> English version: `0003-conversations-and-messages.en.md`.

## 上下文

MyAi 原 `/api/chat` 是无状态的：每次调用只把当前消息丢给 AI，AI 看不见历史，用户也看不见历史。用户要求"记录聊天记录 + 新开不同的聊天窗口"后，无状态模型必须被替换为**有记忆的对话模型**：新增 `conversation` 与 `message` 两张表，把 AI 上下文、UI 状态、用户行为边界都收敛到"对话"这个领域对象上。

## 核心决策

### 1. 数据模型
- **`conversation`** 表：`id, user_id, title, title_manually_set, created_at, updated_at, deleted_at`。`user_id` ON DELETE CASCADE。**不存** `key_id`（见决策 2）。
- **`message`** 表：`id, conversation_id, role, content, is_orphaned, created_at`。`conversation_id` ON DELETE CASCADE。`role` CHECK 约束为 `'USER' | 'ASSISTANT' | 'SYSTEM'`。
- **`content` 是纯文本 Markdown 源**，渲染仅在前端（`marked` + `highlight.js` + `KaTeX` + `DOMPurify`），后端不做 HTML 转换。

### 2. 对话不绑 Key
- `conversation` 表不存 `key_id`。每次发消息都查 `User.default_key_id` 派发到对应 `UserApiKey`。
- 接受"假连贯"代价：用户中途改默认 Key 后，对话里前后消息可能来自不同模型 / 厂家。
- 理由：Key 是用户偏好，对话是工作内容，二者解耦让多 Key 工作流（"GPT-4 头脑风暴" / "Claude 代码 review" 两个对话并列）不增加数据模型负担。

### 3. AI 上下文 = 同对话内全部未作废消息
- 调 AI 前 fetch `WHERE conversation_id = ? AND is_orphaned = FALSE ORDER BY created_at` 的全部消息，拼成对话历史喂给 AI provider。
- 不同对话之间互不干扰（对话 A 的消息不会进入对话 B 的 prompt）。

### 4. 编辑与重新生成（`is_orphaned` 软标记）
- 用户编辑一条 USER 消息 → 该消息之后所有消息标 `is_orphaned = TRUE` → AI 用新内容 + 该消息之前的全部未作废消息重新跑。
- 用户点 ASSISTANT 消息的"重新生成" → 用同样的历史重新调 AI，新回复覆盖旧的（旧的标 `is_orphaned = TRUE`）。
- **不**物理删除被作废的消息，保留在 DB 用于"如果想回到旧版本可以重新生成"。

### 5. 流式输出 + 续传（SSE）
- `POST /api/conversations/{id}/messages` 返回 `text/event-stream`。
- 客户端用 `fetch + ReadableStream` 消费，显示"打字机"动画。
- 完整 token 流结束后才入库一条完整的 `message.content`。
- 用户点"停止" → `AbortController` 关闭流 → 服务端检测到 client disconnect 取消 `ChatClient.stream()` 调用 → 已生成的 token 全部丢弃。
- 续传实现留到实施期，候选：server-side buffer replay / 客户端缓存 / 新 AI 调用跳过已显示。

### 6. 多 tab 实时同步（`BroadcastChannel`）
- 用浏览器原生 `BroadcastChannel('my-ai-conversations')` 在同源 tab 间广播事件：`message:created` / `message:updated` / `message:orphaned` / `conversation:created` / `conversation:updated` / `conversation:deleted` 等。
- 无需后端 SSE 通道，纯客户端跨 tab 通信。
- Fallback：`visibilitychange` 与切 conversation 时从 server 拉最新。

### 7. 默认 Key 不可用 → 报错 + 引导
- 服务端检测 `User.default_key_id IS NULL` / 指向 disabled Key / Key 配置无效 → 抛 `BizException(code=4030)`。
- 前端识别 4030 → toast + "去设置" 按钮跳转 Key 管理。
- 沿用 CLAUDE.md 4.6 节"默认 Key 规则集中在 `UserApiKeyService`"现状，不引入"自动 fallback 到第一个 enabled Key"。

### 8. 软删 + 30 天自动清理
- `DELETE /api/conversations/{id}` → `deleted_at = NOW()`，不真删。
- `POST /api/conversations/{id}/restore` → `deleted_at = NULL`。
- Spring `@Scheduled(cron = "0 3 * * * *")` 每天凌晨 3 点扫 `deleted_at < NOW() - 30 days` 的对话，DELETE 行（CASCADE message）。
- retention 走 `@ConfigurationProperties`：`my-ai.trash.retention-days`（默认 30）。
- UI 上侧栏底部"已删除 (N)"折叠区展示 [恢复] [永久删除] 按钮。

### 9. 上下文窗口管理 = 透传错误
- 后端不实现 token 截断 / 摘要 / 滑动窗口。
- AI provider 返回"上下文窗口超限"错误时，`BizException` 收口后给用户明确文案（"对话过长，请删除部分历史消息后再发送"）。
- **触发升级的条件**：任一用户主动反馈"对话过长"。在那之前不主动实现截断 / 摘要。

## 考虑过的选项

### 2.1 对话绑 Key
- **A. 创建时锁定 `key_id` 到 conversation** — 拒绝。每个对话天然绑定一个 Key，对话就是"GPT-4 那次"或"Claude 那个"，数据模型与心智模型对齐。**不选的理由**：用户多数时候只用一个默认 Key，"锁 Key"对单 Key 用户无意义，对多 Key 用户也只是"换 Key 后再开新对话"的两步操作，不需要 schema 强约束。
- **B. 每条消息独立选 Key** — 拒绝。同一对话内模型都换会让 AI 难以延续"语气"，重看历史的用户也会困惑。Q2 选了"AI 看得见历史"，B 与 Q2 互斥。
- **C. 不存 Key，每次用 `User.default_key_id`** — **已选**。最简 schema，最灵活。

### 4.1 重新生成的实现
- **A. 只追加不可改** — 拒绝。ChatGPT / Claude 主流事实标准都有"编辑 / 重新生成"，没有会缺体验。
- **B. 可编辑 USER，不可重新生成** — 拒绝。改完用户消息后 AI 不重新跑，"改我自己的话"是空动作。
- **C. 可编辑 + 重新生成（`is_orphaned` 软标记）** — **已选**。主流行为，软标记避免物理删除历史。

### 5.1 输出方式
- **A. 非流式，等完整响应** — 拒绝。流式是 ChatGPT / Claude 主流，第一 token < 1s 出现，长回复不等几十秒。
- **B. 流式无续传** — 不选。断网后用户刷新就丢，体验缺口。
- **C. 流式 + 续传（`message_id` 续传 / 客户端缓存 / server buffer）** — **已选**。续传具体实现留到实施期。

### 6.1 多 tab 同步
- **A. 各 tab 独立，刷新拿最新** — 不选。流式场景下"另一个 tab 看不到我刚发的消息"会让用户困惑。
- **B. `BroadcastChannel` 浏览器原生 API 同步** — **已选**。零依赖，纯客户端，事件列表简单。
- **C. WebSocket / SSE 服务端推** — 不选。多此一举：所有 tab 都是同源客户端，浏览器内通信即可，绕一圈 server 浪费。
- **D. 禁止多 tab 同 conversation（Web Locks API）** — 拒绝。用户工作流里"开两个 tab 同一对话"几乎一定出现过（一边看对话一边找资料），被踢出来会烦。

### 7.1 默认 Key 缺失的处理
- **A. 报错 + UI 引导到 Key 设置** — **已选**。沿用现有约定（CLAUDE.md 4.6 节）。
- **B. 自动 fallback 到第一个 enabled Key** — 拒绝。破坏现有约定，让"现在到底在用哪个 Key"变得不透明。
- **C. 完全静默 / 通用错误** — 拒绝。"用户没配 Key"和"AI 服务挂了"必须区分。

### 8.1 删除语义
- **A. 硬删不可恢复** — 不选。用户误删"恢复"路径必须存在。
- **B. 软删 + 永久保留** — 拒绝。H2 文件会无限增长。
- **C. 软删 + 30 天后自动 hard delete** — **已选**。反悔窗口 + 自动清理兼顾。
- **D. 软删 + 清空消息但保留对话壳** — 拒绝。用户看到空对话壳会困惑"我是不是丢东西了"。

### 9.1 上下文窗口管理
- **A. 暂不处理，AI provider 报错透传** — **已选**。Q2=A 时已说"先做 A，爆了再处理"，现在兑现承诺。
- **B. 服务端截断到最近 N 条** — 拒绝（暂时）。N 写死不合理，按 token 估算需要给每个模型配 context window size，实施成本高。
- **C. 滑动窗口 + 旧消息摘要** — 拒绝（暂时）。需要 AI 调用 + 摘要存储 + 摘要更新策略，复杂度跳一个量级。

## 后果

### 数据模型变更
- 新增 `conversation` 表（9 列），`message` 表（5 列 + 2 约束）。
- `schema.sql` 用 `CREATE TABLE IF NOT EXISTS` + `ALTER TABLE ADD COLUMN IF NOT EXISTS` 兼容旧 H2 文件（沿用 CLAUDE.md 4 节"幂等 DDL"模式）。

### API 变更（破坏性）
- 删 `POST /api/chat`。
- 新增 11 个端点（见设计摘要 §2），全部要求登录态。
- `ChatController` 拆为 `ConversationController` + `MessageController`（不混在同一个 controller）。

### 依赖新增
- 后端：`spring-ai-openai` / `spring-ai-ollama` / `spring-ai-anthropic` 的 `stream()` API（已有依赖，不用新增）。
- 前端：`marked` + `markdown-it` 二选一、`highlight.js`、`KaTeX`、`DOMPurify`。总计 ~400KB min+gz。

### 错误码新增
- 4030（默认 Key 不可用）、4031（对话不存在 / 已删）、4032（消息不存在 / 不属于当前用户）、4033（编辑消息不是 USER）、4034（重新生成不是 ASSISTANT）。

### UI 变更
- `App.vue` 从"四面板单 SFC"演化为"左侧栏 + 主区 + 顶部菜单 / 底部抽屉"。SFC 仍保持单文件，不引入 vue-router。
- 用户 / Key 面板从"独立 tab"变成"modal / drawer"，业务逻辑不变（API 不变）。

### 性能 / 资源
- H2 文件增长：每条对话 + 消息各一行；软删 30 天后清理。正常使用下文件体积可控。
- AI provider 调用频率：每个 USER 消息 → 1 次 AI 调用（流式），无续传路径下断网 = 1 次重发（用户手动）。

### 已知风险
- Q19=D 的图片渲染：AI 几乎不返回图片 URL，v1 图片渲染只服务"用户贴图链接"，不做图片上传。
- Q15=C 续传：实施期定候选三选一；v1 可能退化为"新 AI 调用 + 客户端比对 message_id 跳过已显示"，会浪费一次 AI token。
- Q3=C 假连贯：用户改默认 Key 后，同一对话里前后消息可能来自不同模型 —— 已知接受的代价。

## 状态

🚧 **设计中**（2026-07-27 拍板）。实施期需要重新开一次对话，按本 ADR 落地 schema / entity / service / controller / 前端 / 调度任务，**不**直接基于本会话输出开干。

## 关联

- [[0001-defer-wechat-integration]] — 暂缓的微信聊天通道与本决策正交。
- [[0002-wechat-scan-login]] — 微信扫码登录与本决策正交。
- CLAUDE.md 第 4 节"关键架构约定"将在实施期更新，加入"对话"与"消息"两条新约束。
