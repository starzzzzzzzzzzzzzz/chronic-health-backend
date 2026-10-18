# 阿里云开发服务器部署

部署日期：2026-10-18。用于虚构 DEMO、开发验收和后续前后端联调；不属于生产环境。

## 当前部署

| 项目 | 实际配置 |
| --- | --- |
| 主机 | `120.26.14.242`，杭州，2 vCPU / 标称 2 GB / 40 GB |
| 系统 | Ubuntu 24.04.2，x86_64；系统报告内存约 1613 MiB |
| Java | OpenJDK 17.0.20.1，由 Ubuntu 软件源安装 |
| MySQL | 8.4.11，Oracle 官方签名 APT 源，原生 systemd 服务 |
| 后端版本 | `16655cfe63f774525dfe0e983bbb6314c8c63e20` 构建 |
| JAR SHA-256 | `948c06378ab9d26c3b0df6328e2a83717720b2cf778b9b6e278af782796d7a04` |
| JAR | `/opt/chronic-health/app.jar` |
| 后端服务 | `chronic-health.service`，非登录用户 `healthapp` 运行 |
| 后端监听 | `127.0.0.1:9089/ncd-ai` |
| 数据库监听 | `127.0.0.1:3306`；MySQL X 协议关闭 |
| 环境文件 | `/etc/chronic-health/backend.env`，root 所有，0600 |
| Swap | 2 GiB，swappiness=10 |

Docker Hub 连接超时，且原开发 Compose 锁定的是本机 ARM 镜像，因此本次使用官方 MySQL APT 仓库中的 x86_64 8.4.11。安装过程中校验了签名仓库元数据，未绕过签名校验。安装过的 Docker/containerd 已停止并禁用自启动，本部署不依赖容器。

本机数据库未导入云端。云端新建数据库并执行 V1–V11 迁移，由 DEMO 初始化器创建虚构示例，再由验收脚本产生测试方案、任务及反馈。数据库及三个 DEMO 身份使用独立随机凭证；AI 配置通过 SSH 加密上传，未提交 Git。

## 访问方式

已配置 Nginx 公网 HTTPS 入口，后端及数据库仍保持回环绑定。公网基址为 `https://120.26.14.242/ncd-ai`，在线文档为 `/ncd-ai/doc.html`，OpenAPI 为 `/ncd-ai/v3/api-docs/health`。前端伙伴无需 SSH；交接见 [云端联调说明](FRONTEND-CLOUD-HANDOFF.md)。正式登录尚未实现，继续使用三个独立的虚构 DEMO 身份。

Nginx 监听 80/443：80 仅服务 ACME 校验并重定向到 HTTPS，443 反向代理后端；Actuator 不向公网开放。按已有伙伴地址及本地开发端口配置 CORS 白名单，业务请求继续由后端验证 Token。单 IP 限流 10 请求/秒、突发 40，请求体上限 1 MiB，最多 20 个并发连接。

Certbot 5.8.0 安装在 `/opt/certbot`。Let's Encrypt IP 证书初次到期时间为 2026-10-25；`chronic-health-cert-renew.timer` 每天检查两次，并通过 deploy hook 重载 Nginx。IP 证书机制见 [Let's Encrypt 官方说明](https://letsencrypt.org/2026/03/11/shorter-certs-certbot)。配置文件在 `deploy/aliyun/nginx-https.conf` 和 `chronic-health-cert-renew.*`。

自动续期演练已通过，包含 Nginx 配置检查及 reload hook。首次演练遇到 CA staging `serverInternal` 二次校验错误，重试成功，未跳过证书验证。定时器当前 active；下一次检查为 2026-10-19 00:03（北京时间，实际时间含随机延迟）。

管理员在本机建立隧道（已存在同端口隧道时不重复启动）：

```bash
ssh -o ServerAliveInterval=30 -o ExitOnForwardFailure=yes \
  -N -L 127.0.0.1:19089:127.0.0.1:9089 root@120.26.14.242
```

连接期间使用：

- 基址：`http://127.0.0.1:19089/ncd-ai`
- Knife4j：`http://127.0.0.1:19089/ncd-ai/doc.html`
- OpenAPI：`http://127.0.0.1:19089/ncd-ai/v3/api-docs/health`

Apifox 可建立“云端联调”环境使用公网 HTTPS，或为运维另建“云端 SSH 隧道”环境，不覆盖本地 9089 环境。原始 Authorization Token 从云端环境文件对应字段读取；本机私有副本为 `.dev/aliyun/backend.env`，不要填入共享值或提交 Git。前端专用私密交接文件为 `.dev/aliyun/frontend-handoff.private.md`，只含 API 地址及三个 DEMO Token，不包含 SSH、数据库或 DeepSeek 密钥。

当前沿用代码中仅在 `local` profile 生效的 DEMO 身份，Java 保持 loopback，由 Nginx 对外提供 HTTPS；这不代表真实登录已完成。启用 AI 的生成会调用外部 DeepSeek API，但候选仍需人工采用、审核。

## 小内存设置

配置源文件位于 `deploy/aliyun/`：

- Java：堆 128–384 MiB，Metaspace 上限 192 MiB，Direct Memory 上限 64 MiB，Serial GC；systemd 内存上限 768 MiB。
- Tomcat：最多 32 工作线程，100 连接；Hikari 最多 4 数据库连接。
- MySQL：128 MiB buffer pool，20 连接，关闭 Performance Schema；systemd 内存上限 600 MiB。
- 本机编译后上传 JAR，服务器不运行 Maven。

启动后一次观测：Java 约 311 MiB、MySQL 约 229 MiB。完成验收并重启后系统可用内存约 758 MiB，Swap 使用 0。该结果仅代表当前低负载演示，未做并发压力测试。

## 管理和更新

服务器命令：

```bash
systemctl status chronic-health mysql
journalctl -u chronic-health -n 100 --no-pager
systemctl restart chronic-health
curl -fsS http://127.0.0.1:9089/ncd-ai/actuator/health
free -m
systemctl show chronic-health mysql -p MemoryCurrent -p MemoryPeak -p NRestarts
```

更新前在本机运行 `./scripts/test-db.sh`。上传新 JAR 到临时文件，核对 SHA-256，备份数据库及旧 JAR后，停止后端、替换 JAR并恢复 `root:healthapp` / 0640 权限，再启动和验收。应用启动会显式执行 Flyway；数据库变更不可简单靠回退 JAR 撤销。

## 备份

每日北京时间 03:15 附近由 `chronic-health-backup.timer` 执行，保存在服务器 `/var/backups/chronic-health/`，权限 0600。首次完整备份已生成并恢复到独立验证库；核对方案 4/4、执行任务 10/10 一致后删除了临时验证库。

```bash
systemctl start chronic-health-backup.service
systemctl list-timers chronic-health-backup.timer
ls -lh /var/backups/chronic-health/
```

备份暂不自动清理，需要监测磁盘空间。当前只有同机备份，尚无离机备份：下载完整数据库备份的额外操作被自动审批拒绝，未执行；后续须明确授权导出范围与目的地。同机备份不能防止整机/磁盘丢失。

## 验收结果

- 本机真实 MySQL 回归：169 项通过，0 失败、0 跳过，JAR 打包成功。
- 云端 MySQL V1–V11 迁移成功，健康检查 UP。
- 云端验收：41 次 HTTP 请求通过，包含五个可执行维度的方案编辑、条件确认、一审、方案派生任务、排程、二审、发布、今日任务、打卡、更正，以及 401/403/409 和幂等重试。
- 七维结构中用药和中医缺少专业来源，保持关闭；不宣称所有七维规则均已验证。
- 真实 DeepSeek 生成候选成功并采用到草稿；不等于临床内容质量已验收。
- 文档暴露 41 个接口操作；41 次请求并不等于全部 41 个不同接口覆盖。
- MySQL 和后端重启后，已发布任务及打卡更正仍可读取；两服务均已设置开机启动。
- 备份压缩校验与独立库恢复通过。

验收脚本 `deploy/aliyun/smoke.py` 会新增虚构业务记录，可复跑：

```bash
python3 deploy/aliyun/smoke.py --env .dev/aliyun/backend.env
# 增加一次真实 AI 生成（消耗 API 额度）：
python3 deploy/aliyun/smoke.py --env .dev/aliyun/backend.env --ai
```

公网网关验收：34 次业务请求通过，另有 8 项 CORS/鉴权/文档/Actuator 隔离检查通过，使用正常证书校验，不走 SSH 隧道。

```bash
python3 deploy/aliyun/smoke.py --env .dev/aliyun/backend.env \
  --base https://120.26.14.242/ncd-ai --public \
  --report .dev/aliyun/public-smoke-report.json
```

本机脱敏结果：`.dev/aliyun/smoke-report.json`、`public-smoke-report.json`、`gateway-report.json`。未部署模拟前端、未完成伙伴页面联调、未压测。仓库只保存部署配置模板及说明，实际凭证和测试报告保留在忽略目录中。
