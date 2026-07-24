# Defer WeChat Integration

> Related decision: [0002](0002-wechat-scan-login.md) (WeChat scan-to-login) is an independent authentication decision — this document defers the *chat channel*, not *login*. The consequence below that "the `User` model has no WeChat `openId` mapping" holds only within this decision's scope; 0002 designs `openId` columns on `user` for login.

We considered introducing WeChat as a chat channel for MyAi and decided **not to develop it now** (2026-07-24). Investigation showed the most natural interpretation — "在企业微信群里 @ 机器人" — is not supported by the official self-built-app callback API; the only official paths that can read group messages require paid "会话内容存档" qualification, and the alternative 公众号 path is single-chat only with no group support. We prioritize compliance and individual-developer feasibility over feature breadth.

> Chinese version: `0001-defer-wechat-integration.md`.

## Considered Options

- **A. 企业微信自建应用（群聊 @ 机器人）** — rejected. Official callback cannot receive group @bot messages; only "会话内容存档" can read groups.
- **A.1 企业微信自建应用（单聊）** — possible and official, but loses the "group @bot" affordance. Deferred, not rejected.
- **A.2 会话内容存档 SDK** — official, can read group history, requires paid enterprise qualification. Not feasible for current maintainer profile.
- **A.3 第三方 SCRM 中转（语鹦企服、微丰等）** — works for group @bot but adds vendor + paid plan + external trust boundary. Not pursued.
- **B. 公众号（订阅号/服务号）** — official, but no group chat; would change the use case to single-user AI 客服. Out of scope for the original "group @bot" intent.
- **C. 个人微信 hook（web/iPad 协议）** — non-official, violates WeChat ToS. Rejected for compliance reasons (overlaps MyAi 当前安全边界).

## Consequences

- MyAi remains a single-channel app: only the web frontend talks to it.
- The `User` domain model is unchanged — no WeChat `openId` mapping, no group identity, no per-group session.
- Any future revisit must re-validate 企业微信 API surface; capabilities in this space were evolving during 2026 (e.g., "大圆" Agent internal testing). The decision above is anchored to facts verified on 2026-07-24.

## References (verified 2026-07-24)

- [企业微信开发者社区 — 自建应用是否可以接收群聊中的消息](https://developer.work.weixin.qq.com/community/question/detail?content_id=16711991662070117453)
- [企业微信开发者中心 — 会话内容存档](https://developer.work.weixin.qq.com/document/path/91774)
- [企业微信外部群机器人接入 AI 实战](https://cloud.tencent.com/developer/article/2706699)