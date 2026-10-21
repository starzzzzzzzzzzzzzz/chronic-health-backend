# 工作台与业务流程恢复接口

用于前端多人、多浏览器联调。路径均相对于 `/ncd-ai`，鉴权方式沿用现有 Authorization。所有接口只读，不创建任务、不重新发布、不修改审核状态。

## 本次后端补充

本次新增了三个查询接口，方便前端直接从后端找回业务记录：

- **方案列表**：查询授权患者的方案及当前版本，支持分页和状态筛选。
- **审核待办 / 我的提交**：查询当前用户可审核的待办，或自己提交的审核记录。
- **流程恢复**：根据方案版本查询关联的任务包、审核记录、发布结果及同方案的历史版本，区分当前草稿与旧版发布。

前端接入后，即可减少对浏览器本地记录的依赖，避免换浏览器或换人操作后找不到已有流程。新接口沿用现有权限校验，不改变原有编辑、审核和发布规则。

## 方案列表

`GET /care-plans?patientId={可选患者ID}&status=DRAFT&page=1&size=20`

返回 `data: {page, size, total, items}`。每个方案仅返回其当前版本。

条目字段：`planId, patientId, patientName, versionId, versionNo, title, status, rowVersion, startDate, endDate`。

- `status` 可选 DRAFT / IN_REVIEW / APPROVED，指当前版本状态。
- `rowVersion` 是字符串；版本号 `versionNo` 是整数。
- 按当前版本更新时间倒序、版本ID倒序排序。
- 页码至少 1，每页 1–100；无匹配返回 total=0 和空 items。
- 机构、DEMO/REAL 模式、有效患者读取授权均在计数和分页前过滤。

## 审核待办与我的提交

`GET /review-submissions?scope=pending&stage=PLAN&page=1&size=20`

返回相同分页结构。条目字段：`id, stage, status, planVersionId, taskBundleId, schedulePreviewId, rowVersion, targetRevision, patientId, patientName, title, submittedBy`。

- `scope=pending` 默认：只有当前用户有权独立审核的 PENDING 记录；排除本人提交。
- `scope=mine`：当前用户自己的提交，包含全部状态，不限待审。
- `stage` 可选 PLAN / TASK；省略时返回两个阶段。
- 按提交时间、提交ID倒序。修订号均为字符串。
- 任务二审的 `planVersionId` 由任务包解析，前端不必自己追查。
- 待办还需对应 PLAN_REVIEW / TASK_REVIEW 权限；TASK 阶段还需 TASK_READ。读取授权过期或撤销后记录不再返回。
- 返回的ID可用于既有 `GET /review-submissions/{id}` 获取完整快照及决定。

## 恢复某个版本的关联流程

`GET /plan-versions/{versionId}/workflow`

需要该患者有效的 PATIENT_READ 和 TASK_READ 权限。无权返回403，机构/数据模式不可见返回404。

`data` 字段：

| 字段 | 内容 |
| --- | --- |
| planId、patientId | 所属方案、患者 |
| versionId | 本次查询的版本 |
| currentVersionId | 该方案当前版本，可能与 versionId 不同 |
| versions | 同方案全部版本：versionId、versionNo、status、title、rowVersion；版本号倒序 |
| taskBundles | **本次查询版本**的全部任务包：id、status、rowVersion、contentRevision；创建时间倒序 |
| reviewSubmissions | **本次查询版本**的一审和二审：id、stage、status、taskBundleId、schedulePreviewId、rowVersion；提交时间倒序 |
| publications | **本次查询版本**的发布：id、taskBundleId、status、notificationStatus、reviewSubmissionId、schedulePreviewId、publicationPreviewId；创建时间倒序 |

不存在关联对象时返回空数组。PLAN 审核的任务包/排程预览字段可为 null。所有修订号是字符串。该接口返回关联元数据，正文、审核快照、排程详情仍走已有详情接口。

## 前端接入顺序

1. 工作台、方案列表改为查询 `/care-plans`；本地缓存只能加速，不作为记录是否存在的依据。
2. 审核中心使用 `scope=pending`，我的提交使用 `scope=mine`。
3. 打开方案版本时同时读取版本详情及 `/workflow`，依据返回ID加载任务包、审核与发布详情。
4. 一个版本可能有多个任务包/发布，按明确选中的ID展示，不能一律拿数组第一项当唯一业务对象。
5. 新草稿存在时，旧版本仍可能已有生效任务。分别展示版本编辑状态与发布状态，不把当前草稿显示为已发布，也不把旧版任务显示为不存在。

## 另外两处联调约定

管理依据的确认已有生成接口字段 `confirmedManagement`，需要前端补选择和来源绑定表单。此次未新增写接口，不改现有一审阻断规则。

健康事实的 `value` 是结构化对象，并非所有事实都使用 `value.value`。例如诊断为 `{name:"2型糖尿病",note:"..."}`，文本为 `{value:"..."}`，数值还需结合同级 `unit` 展示。不能因为 `value.value` 缺失就显示“未填写”；前端应按事实类型解析并保留未知值含义。

## 验证

新增数据库集成测试覆盖列表分页、授权撤销/过期、机构及数据模式隔离、非本人审核、TASK_READ限制、版本身份及已发布关联恢复。未改前端源码；前端需接入上述接口后才能消除本地索引依赖。
