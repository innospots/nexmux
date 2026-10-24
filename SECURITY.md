# Security Policy

## 漏洞报告

请优先通过 GitHub 仓库的 **Private vulnerability reporting / Security advisories** 提交安全问题。公开 Issue 不得包含：

- API Key、AppKey、Token、密码、私钥或完整凭证文件；
- 真实业务 Prompt/Response、个人身份信息或客户数据；
- 可直接复现生产风险的内网地址、数据库文件或完整堆栈。

报告请附：影响版本/commit、影响端点、复现步骤、预期/实际结果、影响范围与建议修复方式。项目维护者会先确认复现，再讨论修复和披露时间。未获授权的安全测试请限定在本地或你拥有的环境。

## 凭证与密钥管理

- `NEXMUX_CREDENTIAL_SECRET` 用于 Provider 上游凭证 AES-GCM 落库加密，生产必须配置；Console 与 Runtime 同值。
- `CONSOLE_TOKEN_SECRET` 用于宿主认证，生产两个进程必须同值，不得使用示例值。
- 密钥放入 Secret Manager、KMS 或受控密钥注入，不写入 Git、镜像、日志、Issue、CI 输出或文档。
- 轮换 `NEXMUX_CREDENTIAL_SECRET` 前，先用旧密钥解密并轮换全部 Provider 凭证；轮换期间保持两进程配置一致。
- AppKey Secret 仅在创建/轮换时展示一次。当前旧 AppKey 轮换后立即失效，应低峰轮换并同步更新调用方。
- 测试和本地 fixture 使用独立低权限凭证，禁止复用生产密钥。

## 生产认证与网络安全

- 不使用演示账号、默认密码或明文会话。生产接入外部 IAM，并启用 `security.enabled=true`。
- 管理接口 `/api/d/**` 需要 Console 会话；Runtime `/v1/**` 使用独立 AppKey，不复用管理会话。
- 生产使用 HTTPS、受信任证书、防火墙与网络分段。管理端口和 `/actuator/**` 仅允许内网或回环访问。
- 限制 Console、Runtime、数据库和上游 Provider 的出网目标，避免将不可信 Base URL 或 Proxy 暴露给普通配置人员。
- 当前上游为 OpenAI Compatible。其他协议会明确拒绝，避免错误协议报文泄露到非预期端点。

## 数据与日志安全

- `mux_request_log` 可能包含调用身份、上游模型、Token、成本、Route Trace 和按策略保存的 Prompt/Response。
- 默认 `promptPolicy=masked`。启用 `full` 或 `sample` 前必须完成数据分级、访问控制与保存期限评审。
- 配置 `logRetentionDays` 并验证清理任务；备份中的日志与快照也按同等敏感级别保护。
- 日志和审计不得记录明文 Secret、授权头、完整 SQL、私钥或堆栈。排障优先使用 `x-request-id`，不要共享原始敏感报文。
- `standard_usd`、`billing_usd`、`cost_usd` 仅用于内部分析，不生成客户账单或结算记录。

## 数据库与备份

- 生产建议按表级写责分离：Console 写配置，Runtime 仅读配置并写运行表；其他组件按需只读。
- H2 仅用于本地或小规模受控环境。生产数据库选择、账号权限、加密、备份和恢复演练按组织规范执行。
- 备份前停止 Console 与 Runtime，确保数据库文件与 WAL/锁文件一致；恢复后先验证 schema 和只读健康检查，再开放流量。

## 漏洞修复与依赖

- 安全修复先补复现测试，再修改实现，保留异常语义和兼容说明。
- 不在修复中记录或打印敏感值，不以“删除日志”替代根因修复。
- 第三方依赖升级需评估影响，不通过降低 JDK/enforcer 或删除安全校验来规避构建失败。
- 安全相关版本发布应在 `CHANGELOG.md` 标注影响、升级步骤和配置迁移要求。

## 当前安全边界

- 多实例配额与限流为实例级计数，不能作为跨实例精确安全额度。
- Responses/Messages/Embeddings 当前仅非流式；Chat Completions 才支持 SSE。
- Debug 会产生真实上游调用和费用，生产建议 `debugInvokeEnabled=false` 或收紧 `debugDailyQuota`。
- 连接超时需在无透明代理环境验证，不能将代理返回的 HTTP 错误当作连接超时证据。
