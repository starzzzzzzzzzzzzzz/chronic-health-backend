# 第二批：执行条件确认与方案一审实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** 在第一批草稿上实现可追溯的执行条件确认、一审提交、退回、撤回、通过，以及通过后的新版本调整。

**Architecture:** 沿用 Controller / Service / Mapper、MySQL 事务及父版本锁。条件确认是追加记录；审核提交固定方案和确认清单快照，决策永久保留。条件与审核分开，不把勾选条件当作审核通过。

**Tech Stack:** Java 17、Spring Boot 4.1.1、MyBatis-Plus 3.5.17、MySQL 8.4、Flyway；优先项目脚本。

**Spec:** `docs/database/DATABASE-DESIGN.md` 第 3、5、6 章；`TABLE-DICTIONARY.md` condition_confirmation、review_submission、review_decision；本文件的“本批接口与边界”细化本次用户指定的范围。用户已选择当前会话直接实施，本次继续该方式。

## Global Constraints

- 仅 local + 显式 DEMO 开关接受本地凭证；REAL 数据和真实身份接入仍关闭。
- UUID String；机构取自可信身份；每次校验患者模式及有效授权。
- UTC DATETIME(6)；事务锁顺序：patient → care_plan_version → review_submission。
- expectedRevision 是父 row_version；确认适用性和审核目标绑定 content_revision。
- 已送审不能编辑；退回/撤回后允许编辑；已通过旧版不可覆盖，修改建立新版本。
- 不引入新依赖，不改 V1/V2/V3；本批新增 V4 三张业务表，模拟 AI、模板、任务、二审及发布继续后续分批。
- 专业医学规则不由程序自行制定。中医执行条目在平台模板审核未接入前阻断送审。

## 本批接口与边界

| 方法与资源 | 职责 |
|---|---|
| GET /plan-versions/{id}/condition-confirmations | 返回服务端派生的待核实项、当前有效状态及追加历史 |
| POST /plan-versions/{id}/condition-confirmations | 原子追加一批确认；expectedRevision + entries |
| GET /plan-versions/{id}/review-context | 原始方案输入、当前草稿、确认状态、阻断问题、审核历史 |
| POST /plan-versions/{id}/review-submissions | 完整校验后固定快照并进入 IN_REVIEW |
| GET /review-submissions/{id} | 读取不可变送审内容及该次终态/决策 |
| POST /review-submissions/{id}/decisions | 独立审核人 APPROVE / RETURN；退回必须意见 |
| POST /review-submissions/{id}/withdrawals | 申请者本人持 PLAN_EDIT 撤回；说明必填 |
| POST /care-plans/{id}/versions | 从当前 APPROVED 版本克隆新 DRAFT；重新分配子行 ID；不复制确认和审核 |

所有路径前缀 /ncd-ai。POST 创建资源返回 201 + Location。非草稿、修订过期、审核终态重复提交返回 409；结构/依据/确认缺失返回 400；缺少能力或自审返回 403；跨机构、模式不可见返回 404。

### 条件契约

- 每条已选择且 executionGoal=true 的建议，派生 execution.{itemId}，核实执行主体和环境。服务端生成 requirement；客户端不能提供 requirement、confirmedBy 或 bindingHash。
- 执行确认 CONFIRMED 的 value 必须为 `{actorType, available:true, environmentConfirmed:true}`；actorType 与条目相同，confirmationNote 必填。可保存 PENDING 或 REJECTED 以保留待核实/不满足信息。
- 已选择的用药执行条目额外派生 medication.source.{itemId}。CONFIRMED 需要 PLAN_CONFIRM_PROFESSIONAL；sourceFactId 必须是本版本输入快照中 CONFIRMED_PROFESSIONAL 的 medication_order 事实，有确认人/时刻；复制其 value 和来源原文，禁止请求方改写专业值。尚不生成服药时点，不把自由文本解析为医嘱。
- 通用确认需 PLAN_CONFIRM；同批涉及专业项还需专业能力。不存在的条件码、重复条件码、外来来源、非法值整批失败。
- 确认记录追加并链接 supersedes_confirmation_id；绑定方案 contentRevision、contentHash、输入快照和 requirement。内容改变后旧确认显示 STALE，须显式重新核实；普通档案新增不改写本方案输入。
- 追加确认只增加 row_version，不伪造方案内容变化；送审摘要由 planContentHash + 完整有效确认清单计算。review_submission.content_hash 是该送审聚合摘要，区别于方案正文摘要。
- 无执行条目不强行要求确认，也不强制七维齐全。送审要求主目标及其 factIds 依据、至少一个有依据模块和已选建议；禁止 PENDING 维度及空 ENABLED 维度。

### 审核与本地身份

- 本批实施 PLAN 一审。review_submission 保留未来 TASK 字段但以 CHECK 禁止未实现阶段。
- PENDING → APPROVED / RETURNED / WITHDRAWN；一次提交最多一个决策；同一方案最多一个 PENDING，由父锁和数据库唯一键共同兜底。
- 审核操作需 PATIENT_READ + PLAN_REVIEW，禁止提交者自审；审核人不能借审核接口修改内容。
- local DEMO 增加独立 demo-reviewer 随机 Token；编辑身份 demo-clinician 确认和提交，审核身份只读与审核。固定演示患者新增授权只初始化一次，不恢复被撤销授权。
- 新版本沿用原不可变输入快照，保留 based_on_version_id；所有模块/维度/条目/依据 ID 重建，旧版及审核不删。新输入生成和派生任务影响处理留后续任务阶段。

## Review Focus

1. 确认后编辑正文或主体，旧确认必须 STALE，不能靠旧确认提交。
2. 并发确认/提交/撤回/决策必须共享父锁；失败不留下半套确认或孤立审核。
3. 同机构无能力、撤销/过期授权、REAL 或外机构数据均不能经审核新入口绕过权限。
4. 专业来源自述/外患者/请求改值不能被确认；不能用医生患者确认代替平台中医模板审核。
5. 退回重提及批准后新版本不能复用旧决定；历史快照不随草稿、档案或新版本变化。

---

### Task 1: 执行条件追加历史与确认接口

**Files:**
- Create: `V4__create_conditions_and_plan_reviews.sql`；三组 entity/mapper；ConditionConfirmationController、ConditionConfirmationService、PlanReviewSupport、PlanConditionPolicy、相关 DTO/VO。
- Modify: SecurityConfiguration、DemoAuthenticationFilter、DemoDataInitializer、application.yaml、scripts/run-demo.sh；提取 CarePlanServiceImpl 的只读视图/摘要为可复用服务方法。
- Test: `ConditionConfirmationApiTest.java`、`PlanReviewSchemaDatabaseTest.java`。

**Interfaces:**
- Consumes: PatientService.require、PlanDraftStore.load、CarePlanMapper.lockVersion、CarePlanService.get。
- Produces: PlanReviewSupport.context(principal,id,capability,lock)；ConditionConfirmationService.get/save；PlanConditionPolicy.requirements(draft)；有效确认快照供 Task 2 使用。

- [x] 写失败测试：缺失条件派生，合法确认后重开可读，追加不覆盖，过期修订 409，正文编辑 STALE，批内非法值全回滚，专业来源与主体防篡改，机构/模式/能力边界；迁移一审重复待审及决策唯一约束。
- [x] 执行 `./scripts/test-db.sh -Dtest=ConditionConfirmationApiTest,PlanReviewSchemaDatabaseTest`。Expected: 新接口不可用/表缺失引起断言失败，记录 RED。
- [x] 实现 V4 与条件策略/事务/身份；记录新表由本批创建、未来目标 CHECK 限制和摘要边界。
- [x] 再运行同一命令。Expected: 全部通过，原有接口不受影响。
- [x] 提交 `feat: add version-bound execution condition confirmations`。

### Task 2: 一审状态机与不可变送审快照

**Files:**
- Create: PlanReviewController、PlanReviewService、PlanReviewValidation、审查 DTO/VO。
- Modify: 条件服务的有效清单复用、SecurityConfiguration 路由。
- Test: `PlanReviewApiTest.java`、`PlanReviewTransactionTest.java`。

**Interfaces:**
- Consumes: Task 1 context 与有效条件清单；CarePlanService.get 的一致性读视图。
- Produces: submit/context/getSubmission/decide/withdraw；review_submission 与 review_decision 作为后续任务生成只读来源。

- [x] 写失败测试：缺主目标依据/模块/选中项/待核实条件不送审；不强制七维；提交快照含完整依据和确认；待审编辑拒绝；独立审核人通过和退回；自审/越权拒绝；撤回、退回后重提；重复和过期决策；普通资料更新不改送审内容。
- [x] 执行 `./scripts/test-db.sh -Dtest=PlanReviewApiTest,PlanReviewTransactionTest`。Expected: 新一审接口尚未实现，断言失败。
- [x] 实现父锁 + 事务 + 唯一键 + 合并摘要；审核意见仅审计必要标识，不写临床正文或 Token 日志。
- [x] 补并发提交/决策与审计故障注入，执行同一命令。Expected: 一次成功、另一次 409；故障回滚父状态/提交/决策/审计。
- [x] 提交 `feat: implement immutable first-review submissions and decisions`。

### Task 3: 批准后版本调整与联调交付

**Files:**
- Create: 方案版本服务及 Controller、`docs/PHASE-B-API.md`。
- Modify: README、API-CONTRACT、数据库设计与字典、PHASE-A 进展说明。
- Test: `PlanVersionApiTest.java`；HTTP 冒烟脚本置 /tmp，仅虚构 DEMO 数据。

**Interfaces:**
- Consumes: 当前 APPROVED 源版本、PlanDraftStore、条件与审核接口。
- Produces: POST /care-plans/{id}/versions；独立新草稿及来源链，旧审核保留。

- [x] 写失败测试：批准后不能 PATCH；创建新版本与新子 ID/依据；确认与审核不继承；重复旧源请求 409；外机构/模式不越权。
- [x] 执行 `./scripts/test-db.sh -Dtest=PlanVersionApiTest`。Expected: 新版本接口未实现，断言失败。
- [x] 实现克隆事务，补请求示例及真实实现范围。
- [x] 执行 `./scripts/test-db.sh` 和 `./scripts/mvn.sh -B -ntp -o verify`。Expected: 数据库测试全通过；普通构建明确跳过数据库测试。
- [x] 重启本项目 9089 DEMO，HTTP 实测确认→送审→退回→重提→通过→新版本、401/403/409；保留 .env.demo 凭证。
- [x] 提交 `feat: create new drafts from approved plan versions`；独立代码审查并修复重要问题；保留分支，不合并主分支。

## 执行记录

本批不重新开启整体数据库设计讨论；沿用已确认的业务对象和当前会话实施方式。审核角色采用独立 DEMO 身份、禁自审，是正式权限规则确认前的保守开发默认值。真实接入前仍需确认正式角色配置。

### 验证记录

- 条件接口和约束测试 RED → GREEN，8/8；连同第一批回归 36/36。
- 一审接口 RED 10/10；实现后 9 项通过，并发用例在容器资源争用下触发 10 秒等待超时。MySQL 显示 handler commit 等待；测试等待改为 60 秒、清理合并为单一事务后 10/10 通过，未放宽业务断言。
- 新版本接口 RED 2/2 → GREEN 2/2。
- `./scripts/test-db.sh`：48/48 通过；`./scripts/mvn.sh -B -ntp -o verify`：6 项通过、42 项数据库测试明确跳过，构建成功。
- 9089 DEMO 实际 HTTP 24 步通过，覆盖确认失效、退回重提、独立审核、新版本、撤回及 200/201/401/403/409。
- 分支 feat/plan-conditions-review，从第一批提交 163a66d 继续，未合并 main。

### 实施取舍

- 沿用当前 checkout 和当前会话实施方式，新增第二批功能分支；不额外创建 worktree，后续可迁移分支。
- 独立 DEMO 审核身份、暂禁自审；正式角色确认后可调整策略。
- 确认只增加 row_version，审核摘要组合正文摘要与有效确认清单；后续任务阶段需使用审核聚合摘要校验来源。
- 写事务采用 READ_COMMITTED 并共享患者/版本锁，防止等待锁后读取旧子项；后续写入应遵守相同锁顺序。
- 登记实际通过的测试命令结果，不为登记再次重复相同测试。
- 用户明确授权停止另一项目的 health-assistant-worker、health-assistant-flower 且不再启动；两者已确认 exited。未删除容器或数据，本项目服务和数据库继续运行。

### 独立审查

独立审查覆盖第二批全部代码及已有授权/摘要调用链，核对测试日志与 HTTP 联调结果，未修改代码或数据库。结论：Critical 0、Important 0，可交付当前明确的 local DEMO 范围。

两项非阻断建议暂不扩展本批范围：

1. 补强确认对提交、撤回对决策、同源并发克隆的混合操作回归；当前父锁、读隔离及克隆锁后重读逻辑已审查。
2. 直接覆盖快照内 REPORTED 医嘱、外患者医嘱及缺确认人/时间的来源；当前专业状态、快照成员、条目依据校验已审查。

范围裁定：真实身份及正式角色配置留后续接入；完整医学规则、自由文本与医嘱一致性、任务排程、平台模板审核、二审及发布留后续批次，未接入前不开放 REAL 执行。数据库管理员绕过应用直接写库不属于业务 API 权限边界；部署时另行划分迁移/运行数据库账号。本批没有这类直接改库业务入口。
