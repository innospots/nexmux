# Nexmux UI 数据接口清单（已实现部分）

- **日期**：2026-10-05（随实现批次滚动更新）
- **设计依据**：[nexmux-ui-data-api-design.md](./nexmux-ui-data-api-design.md)
- **覆盖范围**：已切换真实后端的域——**Provider（B1）**、**Model**、**Route（B2）**、**Client + ClientTemplate（B4）**；其余域仍走 mock（见 §5）
- **分层**：

```text
浏览器 → nexmux-ui BFF（/api/data/**，Next 服务端，cookie 会话鉴权）
       → nexmux-spring 后端（/api/nexmux/**，R<T> 包装，dev-session 或 Bearer）
```

---

## 1. 后端接口（nexmux-spring，基路径 `/api/nexmux`）

### 通用约定

| 项 | 说明 |
|----|------|
| 响应包装 | `R<T> = { success: boolean, code: string, message: string, data: T, display: I18nObject \| null }`；成功时 `code="OK"` |
| 分页包装 | `PageResult<T> = { records: T[], pageNo, pageSize, total, pages }` |
| 鉴权 | `@NexusAuthenticatedApi`；当前 dev-session（security.enabled=false），正式环境 Bearer Token |
| 错误码 | 模块码 `NMX`：`NMX130001`=资源不存在(404)、`NMX070001`=业务键重复(409)、`NMX020001`=前置条件不满足(422)、`NMX020002`=路由未发布(503)、`NMX040001`=密钥操作被拒(403) |

### 1.1 Provider（提供方）

#### GET `/providers` — 分页查询提供方

| Query 参数 | 类型 | 必填 | 说明 |
|-----------|------|------|------|
| pageNo | long | 否 | 默认 1 |
| pageSize | long | 否 | 默认 20 |
| keyword | string | 否 | 名称 / 编码 / baseUrl 模糊 |
| status | string | 否 | `ENABLED` / `DISABLED` |
| keyStatus | string | 否 | `pending` / `none`（密钥状态过滤） |
| types | string | 否 | 厂商编码，逗号分隔（匹配 `vendor_code`） |

**响应** `R<PageResult<ProviderVo>>`：

```jsonc
{
  "providerId": "pv909e78a0...",      // 技术主键
  "providerCode": "dashscope",        // 稳定业务键
  "displayName": "阿里云百炼",
  "providerType": "OPENAI",           // COMMERCIAL/CLOUD/PRIVATE/CUSTOM/OPENAI…
  "defaultProtocol": "openai-completions",
  "vendorCode": "dashscope",
  "sourceVendorCode": "dashscope",
  "iconUri": null,
  "baseUrl": "https://...",
  "region": "cn-hangzhou",
  "timeoutMs": 60000,
  "status": "ENABLED",                // ENABLED / DISABLED
  "keyStatus": "none",                // configured / pending / none（由凭证表派生）
  "modelCount": 0                     // 主绑定引用该提供方的模型数
}
```

#### GET `/providers/{providerId}` — 按 ID 查询概要

响应 `R<ProviderVo>`（结构同上）。

#### GET `/providers/by-code/{providerCode}` — 按编码查询详情

**响应** `R<ProviderDetailVo>`（ProviderVo 全部字段，外加）：

```jsonc
{
  "proxy": null,
  "description": "...",
  "capabilityExtension": null,        // 能力扩展 JSON 原文
  "keyVersion": 1,                    // 无凭证为 null
  "rotatedAt": null,                  // 密钥轮换时间
  "models": [{                        // 主绑定在该提供方下的模型行
    "modelCode": "qwen3.5-flash",
    "displayName": "Qwen3.5 Flash",
    "modelType": "LLM",
    "providerModelId": "qwen3.5-flash",
    "status": "ENABLED"
  }],
  "createdAt": "2026-10-05T08:00:00",
  "updatedAt": "2026-10-05T08:00:00",
  "updatedBy": "system"
}
```

未命中：`success=false, code=NMX130001`。

#### POST `/providers` — 创建提供方

**请求体** `ProviderCreateRequest`：

```jsonc
{
  "providerCode": "dashscope",        // 必填，唯一；重复 → NMX070001(409)
  "displayName": "阿里云百炼",         // 必填
  "providerType": "OPENAI",
  "defaultProtocol": "OpenAI / Chat Completions",
  "vendorCode": "dashscope",          // 必填
  "sourceVendorCode": "dashscope",    // 空 = 同 vendorCode
  "iconUri": null,
  "baseUrl": "https://...",           // 必填
  "region": "cn-hangzhou",
  "timeoutMs": 60000                  // 空 = 60000
}
```

**响应** `R<ProviderVo>`（初始 `status=ENABLED`）。

#### PUT `/providers/{providerId}` — 更新提供方

**请求体** `ProviderUpdateRequest = { displayName, baseUrl, region, timeoutMs }`；**响应** `R<ProviderVo>`。

#### POST `/providers/{providerId}/enable` · `/disable` — 启停

无请求体；**响应** `R<ProviderVo>`。

#### POST `/providers/{providerId}/connection-test` — 连通性测试

无请求体。无凭证时快速失败（不探测）；有凭证时对 baseUrl 做 TCP 连接探测。

**响应** `R<ConnectionTestVo>`：

```jsonc
{ "ok": false, "latencyMs": 0, "detail": "未配置 API Key" }
```

#### POST `/providers/{providerId}/pull-models` — 拉取上游模型

无请求体。OpenAI 兼容 `GET {baseUrl}/models`（Bearer = 凭证密文）。

**响应** `R<UpstreamModelVo[]>`：

```jsonc
[{ "providerModelId": "qwen3.5-flash", "existsLocally": true }]
```

前置失败（无凭证 / 上游不可达）：`code=NMX020001`(422)。

---

### 1.2 Model（逻辑模型）

> **code 含 `/`**：catalog 模型编码形如 `openai/o4-mini`；`by-code` 路径用 `{modelCode: .+}` 多段匹配。

#### GET `/models` — 分页查询模型

| Query 参数 | 类型 | 必填 | 说明 |
|-----------|------|------|------|
| pageNo / pageSize | long | 否 | 默认 1 / 20 |
| type | string | 否 | `LLM` / `Embedding` / `Image` / `Audio` |
| providers | string | 否 | 厂商编码，逗号分隔（先 code→id 再过滤主绑定） |

**响应** `R<PageResult<ModelVo>>`：

```jsonc
{
  "modelId": "md...",
  "modelCode": "openai/o4-mini",
  "displayName": "o4-mini",
  "status": "DRAFT",                  // DRAFT / ENABLED / DISABLED
  "modelType": "LLM",                 // LLM / Embedding / Image / Audio
  "contextLength": 200000,
  "maxOutputTokens": 100000,
  "primaryProviderId": "pv...",
  "pricingSpec": "{...}",             // 定价 JSON 原文（tiers 整包或 catalog 平铺单价）
  "extraBindings": "[]"               // 扩展绑定 JSON 原文（ModelBindingItem[]）
}
```

#### GET `/models/{modelId}` — 按 ID 查询概要

响应 `R<ModelVo>`。

#### GET `/models/by-code/{modelCode: .+}` — 按编码查询详情

**响应** `R<ModelDetailVo>`（ModelVo 全部字段，外加）：

```jsonc
{
  "iconUri": null,
  "releaseDate": "2026-05-01",
  "inputModalities": "text,image",    // CSV
  "outputModalities": "text",
  "capabilityTags": "[\"reasoning\",\"vision\"]",  // JSON 数组原文
  "tags": "[\"pi-ai-catalog\",\"openai\"]",
  "description": null,
  "metadataSpec": null,               // 元数据 JSON 原文
  "defaultParams": null,              // 默认参数 JSON 原文
  "primaryProviderModelId": "o4-mini",
  "primaryUpstreamProtocol": "openai-responses",
  "primaryBindingWeight": 100,
  "primaryCostMultiplier": 1.0,       // 成本倍率 C
  "primaryReferenceMultiplier": 1.0,  // 参考倍率 M
  "primaryBindingEnabled": true,
  "createdAt": "...", "updatedAt": "...", "updatedBy": "system"
}
```

#### POST `/models` — 创建模型

**请求体** `ModelCreateRequest`：

```jsonc
{
  "modelCode": "qwen3.5-flash",       // 必填，唯一；重复 → NMX070001
  "displayName": "Qwen3.5 Flash",     // 必填
  "modelType": "LLM",
  "primaryProviderCode": "dashscope", // 必填，按业务键关联；未知 → NMX130001
  "primaryProviderModelId": "qwen3.5-flash",
  "primaryUpstreamProtocol": null,    // 空 = 继承 Provider 默认协议
  "contextLength": 256000,
  "maxOutputTokens": 32000,
  "inputModalities": ["text", "image"],
  "outputModalities": ["text"],
  "tags": ["general"],
  "description": "..."
}
```

**响应** `R<ModelVo>`（初始 `status=DRAFT`，主绑定 weight=100、C/M=1.0、enabled）。

#### PUT `/models/{modelId}` — 更新模型

**请求体** `ModelUpdateRequest = { displayName?, description?, contextLength?, maxOutputTokens?, inputModalities?, outputModalities?, tags? }`（null 字段不改）；**响应** `R<ModelVo>`。

#### POST `/models/{modelId}/enable` · `/disable` — 启停

无请求体；**响应** `R<ModelVo>`。

#### POST `/models/{modelId}/invoke-test` — 调用测试（占位）

**响应** `R<Boolean>`（当前恒 true，真实调用随调试台批次落地）。

---

### 1.3 Route（路由，B2）

> 业务键：`route_code`（`uk_mux_route_code`，B2 新增列，存量行以 alias 回填）；`alias` 为对外逻辑模型名，与 code 语义分离。

#### GET `/routes` — 分页查询路由

| Query 参数 | 类型 | 必填 | 说明 |
|-----------|------|------|------|
| pageNo / pageSize | long | 否 | 默认 1 / 20 |
| status | string | 否 | `PUBLISHED` / `DISABLED` / `DRAFT`（SQL） |
| type | string | 否 | `single` / `pool`（按候选数内存过滤） |
| tags | string | 否 | 标签，逗号分隔（取交集，内存过滤） |

**响应** `R<PageResult<RouteVo>>`：

```jsonc
{
  "routeId": "rt...",
  "routeCode": "general-chat",       // 稳定业务键（URL 用）
  "alias": "general",               // 对外逻辑模型名
  "displayName": "通用对话路由",
  "status": "PUBLISHED",            // DRAFT / PUBLISHED / DISABLED
  "routeSpec": { /* RouteSpec，见下 */ },
  "publishedAt": "2026-10-05T11:00:00"
}
```

`RouteSpec`（schema v1，B2 扩展字段向后兼容）：

```jsonc
{
  "alias": "general",
  "ingress": "/v1/chat/completions",  // 请求路径
  "strategyType": "FIXED",           // FIXED/WEIGHTED/ROUND_ROBIN/PRIORITY/HASH/RANDOM
  "candidates": [{
    "modelCode": "openai/o4-mini",
    "weight": 100,
    "enabled": true,                  // B2 新增，null = 启用
    "priority": "P0",                 // B2 新增，P0/P1/P2
    "costRate": 1.0,                  // B2 新增，null = 继承 Model
    "billingRate": 1.2                // B2 新增
  }],
  "filterUnhealthyBindings": true,
  "schemaVersion": 1,
  "description": "...",               // B2 新增
  "protocol": "OpenAI / Chat Completions",  // B2 新增
  "tags": ["general"],                 // B2 新增
  "stickySession": false                // B2 新增
}
```

#### GET `/routes/{routeId}` — 按 ID 查询概要

响应 `R<RouteVo>`。

#### GET `/routes/by-code/{routeCode}` — 按编码查询详情

**响应** `R<RouteDetailVo>`（RouteVo 全部字段，外加）：

```jsonc
{
  "publishedRouteSpec": { /* 发布快照（RouteSpec），未发布为 null */ },
  "publishedBy": "admin",
  "createdAt": "...", "updatedAt": "...", "updatedBy": "..."
}
```

#### POST `/routes` — 创建路由（单页表单一次建全）

**请求体** `RouteCreateRequest`：

```jsonc
{
  "routeCode": "general-chat",       // 必填，唯一；重复 → NMX070001
  "alias": "general",                // 必填，唯一；重复 → NMX070001
  "displayName": "通用对话路由",      // 必填
  "description": "...",
  "path": "/v1/chat/completions",    // 空 = /v1/chat/completions
  "protocol": "OpenAI / Chat Completions",
  "strategyType": "FIXED",           // 空 = FIXED
  "stickySession": false,
  "healthFilter": true,               // 空 = true
  "tags": ["general"],
  "models": [/* RouteCandidate 行 */]
}
```

**响应** `R<RouteVo>`（初始 `status=DRAFT`）。

#### PUT `/routes/{routeId}` — 更新路由草稿

**请求体** `RouteUpdateRequest = { displayName?, routeSpec? }`；**响应** `R<RouteVo>`。

#### POST `/routes/{routeId}/publish` — 发布路由

**请求体** `RoutePublishRequest = { changeSummary?, operatorId? }`；草稿 `route_spec` 拷贝为 `published_route_spec`，`status=PUBLISHED`，写审计。**响应** `R<RouteVo>`。

#### POST `/routes/{routeId}/enable` · `/disable` — 启停

状态机：`DISABLED ↔ PUBLISHED`；**DRAFT 启停拒绝**（`NMX020001`，引导走发布）。disable 前经 `DependencyImpactAnalyzer` 影响分析并写审计。无请求体；**响应** `R<RouteVo>`。

---

### 1.4 Client（接入客户端，B4）

#### GET `/clients` — 分页查询客户端

| Query 参数 | 类型 | 必填 | 说明 |
|-----------|------|------|------|
| pageNo / pageSize | long | 否 | 默认 1 / 20 |
| status | string | 否 | `ENABLED` / `DISABLED` |
| name / code | string | 否 | 名称 / 编码模糊 |
| tags | string | 否 | 标签，逗号分隔（取交集，内存过滤） |

**响应** `R<PageResult<ClientVo>>`：

```jsonc
{
  "clientId": "cl...",
  "clientCode": "mobile-app",
  "displayName": "移动 App",
  "status": "ENABLED",
  "authorizedRouteIds": ["rt..."],
  "quotaSpec": "[...]",              // QuotaRule[] JSON 原文
  "rateLimitSpec": "[...]",          // RateLimitRule[] JSON 原文
  "appKeyVersion": 2,
  "appKeyId": "gw-ak-VHJTyOxe",     // 展示 ID：gw-ak- + 哈希前 8 位（不含可逆信息）
  "tags": "[\"mobile\"]",           // JSON 数组原文
  "sourceTemplateId": "ct...",
  "expiresAt": "2027-10-05T11:05:58",
  "appKeyRotatedAt": "2026-10-05T11:09:00"
}
```

#### GET `/clients/by-code/{clientCode}` — 按编码查询详情

**响应** `R<ClientDetailVo>`（ClientVo 字段，`tags` 为解析后数组，外加 `createdAt/updatedAt/updatedBy`；**不含 AppKey 明文与完整哈希**）。

#### POST `/clients` — 创建客户端

**请求体** `ClientCreateRequest`：

```jsonc
{
  "clientCode": "mobile-app",        // 必填，唯一；重复 → NMX070001
  "displayName": "移动 App",         // 必填
  "tags": ["mobile"],
  "sourceTemplateCode": "team-default",  // 模板创建：复制授权/配额/限流，按有效期规则算到期；未知模板 → NMX130001
  "routeIds": ["rt..."],             // 空白创建时（模板创建被模板覆盖）
  "expiresAt": "2027-01-01 23:59:59"  // 空白创建时
}
```

**响应** `R<ClientCreateResultVo>`：

```jsonc
{
  "client": { /* ClientVo */ },
  "appKeyPlaintext": "2ZPUBBK4..."   // 明文 AppKey，仅创建时返回一次
}
```

#### PUT `/clients/{clientId}` — 更新客户端

**请求体** `ClientUpdateRequest = { displayName?, quotaSpec?, rateLimitSpec?, tags? }`（null 不改）；**响应** `R<ClientVo>`。

#### POST `/clients/{clientId}/enable` · `/disable` — 启停

无请求体；**响应** `R<ClientVo>`。

#### POST `/clients/{clientId}/rotate-app-key` — 轮换 AppKey

无请求体；版本号 +1，回写 `app_key_rotated_at`。**响应** `R<ClientRotateResultVo>`：

```jsonc
{ "clientId": "cl...", "appKeyPlaintext": "...", "appKeyVersion": 2 }
```

#### PUT `/clients/{clientId}/route-grants` — 更新路由授权

**请求体** `ClientRouteGrantsRequest = { routeIds: string[] }`；**响应** `R<ClientVo>`。

---

### 1.5 ClientTemplate（客户端模板，B4 新增）

#### GET `/client-templates` — 分页查询模板

| Query 参数 | 说明 |
|-----------|------|
| pageNo / pageSize | 默认 1 / 20 |
| status | `ENABLED` / `DISABLED` |
| keyword | 名称 / 编码模糊 |
| tags | 标签，逗号分隔（取交集，内存过滤） |

**响应** `R<PageResult<ClientTemplateVo>>`：

```jsonc
{
  "templateId": "ct...",
  "templateCode": "team-default",
  "displayName": "团队默认",
  "status": "ENABLED",
  "tags": "[\"team-default\"]",
  "validityType": "days",            // days / fixed / never
  "validityDays": 365,
  "validTo": null,                   // fixed 时的固定到期日
  "authorizedRouteIds": ["rt..."],
  "quotaSpec": "[...]", "rateLimitSpec": "[...]",
  "createdClientCount": 1            // 按 source_template_id 实时统计
}
```

#### GET `/client-templates/by-code/{templateCode}` — 按编码查询详情

**响应** `R<ClientTemplateDetailVo>`（外加 `description`、`tags` 数组、审计字段）。

#### POST `/client-templates` — 创建模板

**请求体** `ClientTemplateCreateRequest`：

```jsonc
{
  "templateCode": "team-default",    // 必填，唯一；重复 → NMX070001
  "displayName": "团队默认",         // 必填
  "description": "...",
  "tags": ["team-default"],
  "validityType": "days",
  "validityDays": 365,               // days 时必填
  "validityFixedDate": "2027-12-31", // fixed 时必填（ISO 日期）
  "routeIds": ["rt..."],
  "quotaSpec": "[...]", "rateLimitSpec": "[...]"
}
```

**响应** `R<ClientTemplateVo>`（初始 `status=ENABLED`）。

#### PUT `/client-templates/{templateId}` — 更新模板

**请求体** `ClientTemplateUpdateRequest`（同创建字段，null 不改）；**响应** `R<ClientTemplateVo>`。

#### POST `/client-templates/{templateId}/enable` · `/disable` — 启停

无请求体；**响应** `R<ClientTemplateVo>`。

#### GET `/client-templates/{templateId}/created-clients` — 基于模板创建的客户端

**响应** `R<ClientVo[]>`（按创建时间降序）。

---

## 2. BFF 接口（nexmux-ui，基路径 `/api/data`）

### 通用约定

| 项 | 说明 |
|----|------|
| 鉴权 | cookie 会话（`/api/auth/login`），匿名 401 `{ "error": "未登录" }` |
| 响应 | **裸 JSON**（无 R 包装），形状即页面 DSL 绑定契约（与 mock 类型逐字段一致） |
| 错误 | `{ "error": string, "code": string }` + HTTP 状态（`NMX130001`→404、`NMX070001`→409、`NMX020001`→422、`NMX020002`→503、`NMX040001`→403；后端不可达→502） |
| 演示模式 | `NEXMUX_BACKEND_BASE_URL=mock` 或未配置后端时读走 mock；**写通道一律 503** |
| code 映射 | Model 的 UI code 中 `~` ↔ 后端 `modelCode` 的 `/`（如 `openai~o4-mini`） |

### 2.1 Provider

#### GET `/api/data/providers` — 提供方列表

| Query 参数 | 说明 |
|-----------|------|
| status | `all` / `enabled` / `disabled` / `pending` / `none`（后两者 BFF 拆为 keyStatus 下发后端） |
| keyword | 名称 / 编码 / baseUrl 模糊 |
| types | 厂商多选，逗号串 |

**响应** `ProviderListResult`：

```jsonc
{
  "items": [/* ProviderRecord（见下），全量不分页 */],
  "total": 42,                        // 构成口径（不随筛选变）
  "counts": {
    "all": { "enabled": 1, "total": 42 },
    "status": { "enabled": 1, "disabled": 41, "pending": 0, "none": 42 },
    "types": { "openai": { "enabled": 1, "total": 1 }, /* 按真实 vendor 动态聚合 */ }
  },
  "filters": {
    "status": [ { "value": "enabled", "text": "已启用 1", "dot": "#22c55e" } /* … */ ],
    "types": [ { "value": "openai", "text": "OpenAI 1/1", "dot": "#3b82f6" } /* 动态生成，总数降序 */ ]
  }
}
```

`ProviderRecord` 关键字段（派生在 BFF 完成）：

```jsonc
{
  "code": "openai", "name": "OpenAI", "displayName": "OpenAI",
  "icon": "zap",                      // vendor→白名单图标映射
  "providerType": "CLOUD", "protocol": "openai-responses",
  "family": "openai",                 // = vendorCode
  "status": "enabled",                // ENABLED→enabled，其余→disabled
  "keyStatus": "none",
  "baseUrl": "...", "region": "", "timeout": 60000, "proxy": "",
  "maskedKey": "—",                   // configured → "•••••••• (v2)"；pending → "待配置"
  "lastTest": null,                   // 未持久化
  "capabilities": [],                 // capabilityExtension JSON 解析
  "routes": { "total": 0, "enabled": 0 },   // 经 models 关联 route_spec 统计
  "routeSummary": "未关联路由",
  "models": [/* 详情才有：{code,name,type,providerModelId,pricing,status: ONLINE|OFFLINE,routes} */],
  "modelCount": 0, "enabledModelCount": 0,
  "description": "", "capabilityExtension": "",
  "createdAt": "2026-10-05 08:00", "updatedAt": "...", "operator": "system"
}
```

#### GET `/api/data/providers/{code}` — 提供方详情

**响应** `ProviderRecord`（含 `models` 行）；未命中 → 404。

#### GET `/api/data/providers/{code}/pull-models` — 拉取上游模型

**响应** `PullModelRow[] = [{ "id": "qwen3.5-flash", "name": "qwen3.5-flash", "local": "已存在" | "新增" }]`。

#### 写通道（POST/PUT）

| 调用 | 后端端点 | 请求体 |
|------|---------|--------|
| `POST /api/data/providers` | `POST /providers` | `ProviderCreateRequest` |
| `PUT /api/data/providers/{code}` | `PUT /providers/{id}`（BFF code→id） | `ProviderUpdateRequest` |
| `POST /api/data/providers/{code}/enable` · `/disable` | 对应端点 | 无 |
| `POST /api/data/providers/{code}/connection-test` | 对应端点 | 无 → `ConnectionTestVo` |

### 2.2 Model

#### GET `/api/data/models` — 模型列表

| Query 参数 | 说明 |
|-----------|------|
| type | `all` / `LLM` / `Embedding` / `Image` / `Audio` |
| providers | 厂商多选，逗号串（providerCode） |

**响应** `ModelListResult`：

```jsonc
{
  "items": [/* ModelRecord，全量（当前 1616） */],
  "total": 1616,
  "counts": {
    "all": { "enabled": 0, "total": 1616 },
    "types": { "LLM": 1560, "Embedding": 0, "Image": 56, "Audio": 0 },
    "providers": { "openai": 44 /* 按真实供应商动态 */ }
  },
  "filters": {
    "type": [ { "value": "LLM", "text": "大语言模型 1560" } /* … */ ],
    "providers": [ { "value": "openai", "text": "OpenAI 44" } /* 动态 */ ]
  }
}
```

`ModelRecord` 关键字段：

```jsonc
{
  "code": "openai~o4-mini",           // ~ ↔ / 映射
  "name": "o4-mini",
  "providerCode": "openai", "providerName": "OpenAI", "providerIcon": "zap",
  "type": "LLM", "typeLabel": "大语言模型 (LLM)",
  "status": "disabled",               // ENABLED→enabled，其余（DRAFT/DISABLED）→disabled
  "releaseDate": "", "contextWindow": 200000, "maxTokens": 100000,
  "inputModalities": ["text","image"], "outputModalities": ["text"],
  "modalityPreview": "T+I → T",
  "inputModalitiesText": "text · image", "outputModalitiesText": "text",
  "tags": ["pi-ai-catalog","openai"], "tagsText": "pi-ai-catalog · openai",
  "specSummary": "200K · 100K",
  "pricingSummary": "$1.1 / $4.4 /M", // 首档；未定价 → "未定价"
  "tierLabel": "",                    // 多档 → "4档"
  "tiers": [{"upto":null,"input":1.1,"output":4.4,"cacheRead":0.28,"cacheWrite":null}],
  "timeMultipliers": [], "currency": "USD",
  "priceVersion": "", "priceEffective": "", "priceVersions": [],
  "suppliers": [{                     // 主绑定 + extraBindings 合并
    "providerCode":"openai","providerName":"OpenAI","providerIcon":"zap",
    "providerModelId":"o4-mini","weight":100,
    "costRate":1.0,"billingRate":1.0,  // ← costMultiplier/referenceMultiplier
    "status":"enabled","timeRates":null,
    "keyConfigured":false,             // ← 供应商 keyStatus==configured
    "lastProbe":null
  }],
  "providerSummary": "OpenAI（主） · 共 1 个供应商模型",
  "primaryUpstreamId": "o4-mini",
  "supplierWeightTotal": "100",
  "routes": { "total": 0, "active": 0 },
  "routeSummary": "未关联路由",
  "routeRefs": [/* {name,alias,type:"单模型"|"多模型池",weight,status:"启用"|"停用",health:"HEALTHY"} */],
  "params": { "temperature":"—","topP":"—","topK":"—","maxTokens":"—",
              "frequencyPenalty":"—","presencePenalty":"—","reasoningEffort":"—",
              "seed":"—","stopSequences":"—","responseFormat":"—",
              "capabilities": ["reasoning","vision"] },
  "description": "", "metadata": "",
  "createdAt": "...", "updatedAt": "...", "shortDate": "10-05", "operator": "system"
}
```

#### GET `/api/data/models/{code}` — 模型详情

`code` 为 UI 形态（`openai~o4-mini`）；**响应** `ModelRecord`；未命中 → 404。

#### 写通道（POST/PUT）

| 调用 | 后端端点 | 请求体 |
|------|---------|--------|
| `POST /api/data/models` | `POST /models` | `ModelCreateRequest` |
| `PUT /api/data/models/{code}` | `PUT /models/{id}` | `ModelUpdateRequest` |
| `POST /api/data/models/{code}/enable` · `/disable` | 对应端点 | 无 |

### 2.3 Route

#### GET `/api/data/routes` — 路由列表

| Query 参数 | 说明 |
|-----------|------|
| status | `all` / `enabled`（→PUBLISHED）/ `disabled`（→DISABLED）/ `draft`（→DRAFT） |
| type | `all` / `single` / `pool` / `conditional`（conditional P0 无产出） |
| tags | 标签多选，逗号串（交集） |

**响应** `RouteListResult`：

```jsonc
{
  "items": [/* RouteRecord（见下），全量 */],
  "total": 2,
  "counts": {
    "all": { "enabled": 2, "total": 2 },
    "status": { "enabled": 2, "disabled": 0, "draft": 0 },
    "types": { "single": { "enabled": 1, "total": 1 }, "pool": { "enabled": 1, "total": 1 }, "conditional": { "enabled": 0, "total": 0 } },
    "tags": { "general": 1 /* 键为连字符转下划线 */ }
  },
  "filters": {
    "status": [ { "value": "all", "text": "全部 2/2" }, { "value": "enabled", "text": "已启用 2" }, { "value": "disabled", "text": "已停用 0" }, { "value": "draft", "text": "草稿 0" } ],
    "type":   [ { "value": "all", "text": "全部 2/2" }, { "value": "single", "text": "单模型 1/1" }, { "value": "pool", "text": "多模型池 1/1" }, { "value": "conditional", "text": "条件路由 0/0" } ],
    "tags":   [ { "value": "all", "text": "全部 2" }, { "value": "general", "text": "general 1" } /* 按数据出现顺序 */ ]
  }
}
```

`RouteRecord` 关键字段（type 由候选数派生；operation 由协议映射表派生）：

```jsonc
{
  "code": "general-chat", "name": "通用对话路由",
  "type": "single", "typeLabel": "单模型",
  "alias": "general", "tags": ["general"], "tagsText": "general",
  "description": "...",
  "status": "enabled", "statusLabel": "启用",   // PUBLISHED→enabled / DISABLED→disabled / DRAFT→draft
  "path": "/v1/chat/completions",               // ← spec.ingress
  "protocol": "OpenAI / Chat Completions",
  "operation": "chat.completions",               // 协议映射表；未知 → "—"
  "strategy": "FIXED", "strategyLabel": "固定", "strategySummary": "固定 (FIXED)",
  "stickySession": false, "healthFilter": true,
  "models": [{
    "modelCode": "openai/o4-mini", "modelName": "o4-mini",
    "providerSummary": "OpenAI · o4-mini",        // 供应商名 · 上游模型ID（BFF join）
    "weight": 100, "priority": "P0",
    "costRate": null, "billingRate": null,        // null = 继承 Model
    "status": "enabled"
  }],
  "entrySummary": "/v1/chat/completions · OpenAI / Chat Completions",
  "policySummary": "1 模型 · 固定",
  "enabledModelCount": 1, "weightTotal": 100,
  "createdAt": "...", "updatedAt": "...", "shortDate": "10-05", "operator": "..."
}
```

#### GET `/api/data/routes/{code}` — 路由详情

**响应** `RouteRecord`；未命中 → 404。

#### 写通道（POST/PUT）

| 调用 | 后端端点 | 请求体 |
|------|---------|--------|
| `POST /api/data/routes` | `POST /routes` | `RouteCreateRequest` |
| `PUT /api/data/routes/{code}` | `PUT /routes/{id}` | `RouteUpdateRequest` |
| `POST /api/data/routes/{code}/publish` | `POST /routes/{id}/publish` | `{ changeSummary?, operatorId? }` |
| `POST /api/data/routes/{code}/enable` · `/disable` | 对应端点 | 无（DRAFT → `NMX020001`） |

### 2.4 Client

#### GET `/api/data/clients` — 客户端列表

| Query 参数 | 说明 |
|-----------|------|
| status | `all` / `enabled` / `disabled` / `expired`（expired 为 BFF 展示态：ENABLED 且已过期） |
| name / code | 名称 / 编码模糊 |
| tags | 标签多选，逗号串（交集） |

**响应** `ClientListResult`（`{items, total, counts{all,status,tags}, filters{tags}}`）。`ClientRecord` 关键字段：

```jsonc
{
  "code": "mobile-app", "name": "移动 App",
  "tags": ["mobile","team"], "tagsText": "mobile · team",
  "status": "enabled", "statusLabel": "启用",   // enabled/disabled/expired（展示派生）
  "expiresAt": "2027-10-05 11:05",
  "appKeyId": "gw-ak-VHJTyOxe",
  "secretLastRotatedAt": "2026-10-05 11:09",
  "templateCode": "team-default", "templateName": "团队默认",
  "authRoutes": [{ "code":"general-chat","name":"通用对话路由","alias":"general","typeLabel":"单模型","path":"/v1/chat/completions" }],
  "quotas": [/* QuotaRule + 展示标签（quotaTypeLabel/metricLabel/limitText/modeLabel/cycleLabel）；用量字段 usageUsed/usagePercent/nextResetAt 暂为 null */],
  "rateLimits": [/* RateLimitRule + limitTypeLabel/limitText/scopeLabel */],
  "routeCount": 1,
  "quotaSummary": "总",                           // 总+日循环 / 总+周循环 / 月循环 …
  "rateLimitSummary": "1000 RPM",                  // 或 "N 并发"
  "appKeySummary": "gw-ak-VHJTyOxe · 到期 2027-10-05 · 1 路由 · 1000 RPM",
  "createdAt": "...", "updatedAt": "...", "operator": "..."
}
```

#### GET `/api/data/clients/{code}` — 客户端详情

**响应** `ClientRecord`；未命中 → 404。

#### 写通道（POST/PUT）

| 调用 | 后端端点 | 请求体 / 响应 |
|------|---------|--------------|
| `POST /api/data/clients` | `POST /clients` | `ClientCreateRequest` → `{ client, appKeyPlaintext }` |
| `PUT /api/data/clients/{code}` | `PUT /clients/{id}` | `ClientUpdateRequest` |
| `POST /api/data/clients/{code}/enable` · `/disable` | 对应端点 | 无 |
| `POST /api/data/clients/{code}/rotate-app-key` | 对应端点 | 无 → `{ clientId, appKeyPlaintext, appKeyVersion }` |
| `PUT /api/data/clients/{code}/route-grants` | 对应端点 | `{ routeIds: string[] }` |

### 2.5 ClientTemplate

#### GET `/api/data/client-templates` — 模板列表

| Query 参数 | 说明 |
|-----------|------|
| status | `all` / `enabled` / `disabled` |
| keyword | 名称 / 编码模糊 |
| tags | 标签多选，逗号串（交集） |

**响应** `ClientTemplateListResult`（`{items, total, counts{all,status,tags}, filters{status,tags}}`）。`ClientTemplateRecord` 关键字段：

```jsonc
{
  "code": "team-default", "name": "团队默认",
  "status": "enabled", "statusLabel": "启用",
  "validityType": "days", "validityDays": 365, "validityFixedDate": undefined,
  "validityLabel": "创建后 365 天",               // 创建后 N 天 / 固定至 yyyy-MM-dd / 永不过期
  "authRoutes": [/* 同 Client.authRoutes */],
  "quotas": [/* 同 Client.quotas */], "rateLimits": [/* 同 Client.rateLimits */],
  "createdClientCount": 1,
  "routeCount": 1, "quotaSummary": "总", "rateLimitSummary": "1000 RPM",
  "policySummary": "1 路由 · 总 · 1000 RPM",
  "tagsText": "team-default", "description": "...",
  "createdAt": "...", "updatedAt": "...", "operator": "..."
}
```

#### GET `/api/data/client-templates/{code}` — 模板详情

**响应** `ClientTemplateRecord`；未命中 → 404。

#### GET `/api/data/client-templates/{code}/created-clients` — 基于模板创建的客户端

**响应** `ClientRecord[]`。

#### 写通道（POST/PUT）

| 调用 | 后端端点 | 请求体 |
|------|---------|--------|
| `POST /api/data/client-templates` | `POST /client-templates` | `ClientTemplateCreateRequest` |
| `PUT /api/data/client-templates/{code}` | `PUT /client-templates/{id}` | `ClientTemplateUpdateRequest` |
| `POST /api/data/client-templates/{code}/enable` · `/disable` | 对应端点 | 无 |

---

## 3. 数据来源说明

| 数据 | 来源 |
|------|------|
| 42 供应商 + 1616 模型 | pi-ai catalog 种子（`nexmux-catalog-data-h2.sql`，**只插不更新**，重启保留用户修改；供应商 `DISABLED`、模型 `DRAFT`） |
| keyStatus | 由 `mux_provider_credential` 实时派生（目前全部 `none`） |
| 路由 / 客户端 / 模板 | 联调经写接口手工创建（`mux_route` 2 条、`mux_client_template` 1 条、`mux_client` 1 条） |
| routes/routeRefs/authRoutes | BFF 拉 `mux_route` 的 `route_spec.candidates` / `authorized_route_ids` 内存关联 |
| 客户端 expired 状态 | BFF 展示派生（ENABLED 且 expiresAt 已过）；后端无定时任务 |
| 配额用量（usageUsed/Percent/nextResetAt） | Runtime 未建，统一 null |

## 4. 验证状态

- 后端：`mvn test` 90 用例全绿（Provider 16 + Model 13 + Route 14 + Client/Template 20 新增/扩展）
- BFF：vitest 18 用例全绿（provider 4 + model 6 + route 4 + client/template 4 含黄金形状断言）
- 端到端：双进程（:8082 + :9011）curl 验证通过，含：重启后修改保留（种子只插不更新）、`mux_route.route_code` 与 `mux_client(+template)` 扩列的不清库 ALTER 迁移、模板创建客户端的策略复制与到期计算、AppKey 轮换版本递增

## 5. 未实现（仍 mock，按批次推进）

| 域 | 涉及 BFF 接口 | 批次 |
|----|--------------|------|
| 调用日志 / 审计 / 成本 | `/api/data/call-logs`、`/audit`、`/costs` | B5 |
| Dashboard / 用量 / 可用性 | `/api/data/dashboard`、`/ops/*` | B6 |
| 调试台 | `/api/data/debug/*-invoke` | B7 |
