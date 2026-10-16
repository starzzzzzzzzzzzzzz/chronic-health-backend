# 第一批患者资料与方案草稿接口

2026-10-16。当前实现“患者资料查询 → 手工创建周期方案草稿 → 编辑 → 保存 → 重新读取”。一审、真实 AI、任务生成、排程、二审、发布和打卡尚未实现。

## 启动与身份

```bash
./scripts/db.sh up
SERVER_PORT=9089 ./scripts/run-demo.sh
```

本机联调基址：`http://127.0.0.1:9089/ncd-ai`。9089 是本次验证端口，可通过 SERVER_PORT 调整。run-demo.sh 保持 local profile，显式开启 DEMO 身份，并首次生成 `.env.demo`，其权限为 600、已被 Git 忽略。不要把该文件或凭证提交仓库。原 run.sh 不开启 DEMO 认证；非 local profile 即使配置 demo=true 也不会接受开发 Token。

在本机另一个终端加载凭证并查询：

```bash
source .env.demo
curl -H "Authorization: $HEALTH_DEMO_API_TOKEN" \
  http://127.0.0.1:9089/ncd-ai/patients
```

Authorization 使用原始 Token，无 Bearer 前缀。主体和范围由服务端固定为 `demo-clinician / demo-org / DEMO`；请求不能自行切换机构、角色或 REAL 数据。此凭证仅用于本地 DEMO 联调，不是已完成的真实登录系统。跨机器代理和正式认证另行配置。

初始化器仅首次创建一位完全虚构的患者、六方面示例资料、读患者和编辑方案的授权。再次启动不覆盖患者资料，也不会把已撤销的授权自动补回。

## 已实现资源

| 方法 | 路径 | 说明 | 成功状态 |
|---|---|---|---|
| GET | /patients?page=1&size=20&q=... | 当前身份可读取的患者列表 | 200 |
| GET | /patients/{id}/health-summary | 六方面资料摘要、事实来源、缺项与冲突 | 200 |
| POST | /care-plans | 手工创建方案身份、首个草稿版本及输入快照 | 201 + Location |
| GET | /plan-versions/{id} | 读取草稿内容和创建时的输入快照 | 200 |
| PATCH | /plan-versions/{id} | 修改可编辑草稿；校验预期修订 | 200 |

列表 size 为 1..100，page 从 1 开始。q 最长 100 字符，按姓名字面子串查询；百分号/下划线不作为通配符。排序为 created_at DESC,id DESC。

JSON 包装统一为 `{ "code": 200, "message": "success", "data": ... }`；创建时 code=201。ID、rowVersion、contentRevision、profileRevision 都是字符串。分页 total 是数量，不是业务 ID。

## 创建草稿

先读患者摘要中的 patient.id 和 profileRevision，再提交：

```json
{
  "patientId": "00000000-0000-4000-8000-000000000001",
  "expectedProfileRevision": "1",
  "title": "演示周期方案",
  "startDate": "2026-10-17",
  "endDate": "2026-10-23",
  "zoneId": "Asia/Shanghai",
  "primaryGoal": "演示：建立规律作息"
}
```

以上 patientId 是默认虚构患者的本地编号，profileRevision 必须以实际读取为准。成功返回 planId、versionId、rowVersion="0"、contentRevision="0"、inputSnapshotId，以及完整草稿；Location 指向 `/ncd-ai/plan-versions/{versionId}`。

患者资料已更新时返回 409，前端应重新读取资料；不能把 expectedProfileRevision 直接固定为 1。创建是一次明确的手工形成草稿输入动作，此时保留资料值及来源快照；普通 GET 不创建快照。主目标可为空，其他必填字段仍需有效，这只是草稿保存，不表示可以送审。

## 更新草稿

示例中的 factId 应替换为该草稿 inputSnapshot.facts 内实际可用的事实 ID。不要用其他患者事实或实时新增但不在本快照中的事实替代。

```json
{
  "expectedRevision": "0",
  "primaryGoal": "演示：建立规律作息",
  "modules": [
    {
      "moduleCode": "B0",
      "selectionReason": "用于演示作息目标",
      "factIds": ["替换为本次快照事实ID"]
    }
  ],
  "dimensions": [
    {"dimensionCode": "routine", "decision": "ENABLED", "sortOrder": 0}
  ],
  "items": [
    {
      "dimensionCode": "routine",
      "title": "作息记录候选",
      "content": "虚构演示候选，尚未审核或发布",
      "executionGoal": true,
      "actorType": "PATIENT",
      "isSelected": true,
      "sortOrder": 0,
      "basis": [
        {"moduleCode": "B0", "factId": "替换为本次快照事实ID"}
      ]
    }
  ]
}
```

PATCH 规则：

- expectedRevision 对应最近响应的 rowVersion，必填。陈旧值返回 409，前端保留未保存输入并提示重新载入，不能静默覆盖。
- 未提供的字段保持原状；提供的集合整体替换。空数组表示清空该集合，集合不接受 null。若删除模块/维度，需同时调整引用它们的条目/依据，否则整个请求 400。
- title、周期和 zoneId 不可置 null；primaryGoal、goalBasis、supplementalRequirements、changeReason 可明确置 null 清空。
- 服务端生成新子项 ID。更新已存在条目时带回响应中的 id；不能使用别版本或别患者子项 ID。模块/维度按业务编码匹配并保留原 ID。
- 所有内容在写入前完成结构和关联校验；随后在同一事务保存父子表和审计。任一步失败均回滚。
- 有实际内容变化时 rowVersion 与 contentRevision 各增 1；只变更 changeReason 时只增 rowVersion；完全相同内容保存为无操作。
- 只有 DRAFT 可修改。本批不开放状态字段编辑，也没有“把草稿直接改成 APPROVED”的捷径。
- 本批可保存未审核文字建议；不接受未经绑定的专业参数，也不将参考文献标成已核验。处方、阈值、专业确认接入在后续批次完成。

## 子对象字段

| 集合 | 字段 |
|---|---|
| modules | id（已有时可带回）、moduleCode、selectionReason、factIds |
| dimensions | id、dimensionCode、decision、decisionReason、sortOrder |
| items | id、dimensionCode、title、content、executionGoal、actorType、contraindications、professionalParameters、references、isSelected、sortOrder、basis |
| items[].basis | moduleCode、factId、note |
| managementFocuses | id、diagnosisFactId、primarySubtypeCode、secondarySubtypeCodes、priorityCode、dimensionWeights、contributionBps、adjustmentReason |

模块代码：B0/L/R/C/F/A。维度：medication/diet/exercise/routine/monitoring/tcm/other。维度决定：ENABLED/CLOSED/PENDING，后两者必须填写原因；启用条目必须属于 ENABLED 维度。执行主体：PATIENT/CAREGIVER/PROFESSIONAL/SHARED。

managementFocuses 只能绑定输入快照内已由专业人员确认的 diagnosis 来源事实。七维权重若提供，必须覆盖七维且整数基点总和 10000；多病种 contributionBps 若启用，每条都需提供且合计 10000。代码仅保存并校验结构，不计算医学默认值，也不自行选择真实亚型或优先级字典。

## 读取结果和错误

CarePlanDraftVO 返回 planId、versionId、versionNo、patientId、status、rowVersion、contentRevision、标题/周期/时区/目标、各集合、contentHash、创建时的 inputSnapshot 和 updatedAt。即使患者资料此后变化，旧草稿的 inputSnapshot 保持原样；它不是实时资料接口。

| HTTP 状态 | 含义 |
|---|---|
| 400 | 参数、枚举、时间、集合结构、权重或来源引用不合法 |
| 401 | 无有效身份凭证 |
| 403 | 同机构/模式的患者存在，但缺少有效读写能力 |
| 404 | 资源不存在或不在机构/数据模式范围内 |
| 409 | 修订陈旧、非草稿状态、资料修订变化或数据库引用冲突 |
| 500 | 未预期服务端错误；不回传 SQL、凭证或患者资料 |

响应返回 X-Request-Id，前端可提供自己的请求追踪号。该编号不是发布幂等键；正式发布将在后续批次单独设计。

## 已验证范围

2026-10-16：迁移 V2/V3 已在独立开发 MySQL 执行；真实 HTTP 在 9089 上验证了 5 个接口、创建 201/Location、401 和陈旧修改 409。自动测试覆盖范围查询、未知项/冲突、快照、并发保存、非法来源、审核后拒改、JSON 稳定摘要、DEMO 幂等初始化及事务回滚。

最终验证：`./scripts/test-db.sh` 运行 28 项测试全部通过；普通 `./scripts/mvn.sh -B -ntp verify` 执行 6 项、跳过 22 项真库测试并打包成功。独立审查发现的 null 依据参数问题已回归验证为 HTTP 400；默认非 DEMO 模式真实 HTTP 验证为 401。
