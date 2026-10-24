# Getting Support

## 提问前

请先阅读：

- [README.md](./README.md)
- [运行与部署](./docs/nexmux-deployment.md)
- [模型接入指南](./docs/nexmux-integration-guide.md)
- [排障指南](./docs/nexmux-troubleshooting.md)
- [端测计划](./docs/nexmux-console-runtime-e2e-plan.md)

请同时确认版本、commit、profile、端口和实际解包目录。很多“配置未生效”来自旧包、不同数据库或快照尚未刷新。

## 问题反馈

### Bug

请包含：

- 版本/commit、JDK/Maven/Node 与操作系统；
- Console/Runtime/UI 端口和 profile；
- 最小复现步骤、预期/实际结果、相关端点与 HTTP 状态；
- `x-request-id`、Alias、Client/Route/Model code（可脱敏）；
- 是否使用代理、真实上游或本地桩，以及已执行的清理。

不要提交真实 Secret、数据库文件、完整业务 Prompt 或个人数据。

### 功能建议

请说明：业务场景、当前限制、建议行为、兼容影响、测试范围和是否愿意提交 PR。先检查现有设计与未开放项，避免重复请求已实现能力。

### 文档改进

请指出具体文档、段落、错误描述和推荐改法。历史文档在 `docs/archive/`，当前事实源以 `README.md` 和当前设计/运行文档为准。

## 安全问题

安全漏洞、凭证泄露、越权或敏感数据风险请通过 [SECURITY.md](./SECURITY.md) 的私密渠道报告，不要创建公开 Issue。

## 支持边界

- 当前为 `0.1.0-SNAPSHOT`，不承诺生产 SLA、响应时限或所有上游协议兼容。
- Responses/Messages/Embeddings 当前仅非流式；Chat Completions 才支持 SSE。
- 多实例配额与限流为实例级，不提供分布式精确计数保证。
- 连接超时需考虑透明代理和网络环境，代理返回的错误不等于连接超时。
- 维护者可能要求补充最小复现、日志和端测证据，再决定修复或解释行为。

## 贡献方式

代码、测试、文档和复现用例都欢迎。请遵循 [CONTRIBUTING.md](./CONTRIBUTING.md) 与 [CODE_OF_CONDUCT.md](./CODE_OF_CONDUCT.md)。
