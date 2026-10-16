# 后端目录与分层

本工程采用常见的 Spring Boot 分层结构：Java 17、Spring Boot 4.1.1、MySQL 8.4，单个可执行服务。前端工程由前端成员维护。

```text
Health_system/
├── pom.xml                         Maven 依赖与可执行 JAR
├── compose.yaml                    本地独立 MySQL
├── .mvn/settings.xml               项目 Maven 设置
├── scripts/
│   ├── mvn.sh                      固定 Java 17 和独立依赖缓存
│   ├── init-env.sh                 生成本地数据库配置
│   ├── db.sh                       数据库启停与检查
│   └── run.sh                      加载本地配置并启动服务
├── src/main/java/com/healthsystem/
│   ├── HealthSystemApplication.java
│   ├── controller/                 接收前端请求；已有 HealthController
│   ├── service/                    业务逻辑
│   │   └── impl/                   实现类，按需添加
│   ├── mapper/                     数据访问，MyBatis-Plus 与 XML SQL
│   ├── entity/                     数据库实体
│   ├── dto/                        请求参数、数据传输
│   ├── vo/                         接口返回对象
│   ├── config/                     安全与基础配置
│   ├── common/                     公共组件
│   │   ├── api/                    统一响应 ApiResponse
│   │   └── web/                    请求编号过滤器
│   ├── exception/                  异常定义与处理
│   └── utils/                      工具类
├── src/main/resources/
│   ├── application.yaml
│   ├── application-local.yaml
│   └── db/migration/               Flyway SQL
├── src/test/java/com/healthsystem/
│   └── WebFoundationTest.java
└── docs/
    ├── ARCHITECTURE.md
    └── API-CONTRACT.md
```

请求通常依次经过 Controller → Service → Mapper。Controller 负责参数接收与响应，Service 负责业务规则与事务，Mapper 负责数据库访问。DTO 用于接收与传输数据，VO 用于接口返回，Entity 对应持久化实体。

当前实现健康检查、公共响应、请求编号、安全配置以及框架元数据的 Entity 和 Mapper。其余目录使用 package-info.java 保留并说明职责，尚未添加业务实现。Service 接口与 impl 实现类按实际需要创建；简单健康检查无需额外 Service。业务增多时可在各层下按档案、报告、随访等增加子包。

已接入 MyBatis-Plus 3.5.17 的 Spring Boot 4 starter，独立配置类扫描 mapper 包；自定义 SQL 位于 resources/mapper。基础 CRUD 使用 BaseMapper，复杂查询使用 XML 绑定参数。AppMetadata 映射 V1 已有表，其字符串主键由调用方提供。尚未引入分页、多租户或通用 Service 继承体系。Flyway 继续负责迁移。旧系统迁移、旧 Token 接入及具体业务表均未完成。

安全边界：公开 GET /ncd-ai/health 和 GET /ncd-ai/actuator/health。其他路径默认拒绝，旧 Token 与档案授权尚未实现；不能把此策略当作完整登录系统。默认只监听 127.0.0.1，前端本机可用开发代理。跨机联调前须先配置身份与授权，再显式设置监听地址。

数据库：只连接项目独立的新库。V1 创建框架元数据表并记录应用标识；不创建患者等未确认业务表。Flyway 仅 local 配置默认启用；其他环境须配置迁移流程。已执行迁移文件不可修改，后续追加新版本。
