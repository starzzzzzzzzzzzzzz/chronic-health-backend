# 慢病健康管理核心业务数据库设计初稿

版本：v0.2 · 日期：2026-10-16 · 状态：A 批及 B 批条件确认、一审已实现；模拟生成及 C/D 批待实施。

本稿围绕演示视频的“患者 → 方案 → 一审 → 任务与排程 → 二审 → 发布”主线，从零设计新后端的数据模型。目标是支持页面真实保存、修改、审核及发布，并能追溯每次生成和审核所用的数据。A 批 12 张表已通过 Flyway V2/V3 建立；本批通过 V4 增加 condition_confirmation、review_submission、review_decision，V1/V2/V3 未改。接口见 [第一批](../PHASE-A-API.md)及[条件确认与一审](../PHASE-B-API.md)。

配套文件：[表与字段字典](TABLE-DICTIONARY.md)、[视频与接口字段映射](VIDEO-FIELD-MAPPING.md)。字段字典包含 26 张候选表、377 个字段定义（含各表通用字段），不是一次全部开发的工作量承诺。

## 1 需求依据和证据范围

| 编号 | 来源 | 本稿如何使用 |
|---|---|---|
| V | `714cb995bba7ffe4a56c2f80ff1bd9fc.mp4`，约 322.5 秒 | 按约 10 秒间隔抽帧，并复核关键画面；确认页面控件、栏目与操作顺序。不是录音逐字转写 |
| R1 | 《健康管理系统后端业务拆分与开发顺序》，2026-10-11 更新版 | 六阶段、28 小业务、版本边界、逐批开发及验收规则 |
| R2 | 《健康管理系统后端开发难点与开发方案》，2026-10-11 更新版 | 输入快照、异步生成、双审、排程、幂等发布及权限 |
| R3 | 《慢病管理补充文档-终版(1)》 | 方案到任务、周期更新、版本和执行期边界；一期暂不实现完整打卡与评分 |
| R4 | 《方案到任务AI可读规则与七维任务知识库_整合版V3.0》 | 六方面画像、六类模块、七维、准入与专业内容确认约束 |
| A | [参考 OpenAPI](http://116.178.155.215:18006/ncd-ai/v3/api-docs)，2026-10-16 获取的快照 | 核对字段形态和对象；仅证明接口定义存在，不证明接口可用或背后表结构 |
| D | 本稿设计建议 | UUID、表名、字段类型、索引、数据库时区、发布事务等，均不是从视频直接读取的既有实现 |

原始 DOCX 和视频位于用户微信本地文件目录。为避免把演示中的人物资料和页面截图上传仓库，本稿仅记录来源名、时间范围、字段名与设计，未复制原始病历、截图或业务接口响应。

冲突处理：以本次已确认的“视频核心业务”和 R1/R2 的主线为设计范围；视频还展示病种管理侧重、权重与融合，本稿保留对应字段，同时标记其计算规则待确认。R4 的六方面画像、六模块及七维是三套不同分类，不能互相代替。参考服务器 URL 中的 `/store/` 等路由不直接作为新后端表名或 RESTful 接口规范。

## 2 页面和业务对象对应

| 视频区域 | 主要数据 | 数据归属 |
|---|---|---|
| 患者列表、患者摘要 | 姓名、标识、资料来源、健康情况 | patient、health_fact、patient_access_grant |
| 管理侧重与方案制定 | 目标、模块、病种亚型、七维权重、多病种贡献、七维建议、参考来源 | care_plan、care_plan_version、care_plan_module、plan_management_focus、care_plan_dimension、care_plan_item、plan_item_basis |
| 集中确认执行条件与缺项 | 已有医嘱导入、共享条件、确认说明、执行内容、用量、星期与提醒时点 | condition_confirmation；生成任务后写 task_group / task_group_variant |
| 任务预览与周期设置 | 周期起止、作息锚点、周几、时点、日期、任务名称、内容、频次、反馈 | task_bundle、task_group、task_group_variant、schedule_preview |
| 双审与发布 | 一审方案、二审任务和排程、意见、操作者、版本、生效时间、进度 | review_submission、review_decision、publication、outbox_event |
| 发布后的每日任务 | 某天某场景具体执行一次的任务 | execution_task |

一个页面聚合多张表；一张表也可被多个页面复用。页面“下一步”不必对应一张数据库表，“正在生成/发布”来自作业状态。

## 3 拆表选择与存储原则

考虑过三种方式：按页面一页一表，或把整套方案放在一个 JSON 大字段，都较快起步，但会让审核版本、任务去重、患者查询和来源追溯难以可靠约束。本稿推荐按业务对象拆表：核心身份、状态、关联和筛选字段用普通列；维度差异参数、不可变快照、原始生成返回和规则内容用 JSON/TEXT。

具体约定：

- 使用现有 MySQL 8.4、InnoDB、utf8mb4；业务主键建议应用生成 UUID，数据库 CHAR(36)，接口 String。不是要求把现有元数据表的字符串主键改成 UUID。
- 外部机构、团队、用户、档案标识为 VARCHAR(64)。本地 patient.id 与 external_archive_id 分开；前端若继续使用 archiveId，必须明确是本地别名还是外部编号。
- 每个业务行带 org_id。先支持明确的机构范围与最小患者服务授权，不在这期另做完整组织/账号平台。接入既有授权时可替换 patient_access_grant 的读取来源。
- 查询状态、日期、病人、方案和任务包使用普通列及索引；不把频繁筛选字段仅放 JSON。JSON 字段必须有约定结构及校验，不能任意上传一个对象就视为业务有效。
- 新业务时刻字段按 UTC DATETIME(6) 存储，业务日历用 DATE 与 zone_id。拟默认 Asia/Shanghai；实施时须同步配置 JDBC/应用转换，不能仅靠现有容器 +08:00 就声称已实现 UTC。现有 V1 的 TIMESTAMP 元数据字段不改。
- 编码、主键、哈希和幂等键采用区分大小写比较。普通中文名称保留适合中文展示/搜索的排序规则。
- 草稿允许编辑；审核提交快照、原始事实历史、审核决策、已发布内容不覆盖。发布后的变化建立新方案或任务包版本。
- 物理 FK 默认使用 (org_id,foreign_id) → (org_id,id)，每表增加 UK(org_id,id)；历史记录禁止级联删除。FK 不能替代“同一患者、同一版本、同一模式”的服务校验。
- 不提前建立报告 OCR、设备接入、聊天、记忆、打卡、评分、预警处置等全量子系统表。

## 4 表关系和开发批次

| 批次 | 目标 | 表数 | 候选表 |
|---|---|---|---|
| A | 患者资料与可保存、可编辑的方案草稿 | 12 | patient、patient_access_grant、health_fact、health_input_snapshot、care_plan、care_plan_version、care_plan_module、plan_management_focus、care_plan_dimension、care_plan_item、plan_item_basis、audit_log |
| B | 集中确认、模拟生成、一审 | 5 | condition_confirmation、generation_job、rule_set_version、review_submission、review_decision |
| C | 一审依据生成任务、轮换、排程、二审 | 6 | task_template、task_bundle、task_group、task_group_variant、task_group_source、schedule_preview；复用 B 的确认/作业/审核表 |
| D | 幂等发布和患者每日任务查询 | 3 | publication、execution_task、outbox_event |

批次是业务依赖顺序，不等于每批写一个含所有 FK 的 SQL。存在跨批循环引用时，迁移先建基础表，待目标表建立后再追加外键；不要把引用未建表的草稿直接放进 Flyway。

核心关系可按以下顺序阅读：

1. patient 一对多 health_fact 和 health_input_snapshot；快照保存当时资料内容及来源。
2. patient 一对多 care_plan；care_plan 一对多 care_plan_version。
3. care_plan_version 一对多模块、管理侧重、维度及条目；plan_item_basis 将条目、模块、事实关联起来。
4. 一审提交锁定某个方案版本的修订和完整快照；通过记录成为 task_bundle 的来源。
5. task_bundle 一对多 task_group；task_group 一对多 task_group_variant，通过 task_group_source 保留多个输入依据。
6. schedule_preview 保存某个任务包修订的确定排程；二审提交精确引用该预览。
7. publication 引用两审决策及同一预览，一对多 execution_task 和通知事件。
8. 患者端从已发布、生效、获授权的 publication 查询正式 execution_task，不直接查预览 JSON。

task_bundle 是任务包的版本记录，version_no 在对应 plan_version_id 内递增；task_template 的每行也是一个模板版本。表名未重复加 `_version` 不代表缺少版本管理。

## 5 六方面、六模块、七维和病种侧重

| 分类 | 代码 | 作用 |
|---|---|---|
| 人物画像六方面 | H、F、B、C、E、G | 健康阶段、功能、生活方式、照护环境、执行条件、优先问题；存在 health_fact.axis_code |
| 方案六模块 | B0、L、R、C、F、A | 基础生活、习惯改善、风险复核、慢病、功能照护、支持协同；存在 care_plan_module |
| 七维行动 | medication、diet、exercise、routine、monitoring、tcm、other | 用药、饮食、运动、作息、监测、中医康复、其他；存在维度、条目和任务组 |
| 病种管理侧重 | 主/次亚型、优先级、维度权重、融合贡献 | 记录视频中病种面板的人工配置，存在 plan_management_focus；字典和计算规则待确认 |

不得用 P0—P7 分流标签替代事实，不得把病种标签直接解释为已确认诊断。若视频年龄只有“若干岁”，不能凭此制造出生日期：可保留带观测日期的 reported_age 事实；后续取得生日才填 birth_date。

七维权重使用整数基点避免浮点误差，如 25% 表达为 2500。具体默认值、是否允许编辑、权重如何影响任务，需业务确认；本稿不把视频示例数值变成全局医学规则。

## 6 版本、审核和发布一致性

### 6.1 方案和任务包修订

`version_no` 表示业务版本，`row_version` 用于整行并发控制，`content_revision` 只计业务内容变化。API 使用字符串传修订号，以便与前端既有 expectedRowVersion 习惯对齐。客户端提交 expectedRevision（对应 row_version），服务在事务中锁定/条件更新父聚合，再写子表；内容编辑递增两类修订并重算摘要，单纯审核/发布状态变化仅递增 row_version。不能只更新子表而不更新父聚合。

审核的 target_revision、预览的 bundle_revision 和确认的 subject_revision 绑定 content_revision；生成作业的 expected_revision 则用于回写时的 row_version 条件更新。这样“审核通过导致状态更新”不会让刚产生的审核结论立即失效。确认事务按当前内容修订绑定本次确认；对仍适用的旧条件重新核实并追加新绑定，不能只更改修订数字就宣称已核实。条件追加只递增 row_version。方案正文摘要保持独立；一审 content_hash 另外覆盖正文摘要与有效确认清单，送审后两者共同冻结。

哈希使用字段排序、稳定数组顺序、固定时间格式和数值规范化后的 JSON 做 SHA-256。方案摘要覆盖主目标、模块、管理侧重、维度、条目、来源和专业确认；任务包摘要覆盖任务组、候选、排程规则及确认；不把状态、更新时间等生命周期字段算入内容摘要。

一审通过的方案内容冻结。若医学内容、目标、禁忌、执行主体发生实质变化，创建新的 care_plan_version 并重新一审；克隆子表时生成新 ID 并重建模块、维度和依据关系，不把旧版子行转挂到新版。原任务包仍指向旧版本，不能自动换成新内容。普通档案更新不自动改变旧方案。

任务包草稿有自己的 row_version。二审通过后任何任务/排程修改都使旧二审不能用于新内容；本稿选择创建新的任务包版本再送审，已发布包始终保留。旧审核决策不删，用目标、修订、摘要及来源验证是否仍适用于当前发布。

### 6.2 两轮审核

| 对象 | 状态 | 固定内容 |
|---|---|---|
| 方案版本 | DRAFT → IN_REVIEW → APPROVED；退回/撤回返回 DRAFT | 方案输入、主目标、模块、侧重、七维条目、专业确认、target_revision、content_hash |
| 任务包 | DRAFT → IN_REVIEW → APPROVED → PUBLISHED；退回/撤回返回 DRAFT | 一审来源、任务组与候选、任务快照、指定 schedule_preview、规则版本 |
| 审核提交 | PENDING → APPROVED / RETURNED / WITHDRAWN | 创建时的目标快照不可变；终态不得重开，重提另建提交 |
| 审核决策 | APPROVE / RETURN | 一次提交唯一决策，永久保留 |

待审内容不能边审边改；必须先撤回。正式一审/二审的角色规则仍待产品确认。本批 DEMO 使用独立审核身份、禁止提交人自审；不能由表结构默认允许自审。确认记录和审核记录分开：勾选“执行条件已核实”不等于医生已通过一审。

### 6.3 AI 作业

保存输入快照和预期修订，返回作业 ID，查询作业状态。先接 MOCK 适配器，模拟结果必须显式标识，和真实医学确认分开。模型在事务外调用；写回时若已发生人工修改，标 STALE，保留返回但不覆盖当前草稿。重试新建作业，并关联 retry_of_job_id。

作业详细租约/超时策略在实现异步调度前补齐；本稿不是可直接运行的任务队列。生成失败不得落地正式任务。

### 6.4 任务组、变体、预览和实例

“每天一次测量”是任务组；允许轮换的不同菜谱/动作是组内变体；“某天某个槽位的一次任务”是实例。这三层分别存储，不展开成几十条方案建议，也不把每日实例当成模板。

schedule_rule_json 描述星期、时点、事件锚点、间隔和允许窗口。schedule_preview 固定某个修订已校验的排程。界面日期来自业务时区，不用 UTC 日期直接展示。药物提醒窗与医嘱执行规则分开，不能把“每日几次”擅自换成具体服药时间。

依赖真实早餐、入睡等事件的任务，不以预计时刻冒充实际发生时刻：生成可显示预计提示；正式任务保留 PENDING 锚点，待可信事件到达后解析窗口并审计。真实开放事件任务前需补齐事件接入与超时兜底；第一条演示链路可先使用已确认的固定时点示例。

### 6.5 发布事务与幂等

1. 创建/获取 publication，用 UK(org_id,idempotency_key) 去重。相同键不同 request_hash 返回 409；同一包不同键仍只允许一份发布。
2. 执行发布时，统一锁顺序为方案版本 → 任务包 → 审核提交 → 发布记录，校验同机构、同患者、同模式及两审的有效性；发布与撤回/换版共享父行锁，防止检查后被改变。
3. 一次数据库事务内，按指定 preview 落地全部 execution_task，处理确认的未来替代范围，写 outbox_event，更新 publication=PUBLISHED、任务包=PUBLISHED 和审计。
4. 成功提交后，患者端才可见；失败回滚正式实例、替代操作和事件。publication 的失败状态在单独恢复事务记录，避免和回滚事务混为一谈。
5. 通知由 outbox 独立投递，失败单独重试。通知送达失败不回滚已发布方案。
6. 患者端根据发布时间、生效边界、授权、DEMO/REAL 范围及任务有效状态读取；不能仅凭 bundle.status=APPROVED 查询患者任务。

数据库唯一键兜底：task_bundle 唯一发布；任务实例 UK(org_id,task_bundle_id,occurrence_key)。occurrence_key 至少区分同一天不同组、同组多个时点及同一时点多个执行槽位，不能只用“日期＋组 ID”。

## 7 JSON 字段必须采用的结构

以下仅约定结构，不填医学阈值、处方或默认运动强度。

| 字段 | 必须包含的结构 | 约束 |
|---|---|---|
| health_fact.value_json | valueType、value；必要时 code、displayText | 数值配 unit；数组/对象遵循 fact_code 对应 schema；未知用确认状态表达，不填 0 或 false 冒充正常 |
| health_input_snapshot.payload_json | patient、profileRevision、facts、axes、conflicts、missingFields；结构版本在 schema_version 列，后续外部来源版本放 sourceVersions | facts 保存事实 ID（追加修订身份）、值、来源和当时确认状态；按同一个 profile_revision 一致性读取；不伪造尚未接入平台的版本号 |
| plan_management_focus.dimension_weights_json | 七维代码到整数基点的映射 | 启用时覆盖七维，0 合法，总和 10000；字段和医学默认配比待确认 |
| care_plan_item.references_json | citationId、title、url、sourceType、verificationStatus；作者年份等可选 | verificationStatus 至少 UNVERIFIED / VERIFIED / REJECTED；URL 去重不等于内容已核验 |
| condition_confirmation.value_json | typedValues、sourceRef、professionalBinding（需要时） | 不把下拉选项标签当成业务编码；确认值与来源摘要绑定 |
| task_template.content_json | schemaVersion、action、fixedParameters、editableParameters、entryConditions、feedbackSchema、stopConditions | 由审核模板确定哪些字段可编辑；不能只有一段提示词 |
| task_group.schedule_rule_json | schemaVersion、isoWeekdays、slots、intervalRules、rotationPolicy | weekday=1..7；slot 有 slotKey、mode=FIXED/ANCHOR、localTime 或 anchorCode、offsetMinutes、窗口偏移、提醒配置及适用候选键 variantKeys（需要时） |
| task_group_variant.parameters_json | schemaVersion、quantity、unit、durationMinutes（适用时）、professionalSourceRef；按维度补扩展 | 处方保留原文、来源版本和哈希；饮食可有食材/份量/做法；不对七维硬塞同一字段 |
| task_group.feedback_schema_json | schemaVersion、fields、required、units、presentation | 约定反馈结构，不代表本期已实现打卡、质量评分与预警处理 |
| schedule_preview.occurrences_json | occurrenceKey、groupId、variantId、executionDate、zoneId、slotKey、times/anchor、contentSnapshot | 每条唯一；轮换后具体选择固定下来；不可只存“按原规则生成” |
| schedule_preview.issues_json | code、severity、fieldPath/targetRef、message、ruleRef | BLOCKER 不允许提交二审/发布；WARNING 是否需人工确认按规则版本决定 |
| execution_task.content_snapshot_json | 来源方案/任务版本、模板/规则、具体内容、专业确认、全部事实与模块依据 | 按 preview 拷贝并核对摘要，不从当前模板动态拼装已发布内容 |

对 JSON 引用的行、类型、同机构/患者/方案、模板审核状态进行服务校验。需要频繁列表筛选的字段再提取为普通列/索引；初稿不为全部 JSON 属性创建生成列。

## 8 前后端字段与 RESTful 资源

这部分是建议契约，尚未实现；视频/参考服务已有接口路径不等于新后端必须原样复制。

| 建议接口 | 读取/写入对象 | 结果 |
|---|---|---|
| GET /ncd-ai/patients | patient + 当前授权 | 200，分页患者列表 |
| GET /ncd-ai/patients/{id}/health-summary | patient + health_fact | 200，六方面摘要、来源、缺项和冲突 |
| POST /ncd-ai/care-plans | care_plan + care_plan_version | 201 + Location，创建方案身份和首稿 |
| GET /ncd-ai/plan-versions/{id} | 方案版本、模块、侧重、维度和条目 | 200，组合 VO |
| PATCH /ncd-ai/plan-versions/{id} | 当前草稿聚合 + expectedRevision | 200，新修订；并发冲突 409 |
| POST /ncd-ai/plan-versions/{id}/generation-jobs | 输入快照 + generation_job | 202 + 作业查询地址 |
| GET /ncd-ai/generation-jobs/{id} | generation_job | 200，进度/结果摘要 |
| POST /ncd-ai/plan-versions/{id}/condition-confirmations | condition_confirmation | 201，确认记录，不隐含审核通过 |
| POST /ncd-ai/plan-versions/{id}/review-submissions | 一审提交快照 | 201，提交记录 |
| POST /ncd-ai/review-submissions/{id}/decisions | review_decision | 201，决策记录 |
| POST /ncd-ai/plan-versions/{id}/task-bundles | 一审来源 + task_bundle | 201，新任务包；生成作业另有查询 ID |
| PATCH /ncd-ai/task-bundles/{id} | 任务包、组和变体 + expectedRevision | 200，新修订 |
| POST /ncd-ai/task-bundles/{id}/schedule-previews | schedule_preview | 201，预览和问题 |
| POST /ncd-ai/task-bundles/{id}/review-submissions | 二审提交 + 指定预览 | 201，提交记录；决策复用统一资源 |
| POST /ncd-ai/task-bundles/{id}/publications | publication + 幂等键 | 接受异步发布返回 202 和状态地址；已存在返回原资源，禁止重复落地 |
| GET /ncd-ai/publications/{id} | publication + 通知摘要 | 200，区分发布状态与通知状态 |
| GET /ncd-ai/patients/{id}/execution-tasks?date=... | 授权范围内的有效正式任务 | 200，按业务日期查询 |

接口对象 camelCase、业务 ID 为 String；数据库字段 snake_case。JSON 成功包装继续沿用 ApiResponse；204 不带包装。身份来自可信认证，不能通过前端自行填写 userId/orgId 获得权限。认证接入尚未完成，实施时同步补齐。

## 9 第一批具体交付与验收

建议先落 A 批，形成“选患者 → 看资料 → 新建草稿 → 调整模块/维度/条目 → 保存 → 重新打开”的最小可用功能。12 张 A 批表仍可拆成“患者基础 4 表”与“方案草稿及关系 8 表”两次迁移/开发任务，不要求一次写完。

- 准备一位完全虚构的 DEMO 患者，使用清楚标识的来源；不复制视频中的姓名、病历或处方。示例数据通过 local/demo 专用导入，禁止混入真实默认数据迁移。
- 姓名与档案入口可查询；六方面缺失为 UNKNOWN；输入事实带来源、时间和确认状态；冲突可见。
- 保存草稿时周期、模块、维度、管理侧重、条目和依据一次提交，重新打开可恢复；空草稿可以保存，但不能直接送审。
- 并发修改以 expectedRevision 校验；陈旧请求 409，不丢失前一位操作者修改。
- 资料变更不会反向修改已有生成快照；生成/人工定稿各按实际读取时刻留快照。
- 跨机构或未授权患者不可访问；DEMO 身份及数据不能通过改请求字段变成 REAL。
- 本批没有已实现业务的含义，功能和迁移需要另行开发验证。

## 10 待确认项与建议默认值

| 编号 | 待确认事项 | 初稿建议 / 影响 |
|---|---|---|
| Q01 | patientId 与现有 archiveId 的关系；是否有可复用档案/鉴权平台 | 本地 UUID + 独立 external_archive_id；联调前确认别名，不默认同一个编号 |
| Q02 | 机构、团队、操作人由谁提供 | 先预留外部标识，DEMO 使用明确固定开发主体；真实开放前接可信认证和服务授权 |
| Q03 | 病种主/次亚型、侧重选项、权重/融合贡献的编辑规则 | 保留视频字段，不编造字典和医学默认值；规则未确认时不据此自动分配专业任务 |
| Q04 | 周期天数、开始日期、生效边界、时区和提醒次数/窗口 | 可配置；视频 30 天只是示例；初稿建议 Asia/Shanghai，不能硬编码为唯一周期 |
| Q05 | 一审/二审的角色及能否同人、自审 | 两阶段权限独立；由业务确认角色与同人约束 |
| Q06 | 模板、专业参数、处方和知识版本由谁审核提供 | 首期模拟/固定模板仅供 DEMO；不能将医生核实缺项误认为平台模板已审核 |
| Q07 | 更换方案是否全周期替代，还是按维度/任务替代 | 显式 replacement_scope + effective_at；默认不覆盖已发生历史 |
| Q08 | 通知通道、餐次/入睡等实际事件如何到达 | outbox 预留；事件锚点不伪造实际时间，先用确认固定时点贯通演示 |
| Q09 | R4 中已标注的运动档位等规则冲突 | 业务/专业确认后版本化；数据设计保留 rule_set_version，不选一个未经确认值硬编码 |
| Q10 | 是否保留每次草稿编辑的完整历史版本 | 本稿每次保存留审计，生成输入/送审/预览完整快照；如要求逐次完整恢复，再增通用 revision_snapshot 表 |

以上不阻止先评审表关系和模拟草稿；会影响真实患者开放、自动生成规则及正式发布。A 批和本批增量 SQL 已实施；剩余规则确认后继续生成后续迁移和计划。

## 11 设计验收场景

| 场景 | 预期结果与存储依据 |
|---|---|
| 同时打开同一草稿，先后保存 | 后到的陈旧 row_version 请求返回 409，不覆盖已保存内容 |
| AI 返回前医生已修改草稿 | generation_job=STALE，保留原始返回，不覆盖新草稿 |
| 医生一审通过，方案状态随之更新 | 仅 row_version 改变；content_revision 与哈希保持不变，该审核仍有效 |
| 一审后改变目标或禁忌 | 新建方案版本和子表关系；旧一审不能授权新版本生成/发布 |
| 二审后改变执行时点或轮换候选 | 新任务包版本重新预览与二审，不沿用旧 preview_hash |
| 用药频次只有“每日一次”，无可信时点 | 保存缺项；不自动编造服药时间或绕过确认生成正式任务 |
| 同一天同一组存在两个执行槽位 | 两个 occurrence_key，分别落地，不被日期唯一键错误合并 |
| 预览中有任务冲突或必要条件缺失 | BLOCKER 保留在问题清单，拒绝提交二审或发布 |
| 相同幂等键重复发布；不同键重复同一包 | 返回原发布记录；正式实例仍只有一套。相同键不同内容返回 409 |
| 发布写到一半发生数据库异常 | 实例、替代更新和 outbox 同时回滚，不出现半套可见任务 |
| 发布成功但消息失败 | publication 仍为 PUBLISHED，outbox 独立失败和重试 |
| 新版本尚未生效或已有任务进入历史 | 按 effective_at 和替代范围处理未来任务，历史内容与反馈不覆盖 |
| 跨机构请求、DEMO 请求伪装 REAL | 服务端范围校验拒绝，不仅依赖列表隐藏或前端字段 |

## 12 自检结论

- 已区分主目标、模块、七维、病种侧重；已保留视频里的条件与排程字段。
- 条目—模块—事实关系、共享任务多来源和轮换候选均有存储落点。
- 原始资料、两阶段输入、送审内容、排程预览、正式内容分别保存，不靠当前数据重建历史。
- 审核绑定明确目标和修订；发布检查在同一受控事务内，唯一约束避免重复正式任务。
- 字段字典生成时已校验表名/字段名不重复、普通外键目标存在且类型一致；页面映射也做了字段存在性检查。
- 已执行 A 批建表并验证真实 MySQL；B/C/D 表未创建，未对参考服务器进行业务写入。
