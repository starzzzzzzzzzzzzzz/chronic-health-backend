# 旧版资料与 AI 建议接口适配

日期：2026-10-18。本批覆盖视频开头“选患者 → 读取资料 → 生成建议”，不代表旧前端完整工作流已兼容。

## 对齐依据与边界

重新获取伙伴指定的 [OpenAPI](http://116.178.155.215:18006/ncd-ai/v3/api-docs)。当前定义有 202 个路径；本批涉及的两个操作相对 10 月 17 日快照未变化。冻结路径和关联 schema 保存在 `src/test/resources/contracts/legacy-recommendations.openapi.json`，包含来源、日期和完整源文件 SHA-256。

| 旧入口 | 当前状态 | 限制 |
|---|---|---|
| `GET /store/care/grant-administration/archives` | 第一批已实现 | 本机稳定数字字符串档案别名 |
| `GET /store/care/archives/{archiveId}/input` | 本批增加 | 旧规范只声明动态对象，页面具体取值仍待联调 |
| `POST /store/care/drafts/recommendations/generate` | 本批增加 | 真实 AI 候选；已知请求字段和响应类型适配，状态/分类字符串待前端核对 |
| `POST /store/care/drafts/basis/evaluate` | 后续批次已实现 | 已确认诊断、待确认侧重选项和 sourceHash；不自动医学判型 |
| `POST /store/care/coordination/workflows` 等工作流入口 | 未适配 | 不把方案版本 ID 伪装成工作流 ID |
| 条件确认、动作拆解、双审、发布、今日任务/反馈旧入口 | 未适配 | 新资源接口已有业务能力，旧入口仍需逐段接入 |

请求均使用 `/ncd-ai` 前缀、原始 `Authorization` 和 `code/message/data` 包装，成功 HTTP 200。当前共 41 个操作，不直接导入旧文档中尚未实现的路由。

## 患者输入

档案编号经过精确十进制校验和本机映射，再复用 `PatientService` 验证机构、数据模式、有效授权与患者状态。19 位编号全程不经过浮点数。GET 不创建映射、快照或其他业务数据。

`data` 返回 `scope/patient/profileRevision/axes/facts/conflicts/missingFields`：六方面为 H/F/B/C/E/G，事实使用 `factId/axisCode/factCode/value/unit/sourceType/sourceRef/status/observedAt/confidence/confirmedAt/confirmedBy`。未知不转为正常；缺失出生日期不推算年龄；替换后的旧事实不作为当前事实返回。DEMO 来源保留真实标记，不改写成专业确认。

这些动态键是本机明确提供的投影；旧规范未规定该动态对象的内部键，因此仍需要伙伴实际页面确认。`scope.shopId/teamId` 表示当前授权范围，非旧服务器账号的组织编号。

## 建议生成

```http
POST /ncd-ai/store/care/drafts/recommendations/generate
Authorization: <本机编辑者 Token>
X-Request-Id: legacy-plan-unique-request-001
Content-Type: application/json

{
  "archiveId": "从档案列表取得的精确字符串",
  "prompt": "本期先以记录和回顾作息为主",
  "supplementalRequirements": "建议简单、便于本人完成"
}
```

旧请求 `archiveId` 定义为 int64。本机同时接受整数 JSON 和精确十进制字符串，建议前端保留列表返回的字符串；拒绝小数、指数格式、非正数和超出 signed BIGINT 的编号。

`disease/prompt/focuses/supplementalRequirements` 作为未核实的生成偏好传入现有 AI 流程，不增加或确认患者事实。累计大小受限。`shopId/teamId` 若提交，必须与已授权的本地范围相符，不能用于切换身份；本机 DEMO 使用字符串组织编号，旧数字组织号尚未配置映射，当前联调应省略这些可选字段。

**2026-10-18 后续批次已接入非空 `confirmedManagement`**：先调用 `POST /store/care/drafts/basis/evaluate` 取得原始 evaluation 和 sourceHash，再提交人工侧重确认；来源或字段不匹配时拒绝。侧重、七维权重及确定性多病融合详见 [来源绑定与融合](MANAGEMENT-FOCUS-AND-FUSION.md)。

内部在一个事务中创建草稿、输入快照和 AI 作业，并通过 V11 的 `legacy_recommendation_request` 绑定请求编号。旧请求不含日期，本批内部草稿使用上海当天起 7 天的暂定周期；最终工作流周期入口尚未适配，不作为视频周期设置已完成的依据。

提交事务结束后，旧同步接口最多等待 120 秒，由已有后台 worker 调用 DeepSeek；等待期间不持有数据库事务或患者锁。只有结构和来源校验成功才返回建议；不自动采纳、送审或发布。

响应映射：

- `adviceId`：不可变 AI 作业 ID；不是 workflowId 或 planVersionId。
- `recommendations[].id`：作业 ID 与候选条目序号组成的稳定编号。
- `category`：目前为用药/饮食/运动/起居/监测/中医/其他；原规范只定义 string，需实际前端枚举核对。
- `text/originalText`：候选标题及内容。仅返回纳入本期的建议。
- `evidenceCount`：文献引用条数；不把患者事实数当文献数。
- `actions=[]`、`decompositionPending=true`：本批没有通过旧入口拆解任务，不虚构动作、执行频次或确认结果。
- `retrievalMode=MODEL_ONLY`：未接入文献检索知识库，因此不生成假的 `knowledgeReleaseId`。
- `generationStatus=SUCCEEDED`：旧定义未枚举值；表示通过后端结构/来源校验的候选，不表示医生审核完成。
- `warnings/qualityIssues/decisionExplanation`：保留待确认信息、未纳入维度原因和主目标；不声称完整质量评分已实现。

## 重试与失败

同一主体、同一 `X-Request-Id` 和相同请求体复用同一作业、草稿和候选；更换正文却复用编号返回 409。重放仍重新验证患者权限。编号不提供时，由请求过滤器生成并通过响应头返回；前端应主动生成稳定编号，以便网络中断后准确重放。

等待尚未结束时返回 504，后台作业保留；使用原编号和原正文再次请求，继续等待或获取完成结果。失败作业使用原编号不会再次调用 AI；明确新一轮生成需要新的编号。

模型输出拒绝/上游失败返回 502，模型超时返回 504，配置缺失返回 503，未授权/越权为 401/403，非法输入为 400。不能把任何错误解释为“成功但没有建议”。

## 前一批验证记录（管理侧重接入前）

2026-10-18（上海）完成以下验证：

- `./scripts/test-db.sh -o`：160 项通过，零失败、错误、跳过；包含原有业务回归、新入口权限与输入校验、冻结旧响应字段类型校验、19 位编号、同键重放与冲突、模型失败以及真实事务回滚。日志：`.dev/legacy-full-regression.log`。首次定向测试曾遇数据库连接超时和一个未更新的路径数量断言；修正断言后，全量重跑通过。
- 9089 服务已重启加载 V11 与新接口，健康检查 200；9090 模拟页面仍返回 200；实时 OpenAPI 为 34 个路径、39 个操作。
- 虚构患者“AI虚构-晚睡生活习惯”真实 HTTP 验证：旧档案列表 → 患者输入 → 两个并发同键生成请求，均在约 6.99 秒返回 200，返回建议完全一致；数据库只有一条请求映射、一个生成作业和一个关联草稿版本。
- 生成作业 `1cec5684-082d-48d8-884a-fecb9dc2ce59` 成功，使用配置的 `deepseek-flash`。再次同键请求约 0.026 秒回读相同结果；同键更换正文返回 409。
- 原生接口重读该候选成功；关联版本保持 `DRAFT` 且方案条目为空，确认没有自动采纳、送审或发布。无凭证读取资料为 401，非法编号为 400。
- 本次真实候选只返回已选择的起居建议，其他维度保留关闭原因，未虚构文献引用、任务动作或执行频次。原始本机记录：`.dev/qa/legacy-recommendations-live.json`（不含凭证）。

这些结果验证后端实现与已明确的旧规范字段；尚未运行伙伴的前端页面，不代表整个旧前端联调通过。管理侧重来源绑定已在后续批次完成；工作流创建/读取及双审、任务发布旧路由仍待适配。最新证据见 [来源绑定与融合](MANAGEMENT-FOCUS-AND-FUSION.md)。
