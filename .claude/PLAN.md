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
