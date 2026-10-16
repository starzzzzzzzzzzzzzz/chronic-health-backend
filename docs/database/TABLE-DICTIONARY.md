# 数据表与字段字典 初稿 v0.1

日期：2026-10-16。共 26 张业务候选表；现有 app_metadata、flyway_schema_history 不计入。A 批已按 V2/V3 迁移实现；V4 增加执行条件、一审提交及决策三表；B 批其余两表和 C/D 批仍为设计；实际 SQL 位于 src/main/resources/db/migration。

返回 [设计总览](DATABASE-DESIGN.md) · [页面字段映射](VIDEO-FIELD-MAPPING.md)

## 通用规则

- 字段列“可空”表示数据库 NULL；草稿可空而送审必填的规则，在各表约束中另列。BOOLEAN 为 MySQL 逻辑类型。
- 每表都显式包含 id、org_id、created_at、created_by；可变聚合/作业表另有 updated_at、row_version。子表随父聚合修订，不另设独立修订号。
- “只读/追加”表示内容不就地改写；草稿子表可在父聚合锁下编辑，已审内容冻结。模板/规则只允许受控生命周期状态变更。
- 索引中 PK(id) 与 UK(org_id,id) 为每表共同索引，表内不重复列。跨表 FK 使用 (org_id,foreign_id) → (org_id,id)，禁止级联删除审核/发布历史。
- 各外键默认建议物理 FK；双向引用先建表后加 FK。多态 audit target、JSON 内引用及事实/子表同版本归属，仍需事务内服务校验。
- UTC DATETIME(6) 存时刻，DATE/TIME 或 JSON 本地时间配 IANA zone_id 表达业务日历。编码、主键、幂等键采用区分大小写比较。
- SQL DEFAULT、CHECK、FK 的最终 DDL 在初稿评审后编写；这里没有隐式假定已存在的外部账号、机构或团队表。

## 表目录

| 表 | 职责 | 首次批次 |
|---|---|---|
| `patient` | 患者与档案入口 | A |
| `patient_access_grant` | 患者服务授权 | A |
| `health_fact` | 健康事实及来源 | A |
| `health_input_snapshot` | 生成输入快照 | A |
| `care_plan` | 周期管理方案身份 | A |
| `care_plan_version` | 方案版本及周期目标 | A |
| `care_plan_module` | 方案模块 | A |
| `plan_management_focus` | 病种管理侧重与权重 | A |
| `care_plan_dimension` | 七维启用与关闭决定 | A |
| `care_plan_item` | 七维方案条目 | A |
| `plan_item_basis` | 条目—模块—事实依据关系 | A |
| `condition_confirmation` | 执行条件与专业参数确认 | B |
| `generation_job` | AI / 模拟生成作业 | B |
| `rule_set_version` | 已确认规则版本 | B |
| `task_template` | 已审核任务模板版本 | C |
| `task_bundle` | 任务包版本 | C |
| `task_group` | 任务组与重复规则 | C |
| `task_group_variant` | 任务内容与轮换候选 | C |
| `task_group_source` | 任务组来源关系 | C |
| `schedule_preview` | 不可变排程预览 | C |
| `review_submission` | 审核提交及目标快照 | B |
| `review_decision` | 审核决策历史 | B |
| `publication` | 发布记录与幂等请求 | D |
| `execution_task` | 正式单次执行任务 | D |
| `outbox_event` | 发布通知事件 | D |
| `audit_log` | 关键操作审计 | A |

## 01 patient 患者与档案入口

保存患者标识及展示信息；六方面健康资料另存事实表。

首次批次：A。可变行，带乐观锁。

| 字段 | MySQL 类型 | 可空 | 含义与关联 |
|---|---|---|---|
| `id` | `CHAR(36)` | 否 | 应用生成 UUID 主键 |
| `org_id` | `VARCHAR(64)` | 否 | 机构范围；来自可信身份上下文 |
| `created_at` | `DATETIME(6)` | 否 | 创建时刻，UTC |
| `created_by` | `VARCHAR(64)` | 否 | 操作者或系统主体 ID |
| `updated_at` | `DATETIME(6)` | 否 | 最近修改时刻，UTC |
| `row_version` | `BIGINT UNSIGNED` | 否 | 乐观锁修订号，初值 0，成功修改加 1 |
| `external_system` | `VARCHAR(64)` | 是 | 外部平台来源；纯本地示例为 NULL |
| `external_archive_id` | `VARCHAR(64)` | 是 | 外部档案编号，保留原值，不冒充本地主键 |
| `name` | `VARCHAR(100)` | 否 | 患者姓名；演示使用虚构姓名 |
| `sex` | `VARCHAR(16)` | 否 | MALE / FEMALE / OTHER / UNKNOWN |
| `birth_date` | `DATE` | 是 | 真实生日未知时留空；不从年龄伪造生日 |
| `team_id` | `VARCHAR(64)` | 是 | 所属服务团队的外部标识 |
| `status` | `VARCHAR(16)` | 否 | ACTIVE / INACTIVE |
| `data_mode` | `VARCHAR(16)` | 否 | DEMO / REAL，创建后不在会话中切换 |
| `profile_revision` | `BIGINT UNSIGNED` | 否 | 健康事实或档案发生有效变动时递增 |

索引：UK(org_id,external_system,external_archive_id)；IDX(org_id,status,id)；IDX(org_id,name)。

约束：外部来源与外部编号同时有值或同时为空。DEMO 资料不可发布到真实患者通道。

## 02 patient_access_grant 患者服务授权

最小患者范围授权；接入既有平台时可替换为授权适配查询，不另建完整账号系统。

首次批次：A。可变行，带乐观锁。

| 字段 | MySQL 类型 | 可空 | 含义与关联 |
|---|---|---|---|
| `id` | `CHAR(36)` | 否 | 应用生成 UUID 主键 |
| `org_id` | `VARCHAR(64)` | 否 | 机构范围；来自可信身份上下文 |
| `created_at` | `DATETIME(6)` | 否 | 创建时刻，UTC |
| `created_by` | `VARCHAR(64)` | 否 | 操作者或系统主体 ID |
| `updated_at` | `DATETIME(6)` | 否 | 最近修改时刻，UTC |
| `row_version` | `BIGINT UNSIGNED` | 否 | 乐观锁修订号，初值 0，成功修改加 1 |
| `patient_id` | `CHAR(36)` | 否 | 被授权患者；关联 `patient.id` |
| `subject_id` | `VARCHAR(64)` | 否 | 可信登录主体 ID |
| `capability` | `VARCHAR(32)` | 否 | PATIENT_READ / PLAN_EDIT / PLAN_REVIEW / TASK_REVIEW / PLAN_PUBLISH |
| `valid_from` | `DATETIME(6)` | 否 | 授权开始，UTC |
| `valid_until` | `DATETIME(6)` | 是 | 授权结束，UTC；NULL 表示无预定截止 |
| `revoked_at` | `DATETIME(6)` | 是 | 撤销时刻，UTC |

索引：UK(org_id,patient_id,subject_id,capability)；IDX(org_id,subject_id,capability,revoked_at)。

约束：每次读写重新验证有效服务关系；org_id 或 subject_id 不能直接信任请求体。授权续期更新当前行，历史写 audit_log。

## 03 health_fact 健康事实及来源

按来源保留 H/F/B/C/E/G 六方面事实，避免把病历正文当成唯一输入。事实采用追加修订，历史不覆盖。

首次批次：A。内容受父版本或追加修订规则约束。

| 字段 | MySQL 类型 | 可空 | 含义与关联 |
|---|---|---|---|
| `id` | `CHAR(36)` | 否 | 应用生成 UUID 主键 |
| `org_id` | `VARCHAR(64)` | 否 | 机构范围；来自可信身份上下文 |
| `created_at` | `DATETIME(6)` | 否 | 创建时刻，UTC |
| `created_by` | `VARCHAR(64)` | 否 | 操作者或系统主体 ID |
| `patient_id` | `CHAR(36)` | 否 | 所属患者；关联 `patient.id` |
| `axis_code` | `VARCHAR(8)` | 否 | H 健康、F 功能、B 生活方式、C 照护、E 执行条件、G 优先问题 |
| `fact_code` | `VARCHAR(64)` | 否 | 稳定字段编码，例如 diagnosis、wake_time、available_equipment |
| `value_json` | `JSON` | 是 | 结构化事实值；UNKNOWN 时可为空 |
| `unit` | `VARCHAR(32)` | 是 | 单位；非数值可为空 |
| `source_type` | `VARCHAR(32)` | 否 | PROFESSIONAL_RECORD / SELF_REPORT / DEVICE / DOCUMENT / AI_EXTRACTION / DEMO |
| `source_locator_json` | `JSON` | 是 | 来源系统、记录 ID、文档页或段落定位；不存访问凭证 |
| `observed_at` | `DATETIME(6)` | 是 | 事实观测时刻，UTC；未知不填当前时间冒充 |
| `confidence` | `DECIMAL(5,4)` | 是 | 仅保留来源提供的可信度；无数值依据时留空 |
| `confirmation_status` | `VARCHAR(32)` | 否 | CONFIRMED_PROFESSIONAL / CONFIRMED_SELF / REPORTED / AI_CANDIDATE / UNKNOWN |
| `confirmed_by` | `VARCHAR(64)` | 是 | 确认主体，和原始来源分开 |
| `confirmed_at` | `DATETIME(6)` | 是 | 确认时刻，UTC |
| `supersedes_fact_id` | `CHAR(36)` | 是 | 同一事实上一修订；不同来源可同时保留；关联 `health_fact.id` |
| `conflict_group_key` | `VARCHAR(64)` | 是 | 互相矛盾的事实分组；不得静默合并 |

索引：IDX(org_id,patient_id,axis_code,fact_code,observed_at)；IDX(org_id,patient_id,conflict_group_key)；UK(org_id,supersedes_fact_id)。

约束：confidence 有值时处于 [0,1]。取当前事实时排除被后继取代的行；确认状态更改也追加修订。跨来源冲突由规则/人工决议，不能仅取最新行；事实新增在同一事务递增 patient.profile_revision。

## 04 health_input_snapshot 生成输入快照

方案生成和任务生成各自冻结实际读取的资料。

首次批次：A。内容受父版本或追加修订规则约束。

| 字段 | MySQL 类型 | 可空 | 含义与关联 |
|---|---|---|---|
| `id` | `CHAR(36)` | 否 | 应用生成 UUID 主键 |
| `org_id` | `VARCHAR(64)` | 否 | 机构范围；来自可信身份上下文 |
| `created_at` | `DATETIME(6)` | 否 | 创建时刻，UTC |
| `created_by` | `VARCHAR(64)` | 否 | 操作者或系统主体 ID |
| `patient_id` | `CHAR(36)` | 否 | 所属患者；关联 `patient.id` |
| `stage` | `VARCHAR(16)` | 否 | PLAN / TASK |
| `profile_revision` | `BIGINT UNSIGNED` | 否 | 读取时患者资料修订号 |
| `captured_at` | `DATETIME(6)` | 否 | 读取时刻，UTC |
| `schema_version` | `VARCHAR(32)` | 否 | 快照 JSON 结构版本 |
| `payload_json` | `JSON` | 否 | 六方面事实值、事实 ID、来源、冲突、未知项、患者展示信息及资料版本 |
| `payload_hash` | `CHAR(64)` | 否 | 规范化 JSON 的 SHA-256 |

索引：IDX(org_id,patient_id,stage,captured_at)。

约束：仅在生成或人工形成待审方案/任务依据时建立；打开详情不提前固定。快照同时保存值及来源，不能只存 ID 后重新读当前资料。

## 05 care_plan 周期管理方案身份

同一患者的一次周期方案稳定身份，具体内容放在版本表。

首次批次：A。可变行，带乐观锁。

| 字段 | MySQL 类型 | 可空 | 含义与关联 |
|---|---|---|---|
| `id` | `CHAR(36)` | 否 | 应用生成 UUID 主键 |
| `org_id` | `VARCHAR(64)` | 否 | 机构范围；来自可信身份上下文 |
| `created_at` | `DATETIME(6)` | 否 | 创建时刻，UTC |
| `created_by` | `VARCHAR(64)` | 否 | 操作者或系统主体 ID |
| `updated_at` | `DATETIME(6)` | 否 | 最近修改时刻，UTC |
| `row_version` | `BIGINT UNSIGNED` | 否 | 乐观锁修订号，初值 0，成功修改加 1 |
| `patient_id` | `CHAR(36)` | 否 | 所属患者；关联 `patient.id` |
| `plan_no` | `VARCHAR(64)` | 否 | 业务显示编号 |
| `current_version_id` | `CHAR(36)` | 是 | 最近工作版本，不代表患者当前生效版本；关联 `care_plan_version.id` |
| `status` | `VARCHAR(16)` | 否 | OPEN / CLOSED |
| `closed_reason` | `VARCHAR(500)` | 是 | 关闭原因 |

索引：UK(org_id,plan_no)；IDX(org_id,patient_id,created_at)。

约束：新建先插入方案，再插版本并回填指针，同一事务完成。患者生效方案必须根据 publication 及生效时刻查询。

## 06 care_plan_version 方案版本及周期目标

承载方案草稿和一审状态；通过一审的内容冻结，后续实质改动创建新版本。

首次批次：A。可变行，带乐观锁。

| 字段 | MySQL 类型 | 可空 | 含义与关联 |
|---|---|---|---|
| `id` | `CHAR(36)` | 否 | 应用生成 UUID 主键 |
| `org_id` | `VARCHAR(64)` | 否 | 机构范围；来自可信身份上下文 |
| `created_at` | `DATETIME(6)` | 否 | 创建时刻，UTC |
| `created_by` | `VARCHAR(64)` | 否 | 操作者或系统主体 ID |
| `updated_at` | `DATETIME(6)` | 否 | 最近修改时刻，UTC |
| `row_version` | `BIGINT UNSIGNED` | 否 | 乐观锁修订号，初值 0，成功修改加 1 |
| `plan_id` | `CHAR(36)` | 否 | 稳定方案 ID；关联 `care_plan.id` |
| `version_no` | `INT UNSIGNED` | 否 | 方案内递增版本号，从 1 开始 |
| `based_on_version_id` | `CHAR(36)` | 是 | 修订来源版本；关联 `care_plan_version.id` |
| `title` | `VARCHAR(200)` | 否 | 方案名称 |
| `start_date` | `DATE` | 否 | 周期开始日期，按 zone_id |
| `end_date` | `DATE` | 否 | 周期结束日期，包含当天 |
| `zone_id` | `VARCHAR(64)` | 否 | 业务时区，初稿建议 Asia/Shanghai |
| `primary_goal` | `VARCHAR(1000)` | 是 | 本周期唯一主目标；空草稿可无，一审前必填 |
| `goal_basis_json` | `JSON` | 是 | 主目标依据、事实引用与确定主体 |
| `supplemental_requirements` | `TEXT` | 是 | 医生输入的补充要求 |
| `input_snapshot_id` | `CHAR(36)` | 是 | 生成/人工定稿实际使用的 PLAN 快照；关联 `health_input_snapshot.id` |
| `status` | `VARCHAR(24)` | 否 | DRAFT / IN_REVIEW / APPROVED / SUPERSEDED / CANCELLED |
| `content_hash` | `CHAR(64)` | 是 | 当前方案内容摘要；包括模块、管理侧重、维度、条目及依据 |
| `change_reason` | `VARCHAR(1000)` | 是 | 人工修订或新版本创建原因 |
| `content_revision` | `BIGINT UNSIGNED` | 否 | 内容修订号，初值 0；内容/子表/相关确认变化时加 1，审核和发布状态变化不递增 |

索引：UK(org_id,plan_id,version_no)；IDX(org_id,status,updated_at)。

约束：start_date ≤ end_date。一审提交前主目标、输入快照、依据齐全。row_version 约束整个聚合；任何内容或子表编辑同时递增 content_revision 并重算 content_hash，状态变化仅递增 row_version。审核状态变化不计入内容哈希。

## 07 care_plan_module 方案模块

表达本周期解决哪些问题，与七维行动分类分开。草稿中可调整，冻结后只读。

首次批次：A。内容受父版本或追加修订规则约束。

| 字段 | MySQL 类型 | 可空 | 含义与关联 |
|---|---|---|---|
| `id` | `CHAR(36)` | 否 | 应用生成 UUID 主键 |
| `org_id` | `VARCHAR(64)` | 否 | 机构范围；来自可信身份上下文 |
| `created_at` | `DATETIME(6)` | 否 | 创建时刻，UTC |
| `created_by` | `VARCHAR(64)` | 否 | 操作者或系统主体 ID |
| `plan_version_id` | `CHAR(36)` | 否 | 所属方案版本；关联 `care_plan_version.id` |
| `module_code` | `VARCHAR(8)` | 否 | B0 / L / R / C / F / A |
| `selection_reason` | `TEXT` | 否 | 选中该模块的原因 |
| `basis_json` | `JSON` | 否 | 事实 ID、主目标引用及选择依据 |

索引：UK(org_id,plan_version_id,module_code)。

约束：字段变更受 care_plan_version.row_version 控制。六类模块可组合，不按病种强制整套启用。

## 08 plan_management_focus 病种管理侧重与权重

对应视频中管理侧重、亚型与多病种融合权重；不替代每周期唯一主目标。

首次批次：A。内容受父版本或追加修订规则约束。

| 字段 | MySQL 类型 | 可空 | 含义与关联 |
|---|---|---|---|
| `id` | `CHAR(36)` | 否 | 应用生成 UUID 主键 |
| `org_id` | `VARCHAR(64)` | 否 | 机构范围；来自可信身份上下文 |
| `created_at` | `DATETIME(6)` | 否 | 创建时刻，UTC |
| `created_by` | `VARCHAR(64)` | 否 | 操作者或系统主体 ID |
| `plan_version_id` | `CHAR(36)` | 否 | 所属方案版本；关联 `care_plan_version.id` |
| `diagnosis_fact_id` | `CHAR(36)` | 否 | 已确认病种事实；来源需可追溯；关联 `health_fact.id` |
| `primary_subtype_code` | `VARCHAR(64)` | 是 | 主亚型编码；字典与前端确认 |
| `secondary_subtypes_json` | `JSON` | 是 | 次亚型编码数组 |
| `priority_code` | `VARCHAR(32)` | 是 | 本周期管理优先级编码，候选值待确认 |
| `dimension_weights_json` | `JSON` | 是 | 七维权重，整数基点，10000 表示 100% |
| `contribution_bps` | `INT UNSIGNED` | 是 | 多病种融合贡献，整数基点 |
| `adjustment_reason` | `TEXT` | 是 | 人工调整侧重/权重的依据 |

索引：UK(org_id,plan_version_id,diagnosis_fact_id)。

约束：启用权重时每组七维权重合计 10000，同方案各病种 contribution_bps 合计 10000；零权重不等于允许删除安全任务。首期仅存储和展示，不自行发明医学分值或默认配比。

## 09 care_plan_dimension 七维启用与关闭决定

记录每个维度是否进入本方案及原因；维度下可有多条条目。

首次批次：A。内容受父版本或追加修订规则约束。

| 字段 | MySQL 类型 | 可空 | 含义与关联 |
|---|---|---|---|
| `id` | `CHAR(36)` | 否 | 应用生成 UUID 主键 |
| `org_id` | `VARCHAR(64)` | 否 | 机构范围；来自可信身份上下文 |
| `created_at` | `DATETIME(6)` | 否 | 创建时刻，UTC |
| `created_by` | `VARCHAR(64)` | 否 | 操作者或系统主体 ID |
| `plan_version_id` | `CHAR(36)` | 否 | 所属方案版本；关联 `care_plan_version.id` |
| `dimension_code` | `VARCHAR(16)` | 否 | medication / diet / exercise / routine / monitoring / tcm / other |
| `decision` | `VARCHAR(16)` | 否 | ENABLED / CLOSED / PENDING |
| `decision_reason` | `TEXT` | 是 | 关闭、待确认或人工变更原因 |
| `sort_order` | `INT UNSIGNED` | 否 | 展示顺序 |

索引：UK(org_id,plan_version_id,dimension_code)。

约束：CLOSED/PENDING 必须有原因；不强制七维全部启用。统计从该表和条目聚合，不靠是否有正文推断。

## 10 care_plan_item 七维方案条目

方案建议、执行目标和参考依据；尚不是某天的一次任务。

首次批次：A。内容受父版本或追加修订规则约束。

| 字段 | MySQL 类型 | 可空 | 含义与关联 |
|---|---|---|---|
| `id` | `CHAR(36)` | 否 | 应用生成 UUID 主键 |
| `org_id` | `VARCHAR(64)` | 否 | 机构范围；来自可信身份上下文 |
| `created_at` | `DATETIME(6)` | 否 | 创建时刻，UTC |
| `created_by` | `VARCHAR(64)` | 否 | 操作者或系统主体 ID |
| `plan_version_id` | `CHAR(36)` | 否 | 所属方案版本；关联 `care_plan_version.id` |
| `dimension_id` | `CHAR(36)` | 否 | 所属维度决定；关联 `care_plan_dimension.id` |
| `title` | `VARCHAR(200)` | 否 | 建议标题或项目名称 |
| `content` | `TEXT` | 否 | 建议/专业内容正文 |
| `execution_goal` | `BOOLEAN` | 否 | 是否需要拆为执行任务 |
| `actor_type` | `VARCHAR(16)` | 否 | PATIENT / CAREGIVER / PROFESSIONAL / SHARED |
| `contraindications_json` | `JSON` | 是 | 禁忌与停止/转人工条件 |
| `professional_parameters_json` | `JSON` | 是 | 处方、阈值、形态、强度等专业参数及确认引用 |
| `references_json` | `JSON` | 是 | 参考文献元数据、来源、核验状态；模型给出链接不等于已核验 |
| `is_selected` | `BOOLEAN` | 否 | 是否保留该条目；删除草稿条目可置 false |
| `sort_order` | `INT UNSIGNED` | 否 | 维度内展示顺序 |

索引：IDX(org_id,plan_version_id,dimension_id,sort_order)。

约束：维度、模块依据和事实必须属于同一患者与方案版本。固定专业参数只能采用有效确认来源，AI 不可改写。

## 11 plan_item_basis 条目—模块—事实依据关系

表达多对多关系，支持一个行动来自多个模块或多个事实。

首次批次：A。内容受父版本或追加修订规则约束。

| 字段 | MySQL 类型 | 可空 | 含义与关联 |
|---|---|---|---|
| `id` | `CHAR(36)` | 否 | 应用生成 UUID 主键 |
| `org_id` | `VARCHAR(64)` | 否 | 机构范围；来自可信身份上下文 |
| `created_at` | `DATETIME(6)` | 否 | 创建时刻，UTC |
| `created_by` | `VARCHAR(64)` | 否 | 操作者或系统主体 ID |
| `plan_item_id` | `CHAR(36)` | 否 | 条目；关联 `care_plan_item.id` |
| `module_id` | `CHAR(36)` | 否 | 关联模块；关联 `care_plan_module.id` |
| `fact_id` | `CHAR(36)` | 否 | 来源事实；关联 `health_fact.id` |
| `basis_note` | `VARCHAR(1000)` | 是 | 这项事实如何支持该模块及条目 |

索引：UK(org_id,plan_item_id,module_id,fact_id)；IDX(org_id,fact_id)。

约束：选中条目在提交前至少有一条有效依据。事实必须出现在本方案输入快照，不能以别的患者事实充当依据。

## 12 condition_confirmation 执行条件与专业参数确认

对应集中确认弹窗：保留缺项、人工核实和确认值；每次修改形成新确认行。

首次批次：B。V4 已建表；仅 PLAN 目标可写，TASK 字段保留为空并由 CHECK 限制。内容受父版本或追加修订规则约束。

| 字段 | MySQL 类型 | 可空 | 含义与关联 |
|---|---|---|---|
| `id` | `CHAR(36)` | 否 | 应用生成 UUID 主键 |
| `org_id` | `VARCHAR(64)` | 否 | 机构范围；来自可信身份上下文 |
| `created_at` | `DATETIME(6)` | 否 | 创建时刻，UTC |
| `created_by` | `VARCHAR(64)` | 否 | 操作者或系统主体 ID |
| `plan_version_id` | `CHAR(36)` | 是 | 方案阶段确认目标；关联 `care_plan_version.id` |
| `task_bundle_id` | `CHAR(36)` | 是 | 任务阶段确认目标；关联 `task_bundle.id` |
| `condition_key` | `VARCHAR(100)` | 否 | 条件编码，例如 medication.timing 或 exercise.equipment |
| `requirement_json` | `JSON` | 否 | 需要确认的字段、适用条目、约束和确认权限 |
| `value_json` | `JSON` | 是 | 确认值、单位、星期、提醒时点、专业来源等 |
| `status` | `VARCHAR(24)` | 否 | MISSING / PENDING / CONFIRMED / REJECTED |
| `source_fact_id` | `CHAR(36)` | 是 | 导入的已有医嘱/事实；关联 `health_fact.id` |
| `subject_revision` | `BIGINT UNSIGNED` | 否 | 确认适用的目标 content_revision，按确认事务最终内容修订绑定 |
| `binding_hash` | `CHAR(64)` | 否 | 需确认内容与来源参数的摘要 |
| `confirmed_by` | `VARCHAR(64)` | 是 | 确认主体 |
| `confirmed_at` | `DATETIME(6)` | 是 | 确认时刻，UTC |
| `confirmation_note` | `TEXT` | 是 | 确认说明 |
| `supersedes_confirmation_id` | `CHAR(36)` | 是 | 同一确认项上一记录；关联 `condition_confirmation.id` |

索引：IDX(org_id,plan_version_id,condition_key)；IDX(org_id,task_bundle_id,condition_key)；UK(org_id,supersedes_confirmation_id)。

约束：plan_version_id 与 task_bundle_id 恰好一个有值；CONFIRMED 时须有确认人、时间、合法值及来源。目标正文修订或绑定摘要变化，旧确认查询显示 STALE；新修订未被显式重新核实的旧确认不自动适用。确认追加本身只递增父 row_version；一审通过时确认清单与正文一起固定。共享条件可以引用同一确认记录，界面计数按当前有效确认聚合。

## 13 generation_job AI / 模拟生成作业

记录输入版本、结果与失败；先用模拟适配器，后替换真实 AI。

首次批次：B。可变行，带乐观锁。

| 字段 | MySQL 类型 | 可空 | 含义与关联 |
|---|---|---|---|
| `id` | `CHAR(36)` | 否 | 应用生成 UUID 主键 |
| `org_id` | `VARCHAR(64)` | 否 | 机构范围；来自可信身份上下文 |
| `created_at` | `DATETIME(6)` | 否 | 创建时刻，UTC |
| `created_by` | `VARCHAR(64)` | 否 | 操作者或系统主体 ID |
| `updated_at` | `DATETIME(6)` | 否 | 最近修改时刻，UTC |
| `row_version` | `BIGINT UNSIGNED` | 否 | 乐观锁修订号，初值 0，成功修改加 1 |
| `job_type` | `VARCHAR(16)` | 否 | PLAN / TASK |
| `plan_version_id` | `CHAR(36)` | 是 | 方案生成目标；关联 `care_plan_version.id` |
| `task_bundle_id` | `CHAR(36)` | 是 | 任务生成目标；关联 `task_bundle.id` |
| `input_snapshot_id` | `CHAR(36)` | 否 | 本次输入快照；关联 `health_input_snapshot.id` |
| `expected_revision` | `BIGINT UNSIGNED` | 否 | 提交时目标内容修订号 |
| `status` | `VARCHAR(24)` | 否 | QUEUED / RUNNING / SUCCEEDED / FAILED / STALE |
| `provider` | `VARCHAR(32)` | 否 | MOCK 或明确模型服务名称 |
| `model_name` | `VARCHAR(128)` | 是 | 实际模型标识 |
| `prompt_version` | `VARCHAR(64)` | 否 | 提示模板版本 |
| `rule_set_version_id` | `CHAR(36)` | 否 | 本次规则快照；关联 `rule_set_version.id` |
| `request_payload_json` | `JSON` | 否 | 实际结构化请求，不含访问凭证 |
| `raw_response` | `LONGTEXT` | 是 | 原始返回；可含非法 JSON，因此不限定 JSON 列 |
| `validated_result_json` | `JSON` | 是 | 校验通过的结构化结果 |
| `retry_of_job_id` | `CHAR(36)` | 是 | 重试来源，重试另建作业保留历史；关联 `generation_job.id` |
| `error_code` | `VARCHAR(64)` | 是 | 失败或失效原因码 |
| `error_message` | `VARCHAR(1000)` | 是 | 去敏错误摘要 |
| `started_at` | `DATETIME(6)` | 是 | 开始时刻，UTC |
| `finished_at` | `DATETIME(6)` | 是 | 完成时刻，UTC |

索引：IDX(org_id,status,created_at)；IDX(org_id,plan_version_id,created_at)；IDX(org_id,task_bundle_id,created_at)。

约束：恰好一个目标。结果写回时条件更新 revision 并校验目标状态；旧响应标 STALE，不覆盖人工编辑。模型调用在数据库事务外；工作进程须认领/超时恢复，租约字段在实施作业调度时补齐。

## 14 rule_set_version 已确认规则版本

保存使用的规则快照，而非在代码中散落阈值；本稿只定义结构。

首次批次：B。内容受父版本或追加修订规则约束。

| 字段 | MySQL 类型 | 可空 | 含义与关联 |
|---|---|---|---|
| `id` | `CHAR(36)` | 否 | 应用生成 UUID 主键 |
| `org_id` | `VARCHAR(64)` | 否 | 机构范围；来自可信身份上下文 |
| `created_at` | `DATETIME(6)` | 否 | 创建时刻，UTC |
| `created_by` | `VARCHAR(64)` | 否 | 操作者或系统主体 ID |
| `rule_code` | `VARCHAR(64)` | 否 | 规则包标识 |
| `version_no` | `INT UNSIGNED` | 否 | 版本号 |
| `schema_version` | `VARCHAR(32)` | 否 | 规则 JSON 结构版本 |
| `content_json` | `JSON` | 否 | 经确认的生成、负担、排程、禁忌和校验规则 |
| `content_hash` | `CHAR(64)` | 否 | 规则摘要 |
| `approval_ref` | `VARCHAR(128)` | 是 | 业务/专业审核来源 |
| `approved_by` | `VARCHAR(64)` | 是 | 确认主体 |
| `approved_at` | `DATETIME(6)` | 是 | 确认时间，UTC |
| `status` | `VARCHAR(16)` | 否 | DRAFT / APPROVED / RETIRED |

索引：UK(org_id,rule_code,version_no)。

约束：APPROVED 内容不可编辑；停用以新状态记录或受控管理操作实现，不改历史 content_json。只有有效 APPROVED 规则可用于正式生成和发布。状态列是受控生命周期例外，不能据此修改内容。

## 15 task_template 已审核任务模板版本

固定专业参数、可编辑范围、反馈及停止条件的来源，一条记录是一个模板版本。

首次批次：C。内容受父版本或追加修订规则约束。

| 字段 | MySQL 类型 | 可空 | 含义与关联 |
|---|---|---|---|
| `id` | `CHAR(36)` | 否 | 应用生成 UUID 主键 |
| `org_id` | `VARCHAR(64)` | 否 | 机构范围；来自可信身份上下文 |
| `created_at` | `DATETIME(6)` | 否 | 创建时刻，UTC |
| `created_by` | `VARCHAR(64)` | 否 | 操作者或系统主体 ID |
| `template_code` | `VARCHAR(64)` | 否 | 稳定模板编码 |
| `version_no` | `INT UNSIGNED` | 否 | 模板版本 |
| `dimension_code` | `VARCHAR(16)` | 否 | 七维代码 |
| `title` | `VARCHAR(200)` | 否 | 模板名称 |
| `schema_version` | `VARCHAR(32)` | 否 | 模板 JSON 结构版本 |
| `content_json` | `JSON` | 否 | 动作、固定专业参数、允许编辑参数、准入条件、反馈/停止规则 |
| `content_hash` | `CHAR(64)` | 否 | 模板内容摘要 |
| `rule_set_version_id` | `CHAR(36)` | 否 | 规则包来源；关联 `rule_set_version.id` |
| `status` | `VARCHAR(16)` | 否 | DRAFT / APPROVED / RETIRED |
| `approved_by` | `VARCHAR(64)` | 是 | 平台审核人 |
| `approved_at` | `DATETIME(6)` | 是 | 平台审核时间，UTC |

索引：UK(org_id,template_code,version_no)；IDX(org_id,dimension_code,status)。

约束：APPROVED 内容冻结；具体患者专业参数还需 condition_confirmation 与医生一审支持。中医模板未审核不能因医生点选而绕过。

## 16 task_bundle 任务包版本

一审方案派生的任务组与排程容器，与方案版本分开进行二审。

首次批次：C。可变行，带乐观锁。

| 字段 | MySQL 类型 | 可空 | 含义与关联 |
|---|---|---|---|
| `id` | `CHAR(36)` | 否 | 应用生成 UUID 主键 |
| `org_id` | `VARCHAR(64)` | 否 | 机构范围；来自可信身份上下文 |
| `created_at` | `DATETIME(6)` | 否 | 创建时刻，UTC |
| `created_by` | `VARCHAR(64)` | 否 | 操作者或系统主体 ID |
| `updated_at` | `DATETIME(6)` | 否 | 最近修改时刻，UTC |
| `row_version` | `BIGINT UNSIGNED` | 否 | 乐观锁修订号，初值 0，成功修改加 1 |
| `plan_version_id` | `CHAR(36)` | 否 | 作为生成依据的一审方案版本；关联 `care_plan_version.id` |
| `version_no` | `INT UNSIGNED` | 否 | 该方案版本内任务包版本号 |
| `based_on_bundle_id` | `CHAR(36)` | 是 | 修订来源任务包；关联 `task_bundle.id` |
| `plan_review_decision_id` | `CHAR(36)` | 否 | 一审通过依据；关联 `review_decision.id` |
| `input_snapshot_id` | `CHAR(36)` | 是 | 生成时实际读取的 TASK 快照；关联 `health_input_snapshot.id` |
| `start_date` | `DATE` | 否 | 任务周期开始 |
| `end_date` | `DATE` | 否 | 任务周期结束，包含当天 |
| `zone_id` | `VARCHAR(64)` | 否 | 业务时区 |
| `rule_set_version_id` | `CHAR(36)` | 否 | 任务校验/排程规则；关联 `rule_set_version.id` |
| `status` | `VARCHAR(24)` | 否 | DRAFT / IN_REVIEW / APPROVED / PUBLISHED / SUPERSEDED / CANCELLED |
| `content_hash` | `CHAR(64)` | 是 | 组、变体、来源、条件与规则等摘要，不包含生命周期状态 |
| `change_reason` | `VARCHAR(1000)` | 是 | 任务包修订原因 |
| `content_revision` | `BIGINT UNSIGNED` | 否 | 内容修订号，初值 0；内容/子表/相关确认变化时加 1，审核和发布状态变化不递增 |

索引：UK(org_id,plan_version_id,version_no)；IDX(org_id,status,updated_at)。

约束：一审通过记录必须指向这个冻结方案版本。日期在方案周期内；子表修改受本行 row_version 约束，并递增 content_revision；生命周期状态变化不递增 content_revision。已发布包不编辑，修订另建包；医学目标变化先另建方案版本并一审。

## 17 task_group 任务组与重复规则

描述周期中重复的行动槽位；轮换内容在 task_group_variant，单次执行在 execution_task。

首次批次：C。内容受父版本或追加修订规则约束。

| 字段 | MySQL 类型 | 可空 | 含义与关联 |
|---|---|---|---|
| `id` | `CHAR(36)` | 否 | 应用生成 UUID 主键 |
| `org_id` | `VARCHAR(64)` | 否 | 机构范围；来自可信身份上下文 |
| `created_at` | `DATETIME(6)` | 否 | 创建时刻，UTC |
| `created_by` | `VARCHAR(64)` | 否 | 操作者或系统主体 ID |
| `task_bundle_id` | `CHAR(36)` | 否 | 所属任务包；关联 `task_bundle.id` |
| `group_key` | `VARCHAR(64)` | 否 | 包内稳定分组键 |
| `name` | `VARCHAR(200)` | 否 | 任务组名称 |
| `dimension_code` | `VARCHAR(16)` | 否 | 七维代码 |
| `actor_type` | `VARCHAR(16)` | 否 | PATIENT / CAREGIVER / PROFESSIONAL / SHARED |
| `requirement` | `VARCHAR(16)` | 否 | REQUIRED / OPTIONAL |
| `priority` | `VARCHAR(4)` | 否 | L0 / L1 / L2 / L3 / L4 |
| `pool_policy` | `VARCHAR(16)` | 否 | FIXED / ROTATING |
| `schedule_rule_json` | `JSON` | 否 | 周几、时点或事件锚点、间隔、执行窗口和轮换规则 |
| `feedback_schema_json` | `JSON` | 否 | 反馈方式、字段、单位、必填规则及结构版本 |
| `confirmation_ids_json` | `JSON` | 否 | 已核实的条件记录 ID 数组；须校验所属范围与有效修订 |
| `sort_order` | `INT UNSIGNED` | 否 | 同类展示顺序 |

索引：UK(org_id,task_bundle_id,group_key)；IDX(org_id,task_bundle_id,dimension_code)。

约束：一次服药事件与不同监测结果独立成组；FIXED 必须仅 1 个启用变体。用药/监测不做轮换；患者不同药物不为去重合并结果。

## 18 task_group_variant 任务内容与轮换候选

一组可有多个审核候选，实际排程选择其中一个；保留具体内容与模板版本。

首次批次：C。内容受父版本或追加修订规则约束。

| 字段 | MySQL 类型 | 可空 | 含义与关联 |
|---|---|---|---|
| `id` | `CHAR(36)` | 否 | 应用生成 UUID 主键 |
| `org_id` | `VARCHAR(64)` | 否 | 机构范围；来自可信身份上下文 |
| `created_at` | `DATETIME(6)` | 否 | 创建时刻，UTC |
| `created_by` | `VARCHAR(64)` | 否 | 操作者或系统主体 ID |
| `task_group_id` | `CHAR(36)` | 否 | 所属任务组；关联 `task_group.id` |
| `item_key` | `VARCHAR(64)` | 否 | 组内稳定候选键 |
| `template_version_id` | `CHAR(36)` | 否 | 固定模板版本记录；关联 `task_template.id` |
| `title` | `VARCHAR(200)` | 否 | 任务名称 |
| `content` | `TEXT` | 否 | 面向患者的具体执行说明 |
| `parameters_json` | `JSON` | 否 | 数量、单位、食材/做法、动作/时长、处方等结构化参数 |
| `stop_conditions_json` | `JSON` | 否 | 停止/转人工条件及规则引用 |
| `is_enabled` | `BOOLEAN` | 否 | 是否在当前候选池启用 |
| `sort_order` | `INT UNSIGNED` | 否 | 候选顺序 |

索引：UK(org_id,task_group_id,item_key)。

约束：专业参数与模板及有效医嘱逐字段校验；原文、哈希和来源引用保存在参数中。轮换只能在该组已审池内，不能发布时重新生成新菜谱或新动作。

## 19 task_group_source 任务组来源关系

合并共享任务时仍保留所有方案条目及事实/模块依据。

首次批次：C。内容受父版本或追加修订规则约束。

| 字段 | MySQL 类型 | 可空 | 含义与关联 |
|---|---|---|---|
| `id` | `CHAR(36)` | 否 | 应用生成 UUID 主键 |
| `org_id` | `VARCHAR(64)` | 否 | 机构范围；来自可信身份上下文 |
| `created_at` | `DATETIME(6)` | 否 | 创建时刻，UTC |
| `created_by` | `VARCHAR(64)` | 否 | 操作者或系统主体 ID |
| `task_group_id` | `CHAR(36)` | 否 | 任务组；关联 `task_group.id` |
| `plan_item_basis_id` | `CHAR(36)` | 否 | 条目—模块—事实依据；关联 `plan_item_basis.id` |
| `source_note` | `VARCHAR(1000)` | 是 | 合并或来源说明 |

索引：UK(org_id,task_group_id,plan_item_basis_id)。

约束：每个最终任务组必须有依据，且属于 task_bundle 对应方案版本。若某变体仅适用部分依据，parameters_json 标明依据子集并校验。

## 20 schedule_preview 不可变排程预览

冻结二审和发布所见的同一周期时间表；不作为患者正式任务直接查询。

首次批次：C。内容受父版本或追加修订规则约束。

| 字段 | MySQL 类型 | 可空 | 含义与关联 |
|---|---|---|---|
| `id` | `CHAR(36)` | 否 | 应用生成 UUID 主键 |
| `org_id` | `VARCHAR(64)` | 否 | 机构范围；来自可信身份上下文 |
| `created_at` | `DATETIME(6)` | 否 | 创建时刻，UTC |
| `created_by` | `VARCHAR(64)` | 否 | 操作者或系统主体 ID |
| `task_bundle_id` | `CHAR(36)` | 否 | 任务包；关联 `task_bundle.id` |
| `bundle_revision` | `BIGINT UNSIGNED` | 否 | 生成预览时 task_bundle.content_revision |
| `bundle_content_hash` | `CHAR(64)` | 否 | 该修订任务内容摘要 |
| `rule_set_version_id` | `CHAR(36)` | 否 | 排程规则版本；关联 `rule_set_version.id` |
| `algorithm_version` | `VARCHAR(64)` | 否 | 排程算法版本 |
| `preview_hash` | `CHAR(64)` | 否 | 规范化预览结果摘要 |
| `occurrences_json` | `JSON` | 否 | 日期、时点、窗口、group/variant 引用、occurrence_key、具体内容快照 |
| `issues_json` | `JSON` | 否 | 结构化 BLOCKER / WARNING / INFO 问题清单 |
| `occurrence_count` | `INT UNSIGNED` | 否 | 总次数缓存，必须等于结果数组长度 |

索引：UK(org_id,task_bundle_id,bundle_revision,preview_hash)。

约束：一次新的内容修订重新预览；同修订多次生成预览由提交记录精确指定。约束冲突返回 BLOCKER，不悄悄删安全任务。发布不得重新随机排程。

## 21 review_submission 审核提交及目标快照

一审和二审复用提交存储，业务校验分阶段。一个提交固定一次送审内容。

V4 已实现 PLAN 一审；TASK、task_bundle_id 和 schedule_preview_id 留作未来阶段，目前由 CHECK 限制为 PLAN 及空的未来关联列。

首次批次：B。可变行，带乐观锁。

| 字段 | MySQL 类型 | 可空 | 含义与关联 |
|---|---|---|---|
| `id` | `CHAR(36)` | 否 | 应用生成 UUID 主键 |
| `org_id` | `VARCHAR(64)` | 否 | 机构范围；来自可信身份上下文 |
| `created_at` | `DATETIME(6)` | 否 | 创建时刻，UTC |
| `created_by` | `VARCHAR(64)` | 否 | 操作者或系统主体 ID |
| `updated_at` | `DATETIME(6)` | 否 | 最近修改时刻，UTC |
| `row_version` | `BIGINT UNSIGNED` | 否 | 乐观锁修订号，初值 0，成功修改加 1 |
| `stage` | `VARCHAR(16)` | 否 | PLAN / TASK |
| `plan_version_id` | `CHAR(36)` | 是 | 一审目标；关联 `care_plan_version.id` |
| `task_bundle_id` | `CHAR(36)` | 是 | 二审目标；关联 `task_bundle.id` |
| `target_revision` | `BIGINT UNSIGNED` | 否 | 送审目标 content_revision，不是生命周期 row_version |
| `content_hash` | `CHAR(64)` | 否 | 送审聚合摘要；包含方案正文摘要和有效确认清单 |
| `content_snapshot_json` | `JSON` | 否 | 可独立还原送审正文、模块、权重、来源和确认结果 |
| `schedule_preview_id` | `CHAR(36)` | 是 | 二审固定排程预览；一审为空；关联 `schedule_preview.id` |
| `status` | `VARCHAR(16)` | 否 | PENDING / APPROVED / RETURNED / WITHDRAWN |
| `submitted_by` | `VARCHAR(64)` | 否 | 提交者 |
| `submitted_at` | `DATETIME(6)` | 否 | 提交时刻，UTC |
| `closed_at` | `DATETIME(6)` | 是 | 结案/撤回时刻，UTC |
| `closed_by` | `VARCHAR(64)` | 是 | 结案或撤回主体，来自身份上下文 |
| `closed_reason` | `VARCHAR(1000)` | 是 | 撤回原因；审核意见保存在决策表 |
| `pending_slot` | `TINYINT GENERATED` | 是 | 数据库派生列，PENDING 时 1，其他状态 NULL；配合唯一键避免重复待审 |

索引：IDX(org_id,status,stage,submitted_at)；IDX(org_id,plan_version_id,status)；IDX(org_id,task_bundle_id,status)。

约束：恰好一个目标且与 stage 一致；TASK 必须有 schedule_preview_id。每目标仅一个待审提交，通过目标行锁、事务和 UK(org_id,plan_version_id,pending_slot) 保证；快照及目标字段创建后不可变，只有状态受控更新。

## 22 review_decision 审核决策历史

保存通过或退回，以及对当前版本是否仍适用的判断依据。

首次批次：B。内容受父版本或追加修订规则约束。

| 字段 | MySQL 类型 | 可空 | 含义与关联 |
|---|---|---|---|
| `id` | `CHAR(36)` | 否 | 应用生成 UUID 主键 |
| `org_id` | `VARCHAR(64)` | 否 | 机构范围；来自可信身份上下文 |
| `created_at` | `DATETIME(6)` | 否 | 创建时刻，UTC |
| `created_by` | `VARCHAR(64)` | 否 | 操作者或系统主体 ID |
| `submission_id` | `CHAR(36)` | 否 | 审核提交；关联 `review_submission.id` |
| `decision` | `VARCHAR(16)` | 否 | APPROVE / RETURN |
| `comment` | `TEXT` | 是 | 审核意见；退回必须有说明 |
| `reviewer_id` | `VARCHAR(64)` | 否 | 审核人 |
| `reviewed_at` | `DATETIME(6)` | 否 | 审核时刻，UTC |

索引：UK(org_id,submission_id)；IDX(org_id,reviewer_id,reviewed_at)。

约束：一次提交只有一个最终决策。撤回用提交状态，不伪造决策。有效性通过原目标状态、修订、哈希及方案来源计算；旧决策永不删除，不能仅靠最新“通过”标志判断。

## 23 publication 发布记录与幂等请求

绑定二审结果、精确预览和生效时间，提供发布作业状态。

首次批次：D。可变行，带乐观锁。

| 字段 | MySQL 类型 | 可空 | 含义与关联 |
|---|---|---|---|
| `id` | `CHAR(36)` | 否 | 应用生成 UUID 主键 |
| `org_id` | `VARCHAR(64)` | 否 | 机构范围；来自可信身份上下文 |
| `created_at` | `DATETIME(6)` | 否 | 创建时刻，UTC |
| `created_by` | `VARCHAR(64)` | 否 | 操作者或系统主体 ID |
| `updated_at` | `DATETIME(6)` | 否 | 最近修改时刻，UTC |
| `row_version` | `BIGINT UNSIGNED` | 否 | 乐观锁修订号，初值 0，成功修改加 1 |
| `task_bundle_id` | `CHAR(36)` | 否 | 发布任务包；关联 `task_bundle.id` |
| `plan_review_decision_id` | `CHAR(36)` | 否 | 一审通过记录；关联 `review_decision.id` |
| `task_review_decision_id` | `CHAR(36)` | 否 | 二审通过记录；关联 `review_decision.id` |
| `schedule_preview_id` | `CHAR(36)` | 否 | 二审已固定的预览；关联 `schedule_preview.id` |
| `idempotency_key` | `VARCHAR(128)` | 否 | 调用方发布幂等键，与 X-Request-Id 分开 |
| `request_hash` | `CHAR(64)` | 否 | 任务包、两审、预览、生效时刻、替代范围的摘要 |
| `status` | `VARCHAR(24)` | 否 | QUEUED / PROCESSING / PUBLISHED / FAILED |
| `effective_at` | `DATETIME(6)` | 否 | 确认生效时刻，UTC |
| `replaces_publication_id` | `CHAR(36)` | 是 | 被替代的发布版本；关联 `publication.id` |
| `replacement_scope_json` | `JSON` | 是 | 明确的未来任务替代范围 |
| `published_by` | `VARCHAR(64)` | 否 | 发起发布者 |
| `published_at` | `DATETIME(6)` | 是 | 提交成功时刻，UTC |
| `task_count` | `INT UNSIGNED` | 是 | 落地的正式任务数 |
| `error_code` | `VARCHAR(64)` | 是 | 失败原因码 |

索引：UK(org_id,idempotency_key)；UK(org_id,task_bundle_id)；IDX(org_id,status,created_at)。

约束：同幂等键不同载荷返回 409；不同键重复发布同一包返回已存在记录。失败重试保留该记录，另记审计。失败/处理中任务不可见；发布范围与 preview 完全一致，不在发布时重新调用 AI。

## 24 execution_task 正式单次执行任务

发布后患者可查询的每日任务实例；反馈和评分另期扩展，不混入此表。

首次批次：D。可变行，带乐观锁。

| 字段 | MySQL 类型 | 可空 | 含义与关联 |
|---|---|---|---|
| `id` | `CHAR(36)` | 否 | 应用生成 UUID 主键 |
| `org_id` | `VARCHAR(64)` | 否 | 机构范围；来自可信身份上下文 |
| `created_at` | `DATETIME(6)` | 否 | 创建时刻，UTC |
| `created_by` | `VARCHAR(64)` | 否 | 操作者或系统主体 ID |
| `updated_at` | `DATETIME(6)` | 否 | 最近修改时刻，UTC |
| `row_version` | `BIGINT UNSIGNED` | 否 | 乐观锁修订号，初值 0，成功修改加 1 |
| `publication_id` | `CHAR(36)` | 否 | 来源发布；关联 `publication.id` |
| `patient_id` | `CHAR(36)` | 否 | 执行患者，冗余用于列表与范围查询；关联 `patient.id` |
| `task_bundle_id` | `CHAR(36)` | 否 | 来源任务包；关联 `task_bundle.id` |
| `task_group_id` | `CHAR(36)` | 否 | 来源任务组；关联 `task_group.id` |
| `variant_id` | `CHAR(36)` | 否 | 本次选定内容；关联 `task_group_variant.id` |
| `occurrence_key` | `VARCHAR(128)` | 否 | 包内单次执行唯一键，含日期/场景/槽位；不只用日期 |
| `execution_date` | `DATE` | 否 | 业务当地日期 |
| `zone_id` | `VARCHAR(64)` | 否 | 业务时区 |
| `scheduled_at` | `DATETIME(6)` | 是 | 固定执行时刻；等待真实事件锚点时为空 |
| `opens_at` | `DATETIME(6)` | 是 | 执行窗口开始，UTC |
| `closes_at` | `DATETIME(6)` | 是 | 执行窗口结束，UTC |
| `anchor_status` | `VARCHAR(16)` | 否 | FIXED / PENDING / RESOLVED |
| `anchor_event_key` | `VARCHAR(128)` | 是 | 依赖的真实事件键，如当天早餐发生时间 |
| `state` | `VARCHAR(24)` | 否 | SCHEDULED / WAITING_ANCHOR / CANCELLED / SUPERSEDED |
| `content_snapshot_json` | `JSON` | 否 | 本次具体动作、模板版本、参数、反馈要求、停止条件和全部依据 |
| `superseded_by_publication_id` | `CHAR(36)` | 是 | 替代本任务的新发布；关联 `publication.id` |

索引：UK(org_id,task_bundle_id,occurrence_key)；IDX(org_id,patient_id,execution_date,state)；IDX(org_id,publication_id)。

约束：固定时点满足 opens_at ≤ closes_at；事件未发生不能伪造“实际餐后”时间。展示计划时间须标预计；锚点解析属于执行期调度，真实开放前补齐事件接入。内容快照冻结，状态与合法锚点解析可更新并审计；发布可见性以 publication 状态、生效边界及模式隔离共同决定。

## 25 outbox_event 发布通知事件

事务内记录待通知，事务后投递；通知失败不撤销已发布任务。

首次批次：D。可变行，带乐观锁。

| 字段 | MySQL 类型 | 可空 | 含义与关联 |
|---|---|---|---|
| `id` | `CHAR(36)` | 否 | 应用生成 UUID 主键 |
| `org_id` | `VARCHAR(64)` | 否 | 机构范围；来自可信身份上下文 |
| `created_at` | `DATETIME(6)` | 否 | 创建时刻，UTC |
| `created_by` | `VARCHAR(64)` | 否 | 操作者或系统主体 ID |
| `updated_at` | `DATETIME(6)` | 否 | 最近修改时刻，UTC |
| `row_version` | `BIGINT UNSIGNED` | 否 | 乐观锁修订号，初值 0，成功修改加 1 |
| `publication_id` | `CHAR(36)` | 否 | 发布来源；关联 `publication.id` |
| `event_type` | `VARCHAR(64)` | 否 | 例如 CARE_PLAN_PUBLISHED |
| `payload_json` | `JSON` | 否 | 接收主体和最小业务标识，不携带完整病历 |
| `status` | `VARCHAR(16)` | 否 | PENDING / SENDING / SENT / FAILED |
| `attempt_count` | `INT UNSIGNED` | 否 | 已尝试次数 |
| `next_attempt_at` | `DATETIME(6)` | 是 | 重试时刻，UTC |
| `lease_owner` | `VARCHAR(64)` | 是 | 投递认领者 |
| `lease_until` | `DATETIME(6)` | 是 | 认领到期，UTC |
| `last_error` | `VARCHAR(1000)` | 是 | 去敏失败原因 |
| `sent_at` | `DATETIME(6)` | 是 | 成功时刻，UTC |

索引：UK(org_id,publication_id,event_type)；IDX(org_id,status,next_attempt_at)。

约束：event id 用于下游去重；至少一次投递，不声称跨系统恰好一次。租约更新使用行锁/CAS，不能多个发送者同时抢同一行。

## 26 audit_log 关键操作审计

记录编辑、确认、审核、发布、替代和失败处理，支持定位操作人及修订。

首次批次：A。内容受父版本或追加修订规则约束。

| 字段 | MySQL 类型 | 可空 | 含义与关联 |
|---|---|---|---|
| `id` | `CHAR(36)` | 否 | 应用生成 UUID 主键 |
| `org_id` | `VARCHAR(64)` | 否 | 机构范围；来自可信身份上下文 |
| `created_at` | `DATETIME(6)` | 否 | 创建时刻，UTC |
| `created_by` | `VARCHAR(64)` | 否 | 操作者或系统主体 ID |
| `actor_id` | `VARCHAR(64)` | 否 | 可信操作者/系统主体 |
| `action` | `VARCHAR(64)` | 否 | 操作编码 |
| `target_type` | `VARCHAR(32)` | 否 | 目标类型 |
| `target_id` | `CHAR(36)` | 否 | 目标 ID；按类型由服务校验 |
| `before_revision` | `BIGINT UNSIGNED` | 是 | 操作前修订 |
| `after_revision` | `BIGINT UNSIGNED` | 是 | 操作后修订 |
| `request_id` | `VARCHAR(64)` | 否 | 请求追踪号 |
| `change_summary_json` | `JSON` | 否 | 字段级变更摘要和必要原因；避免全量重复病历 |
| `occurred_at` | `DATETIME(6)` | 否 | 操作时刻，UTC |

索引：IDX(org_id,target_type,target_id,occurred_at)；IDX(org_id,request_id)。

约束：业务变更审计与业务写入同一事务；事务外的请求失败另记去敏运行日志。target_id 为多态逻辑关联，无法声明到单一业务表的 FK。
