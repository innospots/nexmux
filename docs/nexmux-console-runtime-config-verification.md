# Model Console → Model Runtime 配置生效性核查

> **目的**：找出 Model Console 中「用于控制 Model Runtime 行为」的配置项，并逐项验证它们是否真正影响 Runtime。
> **被测**：Console `:8082`、Runtime `:8083`（`local,runtime`，共用 H2 配置库）、控制台 UI `:9011`。
> **方法**：阅读 Console→Runtime 契约代码（快照缓存 / 流水线 / 关口），再以真实调用（DeepSeek 上游）+ 配置变更做 A/B 验证。
> **日期**：2026-10-13
>
> 相关：[Runtime 设计](./design/nexmux-runtime-design.md)、[Console/Runtime 边界 ADR](./design/adr/0001-console-runtime-configuration-boundary.md)、[功能说明书](./nexmux-functional-guide.md)。

---

## 1. 生效链路（Console 配置如何到达 Runtime）

```text
Console（控制面，写配置表）
   │  RuntimeSnapshotRefresher 每 nexmux.runtime.snapshot-poll-interval-seconds 全量重载
   ▼
Runtime 内存快照
   ├── PublishedRouteCache   ← mux_route：仅 status='PUBLISHED' 且 published_route_spec 非空
   ├── RuntimeCatalogCache   ← mux_model / mux_provider / mux_provider_credential / mux_client（无过滤）
   └── BindingHealthRegistry ← mux_binding_health + 主动探活
   ▼
POST /v1/chat/completions → InvocationPipeline
   鉴权(AppKey) → 路由解析(alias+ingress) → 路由授权 → 配额 → 限流
   → 候选选择(CandidateSelector) → Binding 选择(健康+权重) → 上游转发 → 计量落库
```

要点：
- **只有「已发布」的路由**进入 Runtime（`PUBLISHED` + `published_route_spec`）；草稿/停用不可见。
- 配置变更经 **周期快照**生效（默认 30s，本次验证临时调为 5s 以加速；已还原）。
- `promptPolicy` 不走快照，Runtime 落库时实时读 `mux_platform_setting`。

---

## 2. 配置项 × 生效性矩阵

图例：✅ 生效（实测）　🟡 生效（代码确认/部分实测）　❌ 不生效（实测/代码确认）　➖ 不适用

### 2.1 路由（Route）

| Console 配置项 | Runtime 消费点 | 预期 | 实测 | 结论 |
|---|---|---|---|---|
| 启用 / 停用 | `PublishedRouteCache` 仅取 `PUBLISHED` | 停用后该 alias 不可达 | 停用→`404 NMX130002`；启用→`200` | ✅ |
| 逻辑模型 Alias | `findByAlias(request.model)` | 别名不匹配即 404 | `e2e-rt`→200；改名后旧别名→404、新别名→200 | ✅ |
| 入口 请求路径 / 协议 | `ingress` 与固定入口 `/v1/chat/completions` 匹配 | 不一致即 404 | 改 `/v1/other` → `404 NMX130002` | ✅ |
| 路由策略 WEIGHTED | `CandidateSelector` + `WeightedPick` | 权重决定分布 | A w0/B w100 → 选中 B(502)；A w100/B w0 → 选中 A(200) | ✅ |
| 候选权重 | `WeightedPick` | 0 权重不被选中 | 同上 | ✅ |
| 候选优先级 P0/P1/P2 | `bestPriorityTier` | 仅最优 tier 参与 | A P1 + B P0 → 6/6 命中 B(502)；A P0 + B P1 → A(200) | ✅ |
| 候选行 启用/停用 | 候选 `enabled` 过滤 | 停用候选被剔除 | A启B停→A(200)；A停B启→B(502) | ✅ |
| 粘性 Session | `stickySession` + `hashPick` | 同会话固定候选 | 关：`200 200 200 502…`；开：6/6 同一结果 | ✅ |
| 健康过滤 healthFilter | `filterUnhealthyBindings` + `BindingHealthRegistry` | 剔除不健康 | 代码确认；**未构造不健康绑定实测** | 🟡 |
| **候选行 C / M 倍率** | **Runtime 未读取** | 应覆盖 Binding C/M | `costRate=3` 后单次成本仍 `1.95e-05`（不变） | ❌ |

### 2.2 客户端 / 访问治理（Client）

| Console 配置项 | Runtime 消费点 | 预期 | 实测 | 结论 |
|---|---|---|---|---|
| AppKey | `AppKeyVerifier` | 无效/缺失 401 | 缺失/伪造 → `401 NMX040002` | ✅ |
| 启用 / 停用 | `"ENABLED".equals(status)` | 停用 401 | 停用→`401`；启用→`200` | ✅ |
| 有效期 | `expiresAt.isBefore(now)` | 过期 401 | `expiresAt=2020-01-01` → `401` | ✅ |
| 路由授权 | `authorizedRouteIds.contains(routeId)` | 未授权 403 | 撤销授权→`403 NMX040003`；恢复→`200` | ✅ |
| 配额 hard | `QuotaGate` 抛 429 | 超限拒绝 | `REQUEST=2` → `200,200,429 NMX100001` | ✅ |
| 配额 soft | 仅放行+标记 | 不拒绝 | `REQUEST=1` 连续调用均 `200` | ✅ |
| 配额 周期/指标（Request/Token/Cost） | `QuotaGate` 各指标 | 按窗口计数 | 代码确认（REQUEST 已实测） | 🟡 |
| **配额 CONCURRENT 指标** | **`QuotaGate` 无该指标实现** | 应限制并发 | 代码确认：`check` 用量恒 0，永不触发 | ❌ |
| 限流 RPM | `RateLimitGate` 分钟窗口 | 超限 429 | `RPM=2` → `200,200,429 NMX100002` | ✅ |
| 限流 TPM / CONCURRENT / Burst / scope | `RateLimitGate` | 按规则生效 | 代码确认 | 🟡 |

### 2.3 模型 / 供应商（Model / Provider / Binding）

| Console 配置项 | Runtime 消费点 | 预期 | 实测 | 结论 |
|---|---|---|---|---|
| Provider 启用 / 停用 | `findProvider(...).filter(ENABLED)` | 停用即上游不可用 | 停用→`502 NMX060001`；启用→`200` | ✅ |
| Provider Base URL | `chatAdapter(baseUrl…)` | 改错即失败 | 改为 `127.0.0.1:1` → `502`；还原→`200` | ✅ |
| Provider Timeout (ms) | `timeoutOf(provider)` | 超时即 502 | `timeoutMs=1` → `502 NMX060001`；还原→`200` | ✅ |
| Provider 凭证 API Key | `credentialOf(...)` | 缺失即 502 | 基线调用依赖凭证成功；缺失分支代码确认 | 🟡 |
| Provider Default Protocol | 仅落日志 `protocol` | 不影响转发 | 适配器固定 OpenAI 兼容 | ➖ |
| Provider Region / Proxy | **未消费** | — | 代码确认 | ❌ |
| Model Binding（providerModelId / 权重 / 启用） | `ModelBindingSelector` | 决定上游与权重 | 代码确认（上游 model 正确透传） | 🟡 |
| **Model 状态 enabled/disabled** | **Runtime 不过滤 `model.status`** | 应影响可用性 | 停用 Model 后调用仍 `200` | ❌ |
| **Model 默认参数（temperature/max_tokens…）** | **`upstreamBody` 未合并；且创建/更新接口无该字段** | 应作为默认执行参数 | 代码确认（既不可持久化，也不消费） | ❌ |
| Model 定价（分档/时段） | `RequestLogWriter` 成本计算 | 影响 cost | 单次成本按档位计算（实测 `1.95e-05`）| ✅（但不可经 API 编辑） |
| Model Binding C / M | `ResolvedBinding.costMultiplier/referenceMultiplier` | 影响成本 | 代码确认（无 API 可编辑） | 🟡 |

### 2.4 服务设置（平台全局）

| Console 配置项 | Runtime 消费点 | 预期 | 实测 | 结论 |
|---|---|---|---|---|
| Prompt/Response 保存策略 | `RequestLogWriter.applyPromptPolicy` | full 留存原文 | `promptPolicy=full` → 日志 `pr.policy=full` 且含 prompt/response | ✅ |
| **默认请求超时 (requestTimeoutMs)** | **Runtime 未消费** | 应作用于转发超时 | 设为 1ms 后调用仍 `200`（实际使用 Provider timeout=60000） | ❌ |
| **上游连接超时 (upstreamConnectTimeoutMs)** | 仅 `DebugService`（调试台） | 应作用于 Runtime | 代码确认 Runtime 未引用 | ❌ |
| **调用日志保留天数 (logRetentionDays)** | **全仓无消费者** | 应清理/归档 | 代码确认无任何引用 | ❌ |
| 允许调试调用 / 调试日配额 | `DebugService`（Console 内模拟） | 控制调试台 | 与 Runtime 无关 | ➖ |

### 2.5 Runtime 进程配置（`config/nexus/nexmux.yaml`，非 Console UI）

| 配置项 | 作用 | 实测 | 结论 |
|---|---|---|---|
| `snapshot-poll-interval-seconds` | 配置快照重载周期 | 调为 5s 后，配置变更约 5–7s 生效（默认 30s） | ✅ |
| `health-probe-interval-seconds` / `health-probe-timeout-ms` / `health-unhealthy-threshold` / `health-degraded-latency-ms` | 主动探活与健康判定 | 代码确认（装配 `BindingHealthRegistry`/`BindingHealthProber`） | 🟡 |
| `quota-resync-interval-seconds` | 配额内存计数与库聚合校准 | 代码确认（`InvocationPipeline.resyncQuotas`） | 🟡 |

---

## 3. 结论汇总

| 结论 | 数量 | 说明 |
|---|---:|---|
| ✅ 实测生效 | 18 | 路由/客户端/供应商/日志策略等主链路配置均能有效控制 Runtime |
| 🟡 代码确认生效（未充分实测） | 7 | 多因缺少可编辑入口或需构造异常绑定 |
| ❌ 不生效（缺口） | 8 | 见下节 |

**总体判断**：控制 Runtime 的**核心配置项（路由发布/别名/入口/候选与策略、AppKey/授权/配额/限流、供应商状态与连接）实测均有效**；但存在 8 处「Console 暴露了配置项、Runtime 却不消费（或无法编辑）」的缺口，其中 3 项属服务设置误导，需产品/实现对齐。

---

## 4. 缺口清单（Console 有配置项，Runtime 不生效）

| # | 配置项 | 位置 | 现象 | 影响 | 建议 |
|---|--------|------|------|------|------|
| **G-01** | **候选行 C / M 倍率** | 路由 → ③ 模型与路由策略（行内 C/M） | 设置 `costRate=3` 后单次成本不变；Runtime 只用 Model「提供商」绑定的 C/M | 用户以为可在路由层覆盖成本倍率，实际无效；成本归属不随配置变化 | 二选一：Runtime 在 `resolve()` 用候选 C/M 覆盖 `ResolvedBinding`；或移除路由行 C/M 字段并在 UI 注明「只在 Model 提供商 Tab 生效」 |
| **G-02** | **Model 默认参数** | 模型 → 默认参数 Tab | Runtime 的 `upstreamBody` 不合并 Model 默认参数；且 `ModelCreateRequest/ModelUpdateRequest` 无参数/定价字段（不可持久化） | 「默认执行参数」完全不生效，配置项名不副实 | 补 Model 参数持久化字段 + 在 `upstreamBody` 按「请求参数 > Model 默认参数」合并（P1 参数预设同源） |
| **G-03** | **服务设置 默认请求超时 (requestTimeoutMs)** | 服务设置 → 运行默认值 | 设为 1ms 后 Runtime 调用仍成功；Runtime 实际用 `Provider.timeoutMs` | 平台级默认超时形同虚设，运维按此调优无效 | Runtime `timeoutOf()` 回退链改为 `Provider.timeoutMs → requestTimeoutMs → 常量` |
| **G-04** | **服务设置 上游连接超时 (upstreamConnectTimeoutMs)** | 服务设置 → 运行默认值 | 仅 `DebugService` 使用，Runtime 未引用 | 调试台与真实链路超时口径不一致 | Runtime 连接超时读取该设置（或明确拆分「调试/运行」两套口径并在 UI 标注） |
| **G-05** | **服务设置 调用日志保留天数 (logRetentionDays)** | 服务设置 → 日志与合规 | 全仓无消费者，无任何归档/清理逻辑 | 保留策略不生效，日志无限增长 | 实现 Runtime/Console 归档任务，或改为「运行时清理」并在 UI 标注未启用 |
| **G-06** | **Model 状态 enabled/disabled** | 模型 → 启停 | 停用被已发布路由引用的 Model 后，Runtime 仍正常选路并返回 200 | 停用模型不能即时止损；与「模型下线」运维预期不符 | 明确语义：若期望即时生效，Runtime 在 `findModel`/候选过滤时校验 model.status；若保持现状，UI 停用确认需明确「已发布路由不受影响」 |
| **G-07** | **配额 CONCURRENT 指标** | 客户端 → ④ 配额策略（计量指标含 Concurrent） | `QuotaGate` 未实现 CONCURRENT（用量恒 0，永不触发）；并发只在限流侧 `RateLimitGate` 支持 | 用户以为配额可限并发，实际无效 | 移除配额侧 CONCURRENT 选项（引导用限流），或在 `QuotaGate` 接入并发租约 |
| **G-08** | **Provider Region / Proxy** | 供应商 → 连接与凭证 | Runtime 转发未读取 region/proxy | 配置无效果（代理场景不可用） | 适配器接入 proxy；region 若仅为元数据，UI 标注「不参与调用」 |

> 说明：G-03/G-04/G-05 均集中在「服务设置 → 运行默认值/日志与合规」，建议一并复核「服务设置」中每个键的消费方，避免同类误导。

---

## 5. 关键证据（可复现）

```bash
# 基线（已发布路由 + 已知 AppKey）
curl -s -X POST http://localhost:8083/v1/chat/completions \
  -H "Authorization: Bearer <AppKeySecret>" -H 'content-type: application/json' \
  -d '{"model":"e2e-rt","messages":[{"role":"user","content":"say pong"}],"max_tokens":8}'
# → 200 OK（上游 DeepSeek）

# 路由停用           → 404 NMX130002
# 路由未授权         → 403 NMX040003
# 客户端停用/过期    → 401 NMX040002
# 配额 hard REQUEST=2 → 第 3 次 429 NMX100001
# 限流 RPM=2          → 第 3 次 429 NMX100002
# Provider 停用/坏 URL/Timeout=1ms → 502 NMX060001

# G-01 路由候选 C=3：costUsd 仍 1.95e-05（不变）
# G-02 模型默认参数：ModelCreate/Update 无参数字段；upstreamBody 不合并
# G-03 requestTimeoutMs=1：调用仍 200（实际用 Provider timeout）
# G-06 Model 停用：调用仍 200
```

配置变更经 `PublishedRouteCache`/`RuntimeCatalogCache` 周期快照生效（验证期将 `nexmux.runtime.snapshot-poll-interval-seconds` 临时设为 5s，**已还原为 30s 并重启 Runtime**）。

---

## 6. 验证环境清理

| 项 | 处理 |
|---|---|
| 测试客户端 `e2e-rt-client/-expired/-quota/-soft/-rate` | 已删除（回到 demo-app / debug-cli / app-web） |
| 测试路由 `e2e-rt-route` | 已删除（回到 3 条 demo 路由） |
| Provider `deepseek`（Base URL / Timeout） | 已还原 `https://api.deepseek.com` / `60000`，状态 enabled |
| Model `deepseek/deepseek-flash` | 已还原 enabled |
| 服务设置 `promptPolicy` / `requestTimeoutMs` | 已还原 `masked` / `60000` |
| Runtime `nexmux.yaml` 快照周期 | 已还原 30s 并重启（CWD=仓库根，库路径 `./out/db`） |
| 误建的游离库 | 已删除（`out/nexmux-runtime/out`） |

---

*本核查以代码契约 + 真实调用为准；🟡 项建议在后续批次补齐构造性用例。*
