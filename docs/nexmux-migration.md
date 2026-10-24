# Nexmux 升级与迁移指南

> 用于升级 Nexmux、数据库与配置。发布流程见 [RELEASE.md](../RELEASE.md)，恢复操作见 [运行手册](./nexmux-runbook.md)。

## 1. 通用原则

- 升级前备份数据库、配置、当前发行包和版本信息，先在隔离环境验证。
- 不直接用新包覆盖唯一数据副本，不在未知 schema 差异下强制启动生产。
- `CREATE/ALTER IF NOT EXISTS` 提供幂等迁移，但不等于应用回滚或数据回滚。
- 代码回滚、数据库回滚和配置回滚分别评估，公开历史不重写。

## 2. Schema 变更

### `config_revision`

Route 和 Client 新增 `config_revision`：

- Route 发布/回滚递增修订号，版本历史复用 `mux_config_audit_log` 的发布差异。
- Client 授权、配额和限流共用策略修订号，更新时记录前后快照。
- 旧数据为空时按既有发布历史或初始版本处理；新发布会自动递增。
- 回滚应用版本不会删除历史修订，回滚本身生成新修订。

### `standard_usd`

请求日志新增 `standard_usd`：

- `standard_usd` = 命中档与 Model 时段倍率后的标准参考金额。
- `billing_usd` = 内部参考消费金额（标准金额 × M）。
- `cost_usd` = 供应商成本估算（标准金额 × C）。
- 旧行缺少标准金额时，聚合查询回退到 `cost_usd` 作为兼容展示，不应据此重算或篡改历史证据。
- 新金额必须可从 Pricing Snapshot 追溯版本、命中档、C/M、币种和 Binding。

### 默认参数与定价

- `default_params` 由 Console 保存，Runtime 按“Model 默认值 < 客户端显式参数”合并已知 OpenAI 字段。
- `pricing_spec` 保存时生成 `priceVersion`、`priceEffective` 和历史 `priceVersions`，不新建价格版本表。
- 老定价只含平铺 input/output 时可继续使用；分档、缓存和时段规则按新计算语义执行。

## 3. 凭证迁移

- 未配置 `NEXMUX_CREDENTIAL_SECRET` 时，本地旧明文凭证可兼容读取并启动告警；生产必须配置。
- 配置后新写/轮换凭证使用 `aesgcm:v1:` 密文；存量明文下次保存自动升级。
- 更换密钥前先用旧密钥解密并轮换全部 Provider 凭证，两个进程保持同值。
- 不将密文当 API Key 发往上游；缺失/错误密钥应明确失败，而不是回退到错误明文。

## 4. 协议兼容

- `/v1/chat/completions` 保持非流式与 SSE；`/v1/responses`、`/v1/messages`、`/v1/embeddings` 当前仅非流式。
- 新协议的 `model` 仍为 Route Alias；每个 Alias 的 Ingress Path 与 Operation 必须匹配。
- Responses/Messages/Embeddings 的 `stream=true` 当前返回 400 `NMX010001`，不是路由不存在。
- 非 OpenAI 上游协议会明确拒绝，老配置需核对 `defaultProtocol`，不能依赖错误协议被静默兼容。

## 5. 配置与安全

- `log-retention-initial-delay-minutes` 默认 10 分钟，`log-retention-period-minutes` 默认 1440 分钟，旧配置无需修改。
- `sample` 为确定性系统抽样，与旧随机/空实现相比，抽样命中是可重复且可解释的。
- 外部 IAM、HTTPS、密钥和数据库权限属于生产前置条件，不能从本地 dev-session 直接推断生产安全配置。

## 6. 升级执行顺序

1. 备份并记录旧版本、commit、DB URL、配置和 AppKey 清单。
2. 在隔离库启动新 Console，验证 schema 与配置，再启动新 Runtime。
3. 验证 AppKey、Alias、调用、配额/限流、Pricing Snapshot、Request ID 与版本回滚。
4. 运行相关端测，记录 FAIL/BLOCKED 和环境说明。
5. 备份可恢复后，再安排生产切换与观察期。

## 7. 回退策略

- 先停止新进程，再启动保留的旧发行包，保留原数据库与配置备份。
- 新增列通常可保留，不强制降级删除；不要为回退而删除新修订或新价格历史。
- 如果新语义涉及金额、凭证或权限，优先恢复配置/密钥兼容性，再评估代码回退。
- 回退后重新执行关键链路验证，并记录原因、影响和后续修复计划。
