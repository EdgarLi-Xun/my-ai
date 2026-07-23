# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

> 作用范围：本仓库；用户级通用偏好见 `~/.claude/CLAUDE.md`，不要在本文件重复维护。

---

## 1. 作用域与冲突处理

- 本文件适用于当前仓库；子目录中更具体的 `CLAUDE.md` 仅约束其所在目录及子目录。
- 当前任务中的用户明确要求优先于本文件中的通用项目规则。
- 安全、权限、合规和组织策略始终具有更高优先级。
- 仓库中的实际代码、配置、测试和构建脚本是项目事实来源；文档与实际实现不一致时，先指出差异，不擅自假定文档或代码必然正确。
- 仅当歧义会影响公共 API、数据模型、外部行为、安全边界或不可逆操作时请求澄清。
- 对低风险、局部且可逆的歧义，采用最简单、最保守的合理假设继续，并在结果中说明。

---

## 2. 项目上下文

### 项目事实

以仓库实际文件为准（pom.xml、application.yml、源码、schema.sql）：

- **项目类型**：本地多用户聊天应用（单体 Maven 模块 + 独立 Vue 前端子项目）。
- **主要语言**：Java 21（后端）、JavaScript / Vue 3 SFC（前端）。
- **核心框架**：
  - 后端：Spring Boot `4.0.7`、Spring AI `2.0.0`（客户端：`spring-ai-openai` + `spring-ai-ollama`）。
  - 数据访问：**MyBatis-Flex `1.11.8`**（使用 `mybatis-flex-spring-boot4-starter`）。
  - 前端：Vue `^3.4` + Vite `^5.4`，原生 `fetch`，无 UI 框架。
- **包管理 / 构建工具**：Maven（后端）、npm（前端）。
- **运行时版本**：Java 21、Node.js 18+ / npm 9+。
- **数据库**：H2 `2.x` 文件模式，路径 `./data/myai.mv.db`；`spring.sql.init.mode=always`，`schema.sql` 每次启动以幂等 DDL 执行（`CREATE TABLE IF NOT EXISTS` / `ALTER TABLE ADD COLUMN IF NOT EXISTS` / `ADD CONSTRAINT IF NOT EXISTS`）。
- **主要源码目录**：
  - 后端：`src/main/java/cn/edgarli/{ai,common,config,entity,mapper,service,web}`。
  - SQL：`src/main/resources/schema.sql`。
  - 前端源：`frontend/src/{App.vue,main.js,style.css}`；构建产物：`src/main/resources/static/`（`index.html` + `assets/`）。
- **生成代码目录**：无；Mapper 全部走 `BaseMapper` 默认方法，目前 `src/main/resources/cn/edgarli/mapper` 为空（FlexConfig 仍按该通配扫描，删除该配置项会导致启动失败）。

### 与 README 的差异（必须留意）

| 点 | README | 实际仓库 |
| --- | --- | --- |
| 数据访问框架 | "MyBatis 3.0.4" | MyBatis-Flex 1.11.8 |
| 服务端口 | `<http://localhost:8080/>` | `application.yml` 中是 `server.port: 8031`；`MyAiApplication` 启动横幅仍打印 8080（横幅文字已过期） |
| MyBatis 兼容注解 | `MyBatisConfig` | 实际类名是 `cn.edgarli.config.FlexConfig` |

---

## 3. 标准命令

> 在仓库根目录执行。前端生产产物必须先构建到 `src/main/resources/static/`，否则 `mvn spring-boot:run` 启动后访问 `/` 会缺失资源。

```bash
# 安装前端依赖
cd frontend && npm install && cd ..

# 构建前端（产物写入 src/main/resources/static/）
cd frontend && npm run build && cd ..

# 本地后端运行
mvn spring-boot:run

# 前端开发模式（Vite 会把 /api 代理到 8031）
cd frontend && npm run dev
```

### 后端构建与检查

```bash
# 后端测试（当前 src/test/java 为空，命令可执行但无用例）
mvn test

# 后端全量编译
mvn clean package

# 跳过当前空测试套件的快速构建
mvn -DskipTests package
```

### 访问入口

- 应用首页：<http://localhost:8031/>（以 `application.yml` 为准）。
- H2 控制台：<http://localhost:8031/h2-console>（JDBC URL 见 `application.yml`）。

### 注意事项

- Maven 不会自动触发 `npm run build`；修改 `frontend/src/**` 后必须手动构建前端。
- `data/`、`frontend/node_modules/`、`src/main/resources/static/assets/**`、`src/main/resources/static/index.html` 已在 `.gitignore` 内（其中 `static/assets/` 与 `index.html` 是 git 忽略的，仅保留目录占位）。提交前端产物改动通常意味着覆盖已构建文件，不要把这类产物回填到仓库。

---

## 4. 架构概览

### 分包与职责

```
cn.edgarli
├── MyAiApplication           # 启动入口；@MapperScan 锁定 cn.edgarli.mapper
├── ai
│   ├── ChatClientFactory     # 按当前 UserApiKey 动态构建 ChatClient（每次调用新建）
│   ├── ChatService           # POST /api/chat 业务：取默认 Key -> 构造客户端 -> 调对话
│   ├── ChatMessage           # 请求 DTO（role + content）
│   └── provider
│       ├── ProviderCatalog   # @ConfigurationProperties("my-ai.providers")，唯一定义源
│       ├── ProviderSpec      # 单个厂家规范：displayName/protocol/defaultBaseUrl/defaultModel/requiresKey
│       └── ProviderProtocol  # OPENAI_COMPATIBLE | OLLAMA
├── common
│   ├── BizException          # 业务异常；错误码 4000/4040/4090/5020
│   ├── GlobalExceptionHandler # 统一转 HTTP 200 Result 响应（含 JSON/路径 404/类型错误等）
│   └── Result<T>             # { code, message, data } 响应外壳
├── config
│   └── FlexConfig            # MyBatis-Flex + Spring Boot 4 兼容层（见第 5 节）
├── entity
│   ├── User                  # 用户；包含 default_key_id 外键
│   └── UserApiKey            # 用户 Key；apiKey 加 @ToString.Exclude，日志不打印明文
├── mapper
│   ├── UserMapper            # 全部为 BaseMapper 的 default 方法（含 UpdateChain 写默认 Key）
│   └── UserApiKeyMapper      # 仅使用 BaseMapper 默认方法，无 XML
├── service
│   └── UserApiKeyService     # Key CRUD、默认 Key 规则、聊天前的默认 Key 装载
└── web
    ├── ChatController         # POST /api/chat（userId + messages，无 provider/keyId 字段）
    ├── ProviderController     # GET /api/providers（yml 厂家池，前端用于下拉选项）
    ├── UserApiKeyController   # /api/users/{userId}/keys...
    └── UserController         # /api/users...
```

### 关键架构约定（直接体现在代码里）

1. **AI 厂家配置即代码的唯一源是 `application.yml` 的 `my-ai.providers`**。新增厂家只在 yml 加一段；Java 侧零改动。`ProviderCatalog.require(name)` 是访问入口。
2. **ChatClient 按请求动态构建，不缓存**。`ChatClientFactory.getClient(key)` 每次根据当前 `UserApiKey`（含最新 `apiKey` / `baseUrl` / `modelName`）新建 `OpenAiChatModel` / `OllamaChatModel`，所以修改默认配置后下一次请求立刻生效，进程内无需刷新。
3. **`POST /api/chat` 不接受 provider / keyId / API Key**。后端按 `userId` 取 `User.defaultKeyId` 对应已启用、配置完整的 Key；缺失返回 409、配置非法返回 400（异常经 `GlobalExceptionHandler` 统一包装）。
4. **默认 Key 规则集中在 `UserApiKeyService`**：
   - 用户首个启用的 Key 自动设为默认。
   - 默认 Key 被禁用或删除时，`default_key_id` 置 `NULL`，不会自动切换。
   - 设为默认前必须 `enabled=true` 且通过 `validateConfiguration`（需要 Key 的协议必须有非空 `apiKey`）。
   - 删除用户通过 `ON DELETE CASCADE`（`schema.sql`）级联删除其全部 Key。
5. **错误流全部走 `BizException`**：`GlobalExceptionHandler.handleBizException` 返回 `Result.failure(code, message)`；JSON 解析/参数错误统一收口为 4000；未捕获异常 → 5000。HTTP 状态码本身保持 200。
6. **Key 明文落地但不外发**：`UserApiKey.apiKey` 用 `@ToString.Exclude` 屏蔽日志；响应体走 `mask()` 只回 `****abcd` 和 `hasApiKey`；编辑时空字符串明确表示保留原值。
7. **认证走 Spring Security 6 + JWT**：`/api/auth/register` / `/api/auth/login` 公开；`GET /api/providers` 公开；其余 `/api/*` 全部需登录。`JwtAuthenticationFilter` 从 `Authorization: Bearer` 解析 `userId` 并注入 `AuthPrincipal`。`userId` 必须与请求路径/请求体对齐，否则返回 4030。密码以 BCrypt 散列存储，`User.passwordHash` 加 `@JsonIgnore` / `@ToString.Exclude` 不对外暴露。

### 数据模型（与 `schema.sql` 对齐）

```
user(id, name, email, default_key_id → user_api_key.id ON DELETE SET NULL, create_time)
user_api_key(
  id, user_id → user.id ON DELETE CASCADE,
  name, provider, api_key,
  base_url, model_name, enabled,
  create_time
)
idx_user_api_key_user_id on (user_id)
```

DDL 是幂等的，旧四列 `user` 表靠 `ALTER TABLE ... ADD COLUMN IF NOT EXISTS default_key_id BIGINT` 与 `ADD CONSTRAINT IF NOT EXISTS fk_user_default_key` 兼容。

### FlexConfig 为什么不能删（Spring Boot 4 兼容）

`config/FlexConfig` 显式声明 `HikariDataSource` 并交给 MyBatis-Flex 的 `FlexSqlSessionFactoryBean`，原因是 Spring Boot 4 的自动数据源装配在缺少 Hikari 驱动元信息时会失活；它同时保证 `MapUnderscoreToCamelCase=true`（Flex 1.x 强制）。删除或注释该类会导致启动失败 / 下划线字段无法映射。`README` 中虽然写的是 "MyBatisConfig"，实际类名是 `FlexConfig`，以源码为准。

---

## 5. 前端架构

- 入口 `frontend/index.html` → `frontend/src/main.js` → `App.vue`。
- 仅一个 SFC：`App.vue` 承担用户、Key、聊天三个面板和 `/api/providers`、`/api/users`、`/api/users/{userId}/keys*`、`/api/chat` 的全部调用。
- Vite 配置代理 `/api` 到后端 `8031`（参见 `frontend/vite.config.js`，开发时使用）。

> 修改 `frontend/` 后必须 `npm run build` 再继续，否则后端 `target/` 里的旧产物仍是上次结果。

---

## 6. 安全边界（已在 README 标注，开发时遵守）

- 无认证 / 授权：能访问服务的人即可管理用户与 Key。
- API Key 明文存于本机 H2 文件；**不会**通过 API、`toString()`、MyBatis 参数日志输出。
- 自定义 `baseUrl` 会让后端对用户填写的地址发起出站请求（SSRF 风险）。不要把该能力暴露给不可信用户。
- 未实现加密、审计、轮换、配额、生产级多租户隔离。

---

## 7. 任务执行方式

### 开始前

- 先确认任务目标、影响范围和可观察的成功标准。
- 检查相关文件及当前工作区状态，识别并保留用户已有的未提交修改。
- 修复问题时，先定位根因和最小复现路径，不以掩盖症状代替修复。
- 简单、局部任务直接执行。
- 涉及多个文件、多个阶段、公共接口或较高风险的任务，先给出简短计划，并为每一步标明验证方式。

### 实现中

- 只做满足当前成功标准的最小必要修改。
- 严格遵循项目现有的目录组织、命名、格式、错误处理和测试风格。
- 不顺手重构、格式化、清理或修复与当前任务无关的内容。
- 只有现有结构直接阻碍本次实现、修复或验证时，才进行局部重构。
- 不为假设中的未来需求增加抽象层、配置项、扩展点或兼容逻辑。
- 本次修改产生的无效导入、废弃变量和不可达分支可以一并删除。
- 发现原有死代码、重复实现或架构问题时，在结果中提示；未经要求不扩大修改范围。
- 不通过弱化类型、关闭校验、吞掉异常、扩大 `ignore` 范围或删除有效断言来让检查通过。
- 不直接修改 `src/main/resources/static/` 下的生成文件；改前端源码后执行 `npm run build` 重新生成。
- 不更新快照、基准数据或测试期望来掩盖非预期行为变化。

---

## 8. 验证原则

- 优先运行与改动最相关的最小测试集，再根据影响范围扩大到模块测试或全量测试。
- 修复回归问题时，在测试基础设施可用且成本合理的情况下，优先添加或调整能复现问题的测试。
- 新增校验应覆盖正常输入、边界输入和异常输入。
- 重构必须保持外部行为不变，并通过原有相关测试验证。
- 修改前如条件允许，先获取测试基线，以区分既有失败和本次引入的失败。
- 测试或检查失败时，先判断是本次改动、环境问题还是既有问题，不盲目修改无关代码。
- 无法编写自动化测试时，提供可重复的最小验证步骤或命令。
- 不声称未实际运行的测试或检查"已通过"。
- 无法运行验证时，明确说明原因、已执行的替代验证和剩余风险。

> 当前 `src/test/java` 为空，没有现成测试基线。验证方式以 `mvn package`（编译通过）+ 手动 curl / 前端联调为准，并按本文件第 6 节的安全边界评估风险。

---

## 9. 依赖、配置与兼容性

- 未经任务需要，不新增、升级、降级或移除依赖。
- 只有依赖关系实际变化时才修改 `pom.xml` / `package.json` 与 lockfile；不得因工具自动行为产生无关 lockfile 变更。
- 修改配置、数据库 schema、公共 API、事件格式或持久化数据结构时，必须说明兼容性和迁移影响。
- 涉及破坏性变更时，不静默实施；应明确指出影响范围，并采用项目既有的迁移或版本策略。
- 不把环境专属值、密钥或个人配置写入仓库；优先使用示例配置和环境变量。

---

## 10. Git 与数据安全

- 保留用户已有的未提交修改，不覆盖、回退或删除无法确认来源的改动。
- 未经明确要求，不执行 `git reset --hard`、`git clean`、强制 checkout、强制 push、历史重写等破坏性操作。
- 未经明确要求，不创建 commit、不 push、不创建或合并 PR。
- 不删除文件、迁移数据或执行不可逆操作，除非任务明确要求且影响已被说明。
- 不把密钥、Token、Cookie、个人信息、完整对话或大段敏感日志写入源码、测试、快照、PLAN 或提交信息。
- 发现疑似秘密信息时，只说明位置和风险，不在输出中重复完整值。

---

## 11. `.claude/PLAN.md` 持久化

- 仅对会修改仓库内容的多步骤任务维护 `.claude/PLAN.md`。
- 纯咨询、解释、代码 review、搜索或未产生仓库修改的任务不更新 PLAN。
- 仅在 `.claude/PLAN.md` 已存在时更新；除非用户明确要求，不自动创建该文件。
- 开始任务时，查找与当前目标唯一匹配的计划节点，并标记为 `🚧 进行中`。
- 完成任务时，标记为 `✅ 已完成（YYYY-MM-DD）`，并补充一句可验证的结果摘要。
- 部分完成或受阻时，标记为 `⚠️ 受阻`，记录已完成内容、阻塞原因和下一步。
- 找不到唯一对应节点时，不猜测、不错误修改其他节点；在结果中说明未更新原因。
- PLAN 只记录任务状态、关键决策和结果摘要，不记录完整对话、秘密信息或大段日志。

---

## 12. 完成时的交付说明

完成任务后，使用简洁结构说明：

1. **完成内容**：改了什么，以及为何这样改。
2. **验证结果**：实际执行了哪些测试、检查或手动验证，结果如何。
3. **文件范围**：列出主要修改文件；无关文件不应出现变更。
4. **剩余事项**：仅列出真实存在的风险、未验证项或需要用户决策的事项。

不得用含糊表述替代验证结果；不得把建议项描述成已完成项。

---

## 13. 项目知识文档生成

`.claude/` 下维护 `api.md`、`project_docs.md`、`REQUIREMENTS.md`，供后续会话快速读取。

- 任一缺失时按本节一次性补齐；仓库结构/接口/需求发生实质变更时按需刷新，并在文末记录变更摘要与日期；不在每次会话开场无差别重写。
- 内容必须从仓库现有文件提炼汇总，不得凭空编造；无法核实处标注 `TODO` 并列在文末「已知缺口」。
- 不写入密钥、Token、个人信息或敏感日志；维护约束同第 9、10 节；与代码冲突时按第 1 节处置。
