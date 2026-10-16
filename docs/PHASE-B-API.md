# 第二批：执行条件确认与方案一审

2026-10-16。接续 [第一批接口](PHASE-A-API.md)。本批围绕已保存的方案草稿增加执行条件核实、送审、独立审核、退回、撤回及批准后新版本。

当前开发中；以下契约将在本批回归与 HTTP 联调通过后标记完成。模拟 AI、任务生成、排程、二审、发布、打卡和评分继续后续实施。

## 启动与身份

```bash
./scripts/db.sh up
SERVER_PORT=9089 ./scripts/run-demo.sh
```

基址 `http://127.0.0.1:9089/ncd-ai`。`.env.demo` 中保留原 `HEALTH_DEMO_API_TOKEN`，脚本追加独立随机 `HEALTH_DEMO_REVIEWER_TOKEN`。使用原始 Authorization Token；不要加 Bearer。

| Token 对应身份 | 固定演示患者能力 |
|---|---|
| demo-clinician | PATIENT_READ、PLAN_EDIT、PLAN_CONFIRM、PLAN_CONFIRM_PROFESSIONAL |
| demo-reviewer | PATIENT_READ、PLAN_REVIEW |

这些能力通过患者授权表控制，Token 本身不授予所有患者权限。仅 local + 显式 DEMO 开关启用；REAL 数据不可访问。新增演示授权只初始化一次，重启不恢复被撤销的授权。当前禁止提交人自审，正式角色配置需与产品确认。

## 接口

| 方法 | 路径 | 含义 |
|---|---|---|
| GET | /plan-versions/{id}/condition-confirmations | 服务端派生的必核实项、当前状态、确认历史 |
| POST | /plan-versions/{id}/condition-confirmations | 原子追加确认记录 |
| GET | /plan-versions/{id}/review-context | 方案、原输入快照、确认清单、阻断问题和历史审核 |
| POST | /plan-versions/{id}/review-submissions | 固定一次送审内容，进入待审 |
| GET | /review-submissions/{id} | 本次送审快照、状态和最终决策 |
| POST | /review-submissions/{id}/decisions | 审核人通过或退回 |
| POST | /review-submissions/{id}/withdrawals | 提交人撤回待审 |
| POST | /care-plans/{id}/versions | 从当前已批准版本建立新草稿 |

GET 返回 200；POST 成功返回 201 + Location。统一响应仍为 `{code,message,data}`。201 的 Location 可读取新版本、审核聚合或当前确认清单；决策结果包含在审核聚合中。

## 1 执行条件

GET 返回：

```json
{
  "versionId": "方案版本UUID",
  "rowVersion": "1",
  "contentRevision": "1",
  "conditions": [{
    "conditionKey": "execution.条目UUID",
    "kind": "EXECUTION",
    "itemId": "条目UUID",
    "actorType": "PATIENT",
    "requirement": {"schemaVersion":"plan-conditions-v1","kind":"EXECUTION","itemId":"条目UUID","actorType":"PATIENT","required":true,"capability":"PLAN_CONFIRM"},
    "bindingHash": "服务端摘要",
    "status": "MISSING",
    "record": null
  }],
  "history": []
}
```

每条已选且 executionGoal=true 的条目产生一条执行主体/环境确认。方案说明类条目不强行产生执行条件；不要求七维全部启用。

POST 批量确认：

```json
{
  "expectedRevision": "1",
  "entries": [{
    "conditionKey": "execution.条目UUID",
    "status": "CONFIRMED",
    "value": {"actorType":"PATIENT","available":true,"environmentConfirmed":true},
    "confirmationNote": "已核实本人具备执行能力与相应环境"
  }]
}
```

actorType 必须与条目相同；两个布尔字段必须是真正的 true。CONFIRMED、REJECTED 必须说明。PENDING、REJECTED 不提供 value/sourceFactId；不能用“不满足”状态绕过送审检查。条件编码和 requirement 来自服务端，客户端不能指定确认人、确认时刻或摘要。

当前条件状态：MISSING / PENDING / CONFIRMED / REJECTED / STALE。STALE 是查询派生状态，表示旧记录不再适用于当前正文；历史行保留原状态和原修订。重新核实时提交新记录，并用 supersedesConfirmationId 链接上一行。

每次成功提交确认增加方案 rowVersion，保持正文 contentRevision 不变。方案正文、主体、日期、模块等内容改变后，原确认失效，须人工重新核实；单纯 changeReason 元数据不使确认失效。

## 2 已有医嘱引用

用药执行条目额外产生 `medication.source.{itemId}`。该条目须以本次输入快照中的 medication_order 事实为依据，事实已由专业人员确认且有确认人和时刻。

```json
{
  "expectedRevision": "2",
  "entries": [{
    "conditionKey": "medication.source.条目UUID",
    "status": "CONFIRMED",
    "sourceFactId": "已有专业确认医嘱事实UUID",
    "confirmationNote": "已核对已有医嘱及适用患者"
  }]
}
```

需要 PLAN_CONFIRM_PROFESSIONAL。后端原样复制输入快照里的 value，并保存 sourceFactId；请求不得携带 value。该接口保留已有专业依据，不新增医嘱，不解析或推算服药时点；任务阶段还需实现用药零改动等完整规则校验。中医执行条目在平台已审核模板接入前阻断送审。

## 3 一审提交和资料

先 GET review-context 查看 issues；severity=BLOCKER 时不能送审。必要项包括：有依据的主目标、模块、至少一条已选建议，已启用维度有选中条目，无 PENDING 维度，必需条件均为当前修订有效的 CONFIRMED。

第一批允许保存不完整草稿；一审提交时才执行以上完整性检查。goalBasis 需包含本次输入快照中非空 factIds。

```json
{"expectedRevision":"3"}
```

POST review-submissions 接收方案 **rowVersion**，成功后正文 contentRevision 不变，仅 rowVersion 增加，状态 DRAFT → IN_REVIEW。待审版本禁止 PATCH 和追加确认；修改须先撤回。相同目标只能有一个 PENDING 提交。

送审结果包含 id、stage=PLAN、targetRevision、rowVersion、contentHash、status、snapshot、submittedBy/At、closedBy/At/Reason 和 decision。

snapshot 保留完整方案（含输入快照、模块、维度、侧重、条目和依据）及当前确认清单。审核 contentHash 覆盖方案正文摘要和确认清单，区别于 care_plan_version.contentHash。提交后正文及确认均被冻结。

普通档案新增不自动改写已经固定的方案输入或审核快照；任务阶段另读最新资料，不自动替换已审目标。

## 4 审核通过、退回和撤回

使用审核 Token：

```json
{"expectedRevision":"0","decision":"APPROVE","comment":"已核对目标、依据、禁忌及执行主体"}
```

或：

```json
{"expectedRevision":"0","decision":"RETURN","comment":"请补充目标依据"}
```

这里 expectedRevision 对应 **review_submission.rowVersion**，不使用方案 rowVersion。需要 PATIENT_READ + PLAN_REVIEW；提交人即使持有该能力也不能自审。RETURN 必须有非空意见。一次提交最多一个最终决策，重复操作返回 409。

通过后：提交 APPROVED，方案 APPROVED，旧版正文只读。退回后：提交 RETURNED，方案 DRAFT；旧意见和快照保留，修改后另建提交，不覆盖旧提交。

提交人本人持 PLAN_EDIT 可 POST withdrawals：

```json
{"expectedRevision":"0","reason":"补充资料后重新提交"}
```

撤回后：提交 WITHDRAWN，方案 DRAFT，记录撤回人、时间和说明，不伪造审核决定。撤回后的旧提交不能再审核。

## 5 通过后的新草稿

```json
{
  "sourceVersionId": "当前APPROVED版本UUID",
  "expectedRevision": "已审方案rowVersion",
  "changeReason": "医生主动调整本周期目标"
}
```

POST /care-plans/{planId}/versions。只允许从该方案当前已批准版本创建；更新 currentVersionId。新版本为 DRAFT，versionNo 加一、rowVersion/contentRevision 从 0 开始，basedOnVersionId 指向原版。重建模块、维度、侧重、条目及依据 ID，保留原不可变输入快照。执行确认、审核决定均不复制，须重新核实和送审。

旧版及其通过记录完整保留；建立新草稿不会覆盖旧版或声称已审新内容。后续任务阶段负责派生任务和已发布实例的具体换版处理。

## 错误与事务

- 400：未知字段、非法结构/值、确认依据不合法、送审仍有阻断项。
- 401：未认证或 Token 无效；非 local 环境不接受 DEMO。
- 403：同机构患者未授权、缺少操作能力、自审、非提交人撤回。
- 404：机构、数据模式不可见，或目标不存在。
- 409：修订过期、状态不允许、重复待审/决策、新版本来源已非当前。

所有写入在同一事务内完成，父锁顺序为患者 → 方案版本 → 审核提交。正文 PATCH 和本批写入使用 READ_COMMITTED，等待父锁后读取最新子项。确认追加、状态、审核结果和审计任一失败均回滚；不出现已批准但无决策的半完成状态。

本批没有实现完整医学安全规则引擎，也没有建设在线增强规则编辑、发布或版本管理页面。
