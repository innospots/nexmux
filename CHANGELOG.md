# Changelog

本文件记录 Nexmux 的重要变更。版本遵循 [Semantic Versioning](https://semver.org/)，预发布版本使用 `SNAPSHOT` 标识。

## Unreleased

### Added

- 新增 OpenAI Responses、Anthropic Messages 与 OpenAI Embeddings 非流式 Runtime 入口，统一鉴权、授权、配额、限流、选路与计量。
- 增加 Route `config_revision`、版本列表和按版本回滚；回滚生成新修订。
- 增加 Client 策略修订与前后快照，授权、配额和限流作为同一策略版本更新。
- 增加 Model 默认参数、Metadata、主/扩展 Binding 配置与 Runtime 合并；客户端显式参数优先。
- 增加分档、缓存读写、时段倍率、价格版本与 Pricing Snapshot，新增 `standard_usd` 以区分三类金额。
- 增加确定性 sample（每 4 条保留 1 条）与可配置日志清理首跑/周期。
- 增加开源项目首页、工程结构、接入指南、运维与贡献文档，以及 100 项 Console → Runtime 端测计划。

### Changed

- Provider Proxy 支持创建、更新和详情回显；Runtime 代理转发复用同一配置。
- Model 启用前校验 Provider、Binding 和连接，调用测试复用真实 DebugService。
- Model 删除前检查 Route 草稿和已发布快照中的引用。
- Model 停用与 Binding 健康过滤解耦，关闭健康过滤时停用仍返回无可用候选。
- 定价按 input_tokens 命中档计算，并纳入缓存价格和请求时段倍率；标准金额、内部参考消费金额与供应商成本分别聚合。
- Runtime `x-request-id` 与调用日志 `request_id` 对齐。
- `promptPolicy=sample` 改为确定性系统抽样，测试可重复。
- `QuotaGate` 支持注入 Clock，便于确定性验证跨窗口重置。
- 非 OpenAI 上游协议与未声明 Tool Calling 的 Tools 请求明确失败，不再静默透传。

### Fixed

- 修复 Responses/Messages/Embeddings 的 Ingress 与上游操作路径分离，避免 `/v1/*` 路由匹配错误。
- 修复 Pricing Snapshot 缺失价格生效时间、命中档与实际 Binding 的问题。
- 修复旧文档中默认参数、删除接口、模型调用测试、品牌与协议范围的过期描述。
- 历史 P0 设计、旧测试报告、旧核查和缺失接口清单移至 `docs/archive/`。

### Verification

- 后端全量：340 tests，0 failures / 0 errors。
- Console → Runtime 编号端测：99 PASS / 0 FAIL / 1 BLOCKED。保留项为透明代理环境下的连接超时毫秒计时验证。

## 0.1.0 - 2026-10-24

### Added

- 建立 Model Console 与独立 Model Runtime 双进程架构。
- 支持 Provider、Model、Route、Client/AppKey、授权、配额、限流、调试台与配置审计。
- 支持 OpenAI Chat Completions（含 SSE）、模型发现、主动探活、运行日志与基础金额快照。
- 支持 OpenAI Compatible 上游 4xx 透传和 5xx/网络错误归约。
- 增加 Provider 凭证 AES-GCM 落库加密、实例级配额与限流。
- 建立 H2 本地开发库、Spring assembly 发行包、Next.js/Pactor 管理控制台与 CI。
