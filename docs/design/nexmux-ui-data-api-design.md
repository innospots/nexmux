# Nexmux UI 数据接口设计（BFF 适配层 + 后端契约补全）

## 1. 文档定位

- **状态**：草案（grill-me 两轮已确认，待评审）
- **日期**：2026-10-05
- **归属模块**：`nexmux-kernel`、`nexmux-spring`、`nexmux-ui`
- **上级文档**：[nexmux-architecture-design.md](./nexmux-architecture-design.md)、[nexmux-p0-console-design.md](./nexmux-p0-console-design.md)
- **关联文档**：[nexmux-domain-layer-design.md](./nexmux-domain-layer-design.md)、ADR-[0001](./adr/0001-console-runtime-configuration-boundary.md)
- **页面事实源**：`nexmux-ui/src/server/yaml/**`（35 页，grill-me Q2 确认）

## 2. 背景与目标

`nexmux-ui`（Next.js 16 + pactor-app YAML DSL）当前全部数据来自 `src/pactor/mocks/*`（12 个 mock，约 5.8k 行）。后端 `nexmux-kernel` 已提供 6 组 JAX-RS 端点（`/api/nexmux/**`，`R<T>` 包装），但与前端 DSL 绑定的数据形状差距大，且 Dashboard / 用量 / 成本 / 客户端模板等页面无后端服务。

**目标**：让 35 个前端页面全部接真实后端（grill-me Q4 确认），且 **35k 行 YAML 的 `${data.*}` 绑定路径零改动**。

**成功标准**：

1. `src/pactor/services.ts` 注册表中每个 handler 由 mock 实现切换为 BFF 真实实现，签名不变；
2. 读接口（列表/详情/聚合）与写接口（新建/编辑/启停/发布/轮换/调试调用）全部走通；
3. 切换可按服务逐个灰度（同一注册表内 mock 与真实并存）；
4. 后端不出现「UI 专用形状 VO」双轨 API。

## 3. 不在本方案中的能力

| 排除项 | 理由 |
|--------|------|
| sitemap / 页面 DSL 来源切换 | UI 本地 YAML 与 kernel `ui-pages/nexmux/**` 双轨问题另案处理（grill-me Q2） |
| UI 登录体系改造为 Nexus Console 会话 | 列入 §15 开放问题；本期 BFF 持服务账号凭证（Q3-A） |
| Runtime（inbound）请求处理链路实现 | ADR-0001 边界；本方案只定义 `mux_request_log` 的**写入字段契约**供 Runtime 遵循 |
| 多租户 / Workspace 隔离 | 与 PRD §3.7 一致，平台域，无隔离列 |
| 条件路由 / 灰度 / Failover 调度逻辑 | P0 不做，UI 相应字段只读展示 |
| WebSocket / SSE 实时推送 | Dashboard「实时 RPM/TPM」由 stats 接口按近 1 分钟窗口计算，轮询刷新 |

## 4. 归属与词汇（四步法 ①②）

| 概念 | 英文名 | 归属模块/包 | 技术 ID | 稳定业务键 | 备注 |
|------|--------|------------|---------|-----------|------|
| BFF 适配层 | backend adapter | `nexmux-ui` / `src/pactor/backend/` | — | — | TypeScript，非 Maven |
| 服务注册表 | service registry | `nexmux-ui` / `src/pactor/services.ts` | 服务名如 `route.list` | — | mock/真实切换点 |
| 提供方 | Provider | kernel `core/provider` | `provider_id` | `provider_code` | 已有 |
| 逻辑模型 | Model | kernel `core/model` | `model_id` | `model_code` | 已有 |
| 路由 | Route | kernel `core/route` | `route_id` | **`route_code`（新增列）** | 前端 URL 用 code；`alias` 为对外模型名，不可当主键用 |
| 客户端 | Client | kernel `core/access` | `client_id` | `client_code` | 已有 |
| 客户端模板 | ClientTemplate | kernel `core/access` | `template_id` | `template_code` | 表已建，缺 service/endpoint |
| 请求日志 | RequestLog | kernel `core/ops` | `request_id` | `request_id` | 扩列，见 §6.1 |
| 成本记录 | CostRecord | kernel `core/ops`（视图） | 与成功请求日志同 `request_id` | 同左 | **不建独立表**（Q8） |
| 统计聚合 | Stats | kernel `core/stats`（**新包**） | — | — | Dashboard / 用量 / 可用性 |
| 配置审计 | ConfigAuditLog | kernel `core/audit` | `audit_id` | `audit_id` | 已有 |
| 调试调用 | DebugInvoke | kernel `core/debug` | `request_id` | — | 已有 |

**词汇对齐**：前端列表项的 `code` 一律映射后端稳定业务键（`*_code` 列）；前端 `displayName/name` 映射 `display_name`；前端预计算文案（`typeLabel/entrySummary/...`）属展示层产物，**只存在于 BFF 适配器**，不进后端 VO。

## 5. 工程模块与 DDD 边界（四步法 ①③）

### 5.1 Maven 模块归属

| 有界上下文 / 能力 | Owning artifact | 依赖（仅直接） | 新建模块？ |
|------------------|-----------------|----------------|-----------|
| 统计聚合（stats） | `nexmux-kernel` | `innospots-nexus-platform`（既有） | 否，新 Java 包 |
| 客户端模板服务 | `nexmux-kernel`（`core/access` 内扩展） | 同上 | 否 |
| 端点补全（provider/model/route/client/ops/debug） | `nexmux-kernel`（`console/endpoint` 内扩展） | 同上 | 否 |
| `mux_request_log` 扩列 DDL | `nexmux-spring`（`nexus/schema/nexmux-h2.sql`） | — | 否 |
| BFF 适配层 | `nexmux-ui`（非 Maven） | 无新 npm 依赖（用原生 fetch） | 否 |

### 5.2 包结构

**nexmux-kernel 新增/扩展**（领域优先，沿用既有 `core/<domain>/{dao,service,domain/{entity,vo,request,value}}` 结构）：

```text
com.innospots.nexmux.core
├── stats                          # 新增包
│   ├── dao/StatsQueryDao.java             # 基于 mux_request_log 的聚合查询（单表）
│   ├── service/DashboardService.java
│   ├── service/UsageStatsService.java     # model/provider/client 三维度
│   ├── service/AvailabilityService.java
│   └── domain/vo/{DashboardVo, UsageRowVo, UsageSummaryVo, AvailabilityVo, ...}
├── ops                            # 扩展
│   ├── service/CostQueryService.java      # 成本视图（复用 RequestLogDao）
│   └── domain/vo/CostRecordVo.java        # + RequestLogVo 扩字段
├── access                         # 扩展
│   ├── service/ClientTemplateService.java
│   └── domain/{request/ClientTemplate*Request, vo/ClientTemplateVo}
└── route / provider / model       # 扩展：详情 VO 富字段 + 筛选 Request
com.innospots.nexmux.console.endpoint
├── stats/StatsEndpoint.java       # 新增（5 方法）
├── client/ClientTemplateEndpoint.java  # 新增（与 ClientEndpoint 分列，控制单端点 ≤7 方法）
└── provider|model|route|ops|debug # 扩展方法与筛选参数
```

**nexmux-ui 新增**：

```text
src/pactor/backend/
├── client.ts          # fetch 封装：baseUrl、服务账号凭证、R<T> 解包、错误规范化
├── config.ts          # NEXMUX_BACKEND_* 环境变量读取（服务端 only）
├── adapters/          # 每域一个：后端 VO → UI 契约形状（预计算文案在此）
│   ├── provider-adapter.ts / model-adapter.ts / route-adapter.ts
│   ├── client-adapter.ts / client-template-adapter.ts
│   ├── call-log-adapter.ts / cost-adapter.ts / audit-adapter.ts
│   ├── stats-adapter.ts / debug-adapter.ts
└── services-real.ts   # 与 services.ts 同签名的真实实现注册表
```

单包类数均 ≤15；`adapters/` 超 15 时按域再分目录。

### 5.3 Session 与持久化作用域

- BFF 每个 HTTP 请求一次后端调用（或少数几次聚合），**无跨请求服务端状态**；缓存一律不做（P0），`cache-control: no-store` 沿用。
- 后端聚合查询均为只读 Snapshot 语义，按 `requested_at` 区间扫描 `mux_request_log`。
- Entity 基类沿用既有（审计四列 `created_at/updated_at/created_by/updated_by` 已在表中），无新增实体类型。

## 6. 领域模型（四步法 ③④）

### 6.1 实体（Entity）

**无新增表**。一张扩表 + 一张加列：

**(a) `mux_request_log` 扩列**（nexmux-h2.sql，全部 `ALTER`/新列可空，向后兼容）

| 列 | 类型 | 用途（前端来源） |
|----|------|------------------|
| `app_key_id` | VARCHAR(64) | 调用日志筛选 appKey / 客户端用量 |
| `user_email` | VARCHAR(128) | 筛选 email |
| `provider_code` | VARCHAR(64) | 筛选 + 供应商用量/可用性聚合 |
| `protocol` | VARCHAR(32) | 筛选 |
| `route_group` | VARCHAR(64) | 筛选 routeGroup |
| `application` | VARCHAR(128) | 成本筛选 application |
| `alias` | VARCHAR(64) | 成本筛选 alias |
| `http_status` | INT | 详情 httpStatusText |
| `input_tokens` / `output_tokens` | BIGINT | 用量/成本/详情 |
| `cache_read_tokens` / `cache_write_tokens` | BIGINT | 成本缓存费用 |
| `cost_usd` / `billing_usd` | DECIMAL(18,8) | 成本与 Dashboard cost 聚合 |
| `ttft_ms` | BIGINT | 可用性 TTFT 聚合 |
| `upstream_model` / `upstream_endpoint` | VARCHAR(256/1024) | 详情上游信息 |
| `upstream_status` / `upstream_latency_ms` | VARCHAR(16)/BIGINT | 详情 + 可用性 |
| `retries` | INT | 详情 |
| `client_ip` / `ingress_method` / `ingress_path` | VARCHAR | 详情上下文 |
| `trace_id` | VARCHAR(64) | 详情 |
| `error_type` | VARCHAR(64) | 详情 error.type |
| `pricing_snapshot` | CLOB | 成本档位/倍率计算快照（价格版本、命中档、倍率） |
| `prompt_snapshot` | CLOB | 详情 prompt/response 留存（受保留策略截断） |

索引：`idx_mux_request_log_time (requested_at)`、`idx_mux_request_log_model_time (model_code, requested_at)`、`idx_mux_request_log_provider_time (provider_code, requested_at)`、`idx_mux_request_log_client_time (client_id, requested_at)`——聚合区间扫描用。

**(b) `mux_route` 加列**：`route_code VARCHAR(64)` + `uk_mux_route_code`——前端 URL 稳定业务键（grill-me §4 词汇表）。

| 实体 | 表名 | 主键 | 稳定业务键 | 新增索引 | 备注 |
|------|------|------|-----------|----------|------|
| RequestLogEntity | `mux_request_log` | `request_id` | 同主键 | 上表 4 个 idx_ | 扩列，字段映射同步扩 |
| RouteEntity | `mux_route` | `route_id` | `route_code`（新） | `uk_mux_route_code` | 加列 |

### 6.2 枚举（Enums）

后端已有状态字符串列（`status VARCHAR(16)`）。本方案**不新增领域枚举类**，沿用各域既有状态值；BFF 负责后端状态值 → 前端枚举的映射（如后端 `PUBLISHED` → 前端 `enabled`，具体映射表在适配器内实现并排契约测试覆盖）。

### 6.3 Request / VO（record 骨架，后端）

```java
// core/stats/domain/vo —— 聚合结果（原始数值，展示格式化在 BFF）
public record DashboardVo(RealtimeStats realtime, OverviewStats stats,
                          List<TrendPoint> trend, List<DistributionItem> models,
                          List<DistributionItem> clients) {}
public record UsageRowVo(String key, String displayName, long requests,
                         long inputTokens, long outputTokens, long cacheReadTokens,
                         BigDecimal standardCost, BigDecimal billedCost, BigDecimal cost,
                         double successRate, double avgLatencyMs, double avgTtftMs,
                         double avgUpstreamMs, double tps, double failoverRate, double avgAttempts) {}
public record AvailabilityVo(List<ProviderQualityRow> providers,
                             List<ModelProviderCompareRow> compare,
                             List<RecentErrorRow> errors) {}

// core/ops/domain/vo —— 成本视图 = 成功请求日志 + 定价快照展开
public record CostRecordVo(String requestId, LocalDateTime requestedAt, String application,
                           String clientName, String appKeyId, String alias,
                           String modelCode, String modelName, String providerCode, String providerName,
                           long inputTokens, long outputTokens, long cacheReadTokens,
                           PricingSnapshot pricing) {}

// core/route/domain/vo —— 详情富字段（列表 Vo 不动）
public record RouteDetailVo(String routeId, String routeCode, String alias, String displayName,
                            String status, String type, List<String> tags, String description,
                            String path, String protocol, String operation, String strategy,
                            boolean stickySession, boolean healthFilter,
                            List<RouteModelEntryVo> models,
                            LocalDateTime createdAt, LocalDateTime updatedAt, String operator) {}
```

筛选 Request 骨架（既有 `*PageRequest` 扩字段，保持 record）：

```java
public record RoutePageRequest(Long pageNo, Long pageSize,
                               String status, String type, List<String> tags) {}
public record RequestLogPageRequest(Long pageNo, Long pageSize,
                                    String start, String end, String status, String model,
                                    String provider, String protocol, String routeGroup,
                                    String email, String appKey) {}
public record ClientTemplateCreateRequest(String templateCode, String displayName, ...) {}
```

### 6.4 持久化与 Dao

- 无新表 → 无新 Dao 接口；`RequestLogDao` 扩字段映射；新增 `StatsQueryDao`（`mux_request_log` 单表聚合，一表一 Dao 原则下归属 `core/stats`，仅读）。
- 聚合 SQL 走 MyBatis-Plus `QueryWrapper` / `@Select` 注解，**无 mapper.xml**；配置沿用 yaml + Java config。
- 成本视图**复用** `RequestLogDao` + `pricing_snapshot` JSON 展开，无 join（model/provider 展示名由 service 层按 code 二次单表查询补齐）。

## 7. 接口与 API（四步法 ④）

### 7.1 HTTP 端点（后端补全清单，基路径 `/api/nexmux`，全部 `R<T>` + `@NexusAuthenticatedApi`）

**新增端点**：

| 方法 | 路径 | 响应 `R<T>` | 委托 service |
|------|------|-------------|--------------|
| GET | `/routes/by-code/{routeCode}` | `RouteDetailVo` | RouteService |
| GET | `/providers/by-code/{providerCode}` | `ProviderDetailVo` | ProviderService |
| GET | `/models/by-code/{modelCode}` | `ModelDetailVo` | ModelService |
| GET | `/clients/by-code/{clientCode}` | `ClientDetailVo` | ClientService |
| POST | `/routes/{routeId}/enable` | `RouteVo` | RouteService |
| POST | `/routes/{routeId}/disable` | `RouteVo` | RouteService |
| POST | `/providers/{providerId}/disable` | `ProviderVo` | ProviderService |
| POST | `/models/{modelId}/disable` | `ModelVo` | ModelService |
| PUT | `/models/{modelId}` | `ModelVo` | ModelService（补缺） |
| POST | `/providers/{providerId}/pull-models` | `List<UpstreamModelVo>` | ProviderService |
| GET | `/client-templates` | `PageResult<ClientTemplateVo>` | ClientTemplateService |
| GET | `/client-templates/{templateId}` | `ClientTemplateVo` | 同上 |
| POST | `/client-templates` | `ClientTemplateVo` | 同上 |
| PUT | `/client-templates/{templateId}` | `ClientTemplateVo` | 同上 |
| POST | `/client-templates/{templateId}/enable` | `ClientTemplateVo` | 同上 |
| GET | `/client-templates/{templateId}/created-clients` | `List<ClientVo>` | 同上 |
| GET | `/stats/dashboard` | `DashboardVo` | DashboardService |
| GET | `/stats/model-usage` | `UsagePageVo` | UsageStatsService |
| GET | `/stats/provider-usage` | `UsagePageVo` | 同上 |
| GET | `/stats/client-usage` | `UsagePageVo` | 同上 |
| GET | `/stats/provider-availability` | `AvailabilityVo` | AvailabilityService |
| GET | `/costs` | `PageResult<CostRecordVo>` | CostQueryService |
| GET | `/costs/{requestId}` | `CostRecordVo` | 同上 |
| GET | `/audit-logs/operators` | `List<String>` | AuditQueryService |

**既有端点修改**：

| 端点 | 变更 |
|------|------|
| GET `/providers` `/models` `/routes` `/clients` | PageRequest 增加筛选字段（keyword/status/types/tags 等，见 §6.3） |
| GET `/request-logs` `/audit-logs` | 增加时间区间与多维筛选字段 |
| GET `/request-logs/{id}` | `RequestLogVo` 扩 §6.1(a) 全部详情字段 |
| GET `/audit-logs/{id}` | `AuditLogVo` 增加 `beforeSnapshot`/`afterSnapshot`（diffLines 由 BFF 计算） |
| POST `/providers/{id}/connection-test` | 返回 `R<Boolean>` → `R<ConnectionTestVo{ok,latencyMs,detail}>`（**不兼容变更**，见 §10） |
| GET `/routes/{routeId}` | 返回 `RouteVo` → `RouteDetailVo`（含 models 行与入口/策略字段） |

端点数复审：ClientEndpoint 现有 6 方法，模板端点独立为 `ClientTemplateEndpoint`（6 方法）；StatsEndpoint 5 方法，均未触 7 方法红线。

### 7.2 BFF 接口（nexmux-ui，`/api/data/**` 契约不变）

- **读**：`GET /api/data/<res>` 路由表（`route.ts` 既有三张表）不动，handler 实现换为 `backend/services-real.ts`。
- **写（新增约定）**：`POST/PUT /api/data/<res>[/<code>[/<sub>]]`，body 透传后端请求体；`route.ts` 增加**写映射表**：

| 前端调用 | 后端端点 |
|----------|----------|
| `POST /api/data/providers` | `POST /api/nexmux/providers` |
| `PUT /api/data/providers/{code}` | `PUT /api/nexmux/providers/{id}`（BFF 先 code→id 解析） |
| `POST /api/data/providers/{code}/disable` | `POST .../providers/{id}/disable` |
| `POST /api/data/providers/{code}/connection-test` | `POST .../providers/{id}/connection-test` |
| `POST /api/data/providers/{code}/pull-models` | `POST .../providers/{id}/pull-models` |
| `POST /api/data/routes` / `PUT /api/data/routes/{code}` | `POST/PUT /api/nexmux/routes[/{id}]` |
| `POST /api/data/routes/{code}/publish` | `POST .../routes/{id}/publish` |
| `POST /api/data/routes/{code}/enable\|disable` | 对应端点 |
| `POST /api/data/models` / `POST /api/data/models/{code}/enable\|disable` | 对应端点 |
| `POST /api/data/clients` / `PUT /api/data/clients/{code}` | 对应端点 |
| `POST /api/data/clients/{code}/rotate-app-key` | `POST .../clients/{id}/rotate-app-key` |
| `PUT /api/data/clients/{code}/route-grants` | `PUT .../clients/{id}/route-grants` |
| `POST /api/data/client-templates[/{code}...]` | 对应端点 |
| `POST /api/data/debug/model-invoke` / `client-invoke` | `POST /api/nexmux/debug/model-invoke` / `client-invoke` |

- **BFF→后端鉴权**（Q3-A）：`client.ts` 从服务端环境变量读 `NEXMUX_BACKEND_BASE_URL` / `NEXMUX_BACKEND_USERNAME` / `NEXMUX_BACKEND_PASSWORD`（或 token），登录 Nexus Console 获取会话后带 cookie 调后端；凭证不出服务端。
- **错误映射**：后端 `R` 非 ok → BFF 返回 `{ error: message, advice }` + 对应 HTTP 状态；pactor `message` action 展示。
- **形状适配**：`adapters/*` 负责 ① `R<T>` 解包；② `PageResult` → `{items,total}`；③ counts/filters 由 BFF 拉全量（P0 数据量小，`pageSize=500` 上限自保护）按 mock 语义「构成口径」计算（Q7-A）；④ 全部预计算文案（`typeLabel`/`entrySummary`/`usageText`/千分位等）；⑤ 审计 diffLines 由 before/after 快照计算。

### 7.3 分层调用

```text
浏览器 → Next /api/data（鉴权、路由表）
       → backend/services-real（参数规整）
       → backend/adapters（VO→UI 形状）
       → backend/client（R 解包、错误映射）
       → nexmux-spring JAX-RS endpoint → service → dao   （既有分层不动）
```

禁止：adapter 直写 SQL/跨域组装业务规则（业务规则在后端 service）；`services.ts` 出现后端地址硬编码。

## 8. 失败、状态码与异常

复用既有 `NexmuxStatusCode`（模块码 `NMX`），**本方案不新增状态码**：

| 场景 | StatusCode | 抛出边界 | HTTP |
|------|-----------|---------|------|
| by-code 查询未命中 / 日志·成本·审计详情未命中 | `NMX` RESOURCE_DATA 0001 | service | 404 |
| 创建 provider/model/route/client/template 业务键重复 | DATA_CONSISTENCY 0001 | service | 409 |
| 启停/发布前置检查失败（DependencyImpactAnalyzer 命中） | BUSINESS_RULE 0001 | service | 422 |
| 调试/连接测试上游不可达 | BUSINESS_RULE 0001 | service | 422（结果体含 detail） |
| BFF 调后端 401/403（服务账号失效） | 后端返回原码，BFF 透传 message | client.ts | 502 + `{error}` |
| BFF 后端不可达/超时 | —（非应用语义） | client.ts | 502 + `{error: "后端服务不可用"}` |

调试调用失败**不是异常路径**：`DebugInvokeResultVo` 以 `status/error` 字段返回，HTTP 200。

## 9. 事务、幂等与并发

- 后端写操作事务边界沿用既有 service 层约定（本方案不新增写路径类型）。
- 幂等：`pull-models`、`connection-test`、debug 调用为只读副作用，天然幂等；发布/轮换 AppKey 既有语义（版本号递增）不变。
- BFF 无状态、无锁；同一页面并发 reload 由 pactor 自行去重。
- `mux_request_log` 由 Runtime（inbound）单写者追加，聚合查询只读，无并发冲突。

## 10. 兼容与迁移

| 变更 | 影响面 | 处理 |
|------|--------|------|
| `connection-test` 返回 `R<Boolean>` → `R<ConnectionTestVo>` | 仅 console 内部 API，无外部消费者 | 同一 PR 改端点 + 契约测试；OpenAPI 文档同步 |
| GET `/routes/{routeId}` 返回类型扩为 `RouteDetailVo` | 字段超集（原字段保留），向后兼容 | 无需迁移 |
| `mux_request_log` 扩 25 列 + 4 索引 | H2 `CREATE TABLE IF NOT EXISTS` 不覆盖旧表 | 提供 `ALTER TABLE` 迁移段（nexmux-h2.sql 追加 `ALTER TABLE ... ADD COLUMN IF NOT EXISTS`）；空库直接生效 |
| `mux_route` 加 `route_code` + UK | 存量行需回填 | 迁移段：`route_code` 初值 = `alias`，后续编辑流程保证唯一 |
| 数据库文件 | 本地 `out/db/` 重建亦可接受（开发期） | 文档注明两路径（ALTER 迁移 / 删库重建） |

无双写、无灰度开关；BFF 侧按服务逐个从 mock 切真实，切换即删对应 mock 引用（mock 文件保留至全部切换完成后一次性移除，**删除里程碑：全部页面联调通过的 PR**）。

## 11. 测试范围

**后端（`java:develop` 测试先行）**：

| 类型 | 类名（计划） | 断言要点 |
|------|-------------|---------|
| 端点契约 | `StatsEndpointContractTest` | 5 端点路径、参数、R 包装 |
| 端点契约 | `ClientTemplateEndpointContractTest` | CRUD + enable + created-clients |
| 端点契约 | `RouteEndpointContractTest`（扩） | by-code、enable/disable、筛选参数 |
| 端点契约 | `ProviderEndpointContractTest`（扩） | disable、pull-models、ConnectionTestVo |
| 服务行为 | `DashboardServiceTest` / `UsageStatsServiceTest` / `AvailabilityServiceTest` | 聚合口径（区间、分组、合计行、空区间） |
| 服务行为 | `CostQueryServiceTest` | 成功日志→成本视图、pricing_snapshot 展开、筛选 |
| 服务行为 | `ClientTemplateServiceTest` | CRUD + 业务键重复 + created-clients 关联 |
| Entity 契约 | `RequestLogEntityContractTest`（扩） | 新列映射、表名不变 |
| 状态码契约 | `NexmuxStatusCodeContractTest`（沿用） | 无新增码回归 |

**前端 BFF（vitest 或 node:test，随 `nexmux-ui` 构建）**：

| 类型 | 文件（计划） | 断言要点 |
|------|-------------|---------|
| 适配器黄金测试 | `adapters/*.test.ts`（每域一个） | 后端 VO 样例 → UI 形状与 mock 类型逐字段一致（TS 类型 + 快照） |
| client 测试 | `client.test.ts` | R 解包、错误映射、超时 |
| 路由表测试 | `route.test.ts`（扩） | 写映射表 method/path 解析、code→id 解析调用 |

**不测**：pactor 表达式引擎行为；YAML 渲染（既有页面不动）；Nexus 平台鉴权内部。

## 12. AGENTS 对齐

| 检查项 | 结论 |
|--------|------|
| 归属模块 | `nexmux-kernel`（core/console 扩展 + stats 新包）、`nexmux-spring`（DDL）、`nexmux-ui`（BFF）— 符合根 AGENTS.md 四模块表，**无新增 Maven 模块** |
| 依赖方向 | kernel 不新增 Maven 依赖；BFF 在 UI 内闭环，无 npm 新依赖；不存在 kernel→ui 反向依赖 |
| 是否需修订根 AGENTS.md | 否——四模块职责不变；`core/stats` 为 kernel 内既有 `core` 包下的领域子包 |
| grill-me 结论引用 | 本文档 §2/§3/§4/§7 中标注的 Q1–Q10（2026-10-05 两轮，全部确认） |

## 13. 不建什么（防过度设计）

- **不建**后端「UI 专用形状」聚合端点（Q5-A 确认）：展示文案/counts 全在 BFF，避免后端双轨 API。
- **不建**独立成本表 / 成本域包：成本是成功请求日志 + `pricing_snapshot` 的只读视图（`core/ops` 内）。
- **不建**新 Maven 模块、新 npm 运行时依赖（原生 fetch + 既有 `yaml`/`pactor` 即可）。
- **不建** BFF 缓存层 / 会话存储 / 权限再校验（页面级 permission 沿用 Nexus Console；BFF 只做透传）。
- **不引入** GraphQL / tRPC 等中间层框架；不抽象「通用 adapter 基类」（10 个 adapter 各自具体实现，重复代码可接受）。
- **不做** sitemap 双轨合并、UI 登录改造（§15）。
- mock 文件不做「永久保留为示例」双轨——全量切换后删除（§10 里程碑）。

## 14. 架构约束自检

- [x] 归属模块与业务域正确，stats/cost/template 归属与既有域不混同
- [x] 包结构领域优先，单包 ≤15 类
- [x] 术语在端点/实体/DAO/DB/测试间一致（`*_code` 稳定业务键）
- [x] 技术主键（`*_id`）与稳定业务键（`*_code`）区分；route_code 不可变
- [x] 分层 endpoint→service→dao，无 endpoint→dao
- [x] request/vo 为 record
- [x] 无新接口抽象（StatsQueryDao 为具体类，单消费者 service）
- [x] 端点内聚（Client/ClientTemplate 分列，均 ≤7 方法）
- [x] 失败全部归属既有 `NMX` 状态码 + `NexusException`；BFF 侧无业务异常类型
- [x] 持久化一表一 Dao、无 join/XML/properties
- [x] 测试范围已定义（§11），实现测试先行
- [x] 无「先双轨以后再删」无里程碑项（mock 删除里程碑已写）

## 15. 开放问题

| # | 问题 | 当前默认 | 决策截止 |
|---|------|---------|---------|
| 1 | UI 登录何时切 Nexus Console 会话（Q3-B 正式方案） | BFF 服务账号 | Nexus Console 登录 API 确认后 |
| 2 | kernel `ui-pages/nexmux/**` 与 UI 本地 YAML 双轨归宿 | 本地 YAML 为事实源 | sitemap 方案评审时 |
| 3 | 用量/成本数据从哪来（Runtime 未实现写入） | 聚合接口就绪，数据待 inbound 写入链路落地后自然填充；联调用种子数据 | inbound 设计启动时 |
| 4 | counts/filters 数据量增长后是否下沉后端聚合 | BFF 全量计算（pageSize ≤500 自保护） | 单域记录数 >2000 时复审 |
| 5 | 前端表单写操作的 DSL 触发方式（pactor http dataSource 非 autoLoad 调用细节） | 按 pactor 文档实现，BFF 写通道契约不变 | 写操作批次启动前 |

## 附录 A：实施批次（对应 grill-me Q9）

| 批次 | 后端 | BFF / 前端 | 出口 |
|------|------|-----------|------|
| B1 Provider | 筛选、by-code、disable、pull-models、ConnectionTestVo、详情富字段 | provider-adapter + 写通道 | 契约测试 + 4 个 Provider 页面联调 |
| B2 Route | route_code 加列、筛选、by-code、enable/disable、RouteDetailVo | route-adapter | 契约测试 + 4 个 Route 页面联调 |
| B3 Model | 筛选、by-code、PUT、disable、ModelDetailVo | model-adapter | 契约测试 + 4 个 Model 页面联调 |
| B4 Client + 模板 | ClientTemplateEndpoint 全套、Client 筛选/by-code | client/template-adapter | 契约测试 + 8 个页面联调 |
| B5 日志/审计/成本 | mux_request_log 扩列、筛选、成本视图、审计快照 | call-log/cost/audit-adapter | 契约测试 + 6 个页面联调 |
| B6 统计 | stats 包 4 服务 + StatsEndpoint | stats-adapter（dashboard/3 用量/可用性） | 契约测试 + 5 个页面联调 |
| B7 调试台 | debug 端点契约核对（选项数据来自 B1/B3 详情） | debug-adapter | 2 个调试页真实调用验收 |

每批：`mvn clean compile` + 对应测试全绿后合入；mock 切换在该批页面联调通过后进行。
