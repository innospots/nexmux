# Nexmux UI 缺失服务端接口清单

> 核对基准：后端 OpenAPI 规格 `http://localhost:8082/openapi/specs/nexmux-kernel`（nexmux-kernel.yaml），并对各 GET 端点逐一实测探测（2026-10-06）。
> 前端契约基准：`nexmux-ui-data-api-inventory.md`（§1 后端接口 / §2 BFF 接口）与 `nexmux-ui/src/pactor/backend/adapters/*`。
> 结论：前端适配层已全部按契约写好；下列缺口均在**服务端**。凡标注「缺失」的接口，在 OpenAPI 规格中不存在对应路径。

## 一、按用途汇总

| 用途 | 涉及接口 | 状态 | 影响 |
|------|----------|------|------|
| 实体删除 | 5 个 `DELETE`（providers / models / routes / clients / client-templates） | **全部缺失**（后端当前零 DELETE） | 前端删除走 BFF 桩，默认返回成功、数据不真删 |
| 统计聚合 | 5 个 `GET /stats/*` | **模块整体缺失**（实测 404） | 仪表盘、模型/供应商/客户端用量、供应商可用性 5 个页面无数据源 |
| 绑定行级管理 | Provider 绑定模型行写接口；Model 扩展绑定写接口 | **缺失** | 供应商/模型详情页的绑定列表只读，无法单行增删改 |
| 定价版本 | Model 定价历史版本查询/管理 | **缺失** | BFF 契约的 `priceVersion / priceEffective / priceVersions[]` 无数据源 |
| 列表过滤 | `GET /models` 的 `keyword`、`status` 参数 | **缺失**（预留） | 当前模型列表页无搜索框/状态筛选故未暴露；一旦添加即被阻塞 |
| 功能占位 | `POST /models/{modelId}/invoke-test` | **占位实现**（接口存在，功能未落地） | 模型调用测试无真实效果 |

## 二、按模块明细

### 1. Stats（统计聚合）— 整体缺失

| 缺失接口 | 用途 | 影响页面 | 前端状态 |
|----------|------|----------|----------|
| `GET /api/nexmux/stats/dashboard` | 仪表盘汇总卡片与趋势 | `ops/dashboard.yaml` | `stats-adapter.ts` 已就绪 |
| `GET /api/nexmux/stats/model-usage` | 模型用量时间序列 | `ops/model-usage.yaml` | 已就绪 |
| `GET /api/nexmux/stats/provider-usage` | 供应商用量 | `ops/provider-usage.yaml` | 已就绪 |
| `GET /api/nexmux/stats/client-usage` | 客户端用量 | `ops/client-usage.yaml` | 已就绪 |
| `GET /api/nexmux/stats/provider-availability` | 供应商可用性 | `ops/provider-availability.yaml` | 已就绪 |

公共查询参数约定（前端已按此实现）：`start` / `end`（ISO 时间范围）。

### 2. Provider（供应商）

| 接口 | 用途 | 状态 | 前端状态 |
|------|------|------|----------|
| `DELETE /api/nexmux/providers/{providerId}` | 删除供应商 | **缺失** | BFF 桩（`stubMutations`，默认成功） |
| 绑定模型行写接口（路径建议：`POST/PUT/DELETE /providers/{providerId}/bindings[/{bindingId}]`） | 详情页绑定模型列表的添加 / 编辑（providerModelId、单价、启停）/ 移除 | **缺失** | 详情页绑定区只读；仅有 `GET by-code`（读）与 `POST pull-models`（拉上游清单，不落绑定） |

已具备：分页列表（`keyword / status / types / keyStatus` 过滤全支持）、按 ID/编码查询、创建、更新、启停、连接测试、拉取上游模型。

### 3. Model（模型）

| 接口 | 用途 | 状态 | 前端状态 |
|------|------|------|----------|
| `DELETE /api/nexmux/models/{modelId}` | 删除模型 | **缺失** | 无入口（待桩或待接口） |
| `GET /models` 增加 `keyword` 参数 | 按编码/名称搜索 | **缺失**（预留） | 页面暂无搜索框，未暴露 |
| `GET /models` 增加 `status` 参数 | 启用/停用筛选 | **缺失**（预留） | 页面暂无状态 ChipFilter，未暴露 |
| 定价版本历史接口（查询 + 管理） | `priceVersion / priceEffective / priceVersions[]` 数据源 | **缺失** | BFF 契约已定义字段，无后端来源 |
| 扩展绑定单行写接口（suppliers / extraBindings） | 多供应商绑定单条增删改 | **缺失** | 当前只能随 `PUT /models/{modelId}` 整包提交 |
| `POST /api/nexmux/models/{modelId}/invoke-test` | 调用测试 | **占位实现** | 前端已对接并实测可达 |

已具备：分页列表（`providers / type` 过滤）、按 ID/编码查询、创建、更新、启停。

### 4. Route（路由）

| 接口 | 用途 | 状态 | 前端状态 |
|------|------|------|----------|
| `DELETE /api/nexmux/routes/{routeId}` | 删除路由 | **缺失** | 无入口（待桩或待接口） |

已具备：分页列表（`status / type / tags` 过滤全支持）、按 ID/编码查询、创建、更新、发布、启停。

### 5. Client（客户端）

| 接口 | 用途 | 状态 | 前端状态 |
|------|------|------|----------|
| `DELETE /api/nexmux/clients/{clientId}` | 删除客户端 | **缺失** | 无入口（待桩或待接口） |

已具备：分页列表（`code / name / status / tags` 过滤全支持）、按 ID/编码查询、创建、更新、启停、轮换 AppKey（`rotate-app-key`）、路由授权（`route-grants`）。

### 6. ClientTemplate（客户端模板）

| 接口 | 用途 | 状态 | 前端状态 |
|------|------|------|----------|
| `DELETE /api/nexmux/client-templates/{templateId}` | 删除模板 | **缺失** | 无入口（待桩或待接口） |

已具备：分页列表（`keyword / status / tags` 过滤全支持）、按编码查询、创建、更新、启停、基于模板创建的客户端列表（`created-clients`）。

### 7. Ops（调用日志 / 成本 / 审计）— 无缺失

`request-logs`、`costs`、`audit-logs`（含 `/operators`）的分页、详情及全部过滤参数前后端完全匹配。

### 8. Debug（调试台）— 无缺失

`POST /debug/model-invoke`、`POST /debug/client-invoke` 均在规格中；前端适配层已就绪，未 E2E 实测。

## 三、优先级建议

1. **Stats 模块**：5 个页面整块无数据，前端已就绪，后端立项收益最大。
2. **删除接口（5 个 DELETE）**：决定 BFF 桩能否退役、演示数据能否清理；单接口工作量小。
3. **Model 列表过滤（keyword / status）**：成本低，解锁模型列表搜索与状态筛选。
4. **绑定行级写接口（Provider / Model 两侧）**：工作量最大，决定详情页从只读变可管理。
5. **定价版本接口**：依赖计费侧设计，可后置。
6. **invoke-test 落地**：占位转真实调用。
