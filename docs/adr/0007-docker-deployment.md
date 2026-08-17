# 0007 — Docker 部署

## 上下文 + 决策 + 理由

MyAi 当前只能 `mvn spring-boot:run` 在本地跑：没有 Dockerfile、没有 compose、没有 CI 镜像仓；前端产物预 build 进 fat JAR（`src/main/resources/static/`）；状态分散在 H2 文件库（`./data/myai.mv.db`）和本地日志（`./logs/*.jsonl`）。用户希望把 MyAi 容器化部署到 **一台 VPS（公网访问，给移动 App `myAi-app/` 提供后端）+ 一台家用 NAS（内网，仅 LAN 访问）**，源码托管在 Gitee，**镜像不上公网 registry**；Ollama 模型跑在第三台独立的 LAN 服务器上，MyAi 容器通过 HTTP API 走内网调用。本次决策只定 **架构形态 + 配置注入 + 安全边界 + 镜像分发链路**，不写 Dockerfile / compose / CI YAML（实施期产物）。

## Status

proposed（2026-08-17 由第 22 次对话 `/grill-with-docs` 21 题 grilling 落定；本轮只设计，下一轮启动实施）

## Considered Options

### Q1 目标环境

- A. VPS（公网，App + web）
- B. NAS（家庭 LAN，仅内网）
- C. Windows 本机 Docker Desktop
- D. 只让仓库"具备 Docker 能力"

**选 A+B**。理由：App 端可配置后端地址（ADR 0006 Q13）的目标就是让 App 跑起来连一个真实在线后端；VPS 给公网 / NAS 留内网使用面 / C 本机没装 Docker，价值最低 / D 没有交付节点。

### Q2 镜像分发

- A. 多阶段 Dockerfile 在目标机 `docker compose build`
- B. 多阶段 Dockerfile，本地 build + `docker save/load`
- C. CI 自动构建 + 推到公网 registry（Docker Hub / GHCR / 阿里云 ACR）
- D. 把 fat JAR 当产物发

**选 C-priv（Gitee Go + Gitee 镜像仓）**。理由：跟 Gitee 代码同账号同权限，免额外服务；目标机只跑 `docker compose pull && up -d`，零构建工具。**降级路径**：如果 Gitee Go 免费用户无法推镜像仓或多架构 buildx → 切 F（VPS 自建 `registry:2` 容器，NAS 配 insecure-registries / 前置 Caddy）。

### Q3 目标 CPU 架构

- A. 仅 amd64
- B. amd64 + arm64（**推荐**）

**选 B**。理由：群晖 DS920+/DS923+/DS224+、极空间部分型号、Pi 5、飞牛 OS 自部署全是 ARM64；buildx 多架构 manifest 让两台机拉同一份 image:tag。

### Q4 Ollama 容器化

- A. MyAi 容器 `extra_hosts` / `network_mode: host` 直连宿主 Ollama
- B. Ollama 也容器化进同一份 compose
- C. 只跑云端厂家，本地 Ollama 不动

**选 D（Ollama 跑在第三台独立 LAN 服务器，MyAi 容器通过 HTTP API 调用）**。这是用户在前置问答中明确追加的场景，不在原始四选里。理由：模型机器资源与 MyAi 应用机器解耦，可独立升级 GPU。

### Q5 Ollama 服务器网络位置

- A. 公网
- B. 内网 LAN（**推荐**）
- C. Wireguard / Tailscale 零信任

**选 B**。理由：Ollama 原生无 auth，裸跑公网会被刷；家庭 LAN 内 VPS ↔ Ollama 服务器走内网 IP。

### Q6 Ollama base URL 配置

- A. 写死在 compose / yml
- B. 内网 DNS / mDNS
- C. `.env` 文件注入（**推荐**）

**选 C**。理由：换 IP / 换网络只改 `.env`，不进 git；VPS 与 NAS 各持一份。

### Q7 对外端口 + HTTPS

- A. 容器 8031，宿主直接 8031 暴露（**推荐**）
- B. 容器 8080，前置 nginx + certbot
- C. 容器 8031，前置 cloudflared

**选 A**。理由：跟现状 `application.yml` 一致；HTTPS（如果需要）交给前置反代，应用层只管 HTTP。本次 ADR **不包含**反代 / certbot 配置（留给部署期按需追加）。

### Q8 数据持久化 + H2 AUTO_SERVER

- A. 命名卷 `myai-data`、`myai-logs`
- B. bind mount `./data:/app/data`、`./logs:/app/logs`（**推荐**）
- C. bind mount + 定时备份到对象存储

**选 B**。理由：跟现状路径一致（`.gitignore` 已挂）；迁移到 VPS / NAS 直接 `rsync` 即可。AUTO_SERVER 在容器里**关闭**（单进程不需要 TCP 共享，开了还会写 `.lock.db` 多余锁文件）；`MODE=MySQL;DB_CLOSE_DELAY=-1` 保留。

### Q9 / Q9b 镜像内容 + CI 流程

- A. 镜像里跑前端构建
- B. 镜像只跑 JRE + fat JAR（**推荐**）
- C. 镜像跑 mvn package
- D. 只跑 pre-built JAR

**选 B**。CI 流水线 = `mvn -DskipTests package` → `docker buildx build --platform linux/amd64,linux/arm64 -t myai:$CI_COMMIT_ID,myai:latest --push`（**A2 选**）。

### Q11 base image

- A. `eclipse-temurin:21-jre-alpine`（**推荐**）
- B. `eclipse-temurin:21-jre-jammy`
- C. `gcr.io/distroless/java21-debian12`

**选 A**。理由：Alpine 小体积（约 80MB）+ 官方维护 + ARM64 配套齐全 + ash 容器内可调试。

### Q12 JVM 参数 + 内存 + TZ

- A. 显式 `-Xmx512m -Xms256m`
- B. `-XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0`（**推荐**）
- C. 不限 + UseContainerSupport

**选 B**。GC 用 G1（Spring Boot 4 默认），时区 `TZ=Asia/Shanghai` + `apk add tzdata`（让 LogCleanupTask 的 `cron="0 4 * * * *", zone="Asia/Shanghai"` 与宿主机一致）。

### Q13 健康检查

- A. 不做 HEALTHCHECK
- B. 加 spring-boot-starter-actuator
- C. 极简 `wget -qO- http://localhost:8031/`（**推荐**）

**选 C**。理由：不加依赖；根路径 `/` 返回 SPA HTML，启动好就 200。

### Q14 安全面

- **H2 console**：容器里**关**（`SPRING_H2_CONSOLE_ENABLED=false`）—— 文件模式 web shell 无意义，且不该暴露公网。
- **JWT secret**：`application.yml` 改成 `${MYAI_JWT_SECRET}`（无 `:` 默认值），compose 强制 `.env` 注入，缺失就 fail-fast。
- **进程用户**：容器内 **root 跑**（Dockerfile 不建非 root 用户）—— 简单 + 跟现状一致；后续要降权再切。
- **H2 密码**：维持空（H2 文件模式不暴露 TCP，空密码防的是同台 root，没必要）。
- **网络模式**：默认 bridge + `ports: "8031:8031"`。

### Q15 / Q15a 镜像标签

- A. `latest`
- B. `$CI_COMMIT_ID`
- C. `$CI_COMMIT_ID` + `:latest` 同时推（**推荐**）

**选 C**。compose 里 `image: registry.gitee.io/<TODO:your-gitee-namespace>/myai:latest`，tag 由 CI 流水线负责。`<TODO:your-gitee-namespace>` 在本 ADR 留 `TODO`，实施期填。

### Q16 多实例 / 横向扩展

- A. 单实例（**推荐**）
- B. 多实例 + 共享卷
- C. 单实例 + 留扩展余地

**选 A**。当前 H2 文件锁 + 本地文件日志**根本无法多实例**。约束"Dockerfile / compose 所有可变部分走环境变量 / 挂卷 / `.env`，镜像零硬编码"，给将来切 PostgreSQL / S3 留接口。

### Q17 启动 + 优雅关闭

- ENTRYPOINT：`["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]` —— 让 `JAVA_OPTS` compose 里只改 env 不重 build。
- STOPSIGNAL `SIGTERM` + compose `stop_grace_period: 1m` —— SSE 长连接是 5 分钟超时，1m 是 web 请求的合理上限；超过 1m 的 SSE 让它自然断即可。
- 启动顺序：MyAi 无外部依赖（无 Redis / MQ / 外部 DB），不写 `depends_on`。

### Q18 日志收集

- **stdout + 文件双写**：logback 加 `ConsoleAppender` 走 stdout（让 `docker logs myai` 能看）+ 保留 `RollingFileAppender` 写 `./logs/*.jsonl`（容器的"事实日志"，挂卷出来给 logrotate / 备份 / 第三方聚合）。
- 改动点：`logback-spring.xml` 新增 ConsoleAppender，**不动**既有 RollingFileAppender 配置。

### Q19 .dockerignore

**写**。`.git/`、`.idea/`、`frontend/node_modules/`、`data/`、`logs/`、`target/` 之外的所有内容进 build context；典型 1KB 内，省 build 时间 + 防敏感文件意外入镜像层。

### Q20 CI 流水线（Gitee Go）

- 触发：`push` 到 master 自动构建镜像（tag = commit id）+ tag `v*` 额外构建（tag = 版本号）。
- 缓存：Maven 仓库（`~/.m2/repository` 持久化在 Gitee Go workspace）+ Docker layer cache（`--cache-from type=local` 或 Gitee Go 内置缓存层）。
- 时长：单次 2-5 min（冷启动 3-5 min / 热缓存 30-60s / buildx 多架构 1-2 min）。Gitee Go 免费用户构建分钟数是否够 —— **本 ADR 不假设**，由实施期实测决定；不够切 F（自建 registry）。
- secrets：`MYAI_JWT_SECRET` 等走 Gitee Go 密文环境变量；仓库提供 `.env.example` 模板，真实密钥不入 git。

## Consequences

### 落地后新增的约束

1. **`.claude/CLAUDE.md` §4 增第 26 条架构约束**（"docker 部署约束"）：列本 ADR 的 21 题决策摘要 + 关键约束（AUTO_SERVER 关闭、JWT 无默认值、H2 console 容器内关、TZ=Asia/Shanghai、stop_grace_period 1m、单实例硬约束等）。
2. **`.claude/REQUIREMENTS.md` §1.11** 新增「Docker 部署（已设计，未实现）」段：交付清单（Dockerfile / Gitee Go YAML / `docker-compose.yml` / `.env.example` / `.dockerignore` / README 部署段 / 实施期 namespace 填写）。
3. **`application.yml` 改动 3 处**（实施期）：
   - `spring.datasource.jdbc-url` 去掉 `AUTO_SERVER=TRUE`；
   - `my-ai.jwt.secret` 改成 `${MYAI_JWT_SECRET}` 无默认值；
   - `my-ai.providers.ollama.default-base-url` 改成 `${MYAI_OLLAMA_BASE_URL:http://localhost:11434}`（保留 localhost 默认值，compose 通过 env 覆盖）。
4. **`logback-spring.xml` 改动 1 处**：新增 `<appender name="STDOUT" class="ConsoleAppender">`，不影响既有 RollingFileAppender。
5. **新增产物**（实施期）：`Dockerfile`、`docker-compose.yml`、`.env.example`、`.dockerignore`、`.gitee/go.yml`（或等效 Gitee Go 流水线配置）、`README.md` 增"Docker 部署"段。

### 实施期子问题（不在 21 题决策里）

| # | 子问题 | 备注 |
| --- | --- | --- |
| 1 | Gitee 镜像仓命名空间 `<TODO:your-gitee-namespace>` | 实施期填 |
| 2 | `Ollama` provider 在 Docker 镜像里的 base URL 默认值 | 留 `http://localhost:11434` 还是改成 `http://host.docker.internal:11434`？ |
| 3 | 前置反代 / HTTPS / certbot 是否随本轮一起出 | 取决于 VPS 是否真有公网 HTTPS 需求 |
| 4 | 镜像版本起点 | `1.0.0`（首次发版本号）还是继续 `1.0-SNAPSHOT`？ |
| 5 | 镜像 `EXPOSE` 端口声明 | 8031 only？还是 8032（`application-my.yml` profile）也声明？ |

### 留给用户决策（实施期启动前）

1. 是否启动实施（用户历史偏好："先写到文档里，后续执行等我通知" —— 默认不启动）
2. 自建 registry 降级路径（F）的接受度 —— 实际跑一次 Gitee Go 后再拍

## 关联 ADR

- **ADR 0006「uni-app App 端架构」**：App 端可配置后端地址（Q13）是本次 docker 化的业务驱动；后端零改动约束继续成立。
- **ADR 0004「可观测性与日志」**：本 ADR 的 Q18（stdout + 文件双写）+ `./logs/*.jsonl` 挂卷都依赖 0004 的 4 层日志结构（`./logs/access.jsonl` + `app.jsonl` 是 0004 §4 / §8 设计的）。
- **ADR 0005「三层架构」**：本 ADR 不动 0005 的 web → service → mapper 依赖方向；唯一触动的是 `application.yml` 配置文件层。

## 实施 checklist（下一轮）

- [ ] `<TODO:your-gitee-namespace>` 确认 + 替换
- [ ] `application.yml` 3 处改动 + `application-my.yml` 同步 + 验证 `mvn -DskipTests package` 通过
- [ ] `logback-spring.xml` 加 ConsoleAppender + 验证
- [ ] 写 `Dockerfile`（multi-stage? 不，本 ADR 选 B：单 JRE + pre-built JAR 拷贝）
- [ ] 写 `docker-compose.yml`（`./data`、`./logs` bind mount + env 注入 + `stop_grace_period: 1m` + `HEALTHCHECK`）
- [ ] 写 `.env.example`（必填 `MYAI_JWT_SECRET`，可选 `MYAI_OLLAMA_BASE_URL` / `SPRING_PROFILES_ACTIVE`）
- [ ] 写 `.dockerignore`
- [ ] `.gitee/go.yml`（buildx + push myai:$CI_COMMIT_ID,myai:latest --platform linux/amd64,linux/arm64）
- [ ] `README.md` 增"Docker 部署"段：Gitee Go + Gitee 镜像仓 + compose 启动 + 数据迁移 / 备份提示
- [ ] 实施期 namespace 填到 ADR 0007 + 更新 CLAUDE.md / REQUIREMENTS.md
- [ ] 验证：VPS 跑通 → NAS 跑通 → 降级路径 F（如触发）实测