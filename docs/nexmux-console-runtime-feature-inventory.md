# Nexmux Console → Runtime 功能点清单

> 目的：作为 Console 配置生效性端测的功能基线，避免只验证“能调用”而遗漏配置项。
> 范围：`nexmux-ui` / Console `:18082` / Runtime `:18083`；测试辅助 Runtime 使用相同发行包和数据库。
> 依据：产品 PRD、`nexmux-runtime-design.md`、当前页面 DSL 与 Java 契约。
> 不包含：条件路由、灰度、智能路由、熔断/Failover、分布式限流、SLA 告警等明确属于后续阶段的能力。

## 1. Runtime 接入与基本契约

| 功能点 | Console 配置/操作 | Runtime 预期 |
|---|---|---|
| Chat Completions | Provider、Model、Route、Client 完整配置 | 非流式返回上游响应 |
| Streaming | 请求 `stream=true` | SSE 透传并采集 usage |
| 模型发现 | Route Alias + Client 授权 | `/v1/models` 仅返回可用 Alias |
| 上游错误分层 | Provider 返回 4xx/5xx | 4xx 透传；5xx/网络/超时归约 502 |
| 配置快照 | Console 修改并发布 | Runtime 周期刷新；请求链路不逐次读配置表 |

## 2. Provider

| 功能点 | 配置/操作 | 预期 |
|---|---|---|
| 基础资料 | Code、名称、类型、Vendor、图标、描述 | 保存并回显；不直接改变选路 |
| Base URL | 连接与凭证 | 决定实际上游地址 |
| Default Protocol | 上游协议 | 当前仅 OpenAI Compatible 可执行，其他协议不得伪装成功 |
| Region | 区域 | 元数据；不参与当前调用 |
| Timeout | 读取超时 | 控制上游读取等待 |
| Proxy | HTTP 代理 | 保存、回显并用于上游连接 |
| API Key | 凭证 | 不明文回显；Runtime 解密/读取后用于鉴权 |
| 启停 | Provider 状态 | 停用后不可作为上游 |
| 连接测试 | 手动操作 | 返回真实探测结果，不把 TCP 可达等同于模型调用成功 |
| 拉取模型 | 上游 `/models` | 返回上游目录并标记本地状态 |
| 删除与引用 | 删除 Provider | 有 Model/Binding 引用时阻断 |

## 3. Model、Binding、参数与定价

| 功能点 | 配置/操作 | 预期 |
|---|---|---|
| 基础资料 | Code、名称、类型、上下文、模态、Tags、Metadata | 保存并回显 |
| 主 Binding | Provider、Provider Model ID、协议、权重、C/M、状态 | 决定上游模型、选择和金额倍率 |
| 扩展 Binding | 额外 Provider/Model、权重、C/M、状态 | 参与同 Model 内选择 |
| Model 启停 | 状态 | 停用恒不参与 Runtime，不受健康过滤开关影响 |
| 默认参数 | temperature、top_p、max_tokens 等 | 保存并合并；客户端显式参数优先 |
| Capability | Tools、Reasoning、模态等 | 不兼容能力不得被静默当作支持 |
| 标准分档定价 | UPTO、INPUT、OUTPUT | 按 input tokens 命中档位 |
| 缓存价格 | CACHE READ/WRITE | 进入用量与金额计算 |
| 时段倍率 | 日期、时间、倍率 | 按请求时间影响金额 |
| 定价版本 | Current/Historical/Future、Diff | 改价生成新版本；历史请求快照不变 |
| 调用测试 | Model invoke test | 产生真实上游请求，不允许固定成功 |
| 启用校验 | 启用 Model | Provider、凭证、Binding、连接满足前置条件 |
| 删除与引用 | 删除 Model | 有 Route 引用时阻断或给出明确冲突 |

## 4. Route

| 功能点 | 配置/操作 | 预期 |
|---|---|---|
| 草稿与发布 | 保存、发布、启停 | 仅已发布快照进入 Runtime |
| Alias | 逻辑模型名 | 对外 `model` 字段按新 Alias 解析 |
| Ingress | Path、Protocol、Operation | 仅匹配声明入口；未支持协议明确失败 |
| 候选状态 | 每行 enabled/disabled | 停用候选不参与选择 |
| FIXED | 策略 | 固定候选 |
| WEIGHTED | Route Weight | 权重影响同层候选选择 |
| ROUND_ROBIN | 策略 | 候选轮询 |
| PRIORITY | P0/P1/P2 | 仅最优优先级层参与 |
| HASH/RANDOM | 策略与分桶键 | HASH 稳定；RANDOM 覆盖候选 |
| Sticky Session | 会话粘性与 user | 同一会话稳定命中 |
| 健康过滤 | healthFilter | 开启剔除不健康；关闭允许实际调用并暴露失败 |
| 候选 C/M | 行内倍率 | 按当前产品契约决定是否允许；若 UI 提供则必须生效且需与 PRD 对齐 |
| Binding C/M 继承 | 留空 | 继承 Model Binding 倍率 |
| 发布校验 | 候选、模型、Binding、协议 | 无效配置不得替换当前运行快照 |
| 运行版本 | revision、历史 Diff、回滚 | 可查看并恢复历史运行配置 |

## 5. Client、AppKey、模板与访问策略

| 功能点 | 配置/操作 | 预期 |
|---|---|---|
| AppKey | 创建、轮换 | 仅一次展示；旧 Key 按契约失效 |
| Client 启停/有效期 | 状态、expiresAt | 停用/过期返回 401 |
| Route 授权 | routeIds | 未授权返回 403 |
| 配额窗口 | lifetime/cyclic | 窗口内累计，周期切换重置 |
| 配额指标 | REQUEST、INPUT/OUTPUT/TOTAL_TOKEN、COST | 按指标计量 |
| 配额模式 | soft/hard | soft 标记；hard 429 |
| 并发配额 | CONCURRENT | 当前设计归限流，不应作为有效配额选项 |
| RPM/TPM | 限流 | 超限 429；分钟窗口响应带 Retry-After |
| CONCURRENT | 并发信号量 | 并发超过限制时拒绝 |
| Burst | 突发余量 | 按契约增加窗口容量 |
| Scope | All/指定 Route | 仅作用于声明范围 |
| 规则状态 | enabled/disabled | 停用规则不生效 |
| Client Template | 路由、配额、限流、有效期 | 创建 Client 时复制 |
| 模板变更 | 编辑模板 | 不回溯修改已创建 Client |
| 策略发布 | 授权/配额/限流原子发布 | 产生 config revision；失败不部分生效 |

## 6. 全局服务设置

| 设置 | Runtime 预期 |
|---|---|
| requestTimeoutMs | Provider 未覆盖时作为读取超时 |
| upstreamConnectTimeoutMs | 控制上游连接超时 |
| promptPolicy=none/masked/sample/full | 按策略留存请求/响应；敏感信息受控 |
| logRetentionDays | 到期运行日志被清理 |
| debugInvokeEnabled/debugDailyQuota | 仅约束 Console 调试，不改变业务 Runtime 配额 |

## 7. 观测、金额与运行韧性

| 功能点 | 预期 |
|---|---|
| Request ID | 响应头与调用日志一一对应 |
| 成功/失败日志 | 身份、上游、状态、延迟、usage、错误完整 |
| 前置拒绝 | 不消耗 REQUEST 配额；日志口径符合设计 |
| Route Trace | 记录策略、候选、Binding、健康与配额上下文 |
| Pricing Snapshot | 记录价格版本、命中档位、C/M、币种、生效时间 |
| 金额口径 | 标准参考金额、内部参考消费金额、供应商成本估算分别正确 |
| 来源 | RUNTIME 与 DEBUG 可区分 |
| Console 故障 | 已加载快照的 Runtime 继续服务 |
| Runtime 重启 | 配额从运行日志校准，不重复/漏计 |
| 多实例 | 当前仅实例级计数；不得宣称分布式一致 |

## 8. 验收口径

- **PASS**：Console 操作真实持久化，Runtime 行为与契约一致，并有响应/日志/上游桩证据。
- **FAIL**：已执行且行为不符合契约，包括假成功、配置丢失、状态不生效、金额错误或错误码不符。
- **BLOCKED**：因前置功能缺失无法执行；不等于 PASS，也不等于已验证。
- **N/A**：明确不属于本轮范围，必须写明原因。
- 直接数据库写入只允许用于准备定价、健康、历史时间等 fixture 或独立验证 Runtime；不能据此判 Console 配置链通过。
