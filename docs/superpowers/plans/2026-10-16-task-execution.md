# 七维任务到基础打卡实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** 确定性派生已审方案的任务，完成二审、幂等发布、日任务和基础反馈。

**Architecture:** 沿用分层、MyBatis-Plus、READ_COMMITTED 和患者父锁。生成、排程、审核、发布及反馈分服务；内容与预览固定摘要，发布实例与反馈历史独立。

**Tech Stack:** Java 17、Spring Boot 4.1.1、MySQL 8.4、Flyway、MyBatis-Plus；不增加依赖。

**Spec:** docs/superpowers/specs/2026-10-16-task-execution-design.md。用户已确认具体设计并指定当前会话继续实现，无需再次选择执行方式。

## Global Constraints

- 只接受 local + 显式 DEMO；不打开 REAL，保留此前其他项目容器停止状态。
- UUID 与修订号为 String，时间 UTC DATETIME(6)，业务日期配 zoneId。
- 任务从一审来源派生，二次读取档案，不修改已审目标；七维按需，不凑齐。
- 三增量全部完成才称闭环完成；COMPLETION_ONLY，不声称质量评分、真实 AI 或真实消息送达。
- 周期最多 90 天、5000 实例；固定槽位、确定轮换、用药和监测禁止轮换。
- 保留任务来源、不可变审核/预览/发布内容、反馈更正历史，安全结果不可被完成覆盖。
- V1—V4 不改；统一锁顺序 patient → plan_version → bundle → review → publication → execution → feedback/result。

## Review Focus

1. 历史一审不能为其他版本或当前已变更目标授权；新增档案不能反向改写来源，Task 1 测试。
2. 任务分组参数不能绕过来源与专业限制；同日多槽位和轮换不可丢实例，Task 1/2 测试。
3. TASK 审核不得走 PLAN 假设或绕过患者能力校验，Task 2 测试。
4. 发布与新旧版本切换并发不能出现重复、半套或提前取消任务，Task 3 测试。
5. 打卡提交时间不能冒充实际时间，重试/更正不能清除安全信号，Task 4 测试。

---

### Task 1: 来源绑定与规则派生任务包

**Files:** V5__create_task_bundles.sql；task_template/task_bundle/task_group/task_group_variant/task_group_source/schedule_preview 对应 entity/mapper；TaskBundleVO、TaskWorkflowRequest；TaskGenerationService、TaskBundleService、TaskBundleStore、TaskSourcePolicy；TaskBundleController；SecurityConfiguration；TaskGenerationApiTest。

**Interfaces:**
- Consumes: PlanReviewSupport.context(p,id,capability,lock)、CarePlanService.get(p,id)、PatientService.healthSummary(p,id)、已批准 review_submission 与 review_decision。
- Produces: TaskBundleService.generate(p,planVersionId,body)、get(p,id)、update(p,id,body)；TaskBundleStore.context(p,id,capability,lock)、view(bundle)、groups(bundle)、advance/audit；TaskSourcePolicy.validate(context)。

- [x] 写 HTTP 失败测试：未审来源冲突；批准后只派生执行条目、冻结来源和二次快照；非法字段与来源篡改；越权和跨模式；未知专业参数阻断。
- [x] `./scripts/test-db.sh -Dtest=TaskGenerationApiTest`，Expected: 新接口未开放导致201断言失败。
- [x] 实现 V5、来源验证、规则模板与草稿存取；缺必需执行参数保留 BLOCKER，不补医学内容。保留现有 PLAN 审核约束直到 Task 2 明确扩展。
- [x] 同一命令及现有业务回归，Expected: 全绿。
- [x] 提交来源与任务包功能。

### Task 2: 确定排程与任务二审

**Files:** V6__enable_task_reviews.sql；TaskScheduleService、TaskReviewService、TaskReviewController；ReviewWorkflowDispatcher；修改 PlanReviewController 分派；TaskScheduleApiTest、TaskReviewApiTest、TaskSchedulePolicyTest。

**Interfaces:**
- Consumes: Task 1 context/view/groups/source policy。
- Produces: TaskScheduleService.preview(p,bundleId,body)、get(p,previewId)；TaskReviewService.context/submit/get/decide/withdraw；TaskBundleService.newVersion；审核通用路径按stage分派。

- [x] 写失败测试：双槽位/7、14、28天/跨日/DST/轮换/负担/ANCHOR拒绝；旧预览失效；TASK审核独立权限和禁自审；退回撤回重提；已审任务包新版本不继承审核。
- [x] `./scripts/test-db.sh -Dtest=TaskScheduleApiTest,TaskReviewApiTest,TaskSchedulePolicyTest`，Expected: 新路由或TASK阶段尚不可用。
- [x] 确定展开并冻结完整实例和问题；更新V4 CHECK、TASK 外键及待审唯一键；保持PLAN原路径和修订归属不变。
- [x] 同一命令 + `./scripts/test-db.sh`，Expected: 全绿，无一审回归。
- [x] 提交排程与二审。

### Task 3: 事务发布与患者日任务

**Files:** V7__create_publications.sql；publication/execution_task/outbox_event entity/mapper；TaskPublicationService、ExecutionTaskService、TaskPublicationController、ExecutionTaskController；DemoDataInitializer、DemoAuthenticationFilter、run-demo.sh；TaskPublicationApiTest、TaskPublicationTransactionTest。

**Interfaces:**
- Consumes: Tasks 1/2 source验证、已批准 TASK 审核、确定预览。
- Produces: publicationPreview/publish/get；ExecutionTaskService.list/get/require，后者对指定能力与锁进行检查；患者 DEMO Token。

- [x] 写失败测试：未发布不可见；两审/摘要不匹配；相同键回读和不同内容409；同包不同键不重复；实例与预览一致；新版边界只影响未开始实例；患者授权与同机构无权。
- [x] `./scripts/test-db.sh -Dtest=TaskPublicationApiTest,TaskPublicationTransactionTest`，Expected: 新发布接口不可用。
- [x] 实现发布预览、事务实例复制/替代、唯一键和审计；outbox保持NOT_CONFIGURED，不对外发送。增加独立患者开发凭证且限制患者能力。
- [x] 故障注入和并发测试验证原子性，同一命令 + 全量测试，Expected: 全绿。
- [x] 提交发布与日任务。

### Task 4: 一键打卡 补记与追加更正

**Files:** V8__create_task_feedback.sql；task_feedback/task_result entity/mapper；TaskFeedbackService、TaskFeedbackController、TaskFeedbackPolicy、WorkflowClockConfiguration；TaskFeedbackApiTest、TaskFeedbackPolicyTest、TaskFeedbackTransactionTest。

**Interfaces:**
- Consumes: Task 3 ExecutionTaskService.require；发布冻结窗口和补记规则。
- Produces: checkIn(p,taskId,key,body)、amend(p,feedbackId,key,body)、history(p,taskId)；当前独立完成/时效/安全结果和追加历史。

- [ ] 写失败测试：空正文一键完成，部分/未做/中止，字段选填，实际时间未知或与提交时刻不同，固定时钟七维补记边界，幂等、越权、并发、更正和安全保留。
- [ ] `./scripts/test-db.sh -Dtest=TaskFeedbackApiTest,TaskFeedbackPolicyTest,TaskFeedbackTransactionTest`，Expected: 新反馈接口不可用。
- [ ] 实现一任务一主提交、追加版本与独立结果；截止控制和权限检查在同一父锁事务；缺实际时间不猜按时，未填安全不猜无异常。
- [ ] 同一命令与全量测试，Expected: 全绿。
- [ ] 提交基础反馈。

### Task 5: 接口文档与完整交付

**Files:** docs/PHASE-CDE-API.md、README、API-CONTRACT、数据库设计；ApiDocumentationConfiguration/Models 与新Controller注释；ApiDocumentationDatabaseTest；本地HTTP闭环脚本在.dev或/tmp，不含真实资料。

**Interfaces:** Consumes Tasks1—4；Produces 前端契约、Knife4j和可复现闭环验证。

- [ ] 补文档契约失败测试覆盖新请求根字段、患者身份和审核响应；更新旧接口数量断言，保留认证边界。
- [ ] 实现准确文档和示例，记录真实实现范围、DEMO身份和可复制请求。
- [ ] `./scripts/test-db.sh`、`./scripts/mvn.sh -o -B -ntp verify`，Expected: 数据库全绿，离线构建通过且明确跳过真库测试。
- [ ] 重启9089并实际HTTP从方案一审走到任务生成、二审、发布、日任务与打卡；验证401/403/409和重试。Knife4j浏览器调试并保存截图。
- [ ] 全分支独立审查，重要问题RED→GREEN修复后全量回归；提交、推送草稿PR并附着，不合并main。
