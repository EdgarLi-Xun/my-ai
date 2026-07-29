# uni-app App 端架构 + 可配置后端地址（uni-app App Architecture with Configurable Backend Address）

MyAi 在第 17 次 `/grill-with-docs` 会话（2026-07-29）定稿：把现有 Vue 3 单页 Web 前端**镜像扩展**到移动端——Android + iOS + HarmonyOS NEXT + H5（兜底）四端共享同一套 Spring Boot 后端，但前端按**双仓库 + 共享 SDK** 模式物理隔离。新框架**uni-app x**（Vue 3 + Vite + **uts**）因鸿蒙 NEXT 强制要求而选定；MVP 范围 = web 现有功能 1:1 镜像 + **App 端可配置后端地址**（用户自填 URL、可改、可校验）；推送 / 微信扫码 / 多模态 / 上架商店 全部推迟到 v2。

> English version: `0006-uni-app-app-architecture.en.md`.

## 上下文

CLAUDE.md §2 已记录现有栈：Java 21 / Spring Boot 4.0.7 / MyBatis-Flex 1.11.8 / Vue 3.4 + Vite 5.4。前端是一个 1040 行的 `App.vue` 单文件 + 两个 `lib/` 辅助（`sse.js` / `markdown.js`）；后端在 ADR 0003 / 0004 / 0005 落地后已是"严格三层 + 横切关注点 + 可观测性四层 + RBAC + JWT + SSE 流式"。用户提的新需求：

1. 把现有 web 功能搬到移动 App（手机端使用是用户后续高频场景）
2. **App 必须支持用户自填后端地址**——用户可能是自部署多实例（家里 / 公司 / 测试），App 不能写死一个 URL

事实约束：
- **HarmonyOS NEXT 应用 / 元服务只能由 uni-app x 编译产出**（uts → ArkTS），传统 uni-app（Vue + JS）不支持鸿蒙——这是 DCloud 官方划线，无配置项可选
- 后端 ADR 0005 三层架构 + ADR 0004 可观测性 + ADR 0003 SSE 流式 + ADR 0002 微信扫码（设计已就绪、未实施）——都已为多端准备好，**复用价值高**
- 现有 `frontend/src/lib/{sse,markdown}.js` 是端无关代码，理论上可被 SDK 吸收
- CLAUDE.md §6 已锁定"本地单机应用"定位——不暴露公网，App 同样不应直接上架商店
- CLAUDE.md §6 提到 "自定义 baseUrl 会让后端对用户填写的地址发起出站请求（SSRF 风险）"——但那是 `UserApiKey.baseUrl`（后端调 AI 厂家）；**App 自填后端地址**是反向信任（App 信任用户选哪个后端），**不是 SSRF**，但仍要做 URL 校验

`/grill-with-docs` 用 13 个问题澄清事实与决策（含 Q1-A+鸿蒙 / Q13 后端 URL 配置），最终全部落定。

## 核心决策

### 1. 目标平台 = Android + iOS + HarmonyOS NEXT + H5（兜底）

| 平台 | 必要性 | 备注 |
| --- | --- | --- |
| Android | 用户主力场景之一 | 自定义基座 APK 直装 |
| iOS | 用户主力场景之一 | Apple 开发者账号（$99/年）用于真机调试 |
| HarmonyOS NEXT | 用户明确要求 | AppGallery Connect 资质 + 真机 UDID 注册 |
| H5 | 兜底兼容老链接 / 移动浏览器 | `uni-app x` 编译产出 H5，部署到现有 `src/main/resources/static/` |

### 2. 框架 = uni-app x（Vue 3 + Vite + **uts**）

- **不用传统 uni-app（Vue + JS）**——不支持鸿蒙
- **业务逻辑用 uts**（TypeScript 超集，编译到 ArkTS 在鸿蒙原生跑，编译到 Swift/Kotlin 在 iOS/Android）
- **UI 用 Vue 3 SFC**（`<template>` + `<script setup lang="uts">`）
- 构建工具 Vite（HBuilderX 4.x+ 内置；或 CLI 独立项目）
- 不引 TypeScript 配置文件以外的传统 uni-app 插件生态（部分插件只支持传统版）

### 3. 仓库策略 = 双仓库并行（web 与 App 物理隔离）

```
D:/MyWork/myAi/                       # 现有 web + 后端（不动）
D:/MyWork/myAi-sdk/                   # 新建：共享 SDK（独立 npm 包）
D:/MyWork/myAi-app/                   # 新建：uni-app x App 仓库
```

- **双仓库** = git 物理隔离，独立版本号、独立发版节奏
- **共享 SDK** = 跨仓库解耦点，端无关代码只写一遍
- **后端共仓库**（现有 MyAi 仓库内 `src/main/java/`）—— App 通过 HTTP/SSE 与之通信，**后端代码不在双仓库范围**

### 4. 后端策略 = 完全复用现有 Spring Boot + 新增 `/api/app/**` 命名空间

- **后端零分叉**——所有现有端点（`/api/auth/*` / `/api/chat` / `/api/conversations/*` / `/api/users/*/keys/*` / `/api/logs/*` / `/api/providers`）App 直接调
- **MVP 阶段实际不需要新增 `/api/app/**`**——所有功能 web 与 App 共用现有端点
- **`/api/app/**` 命名空间预留**给 v2（如推送 token 注册、HarmonyOS 元服务登记等），MVP 不实际新增
- 不引入新后端技术栈（仍 MyBatis-Flex + Spring Security + Spring AI）

### 5. 代码共享 = 共享 SDK 包（TypeScript 实现，双端消费）

| 仓库 | 消费 SDK 的方式 |
| --- | --- |
| `myAi-app`（uni-app x） | `import { ... } from '@myai/sdk'`（uts 通过 interop 消费 TS 包，HBuilderX 已支持） |
| `myAi`（web Vue 3 + Vite） | `import { ... } from '@myai/sdk'`（现成 TS，App.vue 现有 import path 改一下即可） |

**SDK 内容边界**：

```
myAi-sdk/
├── src/
│   ├── api/             # API 客户端（chat / conversations / keys / auth）
│   ├── streaming/       # StreamingResponse 抽象（H5 EventSource / App fetch+ReadableStream）
│   ├── auth/            # AuthProvider 接口 + EmailPasswordAuthProvider（MVP 实现）
│   ├── storage/         # Token / 后端 URL / 用户偏好持久化（封装 uni.storage）
│   ├── errors/          # 错误码 → 用户提示文案映射
│   ├── media/           # MediaUploadProvider 接口（占位，v2 实现）
│   ├── push/            # PushProvider 接口（占位，v2 实现）
│   ├── utils/           # 通用工具（时间、URL 校验、UUID 等）
│   └── types/           # 类型定义（与后端 Dto/Vo schema 一致）
├── tests/               # vitest 单测
├── package.json
└── tsconfig.json
```

**SDK 内部约定**：
- 纯 TypeScript（不引 `uni.*` API），通过 dependency injection 让 App 端注入 `storage` / `http` 实现
- 不放 UI 组件
- 不放业务规则（业务在 App 端业务代码层）

### 6. App 端鉴权 = MVP = email/密码 + JWT（SDK 第一天建 `AuthProvider` 接口骨架）

```typescript
// SDK 骨架（MVP 第一天就建好，v2 再填实现）
interface AuthProvider {
  login(credentials): Promise<AuthResult>
  register(credentials): Promise<AuthResult>
  logout(): Promise<void>
  getCurrentUser(): Promise<User | null>
  refreshSession(): Promise<AuthResult>
}

class EmailPasswordAuthProvider implements AuthProvider { ... }  // MVP 实现
class HuaweiOAuthProvider implements AuthProvider { ... }        // 占位，v2
class WeChatOAuthProvider implements AuthProvider { ... }        // 占位，v2（触发 ADR 0002）
class AppleIDAuthProvider implements AuthProvider { ... }        // 占位，v2
```

- **MVP 后端零改动**——复用现有 `/api/auth/{register, login, me}`
- **token 存 `uni.storage`**——本地单机应用前提（CLAUDE.md §6）下可接受
- **iOS 上架要求 Apple ID**——MVP 不上商店，v2 再补 AppleIDAuthProvider
- **HarmonyOS 上架要求华为账号**——MVP 不上商店，v2 再补 HuaweiOAuthProvider

### 7. API Key 安全 = 完全复用现有模式（用户输入 → 后端 → 前端只持掩码）

- App 端 Key 输入框提交后端 `/api/users/{id}/keys` 即可
- 前端永远拿不到明文 Key（响应只回 `****abcd` 掩码）
- App 端不做本地缓存（与 web 一致）
- SDK 提供 `KeyInputHintProvider`（剪贴板自动填入 / 截屏保护 / 防云备份）—— **默认全关**，作为 v2 配置项

> **SSRF 误读澄清**：CLAUDE.md §6 提到的"自定义 baseUrl SSRF 风险"是 `UserApiKey.baseUrl`（后端被诱导请求内网）；App 自填后端地址是反向信任（App 信任用户选哪个后端），**不构成 SSRF**。两者不可混淆。

### 8. SSE 流式响应 = 保留后端 SSE，前端 `fetch + ReadableStream` 适配

- **后端零改动**——保住 ADR 0003 §4 SSE 事件边界 + ADR 0004 `ai_call_log` 的 latency 计算
- SDK 写一个 `StreamingResponse` 抽象，对业务代码暴露统一的 `onToken / onDone / onError` 回调
- **H5 端**：用 `EventSource`（与现有 `frontend/src/lib/sse.js` 完全一致，吸收进 SDK）
- **App 端**：用 `uni.request({ streaming: true })` + `body.getReader()`（H5 / iOS / Android 较稳，HarmonyOS 需 PoC 验证）
- **HarmonyOS PoC 不通过的兜底**：后端 chunked + JSON Lines（`Content-Type: application/x-ndjson`）—— **只改后端 streaming 实现，不动 SSE 事件语义**

```typescript
// SDK 抽象
class StreamingResponse {
  onToken: (text: string) => void
  onDone: (usage?: TokenUsage) => void
  onError: (err: Error) => void
  close(): void
}
async function streamChat(params: ChatParams): Promise<StreamingResponse> {
  // H5: new EventSource(...)
  // App: uni.request({ streaming: true, ... })
}
```

### 9. UI 组件库 = uni-ui（DCloud 官方）

- 跨端覆盖最全（含 HarmonyOS NEXT）
- 维护最稳（uni-app 升级时优先适配）
- 风格简约，配合 CSS 变量自定义主题与 web 端品牌色对齐
- **业务组件不放在 SDK 里**（web 与 App 形态差异大）；UI 库只兜基础设施组件（按钮、列表、输入框、tabbar、loading、pull-refresh）
- **业务组件（消息气泡、Key 管理表单、对话列表项）由 App 端业务代码层手写**

### 10. MVP 原生能力 = 1:1 镜像，所有原生能力推迟到 v2

| 原生能力 | MVP 状态 | v2 接入 |
| --- | --- | --- |
| 推送通知 | 推迟 | SDK 预留 `PushProvider` 接口；v2 加 iOS APNs / Android FCM / HarmonyOS Push Kit |
| 微信扫码登录 | 推迟 | 触发 ADR 0002 实施 |
| 华为账号登录 | 推迟 | `HuaweiOAuthProvider` v2 填实现 |
| Apple ID 登录 | 推迟 | iOS 上架时必加 |
| 图片上传对话 | 推迟 | `MediaUploadProvider.uploadImage` + 后端 multipart 端点；~2 周工作量（场景 1+3） |
| 图片生成 | 永久单独评估 | 与对话 App 独立产品能力 |
| 设备指纹 / 风控 | 不做 | 本地单机应用前提 |

**MVP 体验边界**：
- 用户离线时**收不到通知**（需主动打开 App 拉取）
- 只能发**文字**对话
- 登录仅 **email/密码**

### 11. 打包与发布 = 自定义基座 + 本地分发 + 本地离线打包

| 平台 | 打包方式 | 分发 |
| --- | --- | --- |
| Android | 本地离线打包（Android SDK） | debug `.apk` 直接安装；release keystore 自签名 |
| iOS | 本地离线打包（Xcode） | 需 Apple 开发者账号（$99/年）；HBuilderX 内置 iOS 模拟器无需账号 |
| HarmonyOS | 本地离线打包（DevEco Studio） | 需华为开发者账号 + 真机 UDID 注册 |
| H5 | `npm run build:h5` | 产物可本地起 nginx 或上 CDN |

- **不上任何应用商店**——与 CLAUDE.md §6"本地单机应用"定位一致
- **不上传 DCloud 云打包**——代码安全（API Key 业务逻辑 / 鉴权逻辑不上传）
- iOS / HarmonyOS 开发者账号作为实施期子问题决定（不在本 ADR 锁定）

### 12. 测试策略 = SDK 单元测试（vitest）+ App 业务代码手测

**SDK 单测覆盖**（vitest）：
- API 客户端（mock 后端，验证请求路径 / 方法 / body / headers）
- StreamingResponse 解析器（SSE 事件 → 回调）
- 错误码映射（`BizException` code → 用户提示文案）
- Token 持久化 / 恢复
- AuthProvider 接口契约（mock 实现 + EmailPasswordAuthProvider 真实路径）
- 后端 URL 校验（`validateBackendUrl` 的 URL 格式 + `/api/providers` ping）

**App 手测清单**：
- 登录 / 注册 / 退出
- Key CRUD（增 / 改 / 删 / 设默认 / 启用 / 禁用）
- 创建对话 / 切换对话 / 删除对话
- 发送消息 → 流式接收
- 编辑消息 → 触发 regenerate
- 退出账号 token 清理
- **首次启动无 URL → 配置页流程**
- **设置页改 URL → 校验流程**

**跨平台验证**：H5 + iOS + Android + HarmonyOS 各跑一遍手测清单（首版）

### 13. App 后端地址可配置（用户新约束） = 硬编码默认 + 可改

**SDK 设计**：

```typescript
// SDK 接口
interface BackendConfig {
  getBaseUrl(): string                      // 读当前 URL
  setBaseUrl(url: string): Promise<void>    // 改 URL，自动持久化 + 校验
  validateBackendUrl(url: string): Promise<ValidationResult>  // ping /api/providers
  reset(): Promise<void>                    // 清空配置（首启动用）
}

// 持久化
uni.setStorageSync('myai.backend_url', 'https://api.example.com')

// 校验
async function validateBackendUrl(url) {
  try {
    const resp = await fetch(`${url}/api/providers`)
    return { ok: resp.ok, error: resp.ok ? null : `HTTP ${resp.status}` }
  } catch (e) {
    return { ok: false, error: e.message }
  }
}
```

**App 启动流程**：

```
启动
  ↓
读 uni.storage 的 myai.backend_url
  ↓
存在？ ── 否 ─→ 进入「配置后端地址」页（强制）
  ↓ 是
调 validateBackendUrl(url)
  ↓
可达？ ── 否 ─→ 进入「配置后端地址」页（带错误提示 + 当前 URL）
  ↓ 是
读 myai.token
  ↓
存在？ ── 否 ─→ 进入登录页
  ↓ 是
进主界面（对话列表 / Key 管理）
```

**构建时配置默认值**：
- `myAi-app/.env.production`：`VITE_DEFAULT_BACKEND_URL=https://api.example.com`
- 团队分发前替换为自家实例；通用分发时留空（首启动强制配）

**安全注意**：
- URL 必须 `http://` 或 `https://` 开头，校验格式合法
- 警告提示："使用公开后端时请确认是你信任的服务"
- token 与 URL 绑定存储（key 含 URL hash），**避免跨后端 token 泄露**：
  ```typescript
  // 不推荐：uni.storage 存 myai.token（任何后端 URL 都能用）
  // 推荐：uni.storage 存 myai.token.<urlHash>（仅当前 URL 能用）
  ```

**CLAUDE.md §6 SSRF 误读澄清**：原条款指 `UserApiKey.baseUrl`（后端被诱导请求内网），与 App 自填后端地址**完全相反**——App 是主动信任用户选哪个后端，**不构成 SSRF**。本 ADR 不改变 CLAUDE.md §6 既有安全结论。

## 考虑过的选项

### §1.1 目标平台范围
- **A. 仅 H5**（不是真 uni-app） — 拒绝。直接改现有 Vue 项目即可，无需 uni-app。
- **B. 仅微信小程序** — 拒绝。小程序没有 `localStorage`、SSE 需替代方案；用户场景不在微信生态。
- **C. 全端（Android + iOS + 鸿蒙 + 小程序 + H5）** — 不选。维护成本最高。
- **D. Android + iOS + 鸿蒙 + H5（兜底）** — **已选**（用户调整："A+鸿蒙"）。覆盖用户主力场景 + 兼容老链接。

### §2.1 框架形态
- **A. 一开始就用 uni-app x（Vue 3 + Vite + uts）** — **已选**。鸿蒙 NEXT 强制要求；避免"先写一遍再重写一遍"。
- **B. 先用传统 uni-app 出 Android+iOS+H5，鸿蒙之后再说** — 不选。意味着 uts 改造二次开发。
- **C. 双仓库并行**（与 §3.1 C 合并讨论）

### §3.1 仓库策略
- **A. 单仓库 monorepo**（`myAi/{web, app, sdk, backend}`） — 不选。packages 互相耦合，git 历史会混乱。
- **B. 现有仓库加 `app-frontend/` 子目录** — 拒绝。这违背"独立演进"初衷，且 git 操作复杂。
- **C. 双仓库并行 + 共享 SDK 独立仓库** — **已选**。物理隔离；SDK 解耦。

### §4.1 后端策略
- **A. 完全复用现有 Spring Boot** — **已选**。后端零分叉；保住 ADR 0002/0003/0004/0005 全部成果。
- **B. 在现有仓库新建 `app-api` 子模块** — 不选。同一仓不同模块反而增加耦合。
- **C. 完全新建独立后端** — 拒绝。重建意味着推翻既有 ADR，工作量过大。

### §5.1 代码共享
- **A. 零共享** — 拒绝。DTO 改动双端手动同步，类型漂移几乎必然。
- **B. 共享类型定义**（OpenAPI → 双端生成） — 不选。业务代码零共享但生成器维护成本高。
- **C. 共享 SDK 包**（含类型 + API 客户端 + 工具） — **已选**。端无关代码天然只写一遍；TS 实现 + uts interop。
- **D. Git submodule** — 拒绝。IDE 索引 / 依赖安装不友好。

### §6.1 App 鉴权
- **A. MVP = email/密码 + JWT** — **已选**。后端零改动；最快跑通；MVP 不上商店故无需 Apple ID / 华为账号。
- **B. 微信扫码登录**（触发 ADR 0002） — 推迟 v2。
- **C. 微信 mobile OAuth** — 拒绝。仅对微信内 H5 / 小程序有效，App 不适用。
- **D. 多账号体系（email + 微信 + Apple + 华为）** — 不选 MVP 阶段。复杂度太高。

### §7.1 API Key 安全
- **A. 完全复用现有模式**（用户输入 → 后端 → 前端只持掩码） — **已选**。后端零改动；App 端零额外安全责任。
- **B. App 端额外缓存明文到 `uni.storage`** — 拒绝。iOS 审核风险；当前风险已锁定为本地单机不需要。
- **C. 端侧加密 + HarmonyOS KeyStore / iOS Keychain / Android Keystore** — 拒绝。Over-engineering。
- **D. 完全禁止 App 端管理 Key** — 不选。体验割裂。

### §8.1 SSE 流式
- **A. 保留 SSE，前端 fetch + ReadableStream 适配** — **已选**。后端零改动；SDK 抽象统一。
- **B. 后端改 WebSocket** — 拒绝。AI 流式半双工就够；改动 ADR 0003 §4 成本大。
- **C. 后端改 chunked + JSON Lines** — 作为 HarmonyOS PoC 失败兜底方案。
- **D. 改 polling** — 拒绝。体验差。

### §9.1 UI 组件库
- **A. uni-ui（DCloud 官方）** — **已选**。跨端覆盖最全；维护最稳。
- **B. uView Plus** — 不选。社区维护，长期可持续性弱于官方。
- **C. NutUI-UNI** — 不选。HarmonyOS 支持滞后于官方库。
- **D. 全手写** — 拒绝。App 端组件多，工作量约 2-3 周。
- **E. 商业 UI 库** — 不选 MVP 阶段。

### §10.1 MVP 原生能力范围
- **A. MVP = 1:1 镜像** — **已选**。所有原生能力推迟 v2。
- **B. A + 推送通知** — 不选 MVP。推送服务选型复杂。
- **C. A + 微信扫码登录** — 不选 MVP。推迟到 v2 单独 PR。
- **D. A + 图片上传对话** — 不选 MVP。~2 周工作量会拖慢核心验证。
- **E. 全都要** — 拒绝。工作量巨大。

### §11.1 打包与发布
- **A. 自定义基座 + 本地分发** — **已选**。无审核；与 CLAUDE.md §6 锁定一致。
- **B. iOS + HarmonyOS 应用市场 + Android APK 直装**（混合） — 不选 MVP。需 Apple ID / 华为账号与 MVP 鉴权决策冲突。
- **C. 全端上正式商店** — 拒绝。工作量巨大。
- **D. HBuilderX 云打包 vs 本地离线打包** — **已选 本地离线**（与 A 组合）。代码不上云。

### §12.1 测试策略
- **A. 纯手工测试** — 不选。SDK 改动风险高。
- **B. SDK 单测 + App 手测** — **已选**。SDK 是核心，必须自动化；App UI 测试 ROI 低。
- **C. B + 关键流程 E2E** — 不选 MVP。维护成本中等。
- **D. 完整 CI/CD + 真机云测** — 拒绝。MVP 阶段过度。

### §13.1 App 后端地址策略
- **A. 硬编码默认 + 可改** — **已选**（用户澄清后）。构建时配默认 URL，用户可改可校验；适合自家部署 + 团队分发。
- **B. 空默认 + 首次强制配** — 不选。通用分发包场景可考虑，但 MVP 阶段不分发包。
- **C. 硬编码默认 + 多 URL profile** — 不选 MVP。v2 特性。

## 后果

### 新增仓库

| 仓库 | 路径 | 范围 |
| --- | --- | --- |
| `myAi-sdk` | `D:/MyWork/myAi-sdk/` | 共享 SDK（独立 npm 包，TS 实现） |
| `myAi-app` | `D:/MyWork/myAi-app/` | uni-app x App 仓库 |

### 现有仓库变化

- **web 端**：`App.vue` 等前端代码继续在 `frontend/` 维护；可逐步 import SDK 替代内嵌代码（不强制）
- **后端**：MVP 阶段零代码改动；预留 `/api/app/**` 命名空间
- **CLAUDE.md**：实施期补 §2（项目事实 — App 端）+ §4（关键架构约定 — App 段）+ §6（安全边界 — URL 配置 + SSRF 误读澄清）

### 数据模型变更

- **无**。DB schema 不动；现有 6 张表不变。

### API 变更

- **MVP 无新增端点**。所有 App 端需求复用现有 `/api/*`。
- **预留** `/api/app/**` 命名空间给 v2。

### 依赖新增

| 仓库 | 依赖 | 用途 |
| --- | --- | --- |
| `myAi-sdk` | TypeScript 5.x + vitest | SDK 编译 + 单测 |
| `myAi-sdk` | （无运行时依赖） | SDK 保持轻量；`uni.storage` / `fetch` 由 App 端注入 |
| `myAi-app` | uni-app x（Vue 3 + Vite + uts） | App 框架 |
| `myAi-app` | uni-ui | UI 组件库 |
| `myAi-app` | `@myai/sdk`（本地 path 或 npm） | 共享 SDK |
| 后端（MyAi） | **无新增** | MVP 不动后端依赖 |

### 错误码 / 业务码变更

- **无**。现有 `BizException` 码全部复用。

### 性能 / 资源

- SDK 引入一层间接调用，可忽略
- uni-app x 编译产物大小：约 5-8 MB / 平台（含 uni-ui + 业务代码）
- H5 端产物：约 1.5-2 MB（与现有 web 端可比）

### 影响范围预估

| 类别 | 数量 / 工作量 |
| --- | --- |
| 新增仓库 | 2（`myAi-sdk` + `myAi-app`） |
| SDK 源文件 | ~20（API 客户端 + 类型 + 流式 + AuthProvider + 存储 + 错误 + 工具 + 单测） |
| App 端业务组件 | ~15（登录 / Key 管理 / 对话列表 / 消息气泡 / 配置页 / 设置页 等） |
| 后端改动 | **0**（MVP 不动后端） |
| 文档改动 | 本 ADR + PLAN + CLAUDE.md §2/§4/§6 |

### 验证方式

1. **SDK 单测**：`pnpm test`（vitest）必须通过——SDK 是 App 与 web 共用的核心
2. **App 跨端编译**：
   - `npm run build:h5`（H5 端产物可运行）
   - HBuilderX / CLI 编译出 Android `.apk` / iOS `.ipa` / HarmonyOS `.app`（无需上架）
3. **首启动流程**：清空 `uni.storage` → 重启 App → 应进入「配置后端地址」页
4. **URL 校验流程**：填错 URL → 应显示"无法连接到 {url}"
5. **登录流程**：填对 URL → 配置页通过 → 进入登录页 → 注册 / 登录成功 → 进主界面
6. **核心功能**：登录 → 创建对话 → 发消息 → 流式接收；Key CRUD；退出账号
7. **跨平台一致性**：H5 + Android + iOS + HarmonyOS 各跑一遍核心功能

### 落地 commit 拆分（建议）

**Phase 1：SDK 骨架**（独立仓库 `myAi-sdk/`）
1. 仓库初始化 + tsconfig + vitest 配置
2. 类型定义（与后端 Dto/Vo schema 对齐）
3. `BackendConfig`（URL 持久化 + 校验）
4. `AuthProvider` 接口 + `EmailPasswordAuthProvider` 实现
5. API 客户端（auth / users / keys / conversations / messages）
6. `StreamingResponse` 抽象（H5 / App 分支）
7. 错误码映射
8. 单元测试（vitest）

**Phase 2：App 仓库**（独立仓库 `myAi-app/`）
1. uni-app x 项目初始化 + uni-ui 引入
2. `pages/index/` 首启动逻辑（读 URL → 配置页 or 主界面）
3. `pages/config-backend/` 配置页
4. `pages/login/` 登录页
5. `pages/conversations/` 对话列表
6. `pages/conversation/:id` 对话详情 + 流式消息
7. `pages/keys/` Key 管理
8. `pages/settings/` 设置页（含 URL 配置）
9. 跨端编译验证

**Phase 3：现有 web 端接入 SDK**（可选，非阻塞）
- `myAi/frontend/src/App.vue` 改为 `import { ... } from '@myai/sdk'`
- 现有 `lib/sse.js` / `lib/markdown.js` 改为 SDK re-export

### 已知风险

1. **uni-app x 生态薄**——部分插件只支持传统 uni-app；MVP 阶段仅用 uni-ui 官方库可规避
2. **HarmonyOS 流式响应 PoC 未验证**——若 `uni.request({ streaming: true })` 在鸿蒙真机不暴露 chunk 边界，需降级到 §8 C 方案（chunked + JSON Lines）
3. **双仓库版本同步**——SDK 改一次，App 与 web 都要升版本；用 semver + `package.json` 锁版本
4. **iOS / HarmonyOS 开发者账号未申请**——实施期子问题；真机调试需先申请
5. **App 包名 / Bundle ID 未定**——实施期子问题
6. **CLAUDE.md §6 SSRF 误读**——实施期修订时需澄清"UserApiKey.baseUrl SSRF"与"App 自填后端 URL 反向信任"的区别
7. **App 后端 URL 与 token 绑定存储**——避免跨后端 token 泄露；token key 格式 `myai.token.<urlHash>`

### 不做什么（MVP 阶段永不做）

- 推送通知（iOS APNs / Android FCM / HarmonyOS Push Kit）
- 微信扫码登录（ADR 0002 推迟到 v2）
- 华为账号 / Apple ID 登录（上架时再做）
- 图片上传对话（场景 1+3 推迟到 v2，~2 周工作量）
- 图片生成（独立产品能力，永久单独评估）
- 上架应用商店（iOS App Store / 华为应用市场 / 国内 Android 商店）
- 设备指纹 / 风控
- 多后端 URL profile（v2）

## 状态

🚧 **设计定稿**（2026-07-29 grill-with-docs 完成 13 决策）。**等用户通知后实施**——本 ADR 不写任何 App / SDK 业务代码，只落设计。

## 关联

- [[0005-three-layer-architecture]] — 后端三层架构是 SDK API 客户端调用的契约基础；SDK 类型定义与 web 层 Dto/Vo schema 一致
- [[0004-observability]] — App 端 SSE 调用自动获得 `ai_call_log` 写入（latency / tokens 计量）；`X-Trace-Id` 响应头继续回传
- [[0003-conversations-and-messages]] — SSE 事件协议（`event: token` / `event: done` / `event: error`）SDK `StreamingResponse` 抽象的契约基础
- [[0002-wechat-scan-login]] — 推迟到 v2 触发实施；SDK `WeChatOAuthProvider` 占位
- [[0001-defer-wechat-integration]] — 不冲突；App 端鉴权走 email/密码 + JWT
- CLAUDE.md §2 / §4 / §6 实施期更新：新增 App 端项目事实 + 后端 URL 配置安全边界 + SSRF 误读澄清

---

## 实施 checklist（待用户通知后启动）

### Phase 1：SDK 骨架（`myAi-sdk/` 新仓库）
- [ ] 仓库初始化（`package.json` + `tsconfig.json` + `vitest.config.ts` + `.gitignore`）
- [ ] 类型定义（与 `cn.edgarli.web.dto.*Dto` / `web.vo.*Vo` 一一对应）
- [ ] `BackendConfig`（getBaseUrl / setBaseUrl / validateBackendUrl / reset + `uni.storage` 持久化）
- [ ] `AuthProvider` 接口 + `EmailPasswordAuthProvider` 实现
- [ ] API 客户端（auth / users / keys / conversations / messages）
- [ ] `StreamingResponse` 抽象（H5 `EventSource` 分支 + App `fetch + ReadableStream` 分支）
- [ ] 错误码 → 用户提示文案映射
- [ ] vitest 单测（API / Streaming / BackendConfig / AuthProvider 全部覆盖）
- [ ] `pnpm test` 全绿 + `pnpm build` 产物可被 App 与 web 端 import

### Phase 2：App 仓库（`myAi-app/` 新仓库）
- [ ] uni-app x 项目初始化（CLI 模板）+ uni-ui 引入
- [ ] `pages/index/` 首启动逻辑（读 URL → 路由决策）
- [ ] `pages/config-backend/` 配置页（输入 + 校验 + 保存）
- [ ] `pages/login/` 登录页（email/密码）
- [ ] `pages/conversations/` 对话列表
- [ ] `pages/conversation/:id` 对话详情 + 流式消息气泡
- [ ] `pages/keys/` Key 管理（增删改 / 启用禁用 / 设默认）
- [ ] `pages/settings/` 设置页（含 URL 配置 + 退出账号）
- [ ] 跨端编译验证（H5 / Android / iOS / HarmonyOS 各产出可运行）

### Phase 3：现有 web 端接入 SDK（可选，非阻塞）
- [ ] `myAi/frontend/src/App.vue` 改为 `import { ... } from '@myai/sdk'`
- [ ] `frontend/src/lib/{sse,markdown}.js` 移除（SDK 已含）
- [ ] `mvn -DskipTests package` + `npm run build` 验证

### Phase 4：文档收尾
- [ ] CLAUDE.md §2 增加「App 端」项目事实
- [ ] CLAUDE.md §4 增加「App 端」关键架构约定
- [ ] CLAUDE.md §6 澄清 SSRF 误读（`UserApiKey.baseUrl` vs `App.backendUrl`）
- [ ] `.claude/api.md` §6 标注 SDK 调用契约（如有补充）
- [ ] `.claude/REQUIREMENTS.md` 1.10 增加 App 端需求段

### 验证
- [ ] `pnpm --filter @myai/sdk test` 全绿
- [ ] `pnpm --filter @myai/sdk build` 产物可被 App / web 端 import
- [ ] `myAi-app` H5 产物可本地起 nginx 跑通核心流程
- [ ] `myAi-app` Android `.apk` 真机 / 模拟器跑通核心流程
- [ ] `myAi-app` iOS `.ipa` 模拟器跑通核心流程
- [ ] `myAi-app` HarmonyOS `.app` 模拟器跑通核心流程
- [ ] `myAi` web 端 `mvn -DskipTests package` 通过