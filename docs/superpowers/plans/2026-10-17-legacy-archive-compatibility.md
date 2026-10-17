# Legacy Archive Compatibility Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** 让前端旧档案选择请求在本机后端获得字段、分页、权限和编号均正确的结果。

**Architecture:** 新增旧契约适配 Controller/Service/VO，调用现有 PatientService.list。独立持久映射表关联旧格式数字编号与内部 UUID，写入时初始化，GET 只读。

**Tech Stack:** Java 17、Spring Boot 4.1.1、MyBatis-Plus 3.5.17、MySQL 8.4、Flyway、JUnit/MockMvc。

**Spec:** `docs/superpowers/specs/2026-10-17-legacy-api-compatibility-design.md`

## Global Constraints

- 本轮只有 GET `/ncd-ai/store/care/grant-administration/archives`，保留原有 32 个操作。
- raw Authorization；复用当前主体的 org/mode/PATIENT_READ，不能由查询参数扩大授权。
- keyword 默认空、page 默认 1、pageSize 默认 10；合法范围沿用现有分页服务。
- archiveId 为正 signed BIGINT 的十进制字符串；page/pageSize/total 为数字。
- age 按项目 Clock 的 Asia/Shanghai 当日计算周岁；未知/未来出生日期省略；diseaseTags 没来源时为空字符串。
- 既有 external_system/external_archive_id 不覆盖；无映射返回 409；GET 无写副作用。
- 用项目脚本运行真实 MySQL 测试，保留已验证的 birthDate 可空修复；不提交凭证。

## Review Focus

- 重复或并发初始化不能重分配编号；任务 1 验证唯一性、重复初始化和外键约束。
- 测试数据库已有患者的迁移回填，以及空库之后的 DEMO 初始化；任务 1 验证迁移 SQL 和初始化写入。
- 19 位编号不能失真；任务 2 在数据库保存并检查精确字符串，覆盖 JSON 类型。
- 上海午夜/生日边界与未来出生日期；任务 2 注入固定 Clock 验证省略和周岁。
- 部分页缺映射和伪造授权参数不能隐藏数据或扩大可见性；任务 2 验证 409、计数/分页和授权过滤。

## Task 1: 持久档案编号和初始化

**Files:**
- Create: `src/main/resources/db/migration/V9__create_legacy_archive_mapping.sql`
- Create: `src/main/java/com/healthsystem/entity/LegacyArchiveMapping.java`
- Create: `src/main/java/com/healthsystem/mapper/LegacyArchiveMappingMapper.java`
- Create: `src/main/java/com/healthsystem/service/LegacyArchiveMappingService.java`
- Create: `src/main/java/com/healthsystem/service/impl/LegacyArchiveMappingServiceImpl.java`
- Modify: `src/main/java/com/healthsystem/config/DemoConfiguration.java`
- Modify: `src/main/java/com/healthsystem/service/impl/DemoDataInitializer.java`
- Test: `src/test/java/com/healthsystem/LegacyArchiveMappingDatabaseTest.java`

**Interfaces:**
- Consumes: patient(org_id,id) 外键；现有 DemoDataInitializer 的事务。
- Produces: `LegacyArchiveMappingService.ensureForPatient(String orgId, String patientId): Long`，必须在调用方事务中执行；重复调用返回原编号。
- Produces: LegacyArchiveMappingMapper extends BaseMapper，字段 archiveId:Long/orgId:String/patientId:String/createdAt:LocalDateTime，支持 insertIfAbsent(orgId,patientId,createdAt)。

- [ ] 写迁移缺失的失败测试：information_schema 应存在 legacy_archive_mapping。
- [ ] 运行 `./scripts/test-db.sh -o -Dtest=LegacyArchiveMappingDatabaseTest`，预期表不存在断言失败。
- [ ] 写 V9：signed BIGINT AUTO_INCREMENT（MySQL 不允许自增列 CHECK，服务层拒绝非正编号）、唯一(org_id,patient_id)、组合外键，按稳定顺序 INSERT SELECT 回填现有患者。
- [ ] 写初始化测试：迁移 SQL 为旧患者回填；重复 ensure 编号相同；外键拒绝跨机构；DEMO 重启补齐缺失映射且不恢复撤销授权；调用方回滚也回滚映射。
- [ ] 实现实体、Mapper、MANDATORY 事务服务和 DEMO 两条初始化分支；执行定向测试，预期全部通过。
- [ ] 提交本任务源码和测试，运行 task-done 同一测试命令记录结果。

## Task 2: 旧档案列表与契约验证

**Files:**
- Create: `src/main/java/com/healthsystem/controller/LegacyArchiveController.java`
- Create: `src/main/java/com/healthsystem/service/LegacyArchiveService.java`
- Create: `src/main/java/com/healthsystem/service/impl/LegacyArchiveServiceImpl.java`
- Create: `src/main/java/com/healthsystem/vo/LegacyArchivePageVO.java`
- Create: `src/main/java/com/healthsystem/vo/LegacyManagedArchiveVO.java`
- Create: `src/test/resources/contracts/legacy-archives.openapi.json`
- Create: `src/test/java/com/healthsystem/LegacyArchiveApiTest.java`
- Modify: `src/main/java/com/healthsystem/config/SecurityConfiguration.java`
- Modify: `src/main/java/com/healthsystem/config/ApiDocumentationConfiguration.java`
- Modify: `src/test/java/com/healthsystem/ApiDocumentationDatabaseTest.java`
- Modify: `docs/FRONTEND-CONTRACT-ALIGNMENT.md`, `docs/KNIFE4J.md`, `README.md`

**Interfaces:**
- Consumes: Task 1 映射 Mapper，`PatientService.list(BusinessPrincipal,int,int,String): PageVO<PatientSummaryVO>`，Clock。
- Produces: `LegacyArchiveService.list(BusinessPrincipal principal,int page,int pageSize,String keyword): LegacyArchivePageVO`。
- Produces: LegacyArchivePageVO(items:List<LegacyManagedArchiveVO>,total:long,page:int,pageSize:int)；LegacyManagedArchiveVO(archiveId:String,name:String,diseaseTags:String,age:Integer)，age NON_NULL。

- [ ] 从已读旧 OpenAPI 冻结此 GET 及 ResultArchivePage/ArchivePage/ManagedArchive 的引用闭包，保留来源和快照哈希，不加入凭证或业务数据。
- [ ] 写 MockMvc 失败测试：授权旧 GET 应 200，目前被拒绝；精确字段/类型、默认值、分页/空页/字面查询、非法参数、19 位 ID、授权隔离/撤销/过期/非 ACTIVE、未知年龄及生日、无写入和缺映射 409；将响应递归校验到冻结的对象/数组/基本类型 schema。
- [ ] 运行 `./scripts/test-db.sh -o -Dtest=LegacyArchiveApiTest`，预期旧 GET 返回 403 而不是 200。
- [ ] 实现 Controller/Service/VO：复用患者列表，在同一只读事务按 org 和本页 patientId 批量读映射，任一缺失则 409；计算年龄并省略空值。仅添加此 GET 的 authenticated matcher。
- [ ] 为生成文档补充 400/409，更新数量为 29 paths/33 operations；验证新操作、query 默认值、字段类型、age 非必填且不可 null。
- [ ] 执行 `./scripts/test-db.sh -o`，预期全量零失败、零跳过；更新对接说明和实际验证记录。
- [ ] 重启本项目 9089 DEMO，真实 HTTP 验证旧请求 200、无凭证 401、参数错误 400、GET 不写入；导出不含凭证的新本机 OpenAPI，明确尚未验证前端页面。
- [ ] 提交并 task-done 记录已完成验证；按 executing-plans 要求做一次独立代码审查并处理重要发现。

## Execution decisions

2026-10-17：用户在设计审阅后明确要求“开始修正吧”，按既有当前会话直接执行偏好推进。继续使用当前功能分支和本地环境，保留未提交的已验证可空修复；不创建会改变运行目录的新工作树。实施前执行完整基线。
