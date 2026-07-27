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

## 第 9 次对话（2026-07-23）— 🚧 进行中

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
| 2 | 清理历史（filter-branch 或 filter-repo） | ⏳ 待用户授权 |
| 3 | 向用户说明强制推送风险并确认 | ⏳ |
| 4 | 强制推送到 GitHub + Gitee | ⏳ |
| 5 | 提醒用户轮换 MiniMax API Key | ⏳ |

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

