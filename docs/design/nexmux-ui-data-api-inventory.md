# Nexmux UI 数据接口清单（已实现部分）

- **日期**：2026-10-05（随实现批次滚动更新）
- **设计依据**：[nexmux-ui-data-api-design.md](./nexmux-ui-data-api-design.md)
- **覆盖范围**：已切换真实后端的域——**Provider（B1）**、**Model**；其余域仍走 mock（见 §5）
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

---

## 3. 数据来源说明

| 数据 | 来源 |
|------|------|
| 42 供应商 + 1616 模型 | pi-ai catalog 种子（`nexmux-catalog-data-h2.sql`，**只插不更新**，重启保留用户修改；供应商 `DISABLED`、模型 `DRAFT`） |
| keyStatus | 由 `mux_provider_credential` 实时派生（目前全部 `none`） |
| routes/routeRefs | BFF 拉 `mux_route` 的 `route_spec.candidates` 内存关联（目前全空） |

## 4. 验证状态

- 后端：`mvn test` 56 用例全绿（Provider 16 + Model 13 新增/扩展）
- BFF：vitest 10 用例全绿（provider-adapter 4 + model-adapter 6 含黄金形状断言）
- 端到端：双进程（:8082 + :9011）curl 验证通过，含重启后修改保留（`openai` ENABLED）

## 5. 未实现（仍 mock，按批次推进）

| 域 | 涉及 BFF 接口 | 批次 |
|----|--------------|------|
| Route | `/api/data/routes[...]` | B2 |
| Client + 模板 | `/api/data/clients[...]`、`/client-templates[...]` | B4 |
| 调用日志 / 审计 / 成本 | `/api/data/call-logs`、`/audit`、`/costs` | B5 |
| Dashboard / 用量 / 可用性 | `/api/data/dashboard`、`/ops/*` | B6 |
| 调试台 | `/api/data/debug/*-invoke` | B7 |
