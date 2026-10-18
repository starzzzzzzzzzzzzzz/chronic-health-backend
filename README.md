# 慢病健康管理后端

当前已实现患者资料、方案草稿（支持可选 DeepSeek AI 候选）、执行条件与一审，以及七维任务派生、排程与二审、事务发布、日任务、基础打卡和追加更正。支持输入快照、并发修订校验和审计；目前仅开放虚构 DEMO 的计完成流程。采用 Spring Boot 4.1.1 与常见的 Controller / Service / Mapper 分层结构。目录说明见 [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)，接口约定见 [docs/API-CONTRACT.md](docs/API-CONTRACT.md)。

数据库设计初稿见 [设计总览](docs/database/DATABASE-DESIGN.md)、[表与字段字典](docs/database/TABLE-DICTIONARY.md) 和 [视频页面字段映射](docs/database/VIDEO-FIELD-MAPPING.md)。实际迁移已到 V11：V2/V3 患者与方案，V4 条件与审核，V5/V6 任务与二审，V7 发布，V8 反馈与独立安全事件，V9 旧接口档案编号映射，V10 AI 生成候选作业，V11 旧建议生成请求的幂等映射。

当前业务全景见 [后端业务说明](docs/business/README.md)（[浏览器阅读版](docs/business/business-overview.html)）：包含业务流程图、数据流图、功能结构图、数据库 ER 图及用例图，以及角色、状态和 41 个接口索引；[ER 分域详图](docs/business/DATABASE-ER.md)覆盖当前 33 张表和 62 条外键，明确区分原生业务能力、旧前端适配与待开发功能。

第一批“患者资料与方案草稿”的[实施计划](docs/superpowers/plans/2026-10-16-patient-and-plan-drafts.md)已确认并实施；前端对接见 [第一批接口与启动说明](docs/PHASE-A-API.md)。

## 项目目录

```text
chronic-health-backend/
├── pom.xml                              Maven 依赖与构建配置
├── compose.yaml                         独立 MySQL 开发环境
├── .mvn/settings.xml                    项目 Maven 设置
├── scripts/
│   ├── init-env.sh                      初始化本地环境变量
│   ├── mvn.sh                           使用 Java 17 构建
│   ├── db.sh                            数据库启停与检查
│   ├── run.sh                           启动后端
│   ├── test-db.sh                       真实数据库测试与打包
│   ├── run-demo.sh                      显式开启本地 DEMO 联调
│   └── run-demo-web.sh                  启动 Vue 3 本地方案演示页
├── demo-web/                            连接真实后端的独立演示前端
│   ├── src/                            患者资料、AI 候选与草稿编辑
│   ├── local-proxy.mjs                  本地编辑者代理及请求边界
│   └── tests/                          代理和编辑字段边界测试
├── src/main/java/com/healthsystem/
│   ├── HealthSystemApplication.java     Spring Boot 启动入口
│   ├── controller/                      接收请求、返回响应
│   ├── service/                         业务逻辑与事务
│   │   └── impl/                        业务实现类（按需添加）
│   ├── mapper/                          MyBatis-Plus 数据访问接口
│   ├── entity/                          数据库实体
│   ├── dto/                             请求参数、数据传输对象
│   ├── vo/                              接口返回对象
│   ├── config/                          安全、MyBatis-Plus 等配置
│   ├── common/                          公共组件
│   │   ├── api/                         统一响应
│   │   └── web/                         请求编号过滤器
│   ├── exception/                       异常定义与处理
│   └── utils/                           工具类
├── src/main/resources/
│   ├── application.yaml                 通用配置
│   ├── application-local.yaml           本地开发配置
│   ├── mapper/                          自定义 SQL XML
│   └── db/migration/                    Flyway 数据库迁移
├── src/test/java/com/healthsystem/
│   ├── WebFoundationTest.java           Web 基础行为测试
│   └── MybatisPlusDatabaseTest.java     MySQL 数据访问测试
└── docs/
    ├── ARCHITECTURE.md                  分层职责与架构说明
    ├── API-CONTRACT.md                  RESTful 接口约定
    ├── PHASE-A-API.md                   第一批前端联调契约
    ├── PHASE-B-API.md                   执行条件与一审接口
    ├── PHASE-CDE-API.md                 任务、二审、发布、日任务与打卡
    ├── KNIFE4J.md                       接口文档与在线调试
    ├── FRONTEND-CONTRACT-ALIGNMENT.md    旧前端契约兼容进度
    └── database/                        核心业务数据库设计与字段字典
```

业务请求通常依次经过 `Controller → Service → Mapper → MySQL`。患者与方案草稿已具备 Controller、Service 和 Mapper；条件确认与一审见 [第二批接口](docs/PHASE-B-API.md)；任务到打卡见 [第三批联调契约](docs/PHASE-CDE-API.md)。本地 `.env`、依赖缓存 `.dev/` 和构建产物 `.dev/build.nosync/`（历史 `target/`） 已忽略，不提交到仓库。

## 第一批联调

```bash
./scripts/db.sh up
SERVER_PORT=9089 ./scripts/run-demo.sh
```

基址为 `http://127.0.0.1:9089/ncd-ai`。本地随机凭证存于 `.env.demo`，使用原始 Authorization Token。仅 local + 显式 DEMO 开关接受该身份；原 run.sh 默认不开启。示例资料均为虚构，详见 [接口说明与请求示例](docs/PHASE-A-API.md)。

Knife4j 文档与在线调试：<http://127.0.0.1:9089/ncd-ai/doc.html>。先在 **Authorize** 填入 `.env.demo` 对应身份的原始 Token，再选择接口的 **调试 → 发送**。使用方法及开关见 [KNIFE4J.md](docs/KNIFE4J.md)。

## 本地模拟前端

后端已按上面的 DEMO 方式启动后，另开终端运行 `./scripts/run-demo-web.sh`，打开 <http://127.0.0.1:9090/>。页面采用 Vue 3，直接调用本机真实后端，可完成“选择虚构患者 → 查看六方面资料 → DeepSeek 生成七维候选 → 核对依据 → 采纳 → 编辑保存”。生成会实际调用已配置的 AI 服务；页面范围止于草稿编辑。

首次启动自动安装前端依赖，需 Node.js 22.12+（或 20.19+）及 npm。目录、操作和验证记录见 [演示工作台说明](docs/DEMO-WORKBENCH.md)。

## 环境约定

| 组件 | 配置 |
|---|---|
| Java | JDK 17；本机复用 Homebrew 17.0.15 |
| Maven | 本机 3.9.9；项目脚本固定 JDK 17 |
| Maven 构建产物 | `.dev/build.nosync`，避免同步生成重复文件 |
| Maven 依赖缓存 | `.dev/m2`，不写入默认用户依赖缓存 |
| Spring Boot | 4.1.1 |
| MyBatis-Plus | 3.5.17，Spring Boot 4 starter |
| MySQL 镜像 | `mysql:8.4`，通过 compose.yaml 中的 SHA-256 镜像摘要锁定 |
| 实测 MySQL 服务端 | 8.4.11 |
| 数据库地址 | `127.0.0.1:13306`，仅本机可访问 |
| 数据库／用户 | `chronic_health` / `health_app` |
| 字符集／排序规则 | `utf8mb4` / `utf8mb4_0900_ai_ci` |
| 数据库时区 | `+08:00` |
| Compose 项目名 | `chronic-health-dev` |
| 持久数据卷 | `chronic-health-dev_mysql-data` |

Java 与 Maven 二进制复用本机安装，未修改全局 shell、JAVA_HOME 或旧数据库。项目不自动升级现有工具。另一台机器需准备 JDK 17、Maven 3.9.9、Docker 和 Compose；可用 `HEALTH_JAVA_HOME` 显式指定 JDK。当前未包含 Maven Wrapper。

## 日常命令

```bash
./scripts/init-env.sh        # 首次生成随机密码；已存在时保留
./scripts/mvn.sh -version    # 确认 Maven 使用 JDK 17
./scripts/mvn.sh -B -ntp verify # 测试并打包可执行 JAR
./scripts/db.sh up           # 首次下载镜像并等待健康检查
./scripts/db.sh status
./scripts/db.sh check        # 实际使用应用账号查询数据库信息
./scripts/db.sh shell
./scripts/db.sh stop         # 停止容器，保留数据
./scripts/run.sh             # 前台启动，Ctrl+C 停止；需先 db.sh up
```

数据库密码保存在 `.env`，权限 600，已加入 Git 忽略。应用使用 `MYSQL_PASSWORD` 对应的应用用户密码，不使用 root。`init-env.sh` 不覆盖已有文件。

JDBC 连接地址：

```text
jdbc:mysql://127.0.0.1:13306/chronic_health?connectionTimeZone=Asia/Shanghai
```

`scripts/run.sh` 从本地 `.env` 读取数据库配置并注入进程，默认启用 local 配置。普通 Java/IDE 进程不会自动读取 `.env`，需配置 DB_PASSWORD；IDE 本地启动需同时指定 local profile。不要把密码提交到源码。

## IDE 配置

Project SDK、Maven Importer 和 Maven Runner 都选择 JDK 17。Maven 设置选择项目 `.mvn/settings.xml`，本地仓库选择项目 `.dev/m2`，Maven 版本选择 3.9.9。终端构建优先使用 `scripts/mvn.sh`，避免系统 JAVA_HOME 指向旧 Java。

## 数据与维护

容器停止或重建不会自动清除命名卷。不要使用 `docker compose down -v`，该命令会删除本项目数据库数据。修改 `.env` 中密码不会更改已经初始化的数据库账号；需要使用数据库密码变更流程同步修改。已有数据时不要删除 `.env` 后重新生成密码。

`health_app` 仅获得项目数据库内的开发权限；这是本地开发配置，生产环境需要另行划分迁移账号和运行账号。

## 本机验证记录

以下首段为框架初始化时的历史验证，第一批业务验证见本节后的最新记录。

2026-10-16：项目脚本使用 Java 17.0.15 / Maven 3.9.9；`./scripts/mvn.sh -B -ntp verify` 成功，4 项 MVC 测试全部通过，并生成 Spring Boot 可执行 JAR。通过 `./scripts/run.sh` 实际启动应用，连接 MySQL 8.4.11，Flyway V1 迁移成功。HTTP 健康检查和数据库依赖健康均返回 UP，请求编号正常回传，未认证业务路径返回 401。业务功能尚未实现；本次验证覆盖框架启动和基础设施。

官方发布说明存在 8.4.12 容器补丁记录，但本次 Docker Hub 的 `mysql:8.4.12` 标签不存在；实际获取 `mysql:8.4` 后以摘要锁定，并以数据库查询结果记录服务端版本。升级需要显式修改镜像摘要并重新验证。

## 服务验证

启动后默认地址为 `http://127.0.0.1:9088/ncd-ai`：

```bash
curl -i http://127.0.0.1:9088/ncd-ai/health
curl -i http://127.0.0.1:9088/ncd-ai/actuator/health
```

local 启动自动执行 Flyway，V1 为元数据，V2/V3 为患者与方案草稿 12 张业务表，V4 为执行条件与审核 3 张表。常规测试使用 MVC 测试切片，不依赖数据库；真实数据库迁移和依赖健康需启动后验证。生产配置未设置默认数据库密码，迁移默认关闭。不要在公共网络直接暴露开发服务。

## 数据访问

使用 MyBatis-Plus BaseMapper 处理基础 CRUD，自定义 SQL 放在 `src/main/resources/mapper/`。业务逻辑与事务放在 Service。已接入患者、资料、方案及子表 Mapper；复杂范围查询及行锁使用 XML。

普通 `./scripts/mvn.sh -B -ntp verify` 运行 MVC 测试并跳过真实数据库测试。数据库启动后执行 `./scripts/test-db.sh`：加载本地 `.env`，启用 local 迁移并测试真实 MySQL，覆盖实体字段映射、XML SQL 和基础 CRUD，最后打包。测试资料使用事务回滚；独立并发/故障注入测试在 finally 中按其自有随机 ID 清理。该脚本仅用于项目开发数据库。

2026-10-16 第一批历史验证：28 项测试全部通过（含真实 MySQL 与并发事务）；实际 HTTP 已验证患者查询、资料摘要、草稿创建/编辑/读取及 401/409。

2026-10-16 第二批验证：48 项测试全部通过（含真实 MySQL、权限、并发和回滚）；普通离线构建通过，6 项通过、42 项数据库测试明确跳过。实际 HTTP 24 步覆盖执行条件、一审退回重提、通过、新版本和撤回，以及 401/403/409。联调说明见 [PHASE-B-API.md](docs/PHASE-B-API.md)。该历史批次未包含任务；当前进度以文首和第三批契约为准。

2026-10-16 文档接入验证：`./scripts/test-db.sh` 53 项全部通过；普通离线构建通过，6 项通过、47 项数据库测试明确跳过。Knife4j 页面显示当前 14 个操作，浏览器验证未授权 401、原始 Token 授权后患者查询 200；OpenAPI 生成、请求字段和关闭文档/非 local 访问边界均有测试覆盖。使用说明见 [KNIFE4J.md](docs/KNIFE4J.md)。

2026-10-16 第三批验证：真库108/108通过；普通离线构建15项通过、93项真库测试明确跳过。9089真实HTTP30步闭环通过，Knife4j展示32个操作。独立审查的7项边界问题已复现并修复；前端对接见 [第三批契约](docs/PHASE-CDE-API.md)，详细结果见 [验证记录](docs/PHASE-CDE-VALIDATION.md)。目前仍是虚构DEMO计完成流程。

2026-10-17 旧契约适配第一批：新增旧档案选择 GET，使用 `keyword/page/pageSize` 和稳定字符串 `archiveId`；V9 管理兼容编号，现有 32 个操作保留，当前共 33 个。真库 125 项回归通过；后续旧方案与任务入口仍待适配，详情见 [前端契约对齐](docs/FRONTEND-CONTRACT-ALIGNMENT.md)。


### DeepSeek AI 方案草稿

可从 `.env.ai.example` 复制模板，填写本机 `.env.ai` 的 `DEEPSEEK_API_KEY`，设置 `DEEPSEEK_MODEL=deepseek-flash` 和 `HEALTH_AI_ENABLED=true`，使用项目脚本启动。后台根据最新虚构患者资料生成候选，结构与来源校验通过后由人工采用到草稿；生成不覆盖人工修改，也不自动审核或发布。

AI 批次新增 4 个操作；随后旧资料与建议入口增加 2 个，管理侧重再增加 2 个，当前共 41 个；请求、状态机、限制和联调方式见 [AI 方案生成](docs/AI-PLAN-GENERATION.md)。用药与中医专业参数绑定暂不由 AI 生成，相关维度保留待确认/关闭状态。旧工作流兼容入口仍需后续适配。

2026-10-18 AI 验证：149项后端测试通过；4轮24次真实生成迭代，最新6例结构通过；原33接口的Apifox场景55步、103断言通过。内容复查与完整链路状态见 [AI验证记录](docs/AI-PLAN-VALIDATION.md)。

2026-10-18 旧前端适配继续推进：已补患者输入和 AI 建议生成入口，真库 160 项回归通过；虚构患者真实 DeepSeek 请求通过，并发同键只创建一个作业，重放结果一致。当前仍未完成旧工作流、双审与任务入口适配，尚无伙伴页面联调通过证据。动态字段、分类状态及旧工作流的限制见 [旧建议接口对齐说明](docs/LEGACY-RECOMMENDATIONS-ALIGNMENT.md)。

2026-10-18 管理侧重批次：已接入病种依据评估与来源绑定，确定性计算七维权重和多病融合，AI 保留人工确认结果；完整回归 169 项通过，真实虚构双病场景完成生成、幂等重放和采用草稿。医学自动判型尚未启用，正式业务规则仍需审核。见 [管理侧重与融合说明](docs/MANAGEMENT-FOCUS-AND-FUSION.md)。
