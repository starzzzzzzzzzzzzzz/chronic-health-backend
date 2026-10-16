# 前后端接口基线

第一批患者资料与方案草稿接口已实现，完整请求/响应和 DEMO 联调方式见 [PHASE-A-API.md](PHASE-A-API.md)。执行条件与一审扩展见 [PHASE-B-API.md](PHASE-B-API.md)；任务、二审与发布仍未实现。

## RESTful 设计要求

项目业务 API 统一采用 RESTful 设计方式。以下是统一约定；已实现路径以本文接口表和第一、二批接口说明为准，其余示例属于后续设计。

- 保留当前服务前缀 `/ncd-ai`。资源路径使用小写复数名词，多词使用连字符，例如 `/ncd-ai/patients`、`/ncd-ai/recovery-plans`，避免 `getPatient`、`addPatient` 等操作名。
- HTTP 方法表达操作：GET 查询，POST 创建，PUT 完整替换，PATCH 部分更新，DELETE 删除。GET 不修改业务状态；PUT、DELETE 按幂等语义设计，重复请求的响应状态可以不同。POST、PATCH 是否支持幂等键由具体用例约定。
- 单个资源通过路径 ID 定位；筛选、排序、分页使用查询参数；创建与更新数据使用 JSON 请求体。分页参数与返回结构在首个列表接口实现前对齐。
- 资源关系明确时使用子资源，例如 `/ncd-ai/patients/{patientId}/reports`。审核、发布等流程优先建模为审核记录、发布记录等资源，结合实际业务确认路径。
- 正确返回 HTTP 状态：查询或有响应体的更新使用 200；同步创建成功使用 201 并提供 Location；成功且无响应体使用 204；异步受理但尚未完成使用 202，并提供进度查询方式。
- 错误使用对应状态：参数不合法 400，未认证或认证无效 401，已认证但权限不足 403，资源不存在 404，状态或版本冲突 409，服务端异常 500。不能统一返回 HTTP 200 再仅通过响应体表示失败。
- 常规 JSON 响应保留 ApiResponse 包装，code 与 HTTP 状态一致；204 不返回包装或任何响应体。创建接口不能直接使用固定 code=200 的 success 工厂方法；已实现创建接口使用 HTTP 201 并构造 code=201 的响应。
- 数据库 Entity 不直接作为业务接口入参或返回值；请求参数使用 DTO，返回内容使用 VO，业务规则与事务放在 Service。

例如档案资源：GET `/ncd-ai/patients` 查询列表，GET `/ncd-ai/patients/{id}` 查询详情，POST `/ncd-ai/patients` 创建，PATCH `/ncd-ai/patients/{id}` 部分更新，DELETE `/ncd-ai/patients/{id}` 删除。具体资源命名和是否允许删除仍须按业务需求确定。

## 已实现接口

| 方法与路径 | 用途 | 响应 |
|---|---|---|
| GET /ncd-ai/patients | 授权 DEMO 患者列表 | 200，分页 |
| GET /ncd-ai/patients/{id}/health-summary | 六方面资料与来源 | 200 |
| POST /ncd-ai/care-plans | 手工创建草稿与输入快照 | 201 + Location |
| GET /ncd-ai/plan-versions/{id} | 读取完整草稿 | 200 |
| PATCH /ncd-ai/plan-versions/{id} | 编辑草稿 | 200，陈旧修订 409 |
| GET /ncd-ai/health | 进程存活 | 200，公共响应包装 |
| GET /ncd-ai/actuator/health | 依赖就绪，包含数据库检查 | 依赖健康 200，否则 503；Actuator 原生结构 |

```json
{"code":200,"message":"success","data":{"status":"UP","service":"chronic-health"}}
```

所有请求经 X-Request-Id 过滤器：允许 1～64 位字母、数字及 `._:-`；缺失或非法时生成 UUID；响应头返回最终编号。日志上下文在请求结束后清理。请求编号不等于业务幂等键。

无有效认证的非公开路径返回 HTTP 401 和 `{"code":401,"message":"Authentication required","data":null}`。正式登录入口尚未接入；仅 local 且显式开启 DEMO 时接受服务端配置的开发凭证，身份固定为 demo-org 下的 demo-clinician 或 demo-reviewer，数据模式 DEMO；能力和患者范围仍逐次校验。实际患者查询与草稿写入进一步验证有效患者授权。默认或非 local 配置继续拒绝未认证业务访问。

## 业务实现时遵守的约定

- JSON 使用 camelCase，所有业务 ID 使用 String。已实现业务 DTO/VO 的 ID 与修订号使用 String，尚未配置全局 Long 序列化器。
- 常规业务响应使用 ApiResponse 的 code、message、data，HTTP 状态与业务结果保持一致。
- API 时间使用带时区偏移的 ISO 8601 字符串；新业务时刻按 UTC DATETIME(6) 存储，业务日历配 zoneId。
- 患者列表已约定 page/size/q；草稿 PATCH 要求 expectedRevision，冲突返回 409。发布幂等等后续用例在实现时另行冻结。
- 兼容旧系统原始 Authorization Token 的需求保留，真实会话验证未接入；已实现 DEMO 机构/模式隔离及患者授权，尚未对接旧平台会话与档案归属。
- 统一异常处理及首批患者授权测试已实现；文件上传、事件补拉、OpenAPI UI 和正式身份接入为后续工作。

## 执行条件与一审

第二批接口、两种 expectedRevision、审核快照与新版本契约见 [PHASE-B-API.md](PHASE-B-API.md)。POST condition-confirmations 追加历史；POST review-submissions 固定送审目标；POST decisions/withdrawals 在事务中结束本次提交。禁止提交者自审，禁止待审或已批准正文原地修改。
