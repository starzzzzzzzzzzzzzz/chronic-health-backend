# 慢病健康管理后端

当前已建立 Spring Boot 4.1.1 后端骨架，包含健康检查、请求追踪、安全默认拒绝和 Flyway 迁移。采用常见的 Controller / Service / Mapper 分层结构，目前仅保留业务层目录，尚未实现业务功能。目录说明见 [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)，接口约定见 [docs/API-CONTRACT.md](docs/API-CONTRACT.md)。

## 环境约定

| 组件 | 配置 |
|---|---|
| Java | JDK 17；本机复用 Homebrew 17.0.15 |
| Maven | 本机 3.9.9；项目脚本固定 JDK 17 |
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

密码保存在 `.env`，权限 600，已加入 Git 忽略。应用使用 `MYSQL_PASSWORD` 对应的应用用户密码，不使用 root。`init-env.sh` 不覆盖已有文件。

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

2026-10-16：项目脚本使用 Java 17.0.15 / Maven 3.9.9；`./scripts/mvn.sh -B -ntp verify` 成功，4 项 MVC 测试全部通过，并生成 Spring Boot 可执行 JAR。通过 `./scripts/run.sh` 实际启动应用，连接 MySQL 8.4.11，Flyway V1 迁移成功。HTTP 健康检查和数据库依赖健康均返回 UP，请求编号正常回传，未认证业务路径返回 401。业务功能尚未实现；本次验证覆盖框架启动和基础设施。

官方发布说明存在 8.4.12 容器补丁记录，但本次 Docker Hub 的 `mysql:8.4.12` 标签不存在；实际获取 `mysql:8.4` 后以摘要锁定，并以数据库查询结果记录服务端版本。升级需要显式修改镜像摘要并重新验证。

## 服务验证

启动后默认地址为 `http://127.0.0.1:9088/ncd-ai`：

```bash
curl -i http://127.0.0.1:9088/ncd-ai/health
curl -i http://127.0.0.1:9088/ncd-ai/actuator/health
```

local 启动自动执行 Flyway，当前仅创建 app_metadata 和 Flyway 历史表。常规测试使用 MVC 测试切片，不依赖数据库；真实数据库迁移和依赖健康需启动后验证。生产配置未设置默认数据库密码，迁移默认关闭。不要在公共网络直接暴露开发服务。

## 数据访问

使用 MyBatis-Plus BaseMapper 处理基础 CRUD，自定义 SQL 放在 `src/main/resources/mapper/`。业务逻辑与事务放在 Service。AppMetadata / AppMetadataMapper 仅映射已有框架元数据表，没有新增业务接口或业务表。

普通 `./scripts/mvn.sh -B -ntp verify` 运行 MVC 测试并跳过真实数据库测试。数据库启动后执行 `./scripts/test-db.sh`：加载本地 `.env`，启用 local 迁移并测试真实 MySQL，覆盖实体字段映射、XML SQL 和基础 CRUD，最后打包。测试数据在事务中回滚。该脚本仅用于项目开发数据库。
