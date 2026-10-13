# Nexmux UI 缺失服务端接口清单（剩余缺口）

> 初核：2026-10-06；**复核：2026-10-12**；**二次复核：2026-10-13**（补正：model / client / client-template 删除经实体端点 `@DELETE /api/d/{models,clients,client-templates}/{id}` 实现，上一轮漏查误判为缺失）。
> 数据接口层约定：**按 pactor-prd template 最新版**（skill v1.4.0+，commit `dfd9342`，2026-10-08）——mock/services 层已移除，页面数据一律 `type: http` 经纯转发层 `app/api/d/[...parts]/route.ts` 直连服务端，**路径即契约**。
> 结论：下列剩余缺口均在**服务端**；按新约定，缺失接口**无需任何前端桩**——转发层空数据回退自动兜底，后端补齐时页面与转发层零改动。

## 已关闭（2026-10-12 复核确认）

| 原缺口 | 现状 |
|--------|------|
| Stats 统计聚合 5 端点（`stats-dashboard` / `stats-model-usage` / `stats-provider-usage` / `stats-client-usage` / `stats-provider-availability`） | **已实现**（B6 批次，见 `StatsQueryDao` / 各 stats 端点） |
| `POST /api/d/ops/provider-delete` | **已实现**（`OpsWriteEndpoint`） |
| `POST /api/d/ops/route-delete` | **已实现**（`OpsWriteEndpoint`） |
| `DELETE /api/d/models/{modelId}` / `clients/{clientId}` / `client-templates/{templateId}` | **已实现**（实体端点 `@DELETE`，非 ops 前缀；实测删除正常） |
| `GET /api/d/models` 增加 `keyword` 参数 | **已实现**（编码/名称模糊匹配） |

## 〇、对接约定（pactor-prd template 最新版）

- **路径即契约**：页面 dataSources 的 url（`/api/d/<path>`）就是服务端接口路径；转发层按相同路径、方法、查询、请求体原样转发到 `$NEXT_PUBLIC_API_BASE_URL/api/d/<parts>`（携带会话 cookie，匿名 401）。
- **空数据回退（替代桩）**：上游不可达 / 404 / 非 JSON 时，GET 返回 `{}`、POST 返回 `{ ok: true }`，页面按空态渲染，不报错。**缺失接口不需要 BFF 桩**（原 `stubMutations` 机制可随此约定退役）。
- **命名约定**：
  - 列表/选项类一律**单段 kebab-case**：`/api/d/stats-dashboard`、`/api/d/provider-bindings`；**禁止** `/api/d/stats/dashboard` 两段写法（`[...parts]` 会把第二段当详情 id 解析）
  - 详情：`/api/d/<resource>/<code|id>`；子资源：`/api/d/<resource>/<code>/<sub>`
  - **写操作走 `ops/` 前缀 POST**：`/api/d/ops/provider-delete`（转发层仅导出 GET/POST，不用 DELETE/PATCH 动词）
- **返回形状约定**：列表 `{ items, total, filters? }`；选项类同样返回 `{ items: [...] }`，页面绑定 `options: ${data.xxx.items}`（空数据回退 `{}` 时 `.items` 为 undefined，Select 按空选项渲染；**不可用 `${data.xxx || []}`——DSL 表达式不支持数组字面量 `[]`**）；详情对空 id 返回空对象而非 404（新建预填链路依赖）；写操作提交闭环（request → message → reload）不依赖返回体内容。
- **接真实后端**：只需把 `next.config.js` 的 `API_BASE_URLS` 换成真实地址，页面 yaml 与转发层零改动。

## 一、剩余缺口汇总

| 用途 | 契约路径（页面直写） | 状态 | 缺失时回退行为 |
|------|----------------------|------|----------------|
| 实体删除（3 个实体） | `POST /api/d/ops/{model,client,client-template}-delete` | **已实现**（走实体端点 `DELETE /api/d/{models,clients,client-templates}/{id}`；ops 契约路径不再需要） | — |
| 绑定行级管理（Provider / Model 两侧） | `/api/d/provider-bindings`、`/api/d/ops/model-binding-*` | **仍缺失** | 列表回退 `{}` 空态；写回退 `{ ok: true }` |
| 定价版本（Model） | `GET /api/d/model-price-versions` | **仍缺失** | 回退 `{}` 空态 |
| 模型列表 status 过滤 | `GET /api/d/models` 增加 `status` | **仍缺失**（预留） | 参数被忽略即全量，分页口径不受影响再下推 |
| 模型调用测试 | `POST /api/d/models/{modelId}/invoke-test` | **占位实现**（接口存在，功能未落地） | — |

## 二、按模块明细

### 1. Provider（供应商）

| 契约路径 | 建议后端路径 | 用途 | 状态 |
|----------|--------------|------|------|
| `GET /api/d/provider-bindings?provider=<code>` | `GET /api/d/providers/{providerId}/bindings` | 详情页绑定模型行列表 | **仍缺失**；现仅 `GET by-code` 内嵌只读返回 |
| `POST /api/d/ops/provider-binding-save` | `POST/PUT /api/d/providers/{providerId}/bindings[/{bindingId}]` | 绑定行添加 / 编辑（providerModelId、单价、启停） | **仍缺失** |
| `POST /api/d/ops/provider-binding-delete` | `DELETE /api/d/providers/{providerId}/bindings/{bindingId}` | 绑定行移除 | **仍缺失** |

已具备：分页列表（`keyword / status / types / keyStatus` 过滤全支持）、按 ID/编码查询、创建、更新、启停、**删除**、连接测试、拉取上游模型（`pull-models` 只拉清单、不落绑定）。

### 2. Model（模型）

| 契约路径 | 建议后端路径 | 用途 | 状态 |
|----------|--------------|------|------|
| `POST /api/d/ops/model-delete`（body `{ code }`） | `DELETE /api/d/models/{modelId}` | 删除模型 | **已实现**（实体端点 `@DELETE`） |
| `GET /api/d/models` 增加 `status` 参数 | 同左 | 启用/停用 ChipFilter 筛选 | **仍缺失**（预留：页面暂无状态筛选） |
| `GET /api/d/model-price-versions?model=<code>` | `GET /api/d/models/{modelId}/price-versions` | 定价历史版本（BFF 契约 `priceVersion / priceEffective / priceVersions[]` 的数据源） | **仍缺失** |
| `POST /api/d/ops/model-binding-save` / `-delete` | `POST/PUT/DELETE /api/d/models/{modelId}/bindings[/{bindingId}]` | 扩展绑定（suppliers / extraBindings）单条增删改 | **仍缺失**；当前只能随整包更新提交 |
| `POST /api/d/models/{modelId}/invoke-test` | — | 调用测试 | **占位实现**（接口存在，后端自注未落地） |

已具备：分页列表（`providers / type / keyword` 过滤）、按 ID/编码查询、创建、更新、启停。

### 3. Client（客户端）

| 契约路径 | 建议后端路径 | 用途 | 状态 |
|----------|--------------|------|------|
| `POST /api/d/ops/client-delete`（body `{ code }`） | `DELETE /api/d/clients/{clientId}` | 删除客户端 | **已实现**（实体端点 `@DELETE`） |

已具备：分页列表（`code / name / status / tags` 过滤全支持）、按 ID/编码查询、创建、更新、启停、轮换 AppKey（`rotate-app-key`）、路由授权（`route-grants`）。

### 4. ClientTemplate（客户端模板）

| 契约路径 | 建议后端路径 | 用途 | 状态 |
|----------|--------------|------|------|
| `POST /api/d/ops/client-template-delete`（body `{ code }`） | `DELETE /api/d/client-templates/{templateId}` | 删除模板 | **已实现**（实体端点 `@DELETE`） |

已具备：分页列表（`keyword / status / tags` 过滤全支持）、按编码查询、创建、更新、启停、基于模板创建的客户端列表（`created-clients`）。

### 5. 无缺口模块（复核确认）

- **Stats**：5 个统计端点全部落地（B6）。
- **Route**：含删除，全量具备。
- **Ops（调用日志 / 成本 / 审计）**：`request-logs`、`costs`、`audit-logs`（含 `/operators`）分页、详情及全部过滤参数前后端完全匹配。
- **Debug（调试台）**：`POST /debug/model-invoke`、`POST /debug/client-invoke` 均在规格中；前端适配层已就绪。

## 三、与新约定相关的迁移项

1. **桩机制退役**：原 BFF `stubMutations`（删除默认成功）与纯转发层的空数据回退语义重复；按新约定，删除等写操作缺失时转发层自动回退 `{ ok: true }`，桩可移除。
2. **空 id 详情**：各详情接口需对空 id 返回空对象而非 404（新建预填链路依赖），后端补齐时注意此约定。
3. **写动词收敛**：转发层仅导出 GET/POST，页面侧删除/启停等写操作一律 `ops/` 前缀 POST；后端落地时REST 路径（DELETE/PUT）由转发层之后的服务侧映射承担，页面 yaml 不受影响。

## 四、优先级建议（复核后）

1. ~~**删除接口**~~：5 个实体删除均已落地（provider/route 走 ops，model/client/client-template 走实体端点 `@DELETE`）。
2. **绑定行级写接口（Provider / Model 两侧）**：工作量最大，决定详情页从只读变可管理。
3. **Model 列表 status 过滤**：成本低，解锁模型列表状态筛选。
4. **定价版本接口**：依赖计费侧设计，可后置。
5. **invoke-test 落地**：占位转真实调用。
