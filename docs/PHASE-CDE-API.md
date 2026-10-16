# 任务生成、二审、发布与基础打卡

本轮在 **local + 显式 DEMO + 虚构患者** 下贯通。基址 `http://127.0.0.1:9089/ncd-ai`，Knife4j `/doc.html`。正式登录、真实 AI、质量评分、语音图片、设备、实际消息送达和护士工单闭环尚未接入。本轮使用 `COMPLETION_ONLY`，质量为 `NOT_APPLICABLE`，不能展示为质量合格或 100 分。

## 身份与联调顺序

运行 `SERVER_PORT=9089 ./scripts/run-demo.sh`。脚本保留已有凭证，按需追加随机患者凭证；三个 Token 位于被忽略的 `.env.demo`，原样放入 Authorization，不加 Bearer。

| 变量 | 身份 | 指定虚构患者的能力 |
|---|---|---|
| HEALTH_DEMO_API_TOKEN | demo-clinician | 患者资料、方案编辑/条件确认、任务编辑和发布 |
| HEALTH_DEMO_REVIEWER_TOKEN | demo-reviewer | 资料与任务读取、PLAN/TASK 审核；不能自审 |
| HEALTH_DEMO_PATIENT_TOKEN | demo-patient | TASK_READ、TASK_FEEDBACK；无患者资料/方案编辑权限 |

初始化器只给内置虚构患者追加一次开发授权，重启不恢复已撤销授权。新造测试患者需要独立授权。普通编辑身份没有患者打卡能力。所有查询和重试均重新检查机构、患者、数据模式和有效授权。

前端按 **选择患者与档案 → 方案正文 → 执行条件确认 → 一审 → 生成任务包 → 确认任务及排程 → 预览 → 二审 → 发布预览 → 发布 → 日任务 → 打卡/更正** 接入。前半段见 [第一批](PHASE-A-API.md)、[第二批](PHASE-B-API.md)。以下 ID 均替换成接口真实返回值，所有 ID 与修订号用字符串。

## 任务来源与编辑

| 方法与路径 | 请求关键字段 | 成功 |
|---|---|---|
| POST /plan-versions/{id}/task-bundles | expectedRevision：来源方案 rowVersion | 201 + Location |
| GET /task-bundles/{id} | — | 200 |
| PATCH /task-bundles/{id} | expectedRevision：任务包 rowVersion，groups | 200 |
| POST /task-bundles/{id}/schedule-previews | expectedRevision：任务包 rowVersion | 201 + Location |
| GET /schedule-previews/{id} | — | 200 |
| GET /task-bundles/{id}/review-context | — | 200 |
| POST /task-bundles/{id}/review-submissions | expectedRevision：任务包 rowVersion，schedulePreviewId | 201 + Location |
| POST /task-bundles/{id}/versions | expectedRevision：旧包 rowVersion，changeReason | 201 + Location |

只从当前有效的一审 APPROVED 方案派生 `isSelected=true`、`executionGoal=true` 且维度 ENABLED 的条目。medication/diet/exercise/routine/monitoring/tcm/other 按需生成，不强求七维都有。重复读取最新档案留 TASK 快照，新增事实只进入 `nextCycleReference`，不修改已审目标和原始来源。

任务包返回来源方案/一审 ID、输入快照、完整 groups 和 BLOCKER issues。任务组保留全部来源条目、事实、模块、主体和模板版本。用药只接受一审确认导入的结构化医嘱（药名、剂量、途径、频次、餐关系及完整 schedule），禁止重排医嘱或编造时点；中医康复缺医学审核模板时阻断。开发模板为 DEMO_ONLY，不代表医学审核。

PATCH 必须列出包内**全部组 ID**，组内省略字段保留原值。允许字段只有 id、schedule、taskClass、priorityCode、safetyFocus、executionConfirmed、confirmationNote、displayNote。来源、主体、专业参数、原始动作和禁忌只读。安全重点必须 REQUIRED + L0；执行确认需说明。

```json
{
  "expectedRevision":"0",
  "groups":[{
    "id":"实际任务组ID",
    "executionConfirmed":true,
    "confirmationNote":"已核对来源动作、基本目标和执行细节",
    "schedule":{
      "isoWeekdays":[1,2,3,4,5,6,7],
      "rotationPolicy":"FIXED",
      "slots":[{"slotKey":"morning","mode":"FIXED","localTime":"08:00","opensOffsetMinutes":0,"closesOffsetMinutes":60}]
    }
  }]
}
```

排程按 startDate/endDate/zoneId 展开；上限90天、5000实例，超限阻断且不截断。每个具名槽位独立实例，跨日保留 executionDate；DST不存在或歧义时点阻断。ANCHOR 明确不支持。饮食/运动可用模板已有候选池 ROTATE；用药、监测禁止轮换。完全相同的非药物、非监测、非中医建议可合并来源。自理/未知分类保守10项展示、6项主动输入；来源快照中唯一、专业确认的 `functional_classification=CARE_DEPENDENT` 放宽展示至12，主动输入仍6；超限保留安全项并阻断。

预览固定完整 occurrences、问题、内容修订/摘要与规则版本。生成预览会推进任务包 rowVersion；正文未变则 contentRevision 不变。修改正文会使旧预览失效。可重复预览，相同输入的 occurrence 顺序、选择和摘要相同。

## 二审与版本

沿用 `/review-submissions/{id}`、`/decisions`、`/withdrawals`，按 stage=PLAN/TASK 分派。通用响应增加 `taskBundleId`、`schedulePreviewId`；TASK 的 `planVersionId` 为 null，来源在冻结内容中保留。审核决策/撤回的 expectedRevision 仍是**审核提交自己的 rowVersion**。

任务包 DRAFT → IN_REVIEW → APPROVED → PUBLISHED。提交必须指定当前无阻断预览；提交、批准、发布都检查相同来源和摘要。退回/提交人撤回回到 DRAFT，历史不删除。模板失效仍允许撤回或退回，批准和发布继续阻断。待审不可编辑，批准后调整建立新包版本并重新预览、二审；不继承审核决定。

## 发布与衔接

| 方法与路径 | 用途 |
|---|---|
| POST /task-bundles/{id}/publication-previews | 冻结新建、保留、替代清单，201 |
| GET /task-bundles/{id}/publication-previews/{previewId} | 读取上述清单，200 |
| POST /task-bundles/{id}/publications | 事务发布，首次201，重试200 |
| GET /publications/{id} | 发布结果与 notificationStatus |

发布预览正文：

```json
{"expectedRevision":"4","reviewSubmissionId":"实际二审提交ID","schedulePreviewId":"实际排程预览ID","effectiveAt":"2026-10-17T00:00:00Z","replacementScope":"NONE"}
```

正式发布使用**同一份正文**再加 `publicationPreviewId`，携带 `Idempotency-Key`（1—128位字母数字或 `._:-`）。预览创建不推进任务包修订。相同机构内同键同正文回读；同键不同正文409；同一包换键且正文相同也只保留一套发布，并保存键别名；换正文不能再次发布同一包。

首次无旧任务用 NONE；已有同一方案家族任务必须明确 ALL_FUTURE。清单返回 createOccurrenceKeys、replaceTaskIds、retainTaskIds 与 previewHash。正式实例只从二审排程复制清单选中的 occurrence，不重新生成任务。预览后范围发生变化返回409，重新核对。

ALL_FUTURE 的边界不得早于服务器当前时刻；预览后跨过边界，需要重新选择未来时刻，不能回溯取消或补造历史。生效边界前已开始、已有反馈、历史任务保留；边界后未开始且无反馈的旧任务设定取消时刻，生效前仍可见。对应已保留的相同冻结动作、主体、维度、专业参数和日期/槽位不再新增一条，不依赖随方案克隆变化的条目ID，也不靠模糊标题判断。不同方案家族不隐式互相替代。包、实例、替代、幂等记录、outbox与审计同一事务；失败整体回滚。通知状态 `NOT_CONFIGURED`，没有外部消息发送。

## 今日任务与反馈

`GET /patients/{id}/execution-tasks?date=2026-10-17`，省略 date 按配置的业务时区（默认 Asia/Shanghai）取今日。返回数组，每条带自身 zoneId、冻结内容、opensAt/closesAt/normalCutoffAt/makeupCutoffAt、必做/自选标记及 currentResult。`GET /execution-tasks/{id}` 查询详情。预览和未生效发布不产生可见患者任务；新方案草稿不会隐藏旧正式任务。

| 方法与路径 | 请求 |
|---|---|
| POST /execution-tasks/{id}/check-ins | Idempotency-Key；`{}` 即一键自述完成 |
| GET /execution-tasks/{id}/check-ins | 全部反馈历史 |
| GET /check-ins/{id} | 一条不可变反馈 |
| POST /check-ins/{id}/amendments | Idempotency-Key、expectedRevision及补充字段；id用最新反馈ID |

可选字段：completion（COMPLETED/PARTIAL/NOT_DONE/STOPPED）、actualExecutedAt、reportedSafety（布尔或null）、note、reason、feeling、values（最多20个简单值）。不得提交服务器接收时刻或质量评分。更正省略字段保留，null清空可选反馈；expectedRevision 取 currentResult.rowVersion。每个任务只有一次主要提交，重复主要提交换键返回409；后续必须追加更正。首次/更正201 + Location，原键原内容重试200。

```json
{"completion":"COMPLETED","actualExecutedAt":"2026-10-17T00:20:00Z"}
```

服务端分别返回：

| 字段 | 语义 |
|---|---|
| completion / completionRatio | 自述完成100、部分50、未做0；STOPPED比例null、待核实 |
| executionTiming | 按实际时间判断 EARLY/ON_TIME/LATE；不填实际时间为 UNKNOWN |
| submissionSource | NORMAL 或 MAKEUP，使用服务器接收时刻 |
| feedbackCompleteness | COMPLETE/LIMITED；反馈少不撤销完成事实 |
| qualityStatus | NOT_APPLICABLE，不生成质量分数 |
| safetyStatus | 未填UNKNOWN，明确无异常NONE_REPORTED，异常或STOPPED为REQUIRES_REVIEW |

每次反馈的 assessment 固定当次自述；任务 currentResult.assessment 汇总独立安全事件。后来改报完成或无异常不会清除 OPEN 安全事件；本轮没有安全事件关闭接口和护士工单处置。未开放、已取消、无授权、超过补记截止的任务拒绝提交。actualExecutedAt 不得晚于服务器当前时间。

| 维度 | 补记截止（右端不包含） |
|---|---|
| medication | 正常截止后12h，且早于下一次确认的同药任务开始；跨组和新发布版本同样检查 |
| diet/exercise/other | 执行日的次日结束 |
| routine | 次日12:00正常截止，18:00补记截止 |
| monitoring | 窗口结束后12h与执行日结束的较早者 |
| tcm | 窗口结束后6h且不跨执行日 |

医嘱身份只取已确认的 medicationId、drugCode 或精确 drugName，不解析自由文本、不推断药物同义词。读取与提交时若已有更早的下一同药正式任务，按该开始时刻收紧补记边界，原发布快照不被改写；无法取得可信药物身份时不开放补记。作息固定次日12:00的正常截止不能被通用偏移覆盖；其他维度窗口与截止顺序冲突会阻断二审。

补记只记录已经发生的执行，不提示补做后倒填。幂等回读仍检查当前授权。取消未来任务不计未完成；本轮没有依从性报表或完整评分分母计算。

## 错误与验证

400 参数/阻断、401 无有效身份、403 缺能力/自审、404 不存在/范围不可见、409 修订/状态/幂等冲突。所有写入通过患者父锁串行化，DB唯一键兜底。新迁移 V5—V8，原 V1—V4 保持不变。

使用 `./scripts/test-db.sh` 运行真库权限、快照、排程、二审、发布、并发、回滚、七维截止和更正测试；`./scripts/mvn.sh -o -B -ntp verify` 为普通离线构建，数据库测试明确跳过。构建目录为 `.dev/build.nosync`，避免同步目录生成重复编译文件。前端可先按本契约连接本机/共享DEMO环境，正式部署和真实账号另行配置。
