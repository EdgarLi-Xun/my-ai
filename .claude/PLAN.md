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
