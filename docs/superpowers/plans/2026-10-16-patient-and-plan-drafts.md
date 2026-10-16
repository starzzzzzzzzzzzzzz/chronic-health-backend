# 患者资料与方案草稿第一批实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans for inline implementation, or superpowers:subagent-driven-development only if the user explicitly selects that execution method. Execute task-by-task with failing tests first and a verification gate after each task.

**Goal:** 用一位明确虚构的患者完成“患者列表 → 健康资料 → 新建方案草稿 → 编辑 → 保存 → 重新打开”的真实数据库闭环。

**Architecture:** 保持 Controller / Service / Mapper 分层和单体结构。使用 MyBatis-Plus 做基础数据访问、XML 实现范围查询及聚合加锁；事务由 Service 统一控制。开发联调使用显式开启的本地 DEMO 身份，正式认证尚未接入时默认拒绝业务访问。

**Tech Stack:** Java 17、Spring Boot 4.1.1、MyBatis-Plus 3.5.17、MySQL 8.4、Flyway，使用现有项目脚本。

**Spec:** [数据库设计总览](../../database/DATABASE-DESIGN.md)、[字段字典](../../database/TABLE-DICTIONARY.md)、[页面映射](../../database/VIDEO-FIELD-MAPPING.md)。本计划只覆盖 A 批；B/C/D 不在本次交付范围。

## Global Constraints

- 第一批 12 表：patient、patient_access_grant、health_fact、health_input_snapshot、care_plan、care_plan_version、care_plan_module、plan_management_focus、care_plan_dimension、care_plan_item、plan_item_basis、audit_log。
- 原 Flyway V1 不修改；原元数据表和现有数据保留。业务迁移只追加版本，模拟数据不写入默认结构迁移。
- 业务 ID 使用 UUID / CHAR(36)，接口使用 String；外部标识 VARCHAR(64)，不将外部 archiveId 当成本地主键。
- RESTful 路由前缀 `/ncd-ai`；正确使用 200、201、400、401、403、404、409、500。新建成功提供 Location。
- 所有业务查询验证可信机构、DEMO 模式及患者授权，不能由请求体自行指定操作者或切换数据范围。
- 新业务时刻使用 UTC DATETIME(6)，业务日期配 zone_id；在 JSON/API 与 JDBC 边界显式转换，验证不能只靠容器时区。
- row_version 用于并发；content_revision 只随内容变化递增；修改子表必须锁定/更新父版本。
- 模块 B0/L/R/C/F/A、画像 H/F/B/C/E/G、七维枚举是不同分类。
- 首批为手工草稿；模拟患者资料和来源明确标记 DEMO。不自动生成诊断、处方、医学阈值或未确认的权重。
- 保留环境和 scripts/mvn.sh、scripts/db.sh、scripts/run.sh、scripts/test-db.sh 的既有用途；不引入 Redis、消息队列、ORM 替换或额外运行时依赖。

## Review Focus

- 同一机构内另一位患者的事实、条目 ID 被混入请求：拒绝，整个保存回滚。
- JSON 对象键顺序不同但内容相同：规范化后内容摘要一致；不靠普通 Map 遍历顺序算哈希。
- 两个请求以相同 expectedRevision 保存：仅一个成功，另一个 409，不能部分覆盖子表。
- 患者资料改变后重读旧方案：旧输入快照保持原值与来源；不能从当前资料动态拼回旧快照。
- DEMO 关闭、无效凭证、过期/撤销授权或 REAL 记录：不得因开发便利而允许访问。

上述五项分别归属 Task 4、Task 4、Task 4、Task 3/4、Task 2；每项均有对应测试。

## 本批范围和需评审的实施选择

1. 首次创建手工方案草稿视为一次明确的资料输入使用：在同一事务读取患者 profile_revision 与当前事实，创建 PLAN 快照并绑定版本。GET 资料/草稿不创建快照。没有审核或生成接口，后续新增生成可创建自己的输入快照。
2. 手工创建时允许主目标为空、维度为空，便于保存空草稿；已有模块/条目必须结构合法、有来源。草稿保存不等于具备送审资格。
3. local profile 且显式启用 demo 时，使用本机生成的随机原始 Authorization Token。服务端固定主体 `demo-clinician`、机构 `demo-org`、模式 DEMO；请求不能切换主体/机构。原始 Token 格式与前端参考约定一致，这不是生产登录方案。
4. 新增 `scripts/run-demo.sh`：本地初始化/加载 `.env.demo`（权限 600、Git 忽略），设置 demo 开关和 Token，然后调用现有 run.sh。Token 不打印到日志。默认 run.sh 继续不开放 DEMO 身份。
5. demo 启用后由幂等初始化器创建一位虚构患者、六方面示例事实、PATIENT_READ 与 PLAN_EDIT 授权。无真实姓名/联系方式/处方；不创建“已审核”记录。demo 关闭不自动删数据。
6. PATCH 采用普通 application/json，支持修改明确白名单字段；未提供的集合保持原状，提供的集合表示整体替换。初稿集合先在服务端全部校验，再在一个事务替换；原 ID 只允许引用当前目标子行，新子项 UUID 由服务端生成。
7. 首次列表采用 page=1、size=20，size 范围 1..100；q 长度不超过 100，按姓名字面子串匹配并转义 SQL LIKE 特殊字符；排序固定 created_at DESC,id DESC。范围计数与列表使用相同授权条件。
8. 不增加“删除患者”、正式医疗规则编辑、自动分配权重、审核、任务生成、发布、打卡等接口。后续分批实现。

## 文件责任与对外接口

### 待新增或修改文件

| 范围 | 路径 | 责任 |
|---|---|---|
| 数据库迁移 | `src/main/resources/db/migration/V2__create_patient_foundation.sql` | 患者、授权、事实、输入快照四表 |
| 数据库迁移 | `src/main/resources/db/migration/V3__create_care_plan_drafts.sql` | 方案、版本、模块、侧重、维度、条目、依据、审计八表；最后追加循环 FK |
| 实体与 Mapper | `src/main/java/com/healthsystem/entity/`、`mapper/` | 12 表对应具名实体和 Mapper，不使用一个通用 JSON Repository 代替业务对象 |
| XML | `src/main/resources/mapper/PatientMapper.xml`、`CarePlanMapper.xml`、`HealthFactMapper.xml` | 范围查询、父行锁、当前事实查询与原子修订更新；其余表优先 BaseMapper |
| 患者业务 | `service/PatientService.java`、`service/impl/PatientServiceImpl.java` | 分页、患者服务授权和六方面资料读取 |
| 草稿业务 | `service/CarePlanService.java`、`service/impl/CarePlanServiceImpl.java` | 创建、读取、事务编辑、依据校验、快照和审计 |
| DTO/VO | `dto/`、`vo/` | 具名请求记录及返回对象；不得直接暴露 Entity 或含来源凭证的原始对象 |
| API | `controller/PatientController.java`、`controller/CarePlanController.java` | HTTP 参数、校验、状态与 Location |
| 错误与 JSON | `exception/BusinessException.java`、`exception/GlobalExceptionHandler.java`、`common/api/ApiResponse.java`、`common/json/CanonicalJson.java` | 统一异常、与 HTTP 状态一致的响应、稳定哈希 |
| DEMO 身份 | `common/security/BusinessPrincipal.java`、`common/security/DemoAuthenticationFilter.java`、`config/DemoConfiguration.java`、`config/SecurityConfiguration.java` | 显式 DEMO 身份；非 DEMO 默认关闭业务访问 |
| DEMO 数据 | `service/impl/DemoDataInitializer.java`、`src/main/resources/demo/patient.json` | 幂等加载虚构资料和两项开发授权 |
| 配置与脚本 | `application.yaml`、`application-local.yaml`、`.gitignore`、`scripts/run-demo.sh` | demo 开关、UTC 边界和本地凭证文件 |
| 测试 | `src/test/java/com/healthsystem/PatientFoundationDatabaseTest.java`、`PatientApiTest.java`、`CarePlanDraftDatabaseTest.java`、`CarePlanApiTest.java`、`CanonicalJsonTest.java` | 真库行为、HTTP 契约、范围和并发 |
| 说明 | `README.md`、`docs/API-CONTRACT.md`、`docs/database/` | 与实现一致的启动方式、示例请求、已实现/未实现状态 |

路径未写全的 Java 文件均位于 `src/main/java/com/healthsystem/`；测试沿用当前目录，不新设复杂多模块工程。

### 第一批 HTTP 契约

| 方法/路径（均带 /ncd-ai 前缀） | 请求 | 成功结果 |
|---|---|---|
| GET /patients | page、size、q | 200，PageVO<PatientSummaryVO>，包含 page/size/total/items |
| GET /patients/{id}/health-summary | 路径 ID | 200，PatientHealthSummaryVO，含 patient、profileRevision、axes、facts、conflicts、missingFields |
| POST /care-plans | patientId、expectedProfileRevision、title、startDate、endDate、zoneId、primaryGoal（可空） | 201 + `/ncd-ai/plan-versions/{versionId}` Location；CarePlanDraftVO |
| GET /plan-versions/{id} | 路径 ID | 200，CarePlanDraftVO，含内容集合、rowVersion、contentRevision、inputSnapshotId |
| PATCH /plan-versions/{id} | expectedRevision 必填；可选标题、周期、目标、补充要求、模块、侧重、维度、条目/依据集合 | 200，完整 CarePlanDraftVO；陈旧修订或非 DRAFT 返回 409 |

校验失败 400；无有效身份 401；有身份但已知患者无相应能力返回 403；不可见/不存在的资源按统一策略返回 404，避免跨范围枚举。具体采用：患者/方案不存在或不在主体机构/模式范围返回 404；同范围患者缺有效读写能力返回 403。

## Task 1 数据迁移和 MyBatis 映射

**Interfaces:** 产出 12 张 A 批表及对应实体/Mapper；业务引用使用 UUID，created/updated 时间明确为 UTC；V1 与原 MyBatis 元数据测试保持可运行。

- [x] 写 `PatientFoundationDatabaseTest.migrationsCreateScopedTablesAndPreserveMetadata()`：断言 12 表、复合外键、主要唯一键存在，app_metadata 原值不变；插入跨 org 的 patient FK 应失败；同组 supersedes_fact_id 的重复后继应失败。
- [x] 运行 `./scripts/test-db.sh -Dtest=PatientFoundationDatabaseTest`，确认预期因业务表缺失失败。
- [x] 按字典生成 V2/V3，使用 `CHAR(36) ... ascii_bin` 主键与 `VARCHAR(64) ... utf8mb4_bin` 编码/机构标识；普通中文名称保留默认中文排序规则。JSON 列通过明确映射读写，不依赖未经验证的自动 JSON 类型处理。
- [x] V3 先建 care_plan（current_version_id 可空）与版本，再追加循环 FK；外键包含 org_id，检查 current_version_id 归属由服务补齐。B/C/D 表外键不提前生成。
- [x] 为 12 表建立具名实体与 Mapper；按现有 Java 习惯使用显式访问器，不增加 Lombok。JSON 持久化字段可先用 String 在服务显式序列化；API 使用 Jackson 3 树/具名 DTO，不混用 Jackson 2 注解类型处理器。
- [x] 运行本任务真库测试及原有测试，验证北京时间跨日的样例 UTC 写入/读取不发生二次偏移。
- [x] 提交：`feat: add patient and care plan draft schema`。

## Task 2 显式 DEMO 身份与可重复示例数据

**Interfaces:** `BusinessPrincipal(String orgId, String subjectId, String dataMode)`，服务只接受该可信主体；DEMO token 来自环境变量，控制台不输出。后续任务可用同一个主体做范围校验。

- [x] 写身份测试：开关关闭或非 local 时 Token 无效；开启且 Token 正确时允许进入患者/草稿路由；无效 Token=401；尝试读取 REAL 或其他机构数据=404；有效范围内撤销授权=403。
- [x] 先运行身份测试并确认因业务身份未接入失败。
- [x] 实现 DemoAuthenticationFilter：常量时间比较已配置 Token；仅 local + demo 开关有效；不能信任请求中的 orgId/subjectId/dataMode。避免 Filter 同时被 Servlet 自动注册和 SecurityFilterChain 重复执行。
- [x] 限定只给本批 5 个业务入口加 authenticated；其它业务、Actuator 内部接口继续拒绝。错误响应沿用统一 401/403 JSON。
- [x] 初始化一位虚构患者与六方面来源，DEMO 来源不冒充专业确认；幂等第二次启动不覆盖人工改动、不重复加授权。示例 ID 固定并预留命名域；检测同 ID 非 DEMO 冲突时停止初始化而非覆盖。
- [x] 编写 run-demo.sh，原子创建本地随机 Token、权限 600；`.env.demo` 加入忽略；初始化器及脚本不把 Token 写入审计/日志。
- [x] 验证上述边界、默认健康接口和原安全测试；提交：`feat: add explicit local demo identity and seed data`。

## Task 3 授权患者列表和六方面健康资料

**Interfaces:** `PatientService.list(BusinessPrincipal principal, int page, int size, String query): PageVO<PatientSummaryVO>`；`PatientService.healthSummary(BusinessPrincipal principal, String patientId): PatientHealthSummaryVO`。提供仅服务内部使用的患者锁定和授权校验，供 Task 4 复用。

- [x] 写分页和范围测试：同主体仅返回有效授权患者，COUNT 与列表范围一致；无效 page/size=400；姓名 q 中 `%`/`_` 按字面匹配；相同 created_at 时 id 排序稳定。
- [x] 写资料测试：六轴均存在；缺失维度保持 UNKNOWN；事实含来源与观测时间；已被修订的事实不作为当前值；不同来源冲突保留；不因查看资料创建 health_input_snapshot。
- [x] 运行对应测试，确认业务 API 尚不存在而失败。
- [x] 实现 PatientController、PatientServiceImpl 和 XML 查询；枚举值集中在具名类型/常量中；时间与序列化遵循 UTC 存储、API 偏移时间约定。
- [x] 在 exception/ 增加 BusinessException 与统一处理；扩展 ApiResponse 支持 201 等真实状态；保留原健康响应形态。无效 JSON/日期/枚举 400，未识别服务器异常不回传 SQL 或病历内容。
- [x] 运行本任务 HTTP/真库测试与 WebFoundationTest；提交：`feat: expose scoped patient and health summary APIs`。

## Task 4 草稿聚合保存、快照与并发控制

**Interfaces:** `CarePlanService.create(BusinessPrincipal principal, CreateCarePlanRequest request): CarePlanDraftVO`；`get(BusinessPrincipal principal, String versionId): CarePlanDraftVO`；`update(BusinessPrincipal principal, String versionId, UpdateCarePlanRequest request): CarePlanDraftVO`。

- [x] 先写完整往返测试：选虚构患者 → POST 草稿=201 与 Location → PATCH 模块/侧重/维度/条目及依据 → GET 内容一致；所有 API ID/修订号为 String，父子行同 org、同方案、同患者。
- [x] 写并发测试：两个独立事务携相同 expectedRevision 修改，恰好一个成功，一个 409；成功后 row_version 和 content_revision 各增 1，失败事务无子行或审计残留。测试不能用单个外层回滚事务假装并发。
- [x] 写错误测试：来源事实属于别的患者/不在输入快照、条目引用别版本维度/模块、权重不合 10000、结束日期早于开始日期、非法时区、非 DRAFT 编辑、非法枚举，全部拒绝且回滚。
- [x] 写 PATCH 语义测试：省略集合不修改；空集合表示清空允许为空的草稿集合；替换子行时按 FK 依赖顺序处理，保留被请求引用的合法 ID，不能删父模块后残留依据；未知/外来子 ID 拒绝。
- [x] 写快照测试：创建时 expectedProfileRevision 陈旧返回 409；创建成功保存实际读取的患者/事实；随后患者事实追加修订，再读旧草稿时快照原值保持。GET 不产生新快照。
- [x] 写 CanonicalJsonTest：对象键排序不同哈希相同；相同逻辑数据的不同序列化稳定；条目顺序按显式 sortOrder/稳定 ID 归一；真实内容变化哈希改变。
- [x] 逐项运行测试看见缺失功能失败，再实现事务逻辑：患者范围校验与锁定 → 读取/保存输入快照 → 新建身份/版本/七维决定 → 回填当前工作指针 → 审计；编辑按方案版本行锁 + row_version 验证，校验完整待保存聚合后写入子表、更新双修订与哈希。
- [x] 初稿没有一审/任务包数据，若状态不是 DRAFT 一律拒绝；保存只执行结构、引用与已确认边界校验，不承诺自动医学正确性。
- [x] 测试事务回滚、快照、并发和 HTTP 状态；提交：`feat: persist versioned care plan drafts with input snapshots`。

## Task 5 实际启动联调与交付

**Interfaces:** 提供真实可访问的本地 DEMO 服务和一组不含真实凭证的接口示例，供前端伙伴按视频接页面。

- [x] 运行 `./scripts/db.sh up`，再执行 `./scripts/test-db.sh`；普通 `./scripts/mvn.sh -B -ntp verify` 也通过并明确跳过真库测试。
- [x] 使用 run-demo.sh 在空闲端口启动本次代码；检查现存 9088 进程归属，避免把旧服务误当本次版本。若端口被旧实例使用，显式选 9089 联调，不终止未知进程。
- [x] HTTP 实测列表、健康资料、新建、PATCH、GET 往返、陈旧保存 409、缺凭证 401；检查 Location、X-Request-Id、中文内容和偏移时间。
- [x] 关闭 demo 开关再验证业务访问被拒绝；检查 `.env`/`.env.demo`/缓存/target 未进入暂存区。
- [x] 更新 README、API-CONTRACT 与数据库文档中的“已实现/未实现”，记录执行命令、测试结果、未接真实认证/AI/审核/发布的边界。
- [x] 内联自审聚合事务、跨患者引用、父子修订、幂等初始化及异常路径；修复后只重跑受影响测试和必要完整验证。
- [x] 提交并推送至当前私有仓库，报告提交号和前端可开始对接的 5 个接口。

## 计划自检

- 覆盖 A 批全部表及第一批闭环；B/C/D 仅保留设计，不提前创建半成品业务路由。
- Review Focus 五类风险均已指定测试；JSON、同患者/同版本关联不能靠 FK 类型检查替代业务校验。
- 新增 DEMO Token 是本计划的实施选择，需随计划评审确认；不会悄悄将“任意 Authorization 值”当真实认证。
- 新建与编辑响应、双修订语义、集合替换和快照时机已写明。未确定的病种字典/医学配比不在本批自动生成。
- 当前状态：计划已确认，V2/V3、患者接口和草稿闭环已实现；最终真库测试 28 项全部通过，常规构建通过（6 项执行、22 项真库测试按开关跳过），真实 HTTP 联调通过。

## 执行方式

建议由当前会话直接逐项实现（Native）：本批的数据迁移、身份范围和草稿事务紧密相关，统一实施更容易保持接口一致。只有用户选择分工方式后才启用子代理；计划本身不授权额外代理或真实医疗服务接入。

## 实施结果与裁定

- 当前会话直接实施于功能分支 feat/patient-plan-drafts，未改动其他运行服务。
- 复用四个公共字段的 ScopedEntity；草稿子表存取和校验拆为 PlanDraftStore / PlanDraftValidation，保持 Service 事务边界。
- MyBatis 一级缓存设为 STATEMENT，避免锁定读取复用过时实体；已审状态拒改有真实数据库回归测试。
- 仅修改 changeReason 时只更新行修订，业务内容修订不变；相同内容保存为无操作。
- 独立审查发现 null 依据条目会返回 500，已先复现再修复为 400，并验证无子表/修订/审计残留。
- 真库测试开关显式标注在具体测试类，确保普通构建不初始化真库 Spring 上下文。
- 实际 DEMO 服务在本机 9089 联调；默认模式临时实例已验证拒绝 DEMO 凭证后关闭。原 9088 服务保留。
