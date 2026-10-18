# 病种管理侧重、来源绑定与七维融合

2026-10-18 实现记录。范围为本地 DEMO 后端，围绕视频中的“病种管理侧重 → 方案生成”。已接入前端文档的同步依据评估和 `confirmedManagement`，原生生成作业也使用同一验证规则。

## 业务链路

读取当前已确认诊断与来源 → 返回侧重选项和来源摘要 → 编辑者核对主次亚型、优先级并填写原因 → 服务端校验来源与选择 → 计算七维融合 → 冻结生成输入 → AI 生成建议 → 人工采用为草稿 → 原有执行条件确认与双审发布。

侧重不会创建新诊断，也不直接生成已发布任务。生成、采用、审核、发布仍是独立动作。

## 已实现与保留边界

| 能力 | 本次行为 |
|---|---|
| 诊断来源 | 仅使用当前有效、`CONFIRMED_PROFESSIONAL` 且 `factCode` 以 `diagnosis` 开头的事实；自述或异常读数不能作为诊断 |
| 管理选项 | 提供补充文档 4.3.1—4.3.3 中糖尿病、高血压、失能照护的 20 个主亚型选项及初始权重 |
| 自动医学判型 | 尚未启用；评估返回“待确认”，所有 `typeMatches.status=PENDING`。选项不是已命中的结论 |
| 人工选择 | 主亚型须属于当前病种，次亚型不能重复或等于主亚型；优先级只能为重点/加强/常规管理；确认原因必填 |
| 来源绑定 | `sourceHash` 覆盖机构、患者、资料修订、当前事实及来源、冲突和规则目录摘要；`evaluation` 必须原样回传 |
| 失败保护 | 伪造或过期 hash、跨患者 hash、被改写的 evaluation、诊断冲突、未知亚型和非法权重均拒绝 |
| 不完整确认 | 仍可生成候选，但融合为 `PENDING`、不输出正式融合权重；未确认病种会阻断一审提交，不静默排除后重新分配权重 |
| 模型约束 | 已确认侧重与后端算出的权重直接带入；模型漏写或改写时保留服务端冻结值，记入 `issues.normalizations` |
| 草稿保护 | 采用前再次检查来源摘要。来源变化返回 409；已绑定侧重不能经普通草稿 PATCH 改写，须重新评估并显式确认后生成新候选 |
| 覆盖检查 | 返回各病种的来源引用情况、未确认侧重与完全重复正文提示；多病场景保留 `REVIEW_REQUIRED` |

当前确认人使用已有 `PATIENT_READ + PLAN_EDIT` 授权，记录为 `DEMO_OPERATOR_CONFIRMED`。真实医生身份、医学规则审批和正式患者链路尚未接入；不能把 DEMO 操作者确认表述为真实医生审批。

## 接口与前端调用

所有路径前缀为 `/ncd-ai`，认证为原始 `Authorization` Token。

| 接口 | 用法 |
|---|---|
| `POST /store/care/drafts/basis/evaluate` | 旧契约入口，正文为 `{"archiveId":"精确数字字符串"}`。可选 shopId/teamId 仍须匹配本机授权范围 |
| `GET /patients/{id}/management-basis` | 原生 UUID 患者入口，返回相同依据结构；其中 archiveId 字段值为该 UUID |
| `POST /store/care/drafts/recommendations/generate` | 沿用旧请求与 `X-Request-Id`，新增接受非空 confirmedManagement |
| `POST /plan-versions/{id}/generation-jobs` | 原生生成请求新增可选 confirmedManagement，沿用修订校验与 Idempotency-Key |

依据评估响应保持旧文档的 `Evaluated/Disease/Option/Match` 字段：`archiveId/sourceHash/diseases/medicationFacts/decisionExplanation`。冻结旧契约见 `src/test/resources/contracts/legacy-management.openapi.json`。

生成请求中的单个确认项如下。**evaluation 对象必须来自当前评估响应，不要在页面修改这个原始对象**；编辑结果放在外层选择字段。sourceHash 不是认证凭证，不能替代患者授权。

```javascript
const confirmation = {
  evaluation: evaluated.diseases.find(d => d.name === "糖尿病"),
  sourceHash: evaluated.sourceHash,
  primarySubtype: "β细胞功能衰退型",
  secondarySubtypes: [],
  priority: "常规管理",
  adjustmentReason: "填写人工核对的具体依据或调整原因"
};
// 将 confirmation 放进生成请求的 confirmedManagement 数组。
```

`weights` 可省略，沿用主亚型初始值。如人工调整，必须提供七个整数百分比，总和 100，并在 adjustmentReason 中说明。支持英文七维键，或中文“用药/饮食/运动/起居/监测/中医/其他”；“作息”和“中医康复”分别为起居和中医别名。不接受同维多个别名或小数。初始 `Option.initialWeights` 用上述中文键。

已确诊病种必须有明确名称：读取事实 value 中的 name/diagnosis/disease/value 字符串，也接受字符串 value；精确名称“糖尿病/1型糖尿病/2型糖尿病”“高血压”“失能/失能照护”支持目录匹配。无名称时仅精确识别 `diagnosis.diabetes/hypertension/disability`。不从自由文本做模糊诊断归类；未知病种仍列出，保持待确认。

当前整体 OpenAPI 为 **36 个路径、41 个操作**。前端需重新同步数据源；接口存在不等于已完成伙伴真实页面联调。SSE 评估流、旧 workflow 创建及后续旧审核/任务路由仍未适配。

## 权重与舍入

| 数值 | 单位 |
|---|---|
| 旧确认 weights、依据评估 initialWeights | 整数百分比，七维合计 100 |
| 原生 managementFocuses.dimensionWeights | 整数基点，七维合计 10000；1%=100 基点 |
| contributionBps | 病种占比基点，各病种合计 10000 |
| fusion.dimensionWeights | 融合后基点；按 0.1% 精度，即 10 基点一档，合计 10000 |

按补充文档 4.4：重点/加强/常规管理的内部候选分值分别为 3/2/1。病种占比等于该病分值除以总分值；融合权重按**未舍入的占比**乘各病七维权重后求和，只在最后舍入，不能用页面 33.3% 等显示值再次计算。

本次对“差额加到原始小数最大的维度”采用明确解释：先四舍五入到一位小数，再把与 100% 的差额加到**未舍入权重最大的维度**；并列时按用药、饮食、运动、起居、监测、中医、其他的顺序选择。病种占比同样补齐总和；并列使用稳定的诊断事实顺序。分值本身仍是文档的内部候选值，正式启用需产品与医学负责人确认。

- 双病示例：糖尿病 β 细胞型常规管理 + 高血压晨峰型加强管理，得到 **25.0 / 11.7 / 10.0 / 11.7 / 31.6 / 5.0 / 5.0**，与原文一致。
- 三病示例再加入卒中偏瘫型重点管理：本实现为 **20.0 / 13.3 / 7.5 / 8.3 / 23.4 / 20.0 / 7.5**。原文示例为监测 23.3、中医 20.1，补差位置与上述文字解释不一致。此处已显式记录，待业务/医学负责人确定统一口径，不硬编码示例结果。

权重只表示管理侧重，不能使缺少原医嘱的用药、中医或其他专业操作变为可执行；未安排任务的维度在后续依从性计算中不应扣分。完整依从性评分并未在本批实现。

## 追溯与数据流

无需新增表或迁移，仍是 Flyway V11、33 张表：

- `health_input_snapshot.payload_json.managementContext`：保存来源摘要、规则/目录摘要、原始确认项、确认人/时间、病种来源、侧重与融合结果。
- `plan_generation_job.model_input_json.managementContext`：只向模型提供侧重、病种依据、融合结果和规则版本；不提供操作人身份、患者姓名或完整原始确认对象。
- `plan_generation_job.candidate_json`：候选携带确定的 managementFocuses，goalBasis 中保留 managementContext 和 coordinationReport。
- 人工采用后写入既有 `plan_management_focus`，并关联冻结的输入快照和生成作业。普通内容编辑会重新计算覆盖报告，不能覆盖来源确认值。
- 后续任务从已审版本派生；本批不改写已发布任务或历史评分。

提示词版本 `plan-system-v5`，AI 校验规则 `ai-plan-rules-v4`，目录规则 `management-doc-v1`。规则升级时尚未执行的旧作业仍按已有策略明确失败，不偷偷套用新规则或重复计费。

旧建议响应新增原契约已有的 `coordinationReport`。其中 `SOURCE_LINKED` 仅表示建议 basis 引用了诊断来源；`PENDING_REVIEW` 提醒人工核对是否遗漏，不表示一定需要增加任务。`references` 仍为空，不伪造文献引用。医学冲突、语义重复、可执行性与负担仍需审核。

## 验证证据

- 项目完整回归：`./scripts/test-db.sh -o`，169 项通过，零失败/错误/跳过。覆盖来源篡改、跨患者、事实更新、诊断冲突、错误权重、部分确认、模型改写、草稿保护、采用、原生/兼容入口以及 OpenAPI schema。
- 新增端点与响应字段按冻结旧契约校验。修正了 Jackson 3 JsonNode 在 OpenAPI 中被错误投影为 object 的问题，confirmedManagement 明确展示为带类型的数组。
- 真实 DeepSeek 单病例（虚构双病患者）：作业 `44b14ad4-6fd2-4d28-8d84-daedc006a19e`，约 10.767 秒，返回 3 条建议；两病来源均关联，双病权重符合上述结果，同键重放一致；人工采用后保持 DRAFT。原始记录 `.dev/qa/management-live.json`，无凭证。
- 真实结果没有新增处方或监测阈值，但仍提出一条与已有周期部分重复的补充问题，属于后续生成质量打磨点。一次结构验证不能说明医学正确率或所有多病场景均已验证。

日志 `.dev/management-full-regression.log`。数据库测试只模拟外部模型，其余授权、数据写入、接口、审核校验使用实际应用与本机 MySQL；真实模型调用单独记录，不混为一谈。
