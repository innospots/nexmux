# Nexmux Model Runtime 设计（P0 可调用闭环）

## 1. 文档定位

- **状态**：定稿（当前实现，2026-10-24 核验）
- **日期**：2026-10-10（最近修订：2026-10-24）
- **归属模块**：`nexmux-kernel`（`com.innospots.nexmux.inbound` 包）+ `nexmux-spring`（`runtime` profile 装配）
- **关联文档**：
  - [ADR-0001 Console / Runtime 配置边界](./adr/0001-console-runtime-configuration-boundary.md)
  - [ADR-0002 Runtime 工程形态与库共用](./adr/0002-runtime-deployment-form.md)（本设计的工程形态决策，修订 ADR-0001 决策 5）
  - [nexmux-architecture-design.md](./nexmux-architecture-design.md)（§2 控制面/数据面、§5.2 包结构、§8 运行库归属）
  - [nexmux-p0-console-design.md](../archive/nexmux-p0-console-design.md) §9（P0 联调最小契约）
  - [nexmux-domain-layer-design.md](./nexmux-domain-layer-design.md)（实体与 Binding 结构）
  - PRD：[需求说明书](../../../product-design/nexmux/模型管理系统产品功能需求说明书.md) §16–19、§27、§28、§31、§33–36

### grill-me 摘要（已与开发者确认，2026-10-10）

| # | 问题 | 已确认决策 |
|---|------|-----------|
| 1 | Runtime 工程形态 | **不新增 Maven 模块**；在 `nexmux-spring` 内以 **独立 profile（`runtime`）启动独立 JVM 进程**（见 ADR-0002） |
| 2 | 运行库 | **共用数据库实例**；按表级写责分离（运行表仅 Runtime 写，配置表仅 Console 写），退出条件见 ADR-0002 |
| 3 | AppKey 鉴权 | OK——Runtime 侧 `app_key_hash` + 盐校验，内存 Credential Cache |
| 4 | 协议范围 | OpenAI Chat Completions（含 SSE）+ Responses / Messages / Embeddings（非流式）+ `GET /v1/models`；均复用同一 Runtime 调用链 |
| 5 | 计量 | **全量**：tokens、成本估算、`pricing_snapshot`、`route_trace`、`prompt_snapshot`（按 promptPolicy） |
| 6 | 健康 | **主动探活**：定时探测上游，维护 Binding 健康视图并参与选路过滤 |

## 2. 背景与目标

Console（控制面）P0 已交付，但模型调用尚无真实数据面：当前所有"调用"都来自调试台在 Console 进程内的模拟（审计 A2/B1 已限域并标注 `source=DEBUG`）。本方案落地 **Model Runtime（数据面）**，使 PRD §31 的调用链成为真实链路。

**成功标准：**

1. 业务应用以 AppKey 调 `POST /v1/chat/completions`（`model` 填 Route alias），经鉴权 → 授权 → 配额/限流 → 选路 → Binding 选择 → 上游转发，得到真实响应（含 SSE 流式）。
2. Console 的调用日志 / 用量 / 成本 / 供应商可用性页面展示**真实运行流量**（`source=RUNTIME`）。
3. Console 进程停止后，Runtime 进程凭内存快照继续服务已发布配置（PRD §3.1）。
4. 调试台与 Runtime 选路语义一致（共用 `CandidateSelector`，审计 B1 已落地）。

## 3. 不在本方案中的能力

| 排除项 | 理由 / 归属 |
|--------|------------|
| Responses / Messages / Embeddings Streaming、rerank/images/audio | 三个协议入口已支持非流式；Streaming 与其他模态仍属后续阶段 |
| Anthropic / Gemini / Bedrock Adapter | PRD §18 后续；单协议期**不建**统一协议抽象（见 §13） |
| 内部统一模型协议（ModelRequest/ModelResponse 全套，PRD §17） | 单一协议下抽象无第二消费者；第二 adapter 立项时落地 |
| 条件路由 / 灰度 / 智能路由 | PRD §19.2 P1/P2 |
| 熔断（Circuit Breaker）与有界 Failover | PRD §19.7/§27 P1；P0 选中 Binding 失败后结束请求，不自动切换 |
| Redis 限流 / 分布式配额计数 | PRD §28.3 允许 Local Memory；多实例部署时再评 |
| 独立 `nexmux-runtime` Maven 模块 / artifact | AGENTS.md 四模块约束；见 ADR-0002 |
| 价格版本表 | 领域设计 §7：审计日志 Diff 追溯，`pricing_snapshot` 落请求侧 |
| Binding 健康状态回写 `mux_model.extra_bindings` | 高频运行状态不入配置库（ADR-0001 §3）；改由 `mux_binding_health` 承载，领域设计 §5.1 口径已修订 |

## 4. 归属与词汇（四步法 ①②）

| 概念 | 英文名 | 归属模块/包 | 技术 ID | 稳定业务键 | 备注 |
|------|--------|------------|---------|-----------|------|
| 数据面运行时 | Model Runtime | `inbound`（kernel）+ `runtime` profile（spring） | — | — | 独立 JVM 进程 |
| 入站端点 | Ingress Endpoint | `inbound.ingress` | `/v1/chat/completions`、`/v1/models` | — | OpenAI 兼容 |
| 已发布路由快照 | Published Route Spec | `core.route`（表）/ `inbound.runtime`（缓存） | `route_id` | `alias` / `ingress` | 已有 `PublishedRouteCache` |
| 候选选择器 | CandidateSelector | `core.route.domain.selection` | — | — | **已有**，调试台/Runtime 共用 |
| Binding 选择 | Binding Selection | `inbound.pipeline` | — | `provider_id + provider_model_id` | Model 内健康过滤 + 权重 |
| Binding 健康 | Binding Health | `inbound.runtime`（注册表）+ `mux_binding_health`（表） | `probe_id` | `provider_id + provider_model_id` | HEALTHY / DEGRADED / UNAVAILABLE |
| 客户端凭证缓存 | Client Credential Cache | `inbound.runtime` | `client_id` | `client_code` / AppKey hash | AppKey 校验（决策 3） |
| 调用流水线 | Invocation Pipeline | `inbound.pipeline` | `request_id`（`rq` 前缀） | `request_id` + `trace_id` | PRD §31/§36 |
| 请求日志写入器 | RequestLogWriter | `core.ops.service` | — | — | 从 `DebugService` 抽出复用 |

术语对齐：`alias` / `ingress` / `candidate` / `binding` / `source=RUNTIME|DEBUG` 与 Console 侧实体列名、调试台 `route_trace` 键名一致。

## 5. 工程模块与 DDD 边界（四步法 ①③）

### 5.1 Maven 模块归属

| 有界上下文 / 能力 | Owning artifact | 依赖（仅直接） | 新建模块？ |
|------------------|-----------------|----------------|-----------|
| Runtime 数据面（ingress/pipeline/runtime/protocol） | `nexmux-kernel`（`inbound` 包） | `core.*`（读已发布配置、写运行表） | **否**（ADR-0002） |
| Runtime 进程装配 | `nexmux-spring`（`runtime` profile） | `nexmux-kernel` | 否 |
| 请求日志写入器（调试台/Runtime 共用） | `nexmux-kernel`（`core.ops.service`） | `core.ops.dao` 等 | 否 |

依赖规则沿用架构 §5.2：`inbound → core` 允许；`inbound → console` **禁止**；`core` 不依赖 `console` / `inbound`。

### 5.2 包结构

```text
com.innospots.nexmux.inbound
├── ingress/            # RuntimeIngressEndpoint（Jakarta REST）；Ingress 请求 record
├── pipeline/           # InvocationPipeline（鉴权→授权→配额→限流→选路→Binding→转发→计量）
│                       #   AppKeyVerifier、QuotaGate、RateLimitGate、ModelBindingSelector、RequestContext
├── runtime/            # PublishedRouteCache（已有）、RuntimeRouter（已有）、
│                       #   ClientCredentialCache、ProviderCredentialCache、BindingHealthRegistry、
│                       #   BindingHealthProber（主动探活）、RuntimeSnapshotRefresher（定时刷新）
└── protocol/           # UpstreamChatAdapter（已有，增补流式方法）、OpenAiCompatibleChatAdapter（已有）
```

`core.ops.service` 新增 `RequestLogWriter`（计量装配 + 落库，`DebugService` 改为调用它，行为不变）。

单包 ≤15 类；`pipeline` 超限时按「关口（Gate）/ 选择（Selection）/ 上下文」拆子包。

### 5.3 Session 与持久化作用域

- Runtime 无控制台会话；身份 = AppKey（`Authorization: Bearer gw-sk-*` 或 `X-API-Key`）。
- 配置消费走**内存快照**（`PublishedRouteCache` / `ClientCredentialCache` / `ProviderCredentialCache`），由 `RuntimeSnapshotRefresher` 按 `nexmux.runtime.snapshot-poll-interval-seconds`（默认 30s，键已存在于 `nexus/nexmux.yaml`）周期性全量重载；请求链路**不**逐次查库（ADR-0001 §4）。
- 实体基类沿用 `BaseEntity`；新表仅 `mux_binding_health`（见 §6.1）。
- `mux_request_log`：Runtime 写入 `source=RUNTIME`（调试台已用 `DEBUG`）；Console 侧查询不变。

## 6. 领域模型（四步法 ③④）

### 6.1 实体（Entity）

| 实体 | 表名 | 基类 | 主键 | 稳定业务键 | 索引 | 备注 |
|------|------|------|------|-----------|------|------|
| BindingHealthEntity | `mux_binding_health` | `BaseEntity` | `probe_id` | `provider_id + provider_model_id` | `uk_mux_binding_health_target`（唯一） | **新表，Runtime 拥有写责**，Console 只读（供应商可用性页数据源） |

`mux_binding_health` 字段（表意 DDL）：

```text
probe_id            varchar(32)  PK
provider_id         varchar(32)  NN
provider_model_id   varchar(256) NN        -- 绑定目标（主绑定或 extra_bindings 项）
model_code          varchar(64)            -- 冗余便于按模型过滤
health_status       varchar(16)  NN        -- HEALTHY / DEGRADED / UNAVAILABLE
consecutive_failures int         NN
last_probe_at       timestamp
last_latency_ms     bigint
last_error          varchar(512)
+ BaseEntity 审计列
```

写频：探活周期（默认 60s）× 绑定数，低频 upsert，符合「运行数据 Runtime 写、Console 只读」。

**复用（只读）**：`mux_route`（仅 `published_route_spec`）、`mux_client`、`mux_model`、`mux_provider`、`mux_provider_credential`。
**写入**：`mux_request_log`（`source=RUNTIME`）、`mux_binding_health`。

### 6.2 枚举（Enums）

| 枚举 | 常量 | 语义 | 存储列 | 状态迁移 |
|------|------|------|--------|----------|
| HealthStatus | `HEALTHY` / `DEGRADED` / `UNAVAILABLE` | Binding 健康（PRD §27） | `mux_binding_health.health_status` | 任一 → UNAVAILABLE（连续失败 ≥ 阈值）；UNAVAILABLE → HEALTHY（探测成功）；HEALTHY ↔ DEGRADED（延迟超/回落至阈值） |

`source` 取值约定（字符串列，不建枚举类）：`RUNTIME`（默认真实流量）/ `DEBUG`（调试台，已有）。

### 6.3 Request / VO（record 骨架）

```java
// inbound.ingress —— OpenAI 兼容入站契约（仅 P0 子集）
public record ChatCompletionIngressRequest(
        String model,                    // 必填：Route alias
        List<Map<String, Object>> messages,
        Boolean stream,                  // true → SSE
        Map<String, Object> streamOptions,
        // 其余 OpenAI 参数（temperature 等）原样透传上游
        Map<String, Object> extra) {}

// inbound.pipeline —— 流水线上下文（进程内，不持久化）
public record InvocationContext(
        String requestId, String traceId,
        ClientEntity client, RouteSpec routeSpec,
        CandidateSelector.Selection selection,
        ResolvedBinding binding,         // providerId / providerModelId / costMultiplier / referenceMultiplier
        Map<String, Object> quotaTrace, Map<String, Object> rateLimitTrace) {}
```

### 6.4 持久化与 Dao

- `mux_binding_health` 一表一 Dao（`inbound` 侧 `BindingHealthDao`，Console 侧只读复用同一 Dao 接口，不调写方法）；无 join、无 mapper.xml。
- DDL 归属：`nexmux-spring/src/main/resources/schema/nexmux/nexmux-h2.sql` 追加，脚本内注释「数据面表，Runtime 写 / Console 只读」（沿用审计 A4 口径；拆库时随运行表一并迁出）。
- 配置键（`nexus/nexmux.yaml` 的 `nexmux.runtime.*`）：

| 键 | 默认 | 说明 |
|----|------|------|
| `snapshot-poll-interval-seconds` | 30 | **已有键**；快照全量重载周期 |
| `health-probe-interval-seconds` | 60 | 主动探活周期 |
| `health-probe-timeout-ms` | 5000 | 单次探测超时 |
| `health-unhealthy-threshold` | 3 | 连续失败判 UNAVAILABLE |
| `health-degraded-latency-ms` | 10000 | 探测延迟超此判 DEGRADED |
| `quota-resync-interval-seconds` | 300 | 配额内存计数与库聚合校准周期 |
| `log-retention-initial-delay-minutes` | 10 | 日志清理首跑延迟，测试可设 0 |
| `log-retention-period-minutes` | 1440 | 日志清理周期，默认每日 |

## 7. 接口与 API（四步法 ④）

### 7.1 HTTP 端点（仅 `runtime` profile 装配）

| 方法 | 路径 | 请求 | 响应 | 委托 |
|------|------|------|------|------|
| POST | `/v1/chat/completions` | Chat Completions 请求 | OpenAI chat.completion JSON；SSE；非流式带 `x-request-id` | `InvocationPipeline.invoke` |
| POST | `/v1/responses` | OpenAI Responses 请求（非流式） | OpenAI response JSON + output_text | `InvocationPipeline.invokeOperation` |
| POST | `/v1/messages` | Anthropic Messages 请求（非流式） | Anthropic message JSON + stop_reason | `InvocationPipeline.invokeOperation` |
| POST | `/v1/embeddings` | OpenAI Embeddings 请求 | 上游 Embeddings 原始 JSON | `InvocationPipeline.invokeOperation` |
| GET | `/v1/models` | 同上鉴权头 | OpenAI list 形状：本 AppKey 已授权且已发布的别名清单 | `InvocationPipeline.listModels` |

- **alias 解析**：请求体 `model` 字段 = Route `alias`（PRD §15/§16）；同时校验请求路径与快照 `ingress` 一致（不一致 → 404）。P0 不支持路径参数化 ingress。
- **错误体**：OpenAI 兼容 `{"error": {"message", "type", "code"}}`，`code` 填 Nexmux 状态码 fullCode。端点自捕获 `NexusException` 经 `inbound.ingress.RuntimeErrorResponses` 映射（不走平台全局 R<T> 异常映射，避免 Console 语义渗入数据面）；限流 429 附 `Retry-After`（分钟窗口余秒）。**上游错误分层（2026-10-22 修订）**：上游 4xx 为接入方输入类错误，非流式透传上游状态与错误体（不归约 502；流式状态行已提交，错误体以 SSE 事件带内透传）；上游 5xx/网络/超时才归约 502 `NMX060001`。非流式成功直接透传上游原始 JSON 文本；流式失败在字节写出前以 SSE 错误事件收尾。
- **鉴权豁免**：`/v1/**` 不走控制台会话鉴权（`@NexusAuthenticatedApi` 不适用）；`application-runtime.yaml` 已配置 `nexus.console.web.security.permit-all-patterns: [/v1/**]`（ConsoleAuthenticationFilter 放行，security.enabled=true 时生效）。

### 7.2 Java 接口（非 HTTP）

| 接口 | 包 | 实现方 | 引入理由 |
|------|-----|--------|---------|
| `UpstreamChatAdapter`（已有） | `inbound.protocol` | `OpenAiCompatibleChatAdapter` | PRD §18 多 adapter 演进点（第二消费者 = Anthropic 适配，P1）；**增补** `streamChatCompletions(...)` 流式方法 |

不新增其他接口（各 Gate 用具体类，单消费者）。

### 7.3 分层调用

```text
ingress.RuntimeIngressEndpoint
  → pipeline.InvocationPipeline（编排各 Gate，具体类直接组合，不建 SPI）
  → runtime.*Cache / CandidateSelector / ModelBindingSelector
  → protocol.UpstreamChatAdapter
  → core.ops.service.RequestLogWriter → core.ops.dao.RequestLogDao
```

禁止：`ingress → dao`；`inbound → console`；请求链路逐次 SELECT 配置表（必须走缓存）。

## 8. 失败、状态码与异常

| 场景 | StatusCode | fullCode | HTTP | 抛出边界 |
|------|-----------|----------|------|---------|
| AppKey 缺失 / 无效 / 客户端停用 / 已过期 | `APP_KEY_INVALID`（PERMISSION_SECURITY `0002`） | `NMX040002` | 401 | AppKeyVerifier |
| 客户端未授权该路由 | `ROUTE_ACCESS_DENIED`（PERMISSION_SECURITY `0003`） | `NMX040003` | 403 | InvocationPipeline |
| `model`（alias）无匹配已发布路由 | `ROUTE_NOT_FOUND`（RESOURCE_DATA `0002`） | `NMX130002` | 404 | InvocationPipeline |
| 请求缺 `model` / 参数无效 | `INVALID_REQUEST`（INPUT_VALIDATION `0001`） | `NMX010001` | 400 | InvocationPipeline |
| 路由存在但未发布 | 复用 `ROUTE_NOT_PUBLISHED`（BUSINESS_RULE `0002`） | `NMX020002` | 503 | PublishedRouteCache 语义（未发布即不可见，按 404 处理；此码保留给显式草稿寻址） |
| 配额超限（hard） | `QUOTA_EXCEEDED`（RESOURCE_LIMIT `0001`） | `NMX100001` | 429 | QuotaGate |
| 限流触发 | `RATE_LIMITED`（RESOURCE_LIMIT `0002`） | `NMX100002` | 429 | RateLimitGate |
| 候选池为空（全停用/全不健康） | `NO_AVAILABLE_CANDIDATE`（BUSINESS_RULE `0005`） | `NMX020005` | 503 | InvocationPipeline |
| 上游 5xx / 网络 / 超时 | `UPSTREAM_FAILURE`（EXTERNAL_FAILURE `0001`） | `NMX060001` | 502 | InvocationPipeline（流式已开始则截断流并记日志，不回错误码） |
| 上游 4xx（接入方输入类错误） | 不映射状态码——透传上游状态与错误体 | 上游原码 | 4xx | InvocationPipeline / OpenAiCompatibleChatAdapter（`UpstreamCallException` 携带上游状态与截断错误体；流式以 SSE 事件带内透传） |

失败语义：链路前置失败（401/403/404/429/503）抛 `NexusException` 由 ingress 映射为 OpenAI 错误体；上游失败同样落 `mux_request_log`（status=ERROR）。SSE 流式中途失败按 PRD §19.7「已开始输出的流式响应不重试」，截断并记日志。

## 9. 事务、幂等与并发

| 项 | 结论 |
|----|------|
| 请求日志 | 单表 insert，`request_id` 唯一（`rq-` 前缀 UUID）；重试同键冲突视为重复调用，忽略 |
| 快照刷新 | 全量重载 + `AtomicReference` 切换（已有模式）；刷新失败保留旧快照 |
| 选路与模型状态 | 候选行 `enabled` 过滤之外，**Model `status=DISABLED` 恒剔除**（即时止损，G-06）；全部被剔除 → 503 `NO_AVAILABLE_CANDIDATE`。发布校验仍为候选行级（D-01）：Model 状态是运行期可变属性，发布时点快照无意义 |
| 配额计数 | 进程内 `ConcurrentHashMap` + `LongAdder`；启动与每 `quota-resync-interval-seconds` 以 `mux_request_log` 聚合（`source='RUNTIME'`）校准；hard 超限拒绝。cyclic 窗口按 `cycleUnit`（hour/day/自然月）推算起点。**计量指标不含 CONCURRENT**（配额为窗口累计语义；并发控制归限流信号量），存量 CONCURRENT 配额规则忽略 |
| 限流 | 进程内令牌桶（RPM/TPM，`burst` 为桶容量）+ 并发信号量（CONCURRENT）；多实例部署时为**实例级**限流（文档注明，分布式计数后续评） |
| 探活 | 单调度线程顺序探测；探测请求不计入 `mux_request_log`、不消耗客户端配额 |
| 默参与能力 | Model `default_params` 先合并已知 OpenAI 参数，客户端显式值覆盖；未声明 Tool Calling 的 Model 对 Tools 请求前置返回 400；非 OpenAI 上游协议明确拒绝 |
| 计费 | 请求结束后按 input_tokens 命中档，计算 input/output/cache read/cache write 标准金额，应用 Model 时段倍率，再分别应用 Binding C/M；`standard_usd`、`billing_usd`、`cost_usd` 分列落库，Pricing Snapshot 含版本、生效时间、命中档、C/M 与实际 Binding |
| 日志留存 | `promptPolicy` 支持 none/masked/full/sample；sample 为确定性系统抽样（每 4 条保留 1 条），日志清理首跑与周期可配置 |
| 日志保留 | `RequestLogRetentionCleaner` 按服务设置 `logRetentionDays` 每日清理超期行（≤0 不清理；启动 10 分钟后首跑避让启动高峰） |
| 超时与代理 | 读取超时：`Provider.timeoutMs` → 服务设置 `requestTimeoutMs` → 常量 30s；连接超时：`upstreamConnectTimeoutMs`（缺省 10s）；Provider `proxy` 配置 HTTP 代理（`host:port`），`region` 仅元数据不参与调用。服务设置为秒级 TTL 缓存近实时读取（`PlatformSettingService.getAll`，2026-10-22 起，避免热路径每请求多次全表读） |

## 10. 兼容与迁移

- 兼容面：`mux_request_log.source` 列已存在（审计 A1 修复）；存量行 `source IS NULL` 视为 `RUNTIME` 语义前的调试数据，Console 查询不做迁移（统计 SQL 按 `COALESCE(source,'DEBUG')` 口径消化的时点见 §15 开放问题）。
- `mux_binding_health` 为新增表（`CREATE TABLE IF NOT EXISTS`），无存量迁移。
- 领域设计 §5.1 `ModelBindingItem.healthStatus`「运行探测回写」口径**废止**：健康状态不落配置表，Console 展示侧改读 `mux_binding_health`（Console 页面适配不在本方案，另起 UI 批次）。
- 调试台行为不变（继续 `source=DEBUG`）；`RequestLogWriter` 抽取为纯重构，由既有 `DebugServiceTest` 锁定行为。

## 11. 测试范围

| 类型 | 类名（计划） | 断言要点 |
|------|-------------|---------|
| 状态码契约 | `NexmuxStatusCodeContractTest`（扩） | 新增 6 码的模块码/类别唯一性/HTTP 映射 |
| 实体契约 | `BindingHealthEntityContractTest` | 表名、唯一索引 `uk_mux_binding_health_target` |
| 端点契约 | `RuntimeIngressEndpointContractTest` | 路径 `/v1/chat/completions`、错误体形状、SSE 头 |
| 行为单测 | `AppKeyVerifierTest` | 正确/错误/缺失/停用/过期 AppKey；盐+SHA-256 复用 `AppKeySupport` |
| 行为单测 | `QuotaGateTest` | lifetime/cyclic、soft/hard、校准重置 |
| 行为单测 | `RateLimitGateTest` | RPM/TPM/CONCURRENT、burst、按路由 scope |
| 行为单测 | `ModelBindingSelectorTest` | enabled 过滤、健康过滤（UNAVAILABLE 剔除、DEGRADED 保留）、权重分布、全不可用 → empty |
| 行为单测 | `BindingHealthRegistryTest` | 状态迁移阈值、恢复、DEGRADED 延迟判定 |
| 行为单测 | `BindingHealthProberTest` | 本地 HttpServer 模拟 `/models` 成功/超时/失败 |
| 行为单测 | `InvocationPipelineTest` | 全链路 mock adapter：正常、各拒绝路径、日志字段（source=RUNTIME、pricing_snapshot、route_trace） |
| 行为单测 | `RequestLogWriterTest` | 计量字段全量、promptPolicy=masked/full、DEBUG/RUNTIME 来源 |
| 已有复用 | `PublishedRouteCacheTest`、`OpenAiCompatibleChatAdapterTest`、`CandidateSelectorTest`、`DebugServiceTest` | 重构后全绿 |

不测：真实上游厂商联调（手工验证项）、多实例分布式限流、熔断/Failover（P1）。

## 12. AGENTS 对齐

| AGENTS 条目 | 本方案符合方式 |
|-------------|----------------|
| 仅四模块（勿增删） | **不新增 Maven 模块**；Runtime 代码在 `nexmux-kernel` `inbound` 包，进程形态为 `nexmux-spring` 独立 profile（ADR-0002） |
| `nexmux-kernel` → `innospots-nexus-platform` | 不引入新第三方依赖（JDK HttpURLConnection + 既有 Jackson/MyBatis-Plus） |
| `nexmux-spring` 仅 Boot + assembly | profile 装配类只做 Bean  wiring，无业务逻辑 |
| 模块 POM 禁内联 version | 不动 POM |

对 ADR-0001 的两处偏差（工程形态、共用库）已在 grill-me 确认并落 **ADR-0002**，ADR-0001 状态注记同步更新。

## 13. 不建什么（防过度设计）

- **不建**统一模型协议抽象层（ModelRequest/ModelResponse/ModelStreamEvent 全套）：单协议期无第二消费者，P0 直接 OpenAI 透传 + usage 抽取；第二 adapter 立项时再抽象。
- **不建** `nexmux-runtime` Maven 模块、不改根 POM / BOM。
- ~~不建 Credential 加解密层~~（2026-10-22 决策翻案，已补建）：`core.shared.security.CredentialCipher`（AES-GCM，密钥取 `NEXMUX_CREDENTIAL_SECRET` / 系统属性），写入加密、读取方（Runtime 转发 / 探活 / 连接测试 / 调试台）解密；存量明文行兼容读取、下次保存自动升级；未配密钥时维持明文 + 启动告警。翻案理由：`ciphertext` 列存明文名不副实且为实质安全缺口。
- **不建** Redis / 分布式计数、不建熔断与 Failover（P1）。
- **不建** Binding 健康回写配置表、不建价格版本表、不建 Runtime 独立 OpenAPI 文档站点（端点随既有 OpenAPI 输出即可）。
- **不引入** Reactor/WebFlux：SSE 用 `StreamingOutput`（与调试台一致）。

## 14. 架构约束自检

- [x] 归属：`inbound` 包，无越界；`console` 零依赖侵入（仅 `DebugService` 重构抽取 `RequestLogWriter`）
- [x] 包结构领域优先，单包 ≤15 类；`pipeline` 超限预案已写
- [x] 术语与端点/实体/列名一致（alias/ingress/candidate/binding/source）
- [x] 分层方向：`ingress → pipeline → runtime/protocol → core.ops`；无 `inbound → console`
- [x] request/vo 为 record；`RouteSpec` 既有防御拷贝
- [x] 接口仅 `UpstreamChatAdapter`（有第二消费者演进点）；其余具体类
- [x] 端点 5 个（chat/completions、responses、messages、embeddings、models），非流式协议映射复用同一调用链
- [x] 每个应用可见失败有状态码行；统一 `NexusException`
- [x] 事务/幂等/并发已声明（§9）
- [x] 兼容面：`source` 列口径、新表迁移、§5.1 口径废止均已写（§10）
- [x] 无空分层/占位类型；「不建什么」完整（§13）
- [x] 持久化一表一 Dao、无 join/XML/properties；配置 yaml（§6.4）
- [x] 测试范围可测试先行（§11）

## 15. 开放问题

| # | 问题 | 决策截止 | 建议默认 |
|---|------|---------|---------|
| 1 | `/v1/**` 豁免控制台会话鉴权过滤器 | **已解决**（2026-10-10 实现期确认）：`nexus.console.web.security.permit-all-patterns` 支持前缀放行，`application-runtime.yaml` 已配 `/v1/**` |
| 2 | Console 用量/成本/调用日志页是否过滤 `source='DEBUG'`（或打标展示） | 下个 UI 批次 | 列表加 `source` 筛选项，默认全量 |
| 3 | 供应商可用性页切换到 `mux_binding_health` 为数据源的时点 | Runtime 联调后 | 另起 UI 批次，与 #2 合并 |
| 4 | 多实例部署时配额/限流的分布式一致性 | 出现多实例需求时 | 当前实例级语义写入文档即可 |
| 5 | Runtime 进程的管理端点（/actuator 健康）是否同端口暴露 | 实现批次 1 | 同端口，`application-runtime.yaml` 配置 |

### 已确认决策（grill-me 闭合）

§1 表格中 6 项全部为已确认决策，不再滞留开放问题。
