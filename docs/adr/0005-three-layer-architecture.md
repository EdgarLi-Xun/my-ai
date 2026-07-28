# 三层架构 + XML Mapper + 双语注释（Three-layer Architecture, XML Mappers, and Bilingual Comments）

MyAi 在第 15 次 `/grill-with-docs` 会话（2026-07-28）定稿：从"几乎三层但有几处偏离"演进为**严格教科书式三层架构**——Service 接口与实现分离、按职责拆分 MessageService、AI 调用沉入 service 子层、横切关注点统一进 `infrastructure`、数据对象走 **DO / BO / VO 三层**分离、6 个 entity 加 `Do` 后缀、引入 XML mapper（Conversation + Message 业务查询全迁 XML）、全仓所有 Java 源 + 2 个 XML 文件加**严格双语注释**（每行中文 + 英文同行）。这是一次纯代码组织重构，**不改动运行时行为**（API 路径 / 响应格式 / 业务码 / DB schema 都不变）。

> English version: `0005-three-layer-architecture.en.md`.

## 上下文

CLAUDE.md §2 已记录仓库技术栈：Java 21、Spring Boot 4.0.7、MyBatis-Flex 1.11.8、Spring Security 6、Vue 3。仓库在 ADR 0003（对话与消息）+ ADR 0004（可观测性）落地后已有 ~52 个 Java 源文件，但用户视角下仍有四处"不够传统三层"的痛点：

1. **Entity 直接当 Response 外发**（`User` / `UserApiKey` 由 entity 直接返回；其他实体有专门的 `web/dto/*Response`）。
2. **Service 职责过重**（`MessageService` 同时管 流式 SSE / AI 调用 / ai_call_log 写入 / 自动标题 / 消息持久化 / 事务编排，6 个职责压在一起）。
3. **横切关注点混入业务代码**（`TraceIdFilter` / `AuditAspect` 与 service 同层；`observability` 是顶级包）。
4. **AI 调用不"流过" service 层**（`cn.edgarli.ai` 是顶级包，`MessageService` 直接依赖 `ChatClientFactory`）。

加上用户提出的两个补充要求：
- **注释粒度到函数 / 参数 / 局部变量**，**全仓覆盖**。
- **mapper.xml 也要加注释**——经核实仓库当前**没有任何 XML mapper**，需要重构中引入。

`/grill-with-docs` 用 12 个问题澄清事实与决策，最终全部落定。

## 核心决策

### 1. ORM 不动
- 仓库已用 MyBatis-Flex 1.11.8（`mybatis-flex-spring-boot4-starter` + `@Table` / `BaseMapper<T>`），**不存在任何 JPA / Hibernate 痕迹**。
- 用户原始提的"去除 jpa"基于错误前提，已澄清；本 ADR 不做任何 ORM 切换。
- `FlexConfig` 显式声明 `HikariDataSource` + `FlexSqlSessionFactoryBean` + `classpath:cn/edgarli/mapper/**/*.xml` 扫描路径——XML mapper 直接落 `src/main/resources/cn/edgarli/mapper/`，无需改配置。

### 2. Service 接口与实现分离
- 命名：**同名接口 + Impl 后缀**（`UserService` 接口 + `UserServiceImpl` 实现）。
- **不用** I 前缀（`IUserService`）—— Spring 社区主流风格，IDE 代码跳转更顺。
- Controller 依赖 Service **接口**，Spring 注入**实现**。
- 现有 5 个 service（Auth / UserApiKey / Message / Conversation / AiCallLog）全部拆为接口 + 实现。

### 3. MessageService 拆三件套
- **MessageQueryService** 接口：`list` / `get`（查询类，read-only）
- **MessageCommandService** 接口：`streamReply` / `regenerate` / `edit`（命令类，写操作 + 流式）
- **MessageService** 组合接口：`extends MessageQueryService, MessageCommandService`
- **MessageServiceImpl** 委托给两个分接口实现（避免重复代码）。
- Controller 注入 `MessageService` 一个接口即拿到全部能力。
- `AuditAspect` 与 `@Auditable` 仍按方法签名拦截，不受接口拆分影响。

### 4. AI 子包归宿
- `cn.edgarli.ai` → `cn.edgarli.service.ai`（进 service 层）。
- 新增 **`AiService` 接口 + `AiServiceImpl` 实现**：负责拼 prompt / 取 tokens / 流式 / 处理 `ChatResponse.getMetadata().getUsage()`。
- `ChatClientFactory` 与 `provider/` 保持内部细节，仅 `AiServiceImpl` 依赖。
- `MessageCommandServiceImpl` 不再直接依赖 `ChatClientFactory` / `ChatClient`，改为依赖 `AiService`。

### 5. 基础设施统一进 `cn.edgarli.infrastructure.*`
| 子包 | 当前类 | 备注 |
| --- | --- | --- |
| `security/` | `SecurityConfig` / `JwtService` / `JwtAuthenticationFilter` / `AuthPrincipal` / `RestAuthenticationEntryPoint` / `RestAccessDeniedHandler` | Spring Security 配置 |
| `config/` | `FlexConfig` / `AdminProperties` / `LogProperties` / `TrashProperties` / `FilterConfig` | 配置类 |
| `task/` | `ConversationCleanupTask` / `LogCleanupTask` | 定时任务 |
| `observability/` | `TraceIdFilter` + 新增 `@TraceId` 注解（如有） | 访问日志 / trace_id |
| `audit/` | `@Auditable` / `AuditAspect` / `AuditLogDo` / `AuditLogMapper` | 业务审计 |

### 6. Entity / DTO 分层：**DO / BO / VO 三层**
- **DO（Data Object）**：持久化对象，对应 DB 表（当前 entity/*.java 改名为 Do 后缀）。
- **BO（Business Object）**：业务中间对象，service 层跨 mapper 组合（典型如 `UserWithKeys` / `ConversationWithMessages`）。
- **VO（View Object）**：对外 Response / Request record。
- **转换层**：`cn.edgarli.web.converter` 子包，按 entity 分文件（`UserConverter` / `UserApiKeyConverter` / 等）；DO ⇄ VO 显式手动转换（不引 MapStruct，避免外部依赖）。

### 7. DO 命名：**加 Do 后缀**
- `User` → `UserDo`
- `UserApiKey` → `UserApiKeyDo`
- `Conversation` → `ConversationDo`
- `Message` → `MessageDo`
- `AiCallLog` → `AiCallLogDo`
- `AuditLog` → `AuditLogDo`
- 包名 `entity` 保留——已迁移的 `@Table` 注解扫描路径不变。

### 8. 引入 XML Mapper
- 仓库当前 `src/main/resources/cn/edgarli/mapper/` 为空（仅 schema.sql + logback-spring.xml + rebel.xml）。
- `FlexConfig` 已配置 `classpath:cn/edgarli/mapper/**/*.xml` 扫描路径——**新增 XML 不需改 Java 配置**。
- 迁移范围（本次）：
  - **`ConversationMapper.xml`**：`findActiveByUserId` / `findDeletedByUserId` / `findByIdAndUserId` / `findNonOrphanedContextBefore` / `softDelete` / `restore` / `updateTitle` / `hardDeleteOlderThan` / `touchUpdatedAt`
  - **`MessageMapper.xml`**：`findByConversationId` / `findNonOrphanedContext` / `markOrphansAfter` / `markOrphan` / `existsNonOrphanedUserMessage`
- 其他 mapper（User / UserApiKey / AiCallLog / AuditLog）的动态查询**保持 `QueryWrapper.create()` 链式**——简单 CRUD 仍走 `BaseMapper<T>` 默认方法。

### 9. 注释粒度 + 双语
- **方法 Javadoc**：每个 public / protected 方法都要写；包含 `<p>` 段落、`@param` / `@return` / `@throws`。
- **`@param name`**：每个方法参数都要写。
- **局部变量 `//`**：方法内的每个局部变量（`String trimmedEmail = trim(email);`）也要写行内 `//`。
- **语言**：**每行完全双语**（中文 + 英文同行）。
  - 类级 Javadoc：第一行英文摘要，下面中文详细描述。
  - 方法 Javadoc：中文主体 + 第一行英文摘要。
  - `@param userId 用户 ID / user identifier`
  - `@return AI 回复 SSE emitter / SSE emitter for AI reply`
  - `@throws BizException 4035 默认 Key 不可用 / 4035 default key unavailable`
  - 行内 `//`：中文一行 + 英文一行（例：`String trimmedEmail = trim(email); // 去掉前后空白的邮箱 / email trimmed of leading/trailing whitespace`）
- **覆盖范围**：全仓 ~50 个 Java 源文件 + 2 个 XML mapper 文件。

## 考虑过的选项

### §2.1 Service 接口命名
- **A. I 前缀接口（`IUserService` + `UserServiceImpl`）** — 拒绝。Java/C# 传统风格，但 Spring 社区已不推荐，IDE 跳转不如同名风格顺。
- **B. 同名接口 + Impl 后缀（`UserService` + `UserServiceImpl`）** — **已选**。Spring 主流；同名让 IDE / 编译器跳转最直观。
- **C. 接口与实现不同名（`MessageApi` + `MessageServiceImpl`）** — 拒绝。语义模糊，不推荐。

### §3.1 MessageService 拆法
- **A. 单接口 + 实现内拆分** — 拒绝。改动最少，但 6 个职责仍然压在一个 service，"职责太重"问题没解决。
- **B. 拆 2 接口 + 组合接口（Query + Command + MessageService）** — **已选**。表达清晰；Controller 注入 1 个接口同时拿到查询与命令能力。
- **C. 拆 2 接口 + Controller 分别注入** — 不选。Controller 调用点变多，需在 controller 交叉传 deps。
- **D. 拆 3 接口（Query + Edit + Stream）** — 不选。粒度过细，调用点跨多接口会难维护。

### §4.1 AI 子包归宿
- **A. AI 作为基础设施**（`cn.edgarli.infrastructure.ai`）— 拒绝。AI 调用既是"外部资源"也是"业务"，划入基础设施会模糊业务边界。
- **B. AI 作为 Service 层一部分**（`cn.edgarli.service.ai`） — **已选**。与"AI 调用的语义归业务"对齐。
- **C. AI 作为 Mapper 层一部分**（`cn.edgarli.mapper.ai`） — 拒绝。AI 不是数据访问。
- **D. 保留现状（不动 ai/）** — 拒绝。用户明确痛点。

### §5.1 基础设施归类
- **A. 统一进 `cn.edgarli.infrastructure`** — **已选**。名称一致；按职责拆子包清晰。
- **B. 区分 `shared/` 与 `infrastructure/`** — 拒绝。区分"杂项"与"独立设施"没有可操作标准。
- **C. 全部进 `shared/`** — 拒绝。一个名字不分类，扁平化反模式。

### §6.1 Entity / DTO 分层粒度
- **A. 只补 User / UserApiKey 两个 ResponseVO** — 拒绝。其他 entity 的 VO 已是单独类（如 `ConversationResponse`），形式不一致。
- **B. 每个 entity 都补 VO，entity 不外发** — 不选。被 §6.1 C 替代（更彻底）。
- **C. DO / BO / VO 三层分离** — **已选**。每个 entity 配 DO（持久化）/ BO（业务组合）/ VO（响应）三件；转换显式；持久化与对外响应彻底解耦。

### §7.1 DO 命名
- **A. 加 Do 后缀（`UserDo`）** — **已选**。语义明确；现有 import 批量改可控。
- **B. 不加后缀（保留 `User`），仅迁移包到 `dataobject/`** — 拒绝。service 看到 `User` 不知道是 DO 还是 BO，需看 import 才能判定。
- **C. 换名词 + 改包名（`cn.edgarli.persistence.UserRecord`）** — 拒绝。会让现有代码 import 全面重造，过度。

### §8.1 XML mapper 范围
- **A. 仅 LogsController 的 AI 调用 / 审计查询迁 XML** — 不选。用户选了更大范围。
- **B. Conversation / Message 业务查询全迁 XML** — **已选**。业务查询"传统"化；annotation 只留 DO 映射。
- **C. 全仓所有自定义查询都迁 XML** — 不选。User / UserApiKey / AiCallLog / AuditLog 的动态查询用 QueryWrapper 已经够简洁。
- **D. 仅在"确实需要复杂 SQL"的场景用 XML** — 拒绝。判断标准模糊。

### §9.1 注释语言
- **A. 纯中文** — 拒绝（用户改主意）。
- **B. 纯英文** — 拒绝（与项目现有 Javadoc 不一致）。
- **C. 中英双语（中文一行 + 英文一行）** — 不选。"中文一行 + 英文一行"过于冗长。
- **D. 每行完全双语（中文 + 英文同行）** — **已选**。`@param userId 用户 ID / user identifier` 这种格式统一；信息密度高。

## 后果

### 包结构变化（重构后）

```
cn.edgarli
├── MyAiApplication.java
├── web/                                  # Controller + Request/Response (VO)
│   ├── *Controller.java (8 个)
│   ├── dto/                              # VO: Request / Response record
│   └── converter/                        # DO ⇄ VO 转换类（按 entity 分文件）
├── service/                              # 业务服务（接口 + 实现分离）
│   ├── AuthService + AuthServiceImpl
│   ├── UserService + UserServiceImpl
│   ├── UserApiKeyService + UserApiKeyServiceImpl
│   ├── ConversationService + ConversationServiceImpl
│   ├── MessageQueryService + MessageQueryServiceImpl
│   ├── MessageCommandService + MessageCommandServiceImpl
│   ├── MessageService                    # 组合接口（extends Query + Command）
│   ├── MessageServiceImpl                # 委托
│   ├── AiCallLogService + AiCallLogServiceImpl
│   └── ai/                               # AI 子层
│       ├── AiService + AiServiceImpl
│       └── provider/
├── business/                             # BO 业务对象
├── mapper/                               # DAO 接口
│   ├── ConversationMapper.java
│   └── MessageMapper.java
├── entity/                               # DO 持久化对象（加 Do 后缀）
├── infrastructure/                       # 基础设施
│   ├── security/
│   ├── config/
│   ├── task/
│   ├── observability/
│   └── audit/
├── common/                               # Result, BizException, GlobalExceptionHandler
└── resources/cn/edgarli/mapper/          # XML mapper 落点
    ├── ConversationMapper.xml
    └── MessageMapper.xml
```

### 数据模型变更
- **无**。DB schema 不动；6 个 entity 改名为 `XxxDo` 但表名 / 列名都不变。

### API 变更
- **无**。所有 `/api/*` 端点路径 / 请求体 / 响应格式 / 业务码都不变；纯代码组织重构。

### 依赖新增
- **无**。现有依赖（Spring Boot 4 / MyBatis-Flex / Spring Security / JJWT / HikariCP / Spring AI / Lombok）全部够用。

### 错误码 / 业务码变更
- **无**。

### 性能 / 资源
- **无明显影响**。Service 多了一层接口间接调用（一次额外的方法分派），可忽略。

### 影响范围预估

| 类别 | 数量 |
| --- | --- |
| 新增 Java 文件 | ~38（6 个 Impl + 8 个 Converter + ~6 个 BO + 1 个组合 MessageServiceImpl + 1 个 AiServiceImpl + 1 个组合接口 + 其他） |
| 新增 XML 文件 | 2（`ConversationMapper.xml` + `MessageMapper.xml`） |
| 修改 Java 文件 | ~50（service / controller / mapper / entity / 横切 全 import 改） |
| 注释改动 | 全仓 ~50 个 Java + 2 个 XML — 方法 Javadoc + @param + 局部 //（每行双语） |

### 验证方式
1. `mvn -DskipTests package` 必须通过。
2. 启动后 smoke：
   - `POST /api/auth/register` + `POST /api/auth/login` + `GET /api/auth/me`
   - 创建 conversation + 流式 SSE 发消息（验证 `AiService` 重构后 SSE 协议不变）
   - 创建 Key / 改 Key / 删 Key（验证 `@Auditable` 仍生效，路径更新后 AOP 仍拦截）
   - `MYAI_ADMIN_EMAILS=...` 启动后 admin 调 `/api/logs/ai-calls` + `/api/logs/audit`
3. XML mapper 验证：发消息后查 DB 看 ai_call_log 是否正确写入（验证 Conversation / Message XML 迁移后业务不变）。

### 落地 commit 拆分（5 个）
1. **阶段 A**：基础设施搬迁（security/config/task/observability/audit 进 `infrastructure`），不动业务
2. **阶段 B**：Service 接口/Impl 分离 + AI 子包重构（拆 MessageService + 引入 AiService）
3. **阶段 C**：DO/BO/VO 分层（6 个 entity 加 Do 后缀、补 VO、补 converter、补 BO）
4. **阶段 D**：Conversation + Message XML mapper 迁移
5. **阶段 E**：全仓注释补齐（方法 Javadoc + @param + 局部 //，每行双语）

每阶段 `mvn -DskipTests package` 验证。

### 已知风险
1. **机械重构量大**（~88 个文件改动 / 新增）—— 单 commit 会让 review 困难，必须分 5 个 commit 提交。
2. **Service 接口拆分后 AOP 仍生效**——`AuditAspect @Around @annotation(auditable)` 按方法签名拦截，接口拆分不破坏拦截（接口方法和实现方法签名一致）。
3. **MessageServiceImpl 委托模式有"双重间接"**——调用 `messageCommandService.streamReply(...)` 等于经过两层；性能可忽略，但 debug 栈会深一层。
4. **XML mapper 与 BaseMapper 默认方法并存**——简单 CRUD 仍走 BaseMapper，复杂查询走 XML；维护者需要知道哪些方法在 Java 里、哪些在 XML 里。
5. **双语注释体量大**——每个局部变量都双语会让代码体积增加约 30-50%；运行时无影响。

### 不做什么
- 不引入 MapStruct（外部依赖，手动 converter 够用）
- 不做 ORM 切换（已是 MyBatis-Flex）
- 不动 DB schema / API 路径 / 业务码
- 不重写 service 业务逻辑（仅接口 / 实现分离；方法体内部逻辑不变）

## 状态

🚧 **设计定稿**（2026-07-28 grill-with-docs 完成 12 决策）。**等用户通知后实施**——本 ADR 不写任何业务代码，只落设计。

## 关联

- [[0003-conversations-and-messages]] — MessageService 拆三件套基于 ADR 0003 的 `MessageService` 实现；接口拆分不破坏 §4 PATCH edit 语义。
- [[0004-observability]] — AuditAspect 路径从 `cn.edgarli.observability` 迁到 `cn.edgarli.infrastructure.audit`；AuditLog 实体改名为 `AuditLogDo`；@Auditable 拦截逻辑不变。
- CLAUDE.md §4 关键架构约定将在实施期更新，新增"包结构 / Service 接口分离 / 三层数据对象"约束。
- CLAUDE.md §6 安全边界（不涉及本 ADR）。

---

## 实施 checklist（待用户通知后启动）

- [ ] 阶段 A：基础设施搬迁（security/config/task/observability/audit 进 `infrastructure`）
- [ ] 阶段 B：Service 接口/Impl 分离 + AI 子包重构（拆 MessageService + 引入 AiService）
- [ ] 阶段 C：DO/BO/VO 分层（6 个 entity 加 Do 后缀、补 VO、补 converter、补 BO）
- [ ] 阶段 D：Conversation + Message XML mapper 迁移
- [ ] 阶段 E：全仓注释补齐（方法 Javadoc + @param + 局部 //，每行双语）
- [ ] 每阶段 `mvn -DskipTests package` 通过
- [ ] 启动后 smoke（5 个端到端路径）