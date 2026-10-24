# Nexmux 排障指南

> 优先使用 `x-request-id`、请求时间、Route Alias 和上游模型定位。不要在 Issue 或日志中贴真实 Secret、完整 Prompt 或凭证文件。

## 1. 快速定位顺序

1. 查进程与端口：Console `:8082`、Runtime `:8083`、UI `:9011`。
2. 用 AppKey 调 `GET /v1/models`，确认 Alias 已授权且已发布。
3. 查 Route `published_route_spec`、Client 授权和快照周期。
4. 用响应 `x-request-id` 查调用日志、Route Trace、Pricing Snapshot 与上游错误。
5. 核对实际运行解包目录是否来自本次 tar，而不是旧包。

## 2. 常见错误码

| 错误 | 常见原因 | 处理 |
|---|---|---|
| 400 `NMX010001` | 缺 model、Tools 未声明能力、非 OpenAI 协议、当前不支持的操作 | 修正请求/Capability/协议；三个新协议暂不支持 Streaming |
| 401 `NMX040002` | AppKey 缺失/错误、Client 停用或过期、轮换后仍用旧 Key | 核对 AppKey、Client 状态和有效期，使用新 Key |
| 403 `NMX040003` | Client 未授权该 Route | 添加授权并等待快照 |
| 404 `NMX130002` | Alias 未发布、路径不匹配、Route 停用 | 用 `/v1/models` 核对 Alias 与 Ingress |
| 429 `NMX100001` | hard 配额达到上限 | 等下一窗口或调整配额；被拒请求不消耗 REQUEST 配额 |
| 429 `NMX100002` | RPM/TPM/并发限流 | 按 `Retry-After` 退避，检查 Burst/scope/实例级限制 |
| 502 `NMX060001` | 上游 5xx、网络、Proxy、超时 | 查 Base URL、凭证、代理与上游状态，关联 Request ID |
| 503 `NMX020005` | Model 停用、无启用候选、无健康 Binding | 恢复 Model/Binding 或调整候选与健康过滤 |

上游 4xx 会按上游错误透传，不要当成 Nexmux 自身 5xx 重试。

## 3. 配置修改未生效

- Route 是否真正发布，而非仅保存草稿。
- 默认快照周期为 30 秒，是否已等待。
- Console 与 Runtime 是否使用同一数据库文件；跨 CWD 启动时是否设置绝对 `SPRING_DATASOURCE_URL`。
- 是否启动了旧 tar 的旧解包目录。
- Runtime 日志是否有 snapshot refresh failure，旧快照是否被保留。
- 修改 Route 后是否同时检查 Alias、Ingress 和 Client 授权。

## 4. 日志与 Request ID

- 新 Runtime 的响应 `x-request-id` 应与 `mux_request_log.request_id` 相同；若不同，先升级运行包。
- 查询日志时同时使用 ID、时间范围、Client/Route/Model，避免只按展示文案查找。
- 前置鉴权/授权/配额/限流/选路拒绝按设计不消耗 REQUEST 配额；不要误判为漏记。
- Pricing Snapshot 应含版本、生效时间、命中档、C/M 和 Binding；缺失时先检查 pricing_spec 和 Runtime 版本。
- Prompt/Response 默认 masked；只有 full 或 sample 命中才保留原文。

## 5. 金额问题

- 检查 input_tokens 命中档，不是永远使用第一档。
- 检查 cache read/write usage 是否存在于上游响应，及价格是否配置。
- 检查请求时间的时段倍率，以及 Route 候选 C/M 是否覆盖 Binding C/M。
- 区分 `standard_usd`、`billing_usd`、`cost_usd`，不要把标准金额当供应商成本或客户账单。

## 6. 网络与代理

- Base URL 错、Provider 停用、凭证错误、读取超时都可能导致 502。
- 保留 IP 被透明网关代理时可能返回真实 HTTP 502，不能据此证明连接超时配置无效；换无透明代理环境复测。
- 系统 SOCKS 代理不可用时可加 `-Djava.net.useSystemProxies=false`，但不要为绕过验证而随意关闭生产代理。
- `upstreamConnectTimeoutMs` 管连接，`requestTimeoutMs` 管读取，Provider `timeoutMs` 优先于全局读取默认值。

## 7. H2 与进程

- fresh 库必须先启动 Console 建表和种子，再启动 Runtime。
- 强杀后 H2 `AUTO_SERVER` 握手 EOF：停止全部进程，删除对应 `*.lock.db` 后重启，不删除数据文件。
- 进程启动成功但接口 404：确认 Console 与 Runtime 的 profile 是否颠倒，以及是否访问了错误端口。
- UI 空数据或 `{ok:true}` 不一定代表后端成功，直接请求 Console `/api/d/**` 检查真实响应。

## 8. 需要反馈时提供

- 版本/commit、部署方式、profile、端口和配置文件（脱敏）。
- `x-request-id`、时间、Alias、Client code、Route code、Model code、HTTP 状态。
- 可复现的最小请求与预期/实际结果，以及是否经过代理。
- 相关日志与 Pricing Snapshot（脱敏），而非完整数据库或真实业务 Prompt。
