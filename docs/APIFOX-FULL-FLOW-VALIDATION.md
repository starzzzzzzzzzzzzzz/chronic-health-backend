# Apifox 全接口与业务流程验证

验证时间：2026-10-17T22:44:17+08:00。

## 结果

当前 OpenAPI 的全部 **33 个 method + path** 均已覆盖。最终单轮 **55 次请求、103 项断言、0 失败**，保留响应结构校验。使用真实运行的本地 DEMO 后端和 MySQL，并非 Mock。

Apifox 项目：8938711（慢病健康管理后端）；场景：8862633（慢病 DEMO：33接口完整流程回归）。在「自动化测试」中可找到此场景。

## 流程和边界

患者资料与旧档案兼容接口 → 创建方案、冻结资料快照、编辑方案 → 执行条件确认 → 一审撤回、退回、批准 → 从已审方案派生任务 → 排程、二审批准 → 发布预览、发布 → 患者今日任务、打卡 → 追加更正并保留历史 → 新任务与方案草稿版本不影响已发布任务。

同时验证未认证 401、错误分页 400、编辑者自审及打卡 403、陈旧修订与重复主要打卡 409、发布和打卡的幂等重试。

## 本次发现并修正

- OpenAPI 中 JSON 对象类型缺失，导致 Apifox 将合法对象理解为 null。
- 可空字段及可空引用未正确描述；用显式 null 联合分支处理 OpenAPI 3.0 的引用限制。
- Map<String,Object> 错误限制所有值必须是对象，已允许异构值；保留其他具体类型约束。
- 测试脚本调整了二审资料的阻断项路径；请求体改为运行时显式构造，避免 Apifox 自动填入空日期。

文档数据库回归测试 ApiDocumentationDatabaseTest：7 项通过。此次未重新运行全部后端测试套件。

## 范围限制

本轮以七维结构中的饮食、运动、作息、监测、其他五维形成执行任务；用药、中医因缺少专业来源而关闭。因此是 33 个接口及完整主流程覆盖，不代表所有七维规则、所有异常组合均已穷尽。

使用确定性规则及虚构数据，不包含真实 AI 方案质量、真实通知、临床有效性、压力测试或跨日补记截止边界验证。反馈更正已验证，跨日补记仍需专项测试。

每轮使用独立的 Apifox全流程 测试方案，重跑及排错产生的 DEMO 记录保留在本地数据库供追溯。没有自动删除这些记录。

## 报告与复跑

- 本机 HTML：`.dev/apifox/full-flow/reports/full-flow.html`
- 本机 JSON：`.dev/apifox/full-flow/reports/full-flow.json`
- 本机运行脚本：`.dev/apifox/full-flow/run.py`（读取 .env.demo，运行时传入本地角色凭证）。
- 场景已保存到 Apifox；凭证不写入共享场景，报告仅保留本地并脱敏。
- 复跑需本地 DEMO 服务运行在 `http://127.0.0.1:9089/ncd-ai`，并提供 editorToken、reviewerToken、patientToken 本地环境值；每轮会创建新的虚构业务记录。

## 接口覆盖

| 方法 | 路径 | 场景步骤 | 验证状态码 |
| --- | --- | --- | --- |
| GET | `/check-ins/{id}` | 48 | 200 |
| GET | `/execution-tasks/{id}` | 42, 51 | 200 |
| GET | `/execution-tasks/{id}/check-ins` | 47, 50 | 200 |
| GET | `/health` | 1 | 200 |
| GET | `/patients` | 2, 3 | 200, 401 |
| GET | `/patients/{id}/execution-tasks` | 41, 55 | 200 |
| GET | `/patients/{id}/health-summary` | 6 | 200 |
| GET | `/plan-versions/{id}` | 8, 14, 18, 22, 25 | 200 |
| GET | `/plan-versions/{id}/condition-confirmations` | 11 | 200 |
| GET | `/plan-versions/{id}/review-context` | 13 | 200 |
| GET | `/publications/{id}` | 40 | 200 |
| GET | `/review-submissions/{id}` | 16 | 200 |
| GET | `/schedule-previews/{id}` | 30 | 200 |
| GET | `/store/care/grant-administration/archives` | 4, 5 | 200, 400 |
| GET | `/task-bundles/{id}` | 27, 32, 35, 52 | 200 |
| GET | `/task-bundles/{id}/publication-previews/{previewId}` | 37 | 200 |
| GET | `/task-bundles/{id}/review-context` | 31 | 200 |
| PATCH | `/plan-versions/{id}` | 9, 10 | 200, 409 |
| PATCH | `/task-bundles/{id}` | 28 | 200 |
| POST | `/care-plans` | 7 | 201 |
| POST | `/care-plans/{id}/versions` | 54 | 201 |
| POST | `/check-ins/{id}/amendments` | 49 | 201 |
| POST | `/execution-tasks/{id}/check-ins` | 43, 44, 45, 46 | 200, 201, 403, 409 |
| POST | `/plan-versions/{id}/condition-confirmations` | 12 | 201 |
| POST | `/plan-versions/{id}/review-submissions` | 15, 19, 23 | 201 |
| POST | `/plan-versions/{id}/task-bundles` | 26 | 201 |
| POST | `/review-submissions/{id}/decisions` | 20, 21, 24, 34 | 201, 403 |
| POST | `/review-submissions/{id}/withdrawals` | 17 | 201 |
| POST | `/task-bundles/{id}/publication-previews` | 36 | 201 |
| POST | `/task-bundles/{id}/publications` | 38, 39 | 200, 201 |
| POST | `/task-bundles/{id}/review-submissions` | 33 | 201 |
| POST | `/task-bundles/{id}/schedule-previews` | 29 | 201 |
| POST | `/task-bundles/{id}/versions` | 53 | 201 |
