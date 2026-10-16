# 前后端接口基线

以下是当前骨架的已实现行为及后续 DTO 约定，不表示业务接口已经完成。

## RESTful 设计要求

项目业务 API 统一采用 RESTful 设计方式。以下是后续实现约定，示例路径尚未实现。

- 保留当前服务前缀 `/ncd-ai`。资源路径使用小写复数名词，多词使用连字符，例如 `/ncd-ai/patients`、`/ncd-ai/recovery-plans`，避免 `getPatient`、`addPatient` 等操作名。
- HTTP 方法表达操作：GET 查询，POST 创建，PUT 完整替换，PATCH 部分更新，DELETE 删除。GET 不修改业务状态；PUT、DELETE 按幂等语义设计，重复请求的响应状态可以不同。POST、PATCH 是否支持幂等键由具体用例约定。
- 单个资源通过路径 ID 定位；筛选、排序、分页使用查询参数；创建与更新数据使用 JSON 请求体。分页参数与返回结构在首个列表接口实现前对齐。
- 资源关系明确时使用子资源，例如 `/ncd-ai/patients/{patientId}/reports`。审核、发布等流程优先建模为审核记录、发布记录等资源，结合实际业务确认路径。
- 正确返回 HTTP 状态：查询或有响应体的更新使用 200；同步创建成功使用 201 并提供 Location；成功且无响应体使用 204；异步受理但尚未完成使用 202，并提供进度查询方式。
- 错误使用对应状态：参数不合法 400，未认证或认证无效 401，已认证但权限不足 403，资源不存在 404，状态或版本冲突 409，服务端异常 500。不能统一返回 HTTP 200 再仅通过响应体表示失败。
- 常规 JSON 响应保留 ApiResponse 包装，code 与 HTTP 状态一致；204 不返回包装或任何响应体。创建接口不能直接使用当前固定 code=200 的 success 工厂方法，届时按实际状态扩展响应构造。
- 数据库 Entity 不直接作为业务接口入参或返回值；请求参数使用 DTO，返回内容使用 VO，业务规则与事务放在 Service。

例如档案资源：GET `/ncd-ai/patients` 查询列表，GET `/ncd-ai/patients/{id}` 查询详情，POST `/ncd-ai/patients` 创建，PATCH `/ncd-ai/patients/{id}` 部分更新，DELETE `/ncd-ai/patients/{id}` 删除。具体资源命名和是否允许删除仍须按业务需求确定。

## 已实现接口

| 方法与路径 | 用途 | 响应 |
|---|---|---|
| GET /ncd-ai/health | 进程存活 | 200，公共响应包装 |
| GET /ncd-ai/actuator/health | 依赖就绪，包含数据库检查 | 依赖健康 200，否则 503；Actuator 原生结构 |

```json
{"code":200,"message":"success","data":{"status":"UP","service":"chronic-health"}}
```

所有请求经 X-Request-Id 过滤器：允许 1～64 位字母、数字及 `._:-`；缺失或非法时生成 UUID；响应头返回最终编号。日志上下文在请求结束后清理。请求编号不等于业务幂等键。

无有效认证的非公开路径返回 HTTP 401 和 `{"code":401,"message":"Authentication required","data":null}`。当前没有登录入口，也不会接受任意 Authorization 值。完整权限接入前不要开放业务路由。

## 业务实现时遵守的约定

- JSON 使用 camelCase，所有业务 ID 使用 String。当前只有健康 DTO，尚未配置全局 Long 序列化器。
- 常规业务响应使用 ApiResponse 的 code、message、data，HTTP 状态与业务结果保持一致。
- API 时间使用带时区偏移的 ISO 8601 字符串；数据库时间规则随首个业务模型明确。
- 参数、分页、幂等、期望版本和冲突错误码在首个业务用例实现前与前端冻结，不提前创建无语义的通用 CRUD。
- 兼容旧系统原始 Authorization Token 的需求保留，实际会话验证、机构范围和档案归属未接入。
- 文件上传、事件补拉、OpenAPI UI、统一业务异常处理及业务权限测试均为后续工作，当前未实现。
