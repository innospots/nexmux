# Nexmux 领域分层与类型结构（Entity / Request / Vo / Value）

## 1. 文档定位

- **状态**：草案（2026-09-26 字段与 PRD 对齐修订）
- **归属模块**：`nexmux-kernel`
- **上级文档**：[nexmux-architecture-design.md](./nexmux-architecture-design.md)、[nexmux-p0-console-design.md](./nexmux-p0-console-design.md)
- **控制台入口**：[nexmux-console-entry-plugin-design.md](./nexmux-console-entry-plugin-design.md)（Entity 与菜单/pageKey 映射）
- **产品依据**：《管理控制台产品功能设计文档》§7.1–§7.2、线框 Provider/Model 页

## 2. 建模决策摘要

| 项 | 结论 |
|----|------|
| 表前缀 | `mux_*` |
| 列命名 | 存结构化文本用 `*_spec` / `*_schedule` / `*_bindings` / `change_diff` 等；**禁止** `*_json` 后缀 |
| Model 绑定 | 无 binding 子表；主绑定列 + `extra_bindings`（CLOB） |
| Route | 无版本子表；`route_spec` + `published_route_spec` |
| Client 授权 | `authorized_route_ids`（CLOB，序列化 ID 列表） |
| Client 模板 | `mux_client_template` |
| Model 定价 | 单列 `pricing_spec`（CLOB），承载分档/时段/币种等；**无定价子表** |
| 乐观锁 | **不使用** `row_version` |

## 3. 分层与命名

| 类型 | 包 | 说明 |
|------|-----|------|
| Entity | `domain.entity` | 与表 1:1 |
| Request / Vo | `domain.request` / `domain.vo` | `record` |
| Value | `domain.value` | 与 CLOB 列同构的内存结构 |

审计列：`BaseEntity` → `created_at`, `updated_at`, `created_by`, `updated_by`。

---

## 4. Provider 域

### 4.1 `ProviderEntity` → `mux_provider`

| Java 属性 | 列名 | 类型 | 可空 | 说明 |
|-----------|------|------|------|------|
| providerId | provider_id | varchar(32) | N | PK，`pv` |
| providerCode | provider_code | varchar(64) | N | 连接实例编码；`uk_mux_provider_code` |
| displayName | display_name | varchar(128) | N | 控制台展示名（可与品牌名不同） |
| providerType | provider_type | varchar(16) | N | COMMERCIAL / CLOUD / PRIVATE / CUSTOM |
| defaultProtocol | default_protocol | varchar(32) | N | 默认 Adapter 协议（PRD Default Protocol） |
| description | description | varchar(500) | Y | |
| vendorCode | vendor_code | varchar(64) | N | **实际供应商/品牌** catalog 键（如 `openai`、`aliyun`）；Logo 默认图、厂商筛选 |
| sourceVendorCode | source_vendor_code | varchar(64) | N | **对外归因的实际供应商**（计费/展示来源）；默认等于 `vendor_code`，代理场景可不同 |
| iconUri | icon_uri | varchar(1024) | Y | 供应商图标 URL 或对象键；空则用 `vendor_code` 内置图标 |
| baseUrl | base_url | varchar(1024) | N | |
| region | region | varchar(64) | Y | 区域 |
| timeoutMs | timeout_ms | int | Y | 默认 60000 |
| proxy | proxy | varchar(512) | Y | |
| capabilityExtension | capability_extension | clob | Y | 能力扩展（结构化文本；UI 可切表单/文本模式） |
| extensionConfig | extension_config | clob | Y | 连接扩展配置 |
| status | status | varchar(16) | N | ENABLED / DISABLED |

> PRD 未单独列 `ingress_protocol`：对外 Ingress 在 **Route** 上配置；Provider 以 `default_protocol` + `base_url` 描述上游。

### 4.2 `ProviderCredentialEntity` → `mux_provider_credential`

| Java 属性 | 列名 | 类型 | 可空 | 说明 |
|-----------|------|------|------|------|
| credentialId | credential_id | varchar(32) | N | PK，`pc` |
| providerId | provider_id | varchar(32) | N | `uk_mux_provider_credential_provider` |
| credentialMode | credential_mode | varchar(16) | N | API_KEY / SECRET_REFERENCE |
| ciphertext | ciphertext | clob | Y | API Key 密文（`API_KEY` 时必填） |
| secretReference | secret_reference | varchar(512) | Y | Vault/KMS 引用（与 API Key 二选一） |
| keyVersion | key_version | int | N | |
| rotatedAt | rotated_at | timestamp | Y | |

### 4.3 Vo 补充

`ProviderVo` 增加：`vendorCode`, `sourceVendorCode`, `iconUri`, `providerType`, `defaultProtocol`, `region`, `timeoutMs`, `hasCredential`。

---

## 5. Model 域

### 5.1 绑定（无子表）

| 存储 | 说明 |
|------|------|
| 主绑定列 | `primary_*`：provider、上游 model id、weight、C/M 倍率、协议覆盖、启用态 |
| `extra_bindings` | CLOB：`List<ModelBindingItem>` 序列化；无扩展时为 `[]` |

`ModelBindingItem`（value）：

| 字段 | 说明 |
|------|------|
| providerId | |
| providerModelId | 上游实际模型 ID |
| upstreamProtocol | 可覆盖 Provider `default_protocol` |
| weight | Binding 权重（与同 Model 下其他 Binding 合计 100） |
| costMultiplier | 成本倍率 C |
| referenceMultiplier | 内部参考倍率 M |
| timeRateRules | 可选；Binding 级时段倍率（成本/参考分列） |
| enabled | |
| healthStatus | 运行探测回写 |

### 5.2 `ModelEntity` → `mux_model`

| Java 属性 | 列名 | 类型 | 可空 | 说明 |
|-----------|------|------|------|------|
| modelId | model_id | varchar(32) | N | PK，`md` |
| modelCode | model_code | varchar(64) | N | `uk_mux_model_code` |
| displayName | display_name | varchar(128) | N | |
| modelType | model_type | varchar(32) | N | LLM / IMAGE / AUDIO / EMBEDDING … |
| status | status | varchar(16) | N | DRAFT / ENABLED / DISABLED |
| iconUri | icon_uri | varchar(1024) | Y | 模型图标；空则继承主绑定 Provider 的 `icon_uri` |
| releaseDate | release_date | date | Y | |
| contextLength | context_length | bigint | Y | Token |
| maxOutputTokens | max_output_tokens | bigint | Y | |
| inputModalities | input_modalities | varchar(256) | Y | 如 `text,image,video` |
| outputModalities | output_modalities | varchar(256) | Y | |
| capabilityTags | capability_tags | clob | Y | 能力标签列表（结构化文本） |
| tags | tags | clob | Y | 业务标签 |
| description | description | varchar(2000) | Y | |
| metadata | metadata | clob | Y | 扩展元数据（PRD Metadata） |
| defaultParams | default_params | clob | Y | 默认执行参数 |
| primaryProviderId | primary_provider_id | varchar(32) | Y | |
| primaryProviderModelId | primary_provider_model_id | varchar(256) | Y | |
| primaryUpstreamProtocol | primary_upstream_protocol | varchar(32) | Y | |
| primaryBindingWeight | primary_binding_weight | int | Y | 默认 100（仅主绑定时） |
| primaryCostMultiplier | primary_cost_multiplier | decimal(10,4) | Y | C，默认 1 |
| primaryReferenceMultiplier | primary_reference_multiplier | decimal(10,4) | Y | M，默认 1 |
| primaryBindingEnabled | primary_binding_enabled | boolean | N | |
| extraBindings | extra_bindings | clob | N | 见 §5.1 |
| pricingSpec | pricing_spec | clob | Y | 定价 Tab 整包（分档表、时段倍率、币种、版本标签等）；与 `ModelPricingSpec` value 同构 |

Binding 层 C/M 仍在主绑定列与 `extra_bindings` 内；Runtime 计费时合并 Model `pricing_spec` + 命中 Binding 倍率。

---

## 6. Route 域

| Java 属性 | 列名 | 类型 | 可空 | 说明 |
|-----------|------|------|------|------|
| routeId | route_id | varchar(32) | N | PK |
| alias | alias | varchar(64) | N | `uk_mux_route_alias` |
| displayName | display_name | varchar(128) | N | |
| status | status | varchar(16) | N | DRAFT / PUBLISHED / DISABLED |
| routeSpec | route_spec | clob | N | 编辑中 `RouteSpec` |
| publishedRouteSpec | published_route_spec | clob | Y | Runtime 读取 |
| publishedAt | published_at | timestamp | Y | |
| publishedBy | published_by | varchar(64) | Y | |

---

## 7. 定价（单列，满足产品目标）

产品定价含分档、时段倍率、币种与版本展示（§7.2.4），工程侧 **不拆表**：

- 持久化：`mux_model.pricing_spec`（CLOB）。
- 内存结构：`ModelPricingSpec`（`domain.value`），含 `currency`、`versionLabel`、`effectiveAt`、`tiers[]`、`timeRules[]` 等，与 UI 表单/「JSON 模式」一致。
- 保存定价：更新 `pricing_spec` + 写 `mux_config_audit_log`（`change_diff` 可支撑「版本历史 Diff」；**不**另建价格版本表）。
- P1 成本明细：`pricing_snapshot` 仍由 Runtime 在请求侧落库（列名沿用 PRD），内容为当时 Model `pricing_spec` + Binding 倍率的快照。

---

## 8. Access 域

### 8.1 `ClientEntity` → `mux_client`

| Java 属性 | 列名 | 类型 | 可空 | 说明 |
|-----------|------|------|------|------|
| clientId | client_id | varchar(32) | N | PK |
| clientCode | client_code | varchar(64) | N | |
| displayName | display_name | varchar(128) | N | |
| status | status | varchar(16) | N | |
| appKeyHash | app_key_hash | varchar(128) | N | |
| appKeySalt | app_key_salt | varchar(64) | N | |
| appKeyVersion | app_key_version | int | N | |
| authorizedRouteIds | authorized_route_ids | clob | N | 路由 ID 列表（序列化） |
| quotaSpec | quota_spec | clob | Y | |
| rateLimitSpec | rate_limit_spec | clob | Y | |
| sourceTemplateId | source_template_id | varchar(32) | Y | P1 |

### 8.2 `ClientTemplateEntity` → `mux_client_template`

字段同 Client 策略部分 + `template_code`、`tags`、`valid_from` / `valid_to`；列 `quota_spec`、`rate_limit_spec`、`authorized_route_ids` 命名与 Client 一致（无 `tags_json`）。

---

## 9. Audit / Ops

| Entity | 表 | 要点 |
|--------|-----|------|
| ConfigAuditLogEntity | mux_config_audit_log | `change_diff`（原 diff_json 命名已废） |
| RequestLogEntity | mux_request_log | `route_trace`；P1 成本可加 `pricing_snapshot` |

---

## 10. 产品文档对照 — 仍须在后续设计/实现补齐的域

以下 PRD 能力 **尚未** 在本文件展开完整 Entity（避免一次堆砌）；实现前须单列章节或迭代 P0 范围：

| PRD 能力 | 说明 | 建议 |
|----------|------|------|
| Model Profile | P1 参数预设、revision | `mux_model_profile` + `mux_model_profile_revision`（P1） |
| Platform Settings | P1 服务设置 | `mux_platform_setting` 键值表 |
| Usage 聚合 | P1 多维用量 | 只读 OLAP/汇总表或查询 Runtime 仓 |
| Cost 明细 | P1 `pricing_snapshot` | `mux_request_cost` 或扩 `mux_request_log` |
| Route 条件/灰度/P2 智能路由 | 策略字段 | 扩 `RouteSpec` value，无新表 |
| Provider 模板导入 | 内置目录 | `mux_provider_catalog` 或 classpath 目录 + `vendor_code` 关联 |
| 健康/熔断 Runtime 态 | §24.3 | **不落** Console 配置库 |

P0 线框已覆盖且本文已建模：**Provider、Model（含 `pricing_spec` 与绑定列）、Route、Client、Template、Audit、RequestLog**。

---

## 11. grill-me 决策（追加）

| # | 决策 | 状态 |
|---|------|------|
| G13 | `vendor_code` + `source_vendor_code` + `icon_uri` on Provider | 已采纳 |
| G14 | Model `icon_uri` + PRD 基本信息/模态/参数列 | 已采纳 |
| G15 | 列名禁止 `*_json` | 已采纳 |
| G16 | 定价：`pricing_spec` 单列；无定价子表 | 已采纳 |
| G17 | 不使用乐观锁 `row_version` | 已采纳 |

---

## 12. 测试范围

| 类 | 断言 |
|----|------|
| `ProviderEntityContractTest` | `vendor_code`, `source_vendor_code`, `icon_uri` |
| `ModelEntityContractTest` | 无 `*_json` 列名；`extra_bindings`、`pricing_spec` |
| `RouteEntityContractTest` | `route_spec`, `published_route_spec` |
