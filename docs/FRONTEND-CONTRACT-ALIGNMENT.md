# 前后端联调契约差异初核

日期：2026-10-17。状态：已核对接口定义，尚未验证旧服务器业务接口。用户随后已确认由后端适配前端既有契约；具体方向与首批范围见[适配设计](superpowers/specs/2026-10-17-legacy-api-compatibility-design.md)。第一批旧档案列表兼容接口已实现；后续方案与任务兼容入口尚未实现。

## 结论与来源

前端伙伴表示当前版本按旧服务器 OpenAPI 预留请求。后端以此作为兼容入口的路径、参数和响应基准，复用现有 RESTful 资源与审核版本模型。当前只有档案列表已完成适配，整个核心流程仍不能仅替换服务器地址后直接接入。

- 旧服务定义：[NCD AI Service API](http://116.178.155.215:18006/ncd-ai/v3/api-docs)，本次获取为 OpenAPI 3.1.0。
- 当前服务定义：[本机 OpenAPI](http://127.0.0.1:9089/ncd-ai/v3/api-docs/health)，本次获取为 OpenAPI 3.0.1。当前代码是接口行为依据。
- 详细契约：[患者与草稿](PHASE-A-API.md)、[执行条件与一审](PHASE-B-API.md)、[任务到基础打卡](PHASE-CDE-API.md)。

两者均使用 `/ncd-ai` 前缀、`Authorization` 请求头以及 `code/message/data` 响应包装。以下差异仍须逐项处理；OpenAPI 版本相同与否不能代表业务兼容。

## 核心差异

下表路由均省略共同前缀 `/ncd-ai`。相邻业务能力不代表可以一对一替换。

| 业务 | 旧文档 | 当前后端 | 联调要点 |
|---|---|---|---|
| 可访问档案/患者列表 | `GET /store/care/grant-administration/archives`；查询 `keyword/page/pageSize`；分页返回 `pageSize` | `GET /patients`；查询 `q/page/size`；分页返回 `size` | 已新增同路径兼容入口，使用 keyword/page/pageSize，默认 pageSize=10 |
| 列表条目 | `archiveId/name/diseaseTags/age` | `id/name/sex/birthDate/dataMode/profileRevision` | 兼容入口返回持久数字字符串 archiveId；无结构化来源时 diseaseTags 为空，未知/未来出生日期省略 age；原 /patients 字段保留 |
| 患者输入与摘要 | `GET /store/care/archives/{archiveId}/input`，定义返回动态对象 | `GET /patients/{id}/health-summary`，返回 `patient/profileRevision/axes/facts/conflicts/missingFields` | 核对六方面资料的展示与来源结构；动态对象不能只按同名键推定兼容 |
| 旧工作流创建 / 当前手工方案草稿 | `POST /store/care/coordination/workflows`；正文含 `archiveId/message/preferences/requestId` | `POST /care-plans`；正文含 `patientId/expectedProfileRevision/title/startDate/endDate/zoneId` 等；随后 `PATCH /plan-versions/{id}` 编辑正文 | 两者流程和输入不同。当前尚未接入真实 AI 方案生成，手工草稿不能替代旧工作流的全部能力 |
| 方案与任务审核 | `POST /store/care/workflows/{id}/approve-plan`、`approve-tasks`；正文含 `expectedRowVersion` 与候选引用 | 分别向 `/plan-versions/{id}/review-submissions`、`/task-bundles/{id}/review-submissions` 送审，再向 `/review-submissions/{id}/decisions` 提交决策 | 旧工作流 ID、方案版本 ID、任务包 ID、审核提交 ID 语义不同；不能仅改路由或字段名。当前决策需审核提交的修订号，并限制自审 |
| 任务派生 | `POST /store/care/drafts/tasks/generate`；正文含 `archiveId/shopId/teamId/confirmed/recommendations` | `POST /plan-versions/{id}/task-bundles`，提交来源方案 `expectedRevision`，由后端读取一审通过内容并确定性派生 | 当前接口不接受客户端任意建议正文替代已审方案；机构、身份与患者范围由服务端校验 |
| 创建响应 | 上述旧创建、任务生成和批准操作定义为 200 | 当前草稿、送审、决策、任务包首次创建等为 201 | 兼容入口按旧契约返回 200；当前内部创建入口仍返回 201。后续兼容适配不能仅改状态码而绕过审核或幂等 |

## 建议的首批对齐范围

以现有旧 OpenAPI 和业务文档启动后端适配。前端实际 API 请求目录、相关类型定义或调用清单可在后续联调时补充，用于核对动态对象细节，不作为开始适配的前提，也不要求覆盖旧文档全部模块。

首批联调建议限定为：

1. 已完成患者列表与选择：旧请求 `keyword/page/pageSize` 映射到当前患者查询，返回持久 archiveId。
2. 读取六方面摘要：保存接口返回的 `profileRevision`，明确缺失资料的呈现。
3. 创建、编辑和重读方案草稿：使用实际 `planId/versionId/rowVersion`，禁止固定修订号。
4. 完成上述链路后，再对齐执行条件、一审、任务包、二审、发布、日任务和打卡。

按用户确认的方向，前端既有请求契约优先保留，由后端增加兼容入口，复用当前业务并处理标识、权限和流程映射。旧工作流不能直接当作当前方案版本；尚未实现的 AI 能力仍须如实区分，不以虚假成功满足接口格式。

本机 `127.0.0.1:9089` 只供本机访问，不能直接作为前端伙伴电脑的共享服务器地址。跨机联调需要实际部署地址或经确认的局域网访问配置，以及各自的开发凭证；不要共享 `.env.demo`。


## 已完成：旧档案列表

```http
GET /ncd-ai/store/care/grant-administration/archives?keyword=&page=1&pageSize=10
Authorization: <本机 DEMO 原始 Token>
```

响应 data 为 `items/total/page/pageSize`。条目为 `archiveId/name/diseaseTags`，有有效出生日期时增加 `age`。编号是本机映射别名，不代表旧服务器的真实档案编号；前端保持字符串，不能转成 Number。当前尚未实现旧 `/archives/{archiveId}/input` 及旧工作流入口。

V9 回填已有患者编号；DEMO 初始化在写事务中初始化编号。新增正式患者入口以后必须在同一事务调用 `LegacyArchiveMappingService.ensureForPatient`。GET 不分配编号；可见患者缺编号返回 409，排查迁移或患者写入入口，不能在查询时临时补号。MySQL 不允许自增列引用 CHECK，signed BIGINT 自增配合服务层非正数拒绝；人工 SQL 写入无效编号会被接口拒绝。

首批验证：`./scripts/test-db.sh -o` 125 项通过，零失败/错误/跳过，包括实际响应与冻结旧 schema 对照、19 位编号、分页、授权、年龄边界及 GET 无副作用。尚无前端页面联调通过证据，不能据此宣称全系统兼容。
