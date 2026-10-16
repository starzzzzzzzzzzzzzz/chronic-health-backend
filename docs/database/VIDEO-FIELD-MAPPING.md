# 视频页面字段与数据库字段映射 初稿 v0.1

日期：2026-10-16。返回 [设计总览](DATABASE-DESIGN.md) · [字段字典](TABLE-DICTIONARY.md)。

此表用于与前端逐项核对，不把参考接口的字段名直接当成数据库列，也不宣称画面小字已全部识别。API 栏中带 `/` 的名称是需前后端确认的命名对应；JSON 子字段写在该栏，数据库栏列出真实存储列。

## 视频定位与来源级别

| 画面编号 | 约时间范围 | 已核对的可见区域 |
|---|---|---|
| V01 | 00:00—00:20 | 新建健康方案，选择患者完成、患者摘要、方案生成状态与步骤 |
| V02 | 00:20—01:00 | 病种管理侧重、主亚型/优先级、七维权重百分比、多病种融合 |
| V03 | 01:00—01:55 | 七维建议卡片、类别选择、修改/删除、参考来源 |
| V04 | 01:55—02:30 | 集中确认执行条件与缺项、已有用药计划导入、共享条件与确认说明 |
| V05 | 02:30—03:50 | 继续条件确认、修改执行内容、执行频次、星期勾选、提醒时点 |
| V06 | 03:50—04:15 | 周期/作息相关设置和每日任务预览：日期、提醒时间、分类、任务名称、执行说明、频次 |
| V07 | 04:30—05:15 | 双审与发布、方案一审/任务二审、后台进度、发布状态 |
| V10 | 05:10—05:22 | 返回列表，方案版本、生成/更新时间等列表信息 |

时间范围是定位区间，控件可能跨区间出现；V06 与 V07 间仍有建议/任务内容回看，不表示遗漏一个额外业务阶段。A 为参考 OpenAPI 字段；R1/R4 为设计总览列出的文档；D 为本稿设计建议。仅标 A/R/D 的条目不作为“视频直接展示”的证据。

## 逐项对应

| 序号 | 页面区域 | 页面/业务字段 | 建议 API 字段 | 数据库存储字段 | 来源 | 处理与校验 |
|---|---|---|---|---|---|---|
| M01 | 患者入口 | 患者姓名 | `patientName` | `patient.name` | V01 | 只保存本地演示虚构姓名；不复制视频人物资料 |
| M02 | 患者入口 | 患者/档案标识 | `patientId / archiveId` | `patient.id`<br>`patient.external_archive_id` | V01 + A Scope | 本地主键与外部编号分开；archiveId 的含义需前后端确认 |
| M03 | 患者入口 | 机构/门店与团队 | `orgId / teamId` | `patient.org_id`<br>`patient.team_id` | A Scope + D | 视频未确认完整组织结构；orgId 从身份范围解析，不能由请求体授权 |
| M04 | 患者入口 | 性别、年龄摘要 | `sex / age` | `patient.sex`<br>`patient.birth_date`<br>`health_fact.value_json` | V01 + D | 有生日才计算年龄；仅有年龄时记录带观测日期的 reported_age，不能倒推出精确生日 |
| M05 | 患者入口 | 健康资料与病历摘要 | `healthSummary / facts` | `health_fact.axis_code`<br>`health_fact.fact_code`<br>`health_fact.value_json` | V02 + R1 P02/P03 | 病历正文可留 DOCUMENT 事实；供生成使用的结构化事实独立保存 |
| M06 | 患者入口 | 来源、时间与是否确认 | `sourceType / sourceRef / observedAt / status` | `health_fact.source_type`<br>`health_fact.source_locator_json`<br>`health_fact.observed_at`<br>`health_fact.confirmation_status` | R1 P03 + A Fact | 来源类型、可信度、确认状态分开；未知不等于正常 |
| M07 | 患者入口 | 六方面画像 | `axes` | `health_fact.axis_code`<br>`health_input_snapshot.payload_json` | R1 P03 + A ConfirmProfile | H/F/B/C/E/G 是资料分类；不和七维或模块共用一套枚举 |
| M08 | 患者入口 | 患者权限范围 | `scope / capabilities` | `patient_access_grant.subject_id`<br>`patient_access_grant.capability` | R1 P01 + D | 每次查询都验证有效授权，页面可见不等于接口有权限 |
| M09 | 方案制定 | 方案名称 | `title` | `care_plan_version.title` | D + R1 B01 | 建议契约，视频名称/编号具体规则需核对 |
| M10 | 方案制定 | 方案版本与更新时间 | `versionNo / updatedAt` | `care_plan_version.version_no`<br>`care_plan_version.updated_at` | V10 + D | version_no 是业务版本，row_version 是编辑并发修订 |
| M11 | 方案制定 | 本周期主目标及依据 | `primaryGoal / goalBasis` | `care_plan_version.primary_goal`<br>`care_plan_version.goal_basis_json` | R1 B01/B03 | 来自文档补充；视频画面不足以确认全部目标输入控件 |
| M12 | 方案制定 | 补充要求/生成要求 | `supplementalRequirements` | `care_plan_version.supplemental_requirements` | A GenerateRecommendationsRequest + D | 补充条件不能越过已确认的专业参数 |
| M13 | 方案制定 | 本次使用的患者资料 | `inputSnapshotId` | `care_plan_version.input_snapshot_id`<br>`health_input_snapshot.payload_hash` | R1 P04 + A ProfileRef | 生成或人工形成方案依据时留快照，不能每次打开页面重新读取覆盖历史 |
| M14 | 方案制定 | 方案模块组合 | `modules` | `care_plan_module.module_code`<br>`care_plan_module.selection_reason` | R1 B03 + A ConfirmProfile | B0/L/R/C/F/A，多模块可选；不能等同于病种列表 |
| M15 | 管理侧重 | 病种/管理类别 | `diagnosisFactId` | `plan_management_focus.diagnosis_fact_id` | V02 + R1 P03 | 关联有来源的病种事实；单次异常不能伪造确诊 |
| M16 | 管理侧重 | 主亚型 | `primarySubtypeCode` | `plan_management_focus.primary_subtype_code` | V02 + A ConfirmedManagement | 编码字典和允许更改的角色待确认 |
| M17 | 管理侧重 | 次亚型 | `secondarySubtypeCodes` | `plan_management_focus.secondary_subtypes_json` | A ConfirmedManagement | API 补充，不能声称视频已完整展示选择控件 |
| M18 | 管理侧重 | 本周期管理优先级 | `priorityCode` | `plan_management_focus.priority_code` | V02 + A ConfirmedManagement | 与任务 L0—L4 优先级分开；取值待确认 |
| M19 | 管理侧重 | 七维百分比权重 | `dimensionWeights` | `plan_management_focus.dimension_weights_json` | V02 + A Weights | 视频百分比转换整数基点；启用时合计 10000，不硬编码视频示例 |
| M20 | 管理侧重 | 多病种融合占比 | `contributionBps` | `plan_management_focus.contribution_bps` | V02 + D | 跨病种融合贡献与单病种七维权重分开保存 |
| M21 | 管理侧重 | 调整侧重原因 | `adjustmentReason` | `plan_management_focus.adjustment_reason` | A ConfirmedManagement | 人工调整须有依据，不由数据库推断医学意义 |
| M22 | 七维建议 | 类别下拉/维度 | `dimensionCode` | `care_plan_dimension.dimension_code`<br>`care_plan_item.dimension_id` | V03 + A Recommendation | 使用七维业务编码，中文标签由前端映射 |
| M23 | 七维建议 | 启用、关闭与原因 | `decision / decisionReason` | `care_plan_dimension.decision`<br>`care_plan_dimension.decision_reason` | R1 B03 + R4 第五章 | 没有必要的维度可以关闭；不是七维都必须有建议 |
| M24 | 七维建议 | 建议标题与正文 | `title / content` | `care_plan_item.title`<br>`care_plan_item.content` | V03 + A Recommendation | 一维可多条，不能一个大字段混合七类建议 |
| M25 | 七维建议 | 修改/删除建议 | `selected / content` | `care_plan_item.is_selected`<br>`care_plan_item.content` | V03 | 草稿编辑同步递增父版本修订；已审内容变更另起版本 |
| M26 | 七维建议 | 是否形成执行目标 | `executionGoal` | `care_plan_item.execution_goal` | A Recommendation | 方案说明不一定全部拆成具体执行任务 |
| M27 | 七维建议 | 执行主体 | `actorType` | `care_plan_item.actor_type` | R1 B03/T02 | 本人、照护者、专业人员、共同执行；不只是某个提醒接收人 |
| M28 | 七维建议 | 参考来源/文献 | `references` | `care_plan_item.references_json` | V03 | 保存标题、链接、来源和核验状态；模型提供不等于已核实 |
| M29 | 七维建议 | 为什么生成这条建议 | `basis` | `plan_item_basis.module_id`<br>`plan_item_basis.fact_id` | R1 B03 + R4 4.1 | 关联主目标、模块和本次快照里的事实，多对多保留 |
| M30 | 集中确认 | 条件名称、缺项与要求 | `conditionKey / requirement` | `condition_confirmation.condition_key`<br>`condition_confirmation.requirement_json` | V04 + A Requirement | 共享条件按 condition_key 组织；业务码与屏幕文案分开 |
| M31 | 集中确认 | 已满足/待补齐/待核实 | `status` | `condition_confirmation.status` | V04 | MISSING/PENDING/CONFIRMED/REJECTED；生成失败状态另由作业给出，不混同条件状态 |
| M32 | 集中确认 | 核实说明、由谁何时确认 | `confirmationNote / confirmedBy / confirmedAt` | `condition_confirmation.confirmation_note`<br>`condition_confirmation.confirmed_by`<br>`condition_confirmation.confirmed_at` | V04 + R1 | 确认人来自身份；不能信任前端任填已核实 |
| M33 | 集中确认 | 导入患者已有用药计划 | `sourceFactId / sourceRef` | `condition_confirmation.source_fact_id`<br>`condition_confirmation.value_json` | V04 + A MedicationTarget | 导入已有有效医嘱并保留原文和版本；无有效来源时保持待核实 |
| M34 | 集中确认 | 药名、剂量、用法、服用时间 | `professionalParameters` | `condition_confirmation.value_json`<br>`care_plan_item.professional_parameters_json` | V04 + R4 用药规则 | 专业参数结构化保存并保持零改动；不能把“每日几次”自动变成服药时点 |
| M35 | 集中确认 | 动作/任务名称、内容 | `action.name / action.content` | `condition_confirmation.value_json`<br>`task_group_variant.title`<br>`task_group_variant.content` | V05 + A Action | 先记录确认值；生成任务后落到具体候选内容，保留来源 |
| M36 | 集中确认 | 数量、份量、时长、单位 | `parameters.quantity / unit / durationMinutes` | `condition_confirmation.value_json`<br>`task_group_variant.parameters_json` | V05 + A Input | 原始文本和结构化量分开，合法范围来自确认模板，不能自行推算专业量 |
| M37 | 集中确认 | 执行频次文本 | `frequencyDisplay` | `condition_confirmation.value_json`<br>`task_group.schedule_rule_json` | V05 + A Input | 频次说明与可排程结构分开；显示文本由结构派生或保留来源文本 |
| M38 | 集中确认 | 执行星期、周一至周日 | `isoWeekdays` | `condition_confirmation.value_json`<br>`task_group.schedule_rule_json` | V05 + A Frequency | 数组 1..7；不能中文逗号拼接，也不能只存“每天” |
| M39 | 集中确认 | 提醒时点与快捷选择 | `slots[].reminderTimes` | `condition_confirmation.value_json`<br>`task_group.schedule_rule_json` | V05 + A Input.times | 可多时点；用药提醒时点不能擅自改变医嘱执行语义 |
| M40 | 集中确认 | 反馈要求与注意事项 | `feedbackSchema / stopConditions` | `task_group.feedback_schema_json`<br>`task_group_variant.stop_conditions_json` | V05 + A Action | 反馈结构和停止条件分别保留；这一期不表示已有打卡评分实现 |
| M41 | 集中确认 | 待处理、可生成、失败等数量 | `counts` | `condition_confirmation.status`<br>`generation_job.status` | V04/V05 + D | 查询聚合，不为每个页面计数建一列；失败按作业/校验结果聚合，不强行映射为确认失败 |
| M42 | 生成进度 | 正在生成方案、错误和重试 | `generationJob` | `generation_job.status`<br>`generation_job.error_code`<br>`generation_job.retry_of_job_id` | V01/V03 + R2 | 进度来自作业，不靠前端定时器假造百分比；可用阶段状态 |
| M43 | 周期设置 | 周期开始、结束或天数 | `startDate / endDate / days` | `care_plan_version.start_date`<br>`care_plan_version.end_date`<br>`task_bundle.start_date`<br>`task_bundle.end_date` | V06 + A Period | days=结束日期-开始日期+1，默认不冗余存储；视频 30 天不是强制固定周期 |
| M44 | 周期设置 | 起床、入睡及餐次等锚点 | `anchors` | `health_fact.value_json`<br>`health_input_snapshot.payload_json`<br>`task_group.schedule_rule_json` | V06 + R1 T05 | 原始作息属于事实；生成读入快照；任务规则引用锚点，不混成全局固定时刻 |
| M45 | 周期设置 | 睡眠跨日与锚点推导 | `anchorOffset` | `task_group.schedule_rule_json` | R1 T05 + D | 本地时间配时区和跨日偏移；固定提醒与真实事件锚点分开 |
| M46 | 任务预览 | 日期 | `executionDate` | `schedule_preview.occurrences_json`<br>`execution_task.execution_date` | V06 | 发布前仅在不可变预览中；发布后形成正式日期列 |
| M47 | 任务预览 | 提醒/执行时间 | `scheduledAt / opensAt / closesAt` | `schedule_preview.occurrences_json`<br>`execution_task.scheduled_at`<br>`execution_task.opens_at`<br>`execution_task.closes_at` | V06 + A Occurrence | 展示时区时间；区分提醒和执行窗口，真实锚点尚未发生时不得假装已知 |
| M48 | 任务预览 | 分类、任务名称 | `dimensionCode / title` | `task_group.dimension_code`<br>`task_group_variant.title` | V06 | 每日预览引用选择的候选；同组轮换不改变来源 |
| M49 | 任务预览 | 执行说明、频次、起始统计/反馈规则 | `content / frequency / feedbackSchema` | `task_group_variant.content`<br>`task_group.schedule_rule_json`<br>`task_group.feedback_schema_json` | V06 + A Task | 右侧栏目小字不能全部可靠辨认；按文档/API 补充含义，前端需核对最后一列命名 |
| M50 | 任务预览 | 合计次数、每日展示/主动录入数量 | `counts` | `schedule_preview.occurrence_count`<br>`schedule_preview.occurrences_json` | V06 + R1 T05 | 总次数可缓存并与数组核对；每日数量按日期与反馈结构计算 |
| M51 | 任务预览 | 任务负担与冲突提示 | `issues` | `schedule_preview.issues_json` | V06 + R1 T03/T05 | severity 分 BLOCKER/WARNING/INFO，具体医学和负担数值来自规则版本 |
| M52 | 任务预览 | 必做/自选、优先级 | `requirement / priority` | `task_group.requirement`<br>`task_group.priority` | R1 T02/T04 + A Group | 任务 L0—L4 与病种侧重的 priority 不共用一套语义 |
| M53 | 任务预览 | 固定/轮换、候选项 | `poolPolicy / variants` | `task_group.pool_policy`<br>`task_group_variant.item_key` | R1 T05 + A Group | 区分组与变体；用药监测固定，不能为多样性自动改写 |
| M54 | 任务预览 | 预览版本/摘要 | `previewHash` | `schedule_preview.preview_hash`<br>`schedule_preview.bundle_revision` | A Preview + R1 S03 | 接口补充；二审锁定该 preview，发布复用同一结果 |
| M55 | 双审发布 | 第一审：方案审核 | `planSubmission / planDecision` | `review_submission.stage`<br>`review_submission.plan_version_id`<br>`review_decision.decision` | V07 + R1 R01—R04 | 一审核方案目标与专业内容；不是任务已发布 |
| M56 | 双审发布 | 第二审：任务审核 | `taskSubmission / taskDecision` | `review_submission.task_bundle_id`<br>`review_submission.schedule_preview_id`<br>`review_decision.decision` | V07 + R1 S01—S04 | 二审必须绑定指定任务包修订及排程预览 |
| M57 | 双审发布 | 审核意见、审核人、审核时间 | `comment / reviewerId / reviewedAt` | `review_decision.comment`<br>`review_decision.reviewer_id`<br>`review_decision.reviewed_at` | R1 R03/S03 + A PlanApproval | 视频不清晰的审核输入项依据文档/API 补齐；退回意见必填 |
| M58 | 双审发布 | 流程步骤/当前阶段 | `workflowStage` | `care_plan_version.status`<br>`task_bundle.status`<br>`review_submission.status`<br>`publication.status` | V07 + D | 组合 VO 派生，不在患者表另存一个万能步骤号 |
| M59 | 双审发布 | 后台处理中/发布中 | `publicationStatus` | `publication.status`<br>`publication.error_code` | V07 + R1 F02 | 区分业务发布成功与消息送达；刷新/重试不可重复写正式任务 |
| M60 | 双审发布 | 发布时间、操作者 | `publishedAt / publishedBy` | `publication.published_at`<br>`publication.published_by` | V07/V10 + R1 F02 | 创建方案时间、审核时间、发布时间分别保存 |
| M61 | 双审发布 | 生效时间和新旧方案替代 | `effectiveAt / replacesPublicationId` | `publication.effective_at`<br>`publication.replaces_publication_id`<br>`publication.replacement_scope_json` | R1 F05 + A Plan | 不同于提交时间；未来生效不提前替换当前任务 |
| M62 | 双审发布 | 发布数量与通知状态 | `taskCount / notificationStatus` | `publication.task_count`<br>`outbox_event.status` | R1 F02/F03 + D | 发布数量与预览一致；通知状态单独查询，不和 publication.status 共用字段 |
| M63 | 患者任务 | 当前有效任务与执行内容 | `executionTasks` | `execution_task.publication_id`<br>`execution_task.content_snapshot_json`<br>`execution_task.state` | R1 F04 + A TaskView | 仅已发布且获授权的范围，不能直接把 schedule_preview 当正式任务 |
| M64 | 列表看板 | 人数/方案数量与分页总数 | `total / counts` | `patient.status`<br>`care_plan_version.status` | V10 + D | 按明确查询范围聚合；“9”等演示数字不存为业务字段 |
| M65 | 技术字段 | 并发修订号 | `expectedRevision / rowVersion` | `care_plan_version.row_version`<br>`task_bundle.row_version` | A CandidateRef/PlanApproval + D | 写接口要求；数据库数值、接口字符串，旧请求 409 |
| M66 | 技术字段 | 发布幂等键 | `Idempotency-Key` | `publication.idempotency_key`<br>`publication.request_hash` | R1 F01 + D | 和追踪请求编号分开，不把 X-Request-Id 当天然幂等键 |
| M67 | 技术字段 | 事实/模板/规则和输入版本 | `sourceRefs` | `task_group_source.plan_item_basis_id`<br>`task_group_variant.template_version_id`<br>`task_bundle.rule_set_version_id` | R1 T02 + A TemplateRef | 不能只保留一段拼装文本后丢失来源 |

## 不直接建字段的页面内容

- 页签人数、待处理/可生成数量、任务总次数以查询聚合为主；只有昂贵且需一致性核对的总次数在预览/发布中保留缓存。
- “下一步”“生成任务草案”“发布”等按钮是对资源的操作，不建 next_step、button_text 等数据库列。
- 读取中、空态、加载动画是 UI 状态；后台阶段由 generation_job/publication 状态返回。
- 中文枚举标签、星期快捷选项、折叠状态由前端字典和组件维护；医生实际选择的业务值才持久化。
- 参考文献展示顺序可存在 references_json；不能因出现 PubMed 链接就把核验状态自动设为 VERIFIED。

## 前端核对清单

1. 核对患者 ID 的命名与含义、管理侧重/亚型/优先级字典、七维权重是否仍需全部可编辑。
2. 核对集中确认弹窗哪些项是共享条件、哪些参数只能医生确认、现有用药计划来自哪个接口。
3. 核对执行频次与具体时点是否分开、多时点和星期如何表达、提醒窗和执行窗如何展示。
4. 核对任务预览最后一列的准确名称与字段结构，以及是否需要某些尚未清晰显示的输入控件。
5. 核对审核和发布是否保留分步页面、是否允许一人两审、生效日期及退回路径。
6. 本稿没有访问参考服务器的业务数据接口；若后续提供前端代码/页面字段清单，再补齐低清画面未覆盖的字段，不以 OCR 猜测替代确认。
