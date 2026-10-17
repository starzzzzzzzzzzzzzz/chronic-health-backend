# Knife4j 本地接口文档与调试

## 启动与入口

优先使用项目脚本：

```bash
./scripts/db.sh up
SERVER_PORT=9089 ./scripts/run-demo.sh
```

- 页面：<http://127.0.0.1:9089/ncd-ai/doc.html>
- 当前接口的 OpenAPI JSON：<http://127.0.0.1:9089/ncd-ai/v3/api-docs/health>

页面按患者资料、方案、条件确认、一审、任务派生、二审、发布、日任务、打卡和运行状态分组，共33个操作（32个业务操作和1个存活检查），包括“11 旧接口兼容：档案选择”。Actuator 依赖健康检查不属于本组。接口说明随 Controller、DTO/VO 和文档模型生成；动态 JSON 请求体通过专门的文档模型展示真实根字段。

## 第一次调用

1. 打开左侧 **Authorize**，在 `AuthorizationToken` 行的参数值中填入本机 `.env.demo` 的 `HEALTH_DEMO_API_TOKEN` 值。只填原始值，不加 `Bearer`，不填变量名。
2. 展开 **01 患者资料 → 查询授权患者列表 → 调试**，点击 **发送**。未授权返回 401，正确授权返回 200。
3. 复制实际患者 ID，在健康摘要接口查询资料；创建草稿时使用摘要返回的 `profileRevision`。方案相关修订号取当前方案 `rowVersion`；审核决策、撤回的修订号取审核提交的 `rowVersion`。
4. 一审决策前，把 Authorize 值换成 `.env.demo` 的 `HEALTH_DEMO_REVIEWER_TOKEN`；审核人必须与提交人不同。若已打开调试页仍显示旧值，关闭该接口标签后重新打开，并在“请求头部”确认 Authorization。
5. 调试结束在 **Authorize → 注销** 清除授权值。Knife4j 会在本机浏览器缓存输入，不要在共享设备上保留凭证，也不要把授权值截图发给别人。

有请求体的接口，在“接口描述”中提供 **可复制的请求示例**；复制到调试正文并替换版本 ID、条目 ID、修订号。Knife4j 4.5 会另外自动生成一份包含所有字段的完整结构，其中空值不能直接当作有效请求。示例创建的草稿不含完整正文，执行条件确认和送审还需要满足 [第二批业务规则](PHASE-B-API.md)。完整契约见 [第一批](PHASE-A-API.md) 、[第二批](PHASE-B-API.md) 和 [第三批](PHASE-CDE-API.md)。响应示例同样来自 schema，不代表实际调用成功；以“调试”返回和测试结果为准。

## 开关与环境

`local` 默认开启文档，运行 `HEALTH_DOCS_ENABLED=false SERVER_PORT=9089 ./scripts/run-demo.sh` 可关闭。通用配置默认关闭；安全规则同时检查 `local` 和 `health.docs.enabled`，非 local 即使误开启生成器也不会放行文档入口。业务接口仍需要原有认证和患者能力授权。

`run.sh` 默认不启用 DEMO 身份，仅查看文档不能自动调用业务接口。默认监听 127.0.0.1，前端同伴的电脑无法直接访问这个地址；共享联调环境部署后再提供实际地址和单独凭证。

## 技术选择与验证

当前使用 `springdoc-openapi-starter-webmvc-ui 3.1.1` 生成 OpenAPI 3.0 文档，搭配 `knife4j-openapi3-ui 4.5.0` 纯 UI。没有引入面向旧 Spring Boot 的 Knife4j starter，也没有改动项目 Java 17 / Spring Boot 4.1.1。参考 [springdoc 兼容说明](https://springdoc.org/#what-is-the-compatibility-matrix-of-springdoc-openapi-with-spring-boot) 和 [Knife4j 模块说明](https://doc.xiaominfo.com/docs/upgrading/upgrading-to-v4)。

验证命令为 `./scripts/test-db.sh`，覆盖真实 MySQL 业务回归、33个文档操作、请求体根字段、原始 Authorization 声明、关闭文档和非 local 访问边界。浏览器还需验证页面加载、未授权 401、授权后 200 和请求示例；自动生成文档不替代这些测试。

患者打卡使用 `.env.demo` 的 `HEALTH_DEMO_PATIENT_TOKEN`，编辑/发布、审核、患者三个身份分开。二审沿用审核接口，审核提交的 expectedRevision 不等于任务包 rowVersion。发布和反馈需另填 Idempotency-Key；重复调用相同键相同正文回读200。打卡空对象为自述完成，质量始终 NOT_APPLICABLE。

旧前端请求的档案列表入口已提供，详见 [契约对齐进度](FRONTEND-CONTRACT-ALIGNMENT.md)。Apifox 若已绑定本机 OpenAPI，需要重新同步数据源才能看到新分组；出生日期的 nullable 修正也包含在新规范中。
