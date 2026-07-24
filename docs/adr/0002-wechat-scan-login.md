# 微信扫码登录（设计决策）

MyAi 的 Web 管理界面将新增**微信扫码登录**，与现有「邮箱 + 密码」登录**并存**（2026-07-24 设计定稿）。这是身份认证决策，与 [[0001-defer-wechat-integration]] 暂缓的「微信聊天通道」无关——0001 决定的是「机器人从哪里收发消息」，本文决定的是「用户怎么登录」。本轮只落设计，不写业务代码；落地前提是办好微信开放平台资质（见「前提条件」）。

> English version: `0002-wechat-scan-login.en.md`。

## 前提条件（落地前必须满足）

- **组织资质认证**：开放平台「网站应用微信登录」仅对组织主体（企业/个体工商户等）开放，个人主体无法认证；认证费 300 元/年（2026-07 核实）。
- **ICP 备案域名**：授权回调域必须是已备案域名。本项目当前定位是本地单机（8031），开发期拟用内网穿透调试，但**穿透域名无法备案**，微信侧可能直接拒绝——落地阶段必须复核，最坏情况需要公网部署。
- **AppSecret 管理**：走环境变量注入，不进仓库；`application.yml` 新增 `my-ai.wechat` 段（`app-id` / `redirect-uri` / `enabled`，`enabled` 默认 `false`），未启用时前端不展示扫码入口。

## 核心流程

1. 登录页内嵌微信二维码（开放平台官方 JS SDK，不整页跳转）。
2. 用户扫码授权后，微信 302 携带 `code` + `state` 到**后端回调接口**（`redirect_uri` 指向后端）。
3. 后端校验一次性 `state`（防 CSRF），用 `code` + AppSecret 换取 `access_token` / `openId` / `unionId`。
4. 按 `unionId`（优先）/ `openId` 查 `user` 表：
   - 已绑定 → 直接签发 JWT；
   - 未绑定 → **自动注册**新用户（`name` = 微信昵称兜底，`email` / `password_hash` 为 `NULL`——现有 `AuthService.login` 对 NULL 密码已有兜底，该用户天然无法密码登录）。
5. 后端 302 跳回前端，JWT 放在 **URL fragment**（`#token=...`），不进服务端日志。

## 关键决策

- **并存而非替代**：密码登录保留，微信扫码是新增身份源；不做老用户强制迁移。
- **自动注册**：未绑定的微信首次扫码即建号，不做「先绑后扫」或白名单（体验优先；本应用是多用户系统，此行为与 `/api/auth/register` 语义一致）。
- **`user` 表加列**：新增 `wechat_open_id`（唯一）、`wechat_union_id`，幂等 `ALTER TABLE` 进 `schema.sql`；不建独立身份表（当前只有一个外部身份源，不为未来需求加抽象）。
- **老账号绑定**：已登录用户在设置页扫码绑定（绑定走独立 state 标记，回调识别后把 openId 写入当前账号，而非自动注册）。
- **state / code 安全**：state 后端签发、一次性、强校验；code 由微信保证一次性，后端不缓存不重放。

## 考虑过的选项

- **整页跳转授权** — 拒绝。实现更简单但打断页面状态；内嵌二维码体验更好且是主流做法。
- **redirect_uri 指向前端路由，前端拿 code 调后端换 token** — 拒绝。与 `/api/auth/login` 对称、token 不进 URL 是其优点，但用户选择了后端回调直发 token（少一次往返）；用 fragment 传 token 缓解其 URL 泄露风险。
- **独立 `user_identity` 身份表** — 拒绝。当前只有微信一个身份源，`user` 加列足够；未来若引入第二个外部身份源再迁移。
- **扫码后引导绑定已有账号（推翻自动注册）** — 拒绝。多一步密码输入，新用户体验差；绑定需求由「登录态下设置页扫码」覆盖。

## 后果

- `user` 表结构变更：`+ wechat_open_id`（唯一索引）、`+ wechat_union_id`。
- 新增公开端点（设计）：二维码 state 签发、微信回调（GET，302）、登录态绑定回调；`SecurityConfig` 白名单需相应放行回调路径。
- 出现无密码、无邮箱的用户类别；任何依赖 email 非空的逻辑（如未来的找回密码）需处理该类别。
- 安全边界松动：为调试微信回调，本地实例需经穿透暴露一个回调路径到公网；落地公网部署时需重新评估 JWT 存储、Token 刷新等（当前 Token 仅存会话内存）。
- 不引入微信 SDK：OAuth 交互是两次 HTTPS 调用（code 换 token、拉 userinfo），用现有 HTTP 客户端即可。

## 参考（2026-07 核实）

- [JustAuth — 微信开放平台登录指南](https://www.justauth.cn/guide/oauth/wechat_open/)
- [微信开放平台官方文档](https://developers.weixin.qq.com/doc/oplatform/en/Third-party_Platforms/2.0/operation/open/create.html)
- [微信登录之开发者资质申请指南](https://blog.lusyoe.com/article/wechat-login-developer-qualification-guide.html)
