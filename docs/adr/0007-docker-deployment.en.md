# 0007 — Docker Deployment

## Context + Decision + Rationale

MyAi currently only runs locally via `mvn spring-boot:run`: no Dockerfile, no compose, no CI image registry; the frontend bundle is pre-built into the fat JAR (`src/main/resources/static/`); state lives in the H2 file database (`./data/myai.mv.db`) and local log files (`./logs/*.jsonl`). The user wants to containerize MyAi and deploy it to **one VPS (public access, backend for the mobile app `myAi-app/`) + one home NAS (LAN access only)**, source hosted on Gitee, **images NOT pushed to any public registry**; Ollama runs on a third independent LAN server, and MyAi containers talk to it via HTTP API on the LAN. This ADR only nails down **architecture shape + config injection + security boundary + image distribution chain**; it does not write the Dockerfile / compose / CI YAML (those are implementation-phase artifacts).

## Status

proposed (2026-08-17, settled by the 22nd conversation `/grill-with-docs` 21-question grilling; design only this round, implementation kicks off in the next round)

## Considered Options

### Q1 Target environment

- A. VPS (public, App + web)
- B. NAS (home LAN, LAN access only)
- C. Windows host Docker Desktop
- D. Just make the repo "Docker-capable"

**Chose A+B.** Reason: ADR 0006 Q13's "configurable backend URL for the App" implies a real online backend; VPS gives public access / NAS keeps LAN use case / C has no Docker installed locally, lowest value / D has no delivery anchor.

### Q2 Image distribution

- A. Multi-stage Dockerfile, `docker compose build` on each target
- B. Multi-stage Dockerfile, build locally + `docker save/load`
- C. CI auto-builds + pushes to public registry (Docker Hub / GHCR / Aliyun ACR)
- D. Ship fat JAR as artifact

**Chose C-priv (Gitee Go + Gitee Container Registry).** Reason: same Gitee account as source code, no extra service; target machines only run `docker compose pull && up -d`, zero build tools. **Fallback path:** if Gitee Go's free tier blocks image registry push or multi-arch buildx → switch to F (self-host `registry:2` container on VPS, NAS configured with `insecure-registries` or a Caddy front).

### Q3 Target CPU architecture

- A. amd64 only
- B. amd64 + arm64 (**recommended**)

**Chose B.** Reason: Synology DS920+/DS923+/DS224+, some Zspace models, Pi 5, Feiniu OS self-hosting are all ARM64; buildx multi-arch manifest lets both machines pull the same image:tag.

### Q4 Containerize Ollama?

- A. MyAi container uses `extra_hosts` / `network_mode: host` to reach host Ollama
- B. Ollama also containerized in the same compose
- C. Only cloud providers, leave local Ollama alone

**Chose D (Ollama runs on a third independent LAN server, MyAi container calls it via HTTP API).** This is the scenario the user explicitly added in pre-questioning, not part of the original four options. Reason: model machine resources decoupled from MyAi app machine; GPU upgrades independent.

### Q5 Ollama server network location

- A. Public internet
- B. Internal LAN (**recommended**)
- C. Wireguard / Tailscale zero-trust

**Chose B.** Reason: Ollama has no native auth, naked public exposure invites abuse; VPS ↔ Ollama server use internal IPs on the home LAN.

### Q6 Ollama base URL config

- A. Hardcoded in compose / yml
- B. Internal DNS / mDNS
- C. `.env` file injection (**recommended**)

**Chose C.** Reason: IP changes only edit `.env`, no git churn; VPS and NAS each carry their own.

### Q7 External port + HTTPS

- A. Container 8031, host exposes 8031 directly (**recommended**)
- B. Container 8080, nginx + certbot in front
- C. Container 8031, cloudflared in front

**Chose A.** Reason: consistent with current `application.yml`; HTTPS (if needed) delegated to a front reverse-proxy, application layer stays HTTP-only. This ADR **does NOT include** reverse-proxy / certbot config (added on-demand at deploy time).

### Q8 Data persistence + H2 AUTO_SERVER

- A. Named volumes `myai-data`, `myai-logs`
- B. bind mount `./data:/app/data`, `./logs:/app/logs` (**recommended**)
- C. bind mount + scheduled backup to object storage

**Chose B.** Reason: matches current layout (already in `.gitignore`); migrate to VPS / NAS via plain `rsync`. AUTO_SERVER **disabled** in container (single-process doesn't need TCP sharing; enabled also writes a stray `.lock.db`). `MODE=MySQL;DB_CLOSE_DELAY=-1` retained.

### Q9 / Q9b Image content + CI flow

- A. Image builds frontend
- B. Image only runs JRE + fat JAR (**recommended**)
- C. Image runs `mvn package`
- D. Only pre-built JAR

**Chose B.** CI pipeline = `mvn -DskipTests package` → `docker buildx build --platform linux/amd64,linux/arm64 -t myai:$CI_COMMIT_ID,myai:latest --push` (**chose A2**).

### Q11 Base image

- A. `eclipse-temurin:21-jre-alpine` (**recommended**)
- B. `eclipse-temurin:21-jre-jammy`
- C. `gcr.io/distroless/java21-debian12`

**Chose A.** Reason: Alpine small footprint (~80MB) + officially maintained + complete ARM64 coverage + ash available for in-container debugging.

### Q12 JVM params + memory + TZ

- A. Explicit `-Xmx512m -Xms256m`
- B. `-XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0` (**recommended**)
- C. Unlimited + `UseContainerSupport`

**Chose B.** GC = G1 (Spring Boot 4 default), TZ = `Asia/Shanghai` + `apk add tzdata` (matches `LogCleanupTask`'s `cron="0 4 * * * *", zone="Asia/Shanghai"` host TZ).

### Q13 Healthcheck

- A. No HEALTHCHECK
- B. Add `spring-boot-starter-actuator`
- C. Minimal `wget -qO- http://localhost:8031/` (**recommended**)

**Chose C.** Reason: no new dependency; root path `/` returns SPA HTML, 200 once started.

### Q14 Security surface

- **H2 console**: **disabled** in container (`SPRING_H2_CONSOLE_ENABLED=false`) — file-mode web shell is pointless and shouldn't be exposed publicly.
- **JWT secret**: `application.yml` switches to `${MYAI_JWT_SECRET}` (no `:` default), compose forces `.env` injection, missing = fail-fast.
- **Process user**: **root in container** (Dockerfile does NOT create a non-root user) — simple + matches current state; harden later if needed.
- **H2 password**: stay empty (file mode doesn't expose TCP; empty password only protects against same-host root, unnecessary).
- **Network mode**: default bridge + `ports: "8031:8031"`.

### Q15 / Q15a Image tag

- A. `latest`
- B. `$CI_COMMIT_ID`
- C. `$CI_COMMIT_ID` + `:latest` both pushed (**recommended**)

**Chose C.** Compose references `image: registry.gitee.io/<TODO:your-gitee-namespace>/myai:latest`; tag is set by the CI pipeline. `<TODO:your-gitee-namespace>` is left as `TODO` in this ADR, filled in during implementation.

### Q16 Multi-instance / horizontal scale

- A. Single instance (**recommended**)
- B. Multi-instance + shared volume
- C. Single instance + leave room to scale

**Chose A.** Current H2 file locks + local file logs **cannot multi-instance at all**. Constraint: "Dockerfile / compose makes every variable go through env / bind mount / `.env`, zero hard-coding in image" — leaves room to swap to PostgreSQL / S3 later.

### Q17 Startup + graceful shutdown

- ENTRYPOINT: `["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]` — `JAVA_OPTS` can change in compose without rebuilding image.
- `STOPSIGNAL SIGTERM` + compose `stop_grace_period: 1m` — SSE long connections timeout at 5 min, 1m is a sane web-request upper bound; SSE beyond 1m drops naturally.
- Startup order: MyAi has no external deps (no Redis / MQ / external DB), no `depends_on`.

### Q18 Log collection

- **stdout + file dual-write**: logback adds a `ConsoleAppender` to stdout (so `docker logs myai` works) + keep the existing `RollingFileAppender` to `./logs/*.jsonl` (the container's "ground truth" logs, bind-mounted out for logrotate / backup / third-party aggregation).
- Change point: `logback-spring.xml` adds a ConsoleAppender, **does not touch** the existing RollingFileAppender.

### Q19 .dockerignore

**Write it.** Exclude `.git/`, `.idea/`, `frontend/node_modules/`, `data/`, `logs/`, `target/` from build context; ~1KB, saves build time + prevents sensitive files leaking into image layers.

### Q20 CI pipeline (Gitee Go)

- Trigger: `push` to master auto-builds image (tag = commit id) + tag `v*` extra-builds (tag = version).
- Cache: Maven repo (`~/.m2/repository` persisted in Gitee Go workspace) + Docker layer cache (`--cache-from type=local` or Gitee Go's built-in cache).
- Duration: 2-5 min/run (cold 3-5 min / warm cache 30-60s / buildx multi-arch 1-2 min). Whether Gitee Go's free tier build minutes suffice — **this ADR does not assume**, decided by actual run during implementation; if insufficient → switch to F (self-host registry).
- Secrets: `MYAI_JWT_SECRET` etc. via Gitee Go's encrypted environment variables; repo ships `.env.example` template, real secrets never enter git.

## Consequences

### Newly added constraints after landing

1. **`.claude/CLAUDE.md` §4 add rule #26 ("docker deployment constraints")**: summary of this ADR's 21-question decisions + key constraints (AUTO_SERVER off, JWT no default, H2 console off in container, TZ=Asia/Shanghai, stop_grace_period 1m, single-instance hard limit, etc.).
2. **`.claude/REQUIREMENTS.md` §1.11** new "Docker Deployment (designed, not implemented)" section: deliverables checklist (Dockerfile / Gitee Go YAML / `docker-compose.yml` / `.env.example` / `.dockerignore` / README deploy section / implementation-phase namespace fill).
3. **`application.yml` 3 changes** (implementation phase):
   - `spring.datasource.jdbc-url` drops `AUTO_SERVER=TRUE`;
   - `my-ai.jwt.secret` becomes `${MYAI_JWT_SECRET}` with no default;
   - `my-ai.providers.ollama.default-base-url` becomes `${MYAI_OLLAMA_BASE_URL:http://localhost:11434}` (keep localhost default; compose overrides via env).
4. **`logback-spring.xml` 1 change**: add `<appender name="STDOUT" class="ConsoleAppender">`, do not touch existing RollingFileAppender.
5. **New artifacts** (implementation phase): `Dockerfile`, `docker-compose.yml`, `.env.example`, `.dockerignore`, `.gitee/go.yml` (or equivalent Gitee Go pipeline), `README.md` adds "Docker Deployment" section.

### Implementation-phase sub-questions (outside the 21-question decision set)

| # | Sub-question | Note |
| --- | --- | --- |
| 1 | Gitee Container Registry namespace `<TODO:your-gitee-namespace>` | filled in during implementation |
| 2 | Default `Ollama` provider base URL inside Docker image | keep `http://localhost:11434` or change to `http://host.docker.internal:11434`? |
| 3 | Front reverse-proxy / HTTPS / certbot bundled this round | depends on whether VPS really needs public HTTPS |
| 4 | Image version starting point | `1.0.0` (first released version) or stay `1.0-SNAPSHOT`? |
| 5 | Image `EXPOSE` port declaration | 8031 only? Or also 8032 (`application-my.yml` profile)? |

### Left for user decision (before implementation kicks off)

1. Whether to start implementation (user's historical preference: "write docs first, wait for my signal before execution" — default: don't start)
2. Acceptability of fallback path F (self-host registry) — only judged after one actual Gitee Go run

## Related ADRs

- **ADR 0006 "uni-app App architecture"**: App-side configurable backend URL (Q13) is the business driver for this dockerization; "zero backend changes" constraint remains.
- **ADR 0004 "Observability & logging"**: this ADR's Q18 (stdout + file dual-write) + `./logs/*.jsonl` bind mount depend on 0004's 4-layer log structure (`./logs/access.jsonl` + `app.jsonl` are 0004 §4 / §8 design).
- **ADR 0005 "Three-layer architecture"**: this ADR doesn't touch 0005's web → service → mapper dependency direction; the only touch is the `application.yml` config layer.

## Implementation checklist (next round)

- [ ] `<TODO:your-gitee-namespace>` confirmed + replaced
- [ ] `application.yml` 3 changes + `application-my.yml` synced + verify `mvn -DskipTests package` passes
- [ ] `logback-spring.xml` add ConsoleAppender + verify
- [ ] Write `Dockerfile` (multi-stage? No, this ADR chose B: single JRE + pre-built JAR copy)
- [ ] Write `docker-compose.yml` (`./data`, `./logs` bind mount + env injection + `stop_grace_period: 1m` + `HEALTHCHECK`)
- [ ] Write `.env.example` (required `MYAI_JWT_SECRET`, optional `MYAI_OLLAMA_BASE_URL` / `SPRING_PROFILES_ACTIVE`)
- [ ] Write `.dockerignore`
- [ ] `.gitee/go.yml` (buildx + push myai:$CI_COMMIT_ID,myai:latest --platform linux/amd64,linux/arm64)
- [ ] `README.md` add "Docker Deployment" section: Gitee Go + Gitee Container Registry + compose startup + data migration / backup hints
- [ ] Implementation-phase namespace filled into ADR 0007 + update CLAUDE.md / REQUIREMENTS.md
- [ ] Verify: VPS works → NAS works → fallback path F (if triggered) actually tested