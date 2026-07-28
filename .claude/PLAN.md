# PLAN

## 修改日志

- `2026-07-23` — 从传统对话记录切换为 plan 持久化格式（见 [[plan-tracking]] 记忆）。

---

## 第 7 次对话（2026-07-23）— ✅ 已完成（2026-07-23）

### 目标
为 MyAi 添加用户密码登录功能（Spring Security 6 + JWT），保护 Key 管理、聊天、用户管理接口。

### 关键决策
- **Auth 机制**：Spring Security 6 + JWT（jjwt 0.12.6）
- **锁定范围**：只锁定 `/api/users`、`/api/users/{userId}/keys*`、`/api/chat`。`/api/providers` 公开
- **注册策略**：专用 `POST /api/auth/register`，独立于 CRUD `POST /api/users`
- **前端登录**：弹窗/卡片（无路由，无 vue-router）

### 子步骤

| # | 步骤 | 状态 | 关键文件 |
| --- | --- | --- | --- |
| 1 | 加后端依赖 | ✅ 已完成 | `pom.xml`（`spring-boot-starter-security`、jjwt 0.12.6） |
| 2 | schema 迁移 | ✅ 已完成 | `schema.sql`（`ALTER TABLE user ADD COLUMN IF NOT EXISTS password_hash`） |
| 3 | 加 JWT 配置到 yml | ✅ 已完成 | `application.yml`（`my-ai.jwt.secret`、`my-ai.jwt.expiration`） |
| 4 | 扩展 BizException + handler | ✅ 已完成 | `BizException.java`（4010/4030）、`GlobalExceptionHandler.java` |
| 5 | 实现 AuthPrincipal | ✅ 已完成 | 新建 `cn/edgarli/security/AuthPrincipal.java` |
| 6 | 实现 JwtService | ✅ 已完成 | 新建 `cn/edgarli/security/JwtService.java` |
| 7 | 实现 JwtAuthenticationFilter | ✅ 已完成 | 新建 `cn/edgarli/security/JwtAuthenticationFilter.java` |
| 8 | 实现 SecurityConfig + entry points | ✅ 已完成 | 新建 `cn/edgarli/security/SecurityConfig.java` 等 |
| 9 | 实现 AuthService + AuthController + DTO | ✅ 已完成 | 新建 `service/AuthService.java`、`web/AuthController.java`、DTO |
| 10 | 锁定现有接口（userId 校验） | ✅ 已完成 | `UserController.java`、`UserApiKeyController.java`、`ChatController.java` |
| 11 | 前端登录卡片 + JWT 携带 | ✅ 已完成 | `frontend/src/App.vue` |
| 12 | 构建 + 汇编验证 | ✅ 已完成 | `mvn -DskipTests package` |
| 13 | 更新知识文档 | ✅ 已完成 | `.claude/api.md`、`.claude/project_docs.md`、`.claude/REQUIREMENTS.md` |

### 显式不做（本期）
- 邮件验证、找回密码、刷新 token、双因素
- 角色/权限矩阵、管理员
- 旧用户密码迁移策略（升级注释见 REQUIREMENTS.md）
- `mvn test` 测试用例（仍是空套件）

---

## 第 8 次对话（2026-07-23）— ✅ 已完成（2026-07-23）

### 目标
1. 修复 Provider 下拉空：`@ConfigurationProperties` 前缀修正
2. 修复密码写库失败：`passwordHash` 改 insert 前赋值
3. 为 Key 增加协议选择列，支持 OpenAI 兼容 / Anthropic / Ollama

### 子步骤

| # | 步骤 | 状态 |
| --- | --- | --- |
| 1 | 修复 ProviderCatalog 前缀 `my-ai.providers` → `my-ai` | ✅ |
| 2 | AuthService 密码 hash 改 insert 前 set | ✅ |
| 3 | 加 `spring-ai-anthropic`、`ProviderProtocol.ANTHROPIC` | ✅ |
| 4 | `user_api_key` 加 `protocol` 列 + entity/DTO/response | ✅ |
| 5 | `ChatClientFactory` 支持 Anthropic 客户端 | ✅ |
| 6 | 编译修复（import + static 问题） | ✅ |
| 7 | 前端 key form 加协议下拉框 | ✅ |
| 8 | 构建验证 | ✅ |

---

## 第 9 次对话（2026-07-23）— ✅ 已完成（2026-07-28）

### 目标
清理已提交到 Git 历史的敏感数据：`data/myai.mv.db` 从历史中彻底移除，停止跟踪。

### 背景
- `d52e520`（优化）提交中 `data/myai.mv.db` 包含 1 个 MiniMax API Key
- 已推送到 GitHub + Gitee
- `data/` 已在 `.gitignore` 中但文件仍被跟踪

### 子步骤

| # | 步骤 | 状态 |
| --- | --- | --- |
| 1 | `git rm --cached` 停止跟踪 | ✅（2026-07-24，仅本仓库；远端未推，H2 文件本身保留在磁盘） |
| 2 | 清理历史（`git-filter-repo`） | ✅（2026-07-28；13 commit 全部不含 `data/myai.mv.db`；磁盘文件保留） |
| 3 | 风险说明 + 用户授权 | ✅（双推送 + 本地备份到 `D:\MyWork\myAi-backup-2026-07-28\.git`） |
| 4 | 强制推送到 GitHub + Gitee | ✅（2026-07-28；Gitee `git ls-remote origin HEAD` 确认 `37c117e`；GitHub 端按日志 09:34:59 输出确认已写 19 个对象） |
| 5 | 提醒用户轮换 MiniMax API Key | ✅（轮换本身需用户去 MiniMax 控制台 — 已知事项） |

### 目标
清理已提交到 Git 历史的敏感数据：`data/myai.mv.db` 从历史中彻底移除，停止跟踪。

### 背景
- `d52e520`（优化）提交中 `data/myai.mv.db` 包含 1 个 MiniMax API Key
- 已推送到 GitHub + Gitee
- `data/` 已在 `.gitignore` 中但文件仍被跟踪

### 子步骤

| # | 步骤 | 状态 |
| --- | --- | --- |
| 1 | `git rm --cached` 停止跟踪 | ✅（2026-07-24，仅本仓库；远端未推，H2 文件本身保留在磁盘） |
| 2 | 清理历史（`git-filter-repo`） | ✅（2026-07-28；13 commit 全部不含 `data/myai.mv.db`；磁盘文件保留） |
| 3 | 风险说明 + 用户授权 | ✅（双推送 + 本地备份到 `D:\MyWork\myAi-backup-2026-07-28\.git`） |
| 4 | 强制推送到 GitHub + Gitee | ✅（2026-07-28 用户在 IDE 中执行；Gitee `git ls-remote origin HEAD` 确认 `37c117e`；GitHub 端按日志 09:34:59 输出"refs/heads/master:refs/heads/master    df73833..37c117e" + "Done" 确认已写 19 个对象；09:40:01 + 09:40:48 的 `--set-upstream` 重试被 Gitee 拒绝，原因是远端已存在相同 `37c117e`，非数据丢失） |
| 5 | 提醒用户轮换 MiniMax API Key | ✅（见交付说明；轮换本身需用户去 MiniMax 控制台） |

### 留给用户做的事
1. GitHub 网页 Settings → Branches → master 解除保护（取消 "Require linear history" 或允许 force pushes）
2. Gitee 网页 Settings → 分支管理 → master 关闭保护
3. 在本仓库执行：
   ```bash
   git push origin master --force-with-lease    # Gitee + GitHub
   git push github master --force-with-lease    # GitHub 独立保险
   ```
4. 去 MiniMax 控制台撤销 / 轮换被泄露的 API Key
5. 其他机器上的本地 clone 需 `git fetch && git reset --hard origin/master`（未推送的本地提交会丢）

---

## 第 10 次对话（2026-07-24）— ✅ 已完成（2026-07-24）

### 目标
1. 重写 `.claude/CLAUDE.md`，补全事实（Anthropic 协议、`application-my.yml`、JWT 认证），精简与 `~/.claude/CLAUDE.md` 重复的开发实践章节。
2. 用 `/grill-with-docs` 评估"引入微信"需求并落 ADR。

### 关键决策
- **CLAUDE.md 结构**：删去与用户级重复的"沟通方式/修改原则/仓库安全/验证原则"展开，仅顶部引用 + 第 7 节"仓库特有验证补充"。
- **ProviderProtocol 表述**：补 `ANTHROPIC` 第三分支；`user_api_key.protocol` 列为可选覆盖。
- **微信需求**：用户表达"在群里 @机器人" → 调研发现企业微信自建应用回调 API **不支持**群聊 @bot → 仅"会话内容存档"或第三方 SCRM 能做到 → 个人开发者画像下不可行 → **决定暂不开发**。

### 子步骤

| # | 步骤 | 状态 | 关键文件 |
| --- | --- | --- | --- |
| 1 | 重写 `.claude/CLAUDE.md` | ✅ | `.claude/CLAUDE.md`（约 9 KB，13 节精简） |
| 2 | 装 grilling + domain-modeling skill | ✅ | `~/.agents/skills/`（symlinked） |
| 3 | 调研企业微信群 @ 机器人能力 | ✅ | 引用 3 条官方文档 |
| 4 | 写 ADR 0001 中文版 | ✅ | `docs/adr/0001-defer-wechat-integration.md` |
| 5 | 写 ADR 0001 英文版 | ✅ | `docs/adr/0001-defer-wechat-integration.en.md` |
| 6 | CLAUDE.md 第 10 节补 ADR 位置 + 命名约定 | ✅ | `.claude/CLAUDE.md` |

### 显式不做（本期）
- 不引入任何微信 SDK；`pom.xml` 不变。
- 不写 `CONTEXT.md`：本次未产出 MyAi 项目独有的领域术语（"微信机器人"是外部平台能力，不是 MyAi 术语）。

---

## 第 11 次对话（2026-07-24）— ✅ 已完成（2026-07-24）

### 目标
用 `/grill-me` 拷问「微信管理登录」需求，澄清为「微信扫码登录 MyAi Web 管理界面」，产出设计决策并落 ADR 0002；本轮只写设计文档，不写业务代码。

### 关键决策（grilling 确认）
1. **场景**：微信扫码登录本应用 Web 界面（与 ADR 0001 的聊天通道决策无关）。
2. **前提挂账**：开放平台网站应用登录需组织资质认证（个人不可办，300 元/年）+ ICP 备案域名；穿透调试域名无法备案，落地阶段需复核。
3. **登录关系**：密码登录与微信扫码并存，不迁移老用户。
4. **首次扫码**：未绑定微信 → 自动注册（name=微信昵称兜底，email/password_hash 为 NULL）。
5. **存储**：`user` 表加列 `wechat_open_id`（唯一）、`wechat_union_id`；不建独立身份表。
6. **部署形态**：保持本地单机，开发期穿透调试回调；公网部署留到落地。
7. **扫码形态**：登录页内嵌二维码（官方 JS SDK）。
8. **回调流程**：redirect_uri 指向后端回调接口，后端 code 换 token 后 302 回前端，token 走 URL fragment；state 一次性强校验。
9. **老账号绑定**：已登录状态下设置页扫码绑定（独立 state 标记）。
10. **配置**：`application.yml` 新增 `my-ai.wechat` 段，AppSecret 走环境变量；enabled 默认 false。

### 子步骤

| # | 步骤 | 状态 | 关键文件 |
| --- | --- | --- | --- |
| 1 | grilling 拷问，确认 10 项决策 | ✅ | 本会话 |
| 2 | 写 ADR 0002 中文版 | ✅ | `docs/adr/0002-wechat-scan-login.md` |
| 3 | 写 ADR 0002 英文版 | ✅ | `docs/adr/0002-wechat-scan-login.en.md` |
| 4 | 0001 中英文版加交叉引用 | ✅ | `docs/adr/0001-*` |
| 5 | 刷新 `.claude` 知识文档 | ✅ | `REQUIREMENTS.md`（1.0.1 + 变更追踪）、`api.md`（规划注记） |
| 6 | PLAN 收尾标记 | ✅ | 本文件 |

### 显式不做（本期）
- 不写任何业务代码（后端回调接口、前端二维码、schema 迁移均留待实现期）。
- 不申请微信资质、不配置穿透域名。

---

## 第 12 次对话（2026-07-27）— ✅ 已完成（2026-07-27）

### 目标
用 `/grill-with-docs` 拷问「记录聊天记录 + 新开不同聊天窗口」需求，落到数据模型、API、UI、共识记录 4 份产出，**本轮只写设计文档，不写业务代码**。

### 关键决策（grilling 19 题确认）

| # | 决策 | 落地形式 |
| --- | --- | --- |
| Q1=A | ChatGPT 式侧栏 | App.vue 左侧栏 UI |
| Q2=A | AI 看得见同对话内全部历史 | 调 AI 前 fetch `is_orphaned=FALSE` 消息拼 context |
| Q3=C | 不绑 Key | 不存 `key_id`，每次用 `User.default_key_id` |
| Q4=A | 可编辑 + 重新生成 | `is_orphaned` flag |
| Q5=D | AI 自动起标题 + 可编辑 | `title_manually_set` flag |
| Q6=B | 软删 | `deleted_at` 列 + restore 端点 |
| Q7=C | 记忆上次激活 | localStorage `last_active_conversation_id` |
| Q8=B | localStorage | 不监听 storage event |
| Q9=A | 默认 Key 缺失报错 | 4030 + UI 引导 |
| Q10=A | 已删对话折叠区 | 侧栏底部 |
| Q11=A+D | 透传错误 + 触发条件 | 触发 = 用户主动反馈"对话过长" |
| Q12=A | `updated_at DESC` | message 新增时 update conversation |
| Q13=B | SYSTEM 角色 | `message.role` 含 `SYSTEM` |
| Q14=B | 30 天后 hard delete | `@Scheduled` + `my-ai.trash.retention-days=30` |
| Q15=C | 流式 + 续传 | SSE，续传实现实施时定 |
| Q16=A | 直接替换 `/api/chat` | 删旧端点 |
| Q17=A | 侧栏顶部用户下拉 | App.vue 布局 |
| Q18=B | 多 tab 实时同步 | `BroadcastChannel` |
| Q19=D | Markdown + 高亮 + 公式 + 图片 | `marked` + `highlight.js` + `KaTeX` + `DOMPurify` |

### 子步骤

| # | 步骤 | 状态 | 关键文件 |
| --- | --- | --- | --- |
| 1 | grilling 拷问 19 题，确认全部决策 | ✅ | 本会话 |
| 2 | 写设计摘要（含数据模型 / API / UI / 共识 / 待办） | ✅ | 本会话输出 |
| 3 | 写 ADR 0003 中文版 | ✅ | `docs/adr/0003-conversations-and-messages.md` |
| 4 | 写 ADR 0003 英文版 | ✅ | `docs/adr/0003-conversations-and-messages.en.md` |
| 5 | PLAN 收尾标记 | ✅ | 本文件 |
| 6 | 更新 `.claude/api.md`（加入新端点） | ✅ | `.claude/api.md` |
| 7 | 更新 `.claude/REQUIREMENTS.md`（加入新需求） | ✅ | `.claude/REQUIREMENTS.md` |

### 显式不做（本期）
- 不写任何业务代码（schema 迁移、entity、service、controller、App.vue、BroadcastChannel、Markdown 渲染、`@Scheduled` 等均留待实现期）。
- 不实施 Q15=C 续传细节（实施时定候选三选一：server buffer / 客户端缓存 / 新 AI 调用跳过已显示）。
- 不暴露 SYSTEM 消息的 UI 输入（v1 只在 message.role 列预留，业务上后端不主动注入）。
- 不实现 Q19=D 的图片上传（v1 图片渲染只为"用户贴图链接"服务）。

---

## 第 13 次对话（2026-07-27）— ✅ 已完成（2026-07-27）

### 目标
实施 ADR 0003「对话与消息」：schema + entity + service + controller + 前端 + 调度任务。落 11 个新端点、保留 `/api/chat` 为 deprecated alias、4030 业务码冲突用 4035 解决。

### 关键决策
- **业务码冲突解决**：4030（FORBIDDEN）保留不动；新增 4035 = `DEFAULT_KEY_UNAVAILABLE` 给"默认 Key 不可用"；4031/4032/4033/4034 用于对话/消息相关错误。**与 ADR §7.3 不一致**，需同步修订 ADR 与 `api.md`。
- **`/api/chat` 不删**：保留为 deprecated alias，内部转发到 `MessageService`，响应头加 `Deprecation: true`。**与 ADR §API 变更"删 POST /api/chat"不一致**，需修订 ADR。
- **PATCH edit 语义**：仅标 `is_orphaned = TRUE`，**不**自动重跑 AI；AI 重跑需用户主动触发（发送 / 重新生成）。**与 ADR §4 第二句不一致**，需加 ADR 补丁。
- **标题生成 v1**：截断首条 USER 消息到 30 字（带 `…`），仅当 `title_manually_set=FALSE` 时覆盖。**与 PLAN §12 Q5=D 偏离**，标记为 follow-up。
- **前端 Markdown 栈**：`markdown-it` + `markdown-it-highlightjs` + `markdown-it-katex` + `dompurify`（用户从"marked vs markdown-it"选项中选了 markdown-it）。
- **SSE 拆短事务**：`streamReply` 4 个事务边界（insertUserMessage / touchConversation / AI 调用 / insertAssistantMessage），5 分钟 `SseEmitter` 超时。
- **续传 v1 不实现**：断网重连走 fallback（GET 已落库消息 + UI 重发按钮）。

### 子步骤

| # | 阶段 | 状态 | 关键文件 |
| --- | --- | --- | --- |
| 1 | 更新 PLAN.md（本表） | ✅ | `.claude/PLAN.md` |
| 2 | 阶段 0 地基 | ✅ | `BizException.java`、`MyAiApplication.java`、`application.yml`、`schema.sql`、`UserApiKeyService.java` |
| 3 | 阶段 1 实体 + Mapper | ✅ | `Conversation.java`、`Message.java`、`ConversationMapper.java`、`MessageMapper.java` |
| 4 | 阶段 2 Service + DTO | ✅ | `ConversationService.java`、`MessageService.java`、4 个 DTO |
| 5 | 阶段 3 Controller + ChatController alias | ✅ | `ConversationController.java`、`MessageController.java`、`ChatController.java` |
| 6 | 阶段 4 @Scheduled 任务 | ✅ | `ConversationCleanupTask.java`、`TrashProperties.java` |
| 7 | 阶段 5 前端 | ✅ | `App.vue` 重写、`lib/markdown.js`、`lib/sse.js`、`vite.config.js`、`package.json` |
| 8 | 阶段 6 文档收尾 | ✅ | `api.md`、`REQUIREMENTS.md`、`CLAUDE.md` §4、ADR 0003 (.md + .en.md)、`PLAN.md` |
| 9 | 阶段 7 验证 | ✅ | `mvn -DskipTests package` 通过；`cd frontend && npm run build` 通过（1.4MB minified / 483KB gzip）；JAR 117MB 落地到 `target/`；schema.sql 含 `conversation` + `message` 两表 + `message_role_check` CHECK 约束。**运行时烟测跳过**——用户 8032 实例正占用 H2 文件，taskkill 被权限拦截，按用户选择以编译为唯一验证依据。 |

### follow-up 收尾（2026-07-28）

实施期发现与 ADR 原文有 3 处偏离，加上 v1 标题用截断降级、CLAUDE.md token
持久化表述与代码不一致，已在本轮补齐：

| # | 偏离 / follow-up | 处理 |
| --- | --- | --- |
| 1 | ADR §7 错误码：原文写"默认 Key 不可用 → 4030"，实际用 4035 | 修订 ADR 中英文版 §7 + §后果，并补 4035 = `DEFAULT_KEY_UNAVAILABLE` 与 4030 FORBIDDEN 不重叠的理由 |
| 2 | ADR §4 PATCH edit：原文写"AI 用新内容重新跑"，实际只标 orphan | 修订 ADR 中英文版 §4 + §后果，明确"不自动重跑 AI；用户需主动发送新消息或点重新生成" |
| 3 | `/api/chat` 保留为 deprecated alias（与 ADR"删旧端点"偏离） | ADR 中英文版 §后果 API 变更段保留原文 + 现状说明，下架时间未定 |
| 4 | v1 自动标题 = 截断降级（偏离 Q5=D） | 实现 v2：`MessageService.maybeAutoTitle` + `generateAiTitle`，异步（`CompletableFuture.runAsync`）从 SSE done 回调启动；AI 调用失败回退 truncate；不动 `title_manually_set` flag |
| 5 | CLAUDE.md §5 / §6 token 持久化表述与代码不一致（App.vue 用 `localStorage` 存 token + active conversation id） | 修订 §5 与 §6，明确键 `myai.token` / `myai.activeConversationId`、4010 / 登出时清除 |
| 6 | api.md §0.5 错误码表 4090 描述里写了"用户没有可用默认 Key"，已迁到 4035 | 同步 api.md：在 §0.5 表加 4035 行；4090 行加迁移说明 |

验证：`mvn -DskipTests clean compile` 通过（50 源文件），未运行端到端
HTTP 烟测（与第 13 次原阶段 7 同约束）。

### 显式不做（v1 / 永不做）
- 续传实现
- 图片上传
- SYSTEM 消息 UI 输入
- `/api/chat` 下架（保留为 alias；下一轮移除）

---

## 第 14 次对话（2026-07-27）— ✅ 已完成（2026-07-28）

### 目标
实施 ADR 0004「可观测性与日志」：4 层日志（AI 调用 + HTTP 访问 + 业务审计 + 系统运行）+ RBAC + 查询 API。ADR 定稿见 `docs/adr/0004-observability.md`。

### 关键决策（/grill-with-docs 20 题，选 D 全做）
| # | 决策 | 选 |
| --- | --- | --- |
| Q3 | 存储 | A. 同 H2 文件，`ai_call_log` + `audit_log` 两表 |
| Q4 | 访问日志 | A. `./logs/access.jsonl`（RollingFileAppender） |
| Q7 | 审计触发 | B. AOP `@Auditable` + `@Around` |
| Q8 | 日志格式 | A. 全 JSON（logback JsonEncoder） |
| Q9 | MDC | C. 全套 6 字段 + 响应头 `X-Trace-Id` |
| Q10 | HTTP 过滤器 | C. Servlet Filter + FilterRegistrationBean（Security 之前） |
| Q11 | 保留期 | C. `my-ai.logs.retention-days`（默认 30） |
| Q12 | 查询 API | C. admin-only（`user.role` + `/api/logs/**` hasRole ADMIN） |
| Q13 | Token 计数 | B. `stream().chatResponse()` + `metadata.getUsage()`（token nullable） |
| Q14 | admin | B. env var `MYAI_ADMIN_EMAILS` 匹配（无 fallback） |
| Q15 | AOP targetId | B. 反射取返回值 `.getId()` / `id()`（record DTO） |
| Q16 | SSE trace_id | B. 仅响应头，事件数据不带 |
| Q17 | logback | A. 新建 `logback-spring.xml` |
| Q18 | 路径过滤 | A. 白名单 `/api/**` |
| Q19 | audit 删除 | B. 软删 `deleted_at` + 30 天后硬删 |

### 验证过的 API 事实
- `ChatClient.stream()` → `StreamResponseSpec`，有 `chatResponse()` → `Flux<ChatResponse>` ✅
- `ChatResponse.getMetadata().getUsage()` → `Usage`（nullable）；`getPromptTokens()`/`getCompletionTokens()` → `Integer` ✅
- Logback 1.5+ 内置 `ch.qos.logback.classic.encoder.JsonEncoder`（零额外依赖）✅
- `SecurityFilterProperties` 在 Boot 4 `spring-boot-security` jar ✅
- **`spring-boot-starter-aop` 在 Spring Boot 4.0 已移除**，需直接依赖 `org.aspectj:aspectjweaver`（BOM 仍管版本）—— 与 PLAN 验证事实不符；改用 aspectjweaver 即可

### 子步骤

| # | 阶段 | 状态 | 关键文件 / commit |
| --- | --- | --- | --- |
| 0 | PLAN.md | ✅ | `.claude/PLAN.md`（本表） |
| 1 | 地基 | ✅ | `pom.xml`（aspectjweaver 替 aop starter）、`application.yml`（`my-ai.logs.*` / `my-ai.admin.emails`）、`application-my.yml`、`schema.sql`（user.role + ai_call_log + audit_log）、`User.java`（role）、`AuthPrincipal.java`（role + authorities）、`JwtService.java`（role claim）、`AuthService.java`（admin 判定）、`SecurityConfig.java`（`/api/logs/**` admin）、`.gitignore`（`logs/`）、`AdminProperties.java`、`LogProperties.java`、`MyAiApplication.java`（`@EnableConfigurationProperties`）。commit `42c0263` |
| 2 | logback + TraceIdFilter | ✅ | `logback-spring.xml`（全 JSON + access/app 三 appender）、`TraceIdFilter.java`、`FilterConfig.java`（HIGHEST_PRECEDENCE+10）。commit `fd879b8` |
| 3 | ai_call_log 全栈 | ✅ | `AiCallLog.java`、`AiCallLogMapper.java`、`AiCallLogService.java`（recordSuccess / recordFailure）。commit `7c0d1a8` |
| 4 | audit_log + AOP | ✅ | `AuditLog.java`、`AuditLogMapper.java`、`@Auditable` 注解、`AuditAspect.java`。commit `bf0129d` |
| 5 | MessageService 改造 | ✅ | `MessageService.streamReply / regenerate`：改 `stream().chatResponse()`；`extractTokenText` helper；从 `ChatResponse.getMetadata().getUsage()` 拿 tokens；onComplete/onError 调 `AiCallLogService.recordSuccess/recordFailure`。commit `9f5f956` |
| 6 | LogCleanup + 查询 API | ✅ | `LogCleanupTask.java`（cron `0 4 * * * *` Asia/Shanghai，ai_call_log 直接物理删、audit_log 先软删再物理删）、`LogsController.java`（4 端点，admin-only，size 上限 200）。commit `9238b2d` |
| 7 | 审计标注 | ✅ | `UserApiKeyService` 4 方法 + `ConversationService` 5 方法加 `@Auditable`；`AuditAspect` 增加 `id()` record 反射兼容。commit `819c2e2` |
| 8 | 文档收尾 | ✅ | `api.md` §6（删重复的规划段）、`CLAUDE.md` §4 第 16-17 条 + §6 管理员边界、`REQUIREMENTS.md` 1.9（已实现）+ §3 已知缺口调整 + §4 变更追踪。commit `c49911e` |
| 9 | 验证 | ✅ | `mvn -DskipTests package` BUILD SUCCESS；JAR 已落地 `target/myAi-1.0-SNAPSHOT.jar`。commit `c49911e` |

### 验证

- `mvn -DskipTests package`：BUILD SUCCESS（aspectjweaver + 14 新文件 + 11 修改文件）
- 运行时烟测：未运行（同第 13 次约束：用户 8032 实例可能占用 H2 文件；且 admin bootstrap 需要先在 yml / env 配 `MYAI_ADMIN_EMAILS` 才能调 `/api/logs/**`）。
- Spring Boot 4 `spring-boot-starter-aop` 已移除是 PLAN 验证事实的修正：原本说"必须加 aop starter"，实际上应该直接用 `org.aspectj:aspectjweaver`（BOM 管版本）。

### 目标
实施 ADR 0004「可观测性与日志」：4 层日志（AI 调用 + HTTP 访问 + 业务审计 + 系统运行）+ RBAC + 查询 API。ADR 定稿见 `docs/adr/0004-observability.md`。

### 关键决策（/grill-with-docs 20 题，选 D 全做）
| # | 决策 | 选 |
| --- | --- | --- |
| Q3 | 存储 | A. 同 H2 文件，`ai_call_log` + `audit_log` 两表 |
| Q4 | 访问日志 | A. `./logs/access.jsonl`（RollingFileAppender） |
| Q7 | 审计触发 | B. AOP `@Auditable` + `@Around` |
| Q8 | 日志格式 | A. 全 JSON（logback JsonEncoder） |
| Q9 | MDC | C. 全套 6 字段 + 响应头 `X-Trace-Id` |
| Q10 | HTTP 过滤器 | C. Servlet Filter + FilterRegistrationBean（Security 之前） |
| Q11 | 保留期 | C. `my-ai.logs.retention-days`（默认 30） |
| Q12 | 查询 API | C. admin-only（`user.role` + `/api/logs/**` hasRole ADMIN） |
| Q13 | Token 计数 | B. `stream().chatResponse()` + `metadata.getUsage()`（token nullable） |
| Q14 | admin | B. env var `MYAI_ADMIN_EMAILS` 匹配（无 fallback） |
| Q15 | AOP targetId | B. 反射取返回值 `.getId()` / `id()`（record DTO） |
| Q16 | SSE trace_id | B. 仅响应头，事件数据不带 |
| Q17 | logback | A. 新建 `logback-spring.xml` |
| Q18 | 路径过滤 | A. 白名单 `/api/**` |
| Q19 | audit 删除 | B. 软删 `deleted_at` + 30 天后硬删 |

### 验证过的 API 事实
- `ChatClient.stream()` → `StreamResponseSpec`，有 `chatResponse()` → `Flux<ChatResponse>` ✅
- `ChatResponse.getMetadata().getUsage()` → `Usage`（nullable）；`getPromptTokens()`/`getCompletionTokens()` → `Integer` ✅
- Logback 1.5+ 内置 `ch.qos.logback.classic.encoder.JsonEncoder`（零额外依赖）✅
- `SecurityFilterProperties` 在 Boot 4 `spring-boot-security` jar ✅
- `spring-boot-starter-aop` **必须**加到 pom（`spring-aop` 传递了但 `org.aspectj:aspectjweaver` 不在 classpath）

### 子步骤

| # | 阶段 | 状态 | 关键文件 |
| --- | --- | --- | --- |
| 0 | PLAN.md | ✅ | `.claude/PLAN.md` |
| 1 | 地基 | 🚧 | pom.xml（加 aop）、application.yml（logs + admin）、application-my.yml（同步）、schema.sql（3 新表 + user.role）、User.java（role）、AuthPrincipal.java（role）、JwtService.java（role claim）、AuthService.java（admin emails 判定）、SecurityConfig.java（/api/logs/** admin）、.gitignore（logs/）、AdminProperties.java、LogProperties.java |
| 2 | logback + TraceIdFilter | ⏳ | logback-spring.xml、TraceIdFilter.java、FilterConfig.java、JwtAuthenticationFilter（MDC user_id） |
| 3 | ai_call_log 全栈 | ⏳ | AiCallLog.java、AiCallLogMapper.java、AiCallLogService.java |
| 4 | audit_log + AOP | ⏳ | AuditLog.java、AuditLogMapper.java、@Auditable.java、AuditAspect.java |
| 5 | MessageService 改造 | ⏳ | MessageService.java（chatResponse + token + ai_call 写入 + MDC 传播） |
| 6 | LogCleanup + 查询 API | ⏳ | LogCleanupTask.java、LogsController.java、AiCallLogResponse.java、AuditLogResponse.java |
| 7 | 审计标注 | ⏳ | UserApiKeyService.java（4 个方法加 @Auditable）、ConversationService.java（5 个方法） |
| 8 | 文档 | ✅ | api.md（§6）、CLAUDE.md（§4 + §6）、REQUIREMENTS.md（1.9） |
| 9 | 验证 | ✅ | mvn -DskipTests package 通过（5.4s，BUILD SUCCESS），JAR 落地 target/ |

### 已知事实修正
- PLAN 验证事实里写的"`spring-boot-starter-aop` **必须**加到 pom"与 Spring Boot 4 实际不符——该 starter 在 Spring Boot 4.0 已移除；正确做法是直接依赖 `org.aspectj:aspectjweaver`（BOM 管版本）。已在实施期修正。

---

## 第 15 次对话（2026-07-28）— 🚧 设计定稿（ADR 已写，待用户通知执行）

### 目标
把仓库从"几乎三层但有几处偏离"演进为**严格教科书式三层架构**，并落地"XML mapper + 全仓双语注释"两项补充要求。本轮**只写设计文档**，不写业务代码。

### 背景
- 用户原始诉求："去除 jpa,采用 mybatis-flex,改为 传统三层架构模式"
- 事实：仓库已在用 MyBatis-Flex 1.11.8，零 JPA 痕迹；`FlexConfig` 已预留 XML mapper 扫描路径（`classpath:cn/edgarli/mapper/**/*.xml`），但目录为空
- 用户后续补充：
  - "有 service 但是没有 实现类"（Service 应接口与实现分离）
  - "注释需要针对到函数，参数，局部参数"（覆盖全仓）
  - "mapper.xml 也要添加"（重构中引入 XML）
  - "注释语言改为中英文吧"（每行完全双语，中文 + 英文同行）

### 关键决策（/grill-with-docs 12 决策全落定）
| # | 决策 | 选 |
| --- | --- | --- |
| 1 | ORM | **不动**（MyBatis-Flex 1.11.8） |
| 2 | Service 命名 | 同名接口 + Impl 后缀（`UserService` + `UserServiceImpl`） |
| 3 | MessageService 拆 | **Query + Command + 组合接口**（Controller 注入 1 个组合接口） |
| 4 | AI 子包归宿 | **`cn.edgarli.service.ai`**（AiService 接口 + 实现） |
| 5 | 基础设施统一 | `cn.edgarli.infrastructure.{security,config,task,observability,audit}` |
| 6 | Entity / DTO 分层 | **DO / BO / VO 三层** |
| 7 | DO 命名 | **加 Do 后缀**（`UserDo` 等 6 个） |
| 8 | 引入 XML mapper | 是；范围 = Conversation + Message 业务查询全迁 XML |
| 9 | 注释粒度 | 方法 Javadoc + @param + 局部变量 // |
| 10 | 注释覆盖 | 全仓所有 Java 源文件 + 2 个 XML |
| 11 | 注释语言 | **每行完全双语**（中文 + 英文同行） |
| 12 | 转换层 | `cn.edgarli.web.converter`（手动 converter，不引 MapStruct） |

### 落地产物
- `docs/adr/0005-three-layer-architecture.md`（中文）
- `docs/adr/0005-three-layer-architecture.en.md`（英文）
- 本 PLAN.md 第 15 次对话段

### 子步骤

| # | 阶段 | 状态 |
| --- | --- | --- |
| 0 | ADR 0005 中英文版 | ✅ |
| 1 | PLAN.md（本表） | ✅ |
| 2 | 阶段 A：基础设施搬迁（security/config/task/observability/audit → `infrastructure`） | ⏳ 待用户通知 |
| 3 | 阶段 B：Service 接口/Impl 分离 + AI 子包重构（拆 MessageService + 引入 AiService） | ⏳ |
| 4 | 阶段 C：DO/BO/VO 分层（6 entity → Do 后缀；补 VO；补 converter；补 BO） | ⏳ |
| 5 | 阶段 D：Conversation + Message XML mapper 迁移（2 个 XML） | ⏳ |
| 6 | 阶段 E：全仓注释补齐（方法 Javadoc + @param + 局部 //，双语） | ⏳ |

### 显式不做
- 不引入 MapStruct（手动 converter 够用）
- 不做 ORM 切换（已是 MyBatis-Flex）
- 不动 DB schema / API 路径 / 业务码
- 不重写 service 业务逻辑（仅接口 / 实现分离）

### 留给用户
- 阶段 A-E 何时启动（用户明确说"先写到文档里面，后续执行等我通知"）
