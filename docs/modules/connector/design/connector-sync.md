# 连接器管理与同步引擎设计

## 1. 业务流程

### 1.1 连接器生命周期

```mermaid
stateDiagram-v2
    [*] --> ACTIVE: 创建（enabled=true，默认）
    [*] --> PAUSED: 创建（enabled=false）
    ACTIVE --> PAUSED: PATCH enabled=false
    PAUSED --> ACTIVE: PATCH enabled=true
    ACTIVE --> ERROR: 同步失败回写 last_error_code
    ERROR --> ACTIVE: 下一轮同步成功 / PATCH enabled=true
    ACTIVE --> REVOKED: DELETE（软删 del_flag=1）
    PAUSED --> REVOKED: DELETE
    note right of REVOKED
        终态：列表不可见
        级联取消 QUEUED/RUNNING 任务
    end note
```

- 创建/更新均经 `ConnectorConfigRules`：provider_key 白名单 + 按类型的 config 必填字段
  （如 sharepoint 需 `siteUrl`、confluence 需 `baseUrl`+`space`）。
- 删除为两段式：先条件取消未完结同步任务（`QUEUED/RUNNING → CANCELLED`），再把连接器
  置 `REVOKED` 并软删；`source_object` 明细保留供审计对账。

### 1.2 连通性校验（validate）

```mermaid
flowchart LR
    A[POST /validate] --> B{provider_key 白名单<br/>+ 必填字段}
    B -- 缺失 --> X[ok=false + 字段名]
    B -- 通过 --> C{适配器已注册?}
    C -- 否 --> Y[ok=false 暂未接入适配器<br/>fail-closed 不假报成功]
    C -- 是 --> D[适配器真实探测<br/>目录存在 / 索引可达]
    D -- 异常 --> Z[ok=false + 原因]
    D -- 通过 --> E[ok=true 校验通过]
```

### 1.3 同步任务（受理 → 执行 → 终态）

```mermaid
stateDiagram-v2
    [*] --> QUEUED: POST /sync（202 + Task 登记）
    QUEUED --> RUNNING: 调度器认领（条件更新）
    QUEUED --> CANCELLED: 用户取消 / 连接器删除
    RUNNING --> CANCELLED: 用户取消（终态写回前校验，不被覆盖）
    RUNNING --> SUCCEEDED: 全部对象处理成功
    RUNNING --> PARTIAL: 存在失败对象（明细入 error_detail）
    RUNNING --> FAILED: 发现阶段失败 / 适配器缺失
```

## 2. 数据流转（同步一轮的完整链路）

```mermaid
flowchart TB
    subgraph 受理[同步受理 - ConnectorServiceImpl]
        U[用户 POST /connections/id/sync] --> V{连接器 ACTIVE?}
        V -- 否 --> ERR1[409 冲突]
        V -- 是 --> W{适配器已注册?}
        W -- 否 --> ERR2[400 暂未接入同步适配器]
        W -- 是 --> J[(sync_job 插入<br/>QUEUED + cursor_before)]
        J --> T[TaskService 登记 202 任务<br/>resourceType=SYNC_JOB]
    end

    subgraph 执行[后台执行 - SyncJobDispatchScheduler]
        S1[每 sync-interval-ms 轮询<br/>QUEUED 任务 LIMIT 3] --> S2[认领: 条件更新 QUEUED→RUNNING]
        S2 --> S3[加载 source_connection<br/>按 provider_key 路由适配器]
        S3 --> S4[适配器 discover<br/>目录扫描 / 索引 GET]
        S4 --> S5[逐对象 upsert source_object<br/>按 tenant+connection+external_id]
        S5 --> S6{job_type = FULL?}
        S6 -- 是 --> S7[消失对象置墓碑<br/>last_sync_job_id != 本轮 且未墓碑]
        S6 -- 否 --> S8
        S7 --> S8[终态写回: 条件 status=RUNNING<br/>SUCCEEDED / PARTIAL + 五项计数]
        S8 --> S9[连接器回写<br/>last_success_at / last_error_code]
    end

    subgraph upsert分支[单对象判定]
        B1{清单行存在?} -- 否 --> C1[insert → created++]
        B1 -- 是 --> B2{源侧墓碑?} -- 是且未墓 --> C2[tombstoned=true → deleted++]
        B2 -- 否 --> B3{版本/地址/摘要变化?} -- 是 --> C3[update → updated++]
        B3 -- 否 --> C4[仅续 last_seen → skipped<br/>无表列 仅日志]
    end

    T -.-> S1
    S5 -.-> upsert分支
```

要点：

- **增量模型**：`uq_source_object_external (tenant_id, connection_id, external_id)` 是
  upsert 定位键；`source_version`（本地目录为 size+mtime 摘要，HTTP 索引为清单声明的
  version/etag）驱动 updated/skipped 分支。
- **删除收敛**：两条路径——源侧显式墓碑（HTTP 索引 `deleted:true`）直接落墓碑；
  FULL 全量对账把「本轮未 seen」的存量行置墓碑（快照式源的删除语义）。
- **取消一致性**：取消用条件更新（`status IN (QUEUED,RUNNING)`），执行器终态写回用
  条件更新（`status=RUNNING`），二者互斥——RUNNING 中被取消的任务不会被执行器覆盖回成功。
- **skipped 计数**：DDL `sync_job` 只有 discovered/created/updated/deleted/failed 五列，
  skipped（无变化对象）以日志汇总输出，不落库（表结构限制）。
- **游标**：`cursor_before` 记录同步前水位，快照式源（目录/静态索引）不推进游标，
  `cursor_after` 原样回填；增量型源（Graph API deltaLink 等）接入时再实现推进。

## 3. 适配器扩展指南（新增一个 ContentConnectorPort 实现）

以新增 `sharepoint` 为例：

1. **实现适配器**：在 `modules/connector/adapter/` 新建 `SharePointConnector`
   （继承 `AbstractContentConnector` 复用 config 加载，或直接实现接口）：

   ```java
   @Component
   @ConditionalOnProperty(name = "ragkb.db.enabled", havingValue = "true")
   public class SharePointConnector extends AbstractContentConnector {
       public SharePointConnector(ObjectMapper om, SourceConnectionMapper m) {
           super(om, m, "sharepoint");          // provider_key 路由键
       }
       @Override public void validate(Map<String, Object> config) { /* Graph API 探测 */ }
       @Override public List<SourceObject> discover(TenantId t, long id, String cursor) { /* 分页拉取 */ }
       @Override public void health(TenantId t, long id) { validate(parseConfig(requireConnection(id))); }
   }
   ```

2. **登记规则**：在 `ConnectorConfigRules.REQUIRED_FIELDS` 补充该类型的必填字段
   （sharepoint 已登记 `siteUrl`），单测 `ConnectorConfigRulesTest` 同步覆盖。
3. **无需改动 Service/调度器**：`ConnectorServiceImpl` 与 `SyncJobDispatchScheduler`
   都按 `providerKey()` 自动路由，注册即生效（重复 provider_key 启动即失败，fail-fast）。
4. **安全红线**：
   - 凭证（密码/token/OAuth secret）不落日志、不回显异常消息；
   - Web URL 遵循 SSRF 防护（参考 `HttpIndexConnector`：scheme 白名单 + 私网默认拒绝）；
   - OAuth 回调不得暴露第三方 token（06-架构方案 §2.3），secret 建议走 `secret_ref` 机制；
   - `discover` 返回量设上限（防超大源拖垮调度线程），单对象失败在调度器侧计数不中断。
5. **增量语义**：能提供游标（deltaLink/nextToken）的源在 discover 中消费 `cursor`
   参数并把新游标经返回值或回写通道交给调度器（当前接口为快照式，游标推进需扩展
   Port 返回结构——建议返回 `record DiscoverResult(List<SourceObject> objects, String nextCursor)`）。

## 4. 配置项

| 配置 | 默认 | 说明 |
| --- | --- | --- |
| `ragkb.connector.sync-interval-ms` | 5000 | `sync_job` 队列轮询周期（环境变量 `RAGKB_CONNECTOR_SYNC_INTERVAL_MS`） |
| `ragkb.db.enabled` | true | 适配器/调度器装配开关（false 时模块端点报「数据访问未启用」） |

## 5. 遗留边界（fail-closed TODO）

| 项 | 现状 | 说明 |
| --- | --- | --- |
| source_object → document 转换 | 未实现 | document 模块无「按 source_object 建档」端口；本轮只维护源侧清单，不假报入库 |
| sharepoint / confluence / s3 SDK | 未接入 | 校验/同步明确报「暂不支持」，不假报成功 |
| secret 管理 | config 明文落库 | `secret_ref` 列已预留，待 secret 模块（KMS/加密存储）接入后迁移凭证 |
| SCHEDULED / WEBHOOK 同步模式 | 仅 MANUAL | `sync_mode` 枚举已留，定时调度与 webhook 触发留待接线 |
| 失败对象自动重试 | 无 | 建议 RECONCILE 任务驱动重放；error_detail.failedObjects 已留明细 |
| 生产级 SSRF | 最小实现 | 私网默认拒绝 + 显式放行；DNS rebinding/重定向审计待加固 |
| rootPath 白名单 | 无 | local_directory 可扫任意路径，生产应限制根目录范围 |
