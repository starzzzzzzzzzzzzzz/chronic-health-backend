# 后端接口索引

日期：2026-10-18（管理侧重批次）。来源：本机运行服务的 `/ncd-ai/v3/api-docs/health`。共 36 个路径、41 个操作，均省略 `/ncd-ai` 前缀。原生能力与旧兼容入口并存，不表示全部旧路由已适配。

| 序号 | 分组 | 方法 | 路径 | 功能 |
|---|---|---|---|---|
| 1 | 01 患者资料 | `GET` | `/patients` | 查询授权患者列表 |
| 2 | 01 患者资料 | `GET` | `/patients/{id}/health-summary` | 读取患者六方面健康摘要 |
| 3 | 02 方案草稿 | `GET` | `/plan-versions/{id}` | 读取方案版本 |
| 4 | 02 方案草稿 | `PATCH` | `/plan-versions/{id}` | 编辑方案草稿 |
| 5 | 02 方案草稿 | `POST` | `/care-plans` | 新建方案草稿 |
| 6 | 03 执行条件 | `GET` | `/plan-versions/{id}/condition-confirmations` | 查询执行条件及确认历史 |
| 7 | 03 执行条件 | `POST` | `/plan-versions/{id}/condition-confirmations` | 追加执行条件确认 |
| 8 | 04 方案一审 | `GET` | `/plan-versions/{id}/review-context` | 查询一审资料与阻断项 |
| 9 | 04 方案一审 | `GET` | `/review-submissions/{id}` | 读取本次送审快照与结果 |
| 10 | 04 方案一审 | `POST` | `/plan-versions/{id}/review-submissions` | 提交方案一审 |
| 11 | 04 方案一审 | `POST` | `/review-submissions/{id}/decisions` | 审核通过或退回 |
| 12 | 04 方案一审 | `POST` | `/review-submissions/{id}/withdrawals` | 提交人撤回待审方案 |
| 13 | 05 方案版本 | `POST` | `/care-plans/{id}/versions` | 从已批准方案建立新草稿版本 |
| 14 | 06 运行状态 | `GET` | `/health` | 检查服务存活 |
| 15 | 07 七维任务包 | `GET` | `/task-bundles/{id}` | 读取任务包与来源 |
| 16 | 07 七维任务包 | `PATCH` | `/task-bundles/{id}` | 编辑任务执行编排 |
| 17 | 07 七维任务包 | `POST` | `/plan-versions/{id}/task-bundles` | 从已审方案派生七维任务草稿 |
| 18 | 08 排程与任务二审 | `GET` | `/schedule-previews/{id}` | 读取冻结排程与阻断项 |
| 19 | 08 排程与任务二审 | `GET` | `/task-bundles/{id}/review-context` | 查询任务二审资料与历史 |
| 20 | 08 排程与任务二审 | `POST` | `/task-bundles/{id}/review-submissions` | 提交任务二审 |
| 21 | 08 排程与任务二审 | `POST` | `/task-bundles/{id}/schedule-previews` | 生成确定周期排程预览 |
| 22 | 08 排程与任务二审 | `POST` | `/task-bundles/{id}/versions` | 从已批准任务包建立新草稿版本 |
| 23 | 09 发布与日任务 | `GET` | `/execution-tasks/{id}` | 查询正式任务及冻结执行窗口 |
| 24 | 09 发布与日任务 | `GET` | `/patients/{id}/execution-tasks` | 查询指定患者正式日任务 |
| 25 | 09 发布与日任务 | `GET` | `/publications/{id}` | 查询发布结果与通知配置状态 |
| 26 | 09 发布与日任务 | `GET` | `/task-bundles/{id}/publication-previews/{previewId}` | 读取冻结发布衔接清单 |
| 27 | 09 发布与日任务 | `POST` | `/task-bundles/{id}/publication-previews` | 核对发布与未来版本衔接清单 |
| 28 | 09 发布与日任务 | `POST` | `/task-bundles/{id}/publications` | 按指定预览事务发布任务 |
| 29 | 10 打卡与补记 | `GET` | `/check-ins/{id}` | 读取一条不可变反馈 |
| 30 | 10 打卡与补记 | `GET` | `/execution-tasks/{id}/check-ins` | 查询任务全部反馈版本 |
| 31 | 10 打卡与补记 | `POST` | `/check-ins/{id}/amendments` | 追加反馈更正 |
| 32 | 10 打卡与补记 | `POST` | `/execution-tasks/{id}/check-ins` | 一键完成或提交基础反馈 |
| 33 | 11 AI 方案候选 | `GET` | `/plan-generation-jobs/{id}` | 读取生成进度与候选 |
| 34 | 11 AI 方案候选 | `GET` | `/plan-versions/{id}/generation-jobs` | 查询最近 20 次生成历史 |
| 35 | 11 AI 方案候选 | `POST` | `/plan-generation-jobs/{id}/adoptions` | 采用候选到草稿 |
| 36 | 11 AI 方案候选 | `POST` | `/plan-versions/{id}/generation-jobs` | 提交 AI 方案生成 |
| 37 | 11 旧接口兼容：档案选择 | `GET` | `/store/care/grant-administration/archives` | 按前端旧契约查询授权档案列表 |
| 38 | 12 旧接口兼容：资料与建议 | `GET` | `/store/care/archives/{archiveId}/input` | 按旧档案编号读取患者输入 |
| 39 | 12 旧接口兼容：资料与建议 | `POST` | `/store/care/drafts/recommendations/generate` | 按旧契约生成 AI 方案建议 |
| 40 | 14 病种管理侧重与来源 | `GET` | `/patients/{id}/management-basis` | 读取患者病种管理依据 |
| 41 | 14 病种管理侧重与来源 | `POST` | `/store/care/drafts/basis/evaluate` | 按旧契约读取病种管理依据 |

源定义 SHA-256：`03aaf6fe6363030d82c3b99ec05f1c578ed830bac91d4cbf3706a3227e05edfd`，对应本次取回的文档快照 `.dev/qa/management-openapi.json`。
