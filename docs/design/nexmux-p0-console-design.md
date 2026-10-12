# Nexmux P0：Model Console 实施设计

## 1. 文档定位

- **状态**：草案
- **日期**：2026-09-26
- **归属模块**：`nexmux-kernel`、`nexmux-spring`、`nexmux-ui`
- **上级文档**：[nexmux-architecture-design.md](./nexmux-architecture-design.md)
- **产品依据**：PRD §25.1、[控制台设计 §25.1](../../../product-design/nexmux/模型管理系统-管理控制台产品功能设计文档.md)

## 2. P0 目标（可调用闭环）

```text
接入上游 Provider → 配置 Model 与 Binding → 配置并发布 Route
→ 创建 Client/AppKey 并授权 Route → 调试调用 → 查询 Request Log → 查看配置审计
```

**P0 明确不做**（仍可能在 UI 占位 disabled）：多维用量页、成本明细、Dashboard、Client Template、条件路由/灰度/Failover、失败后自动切换 Binding。

## 3. 功能与页面对照

| 产品模块 | P0 页面（线框） | 后端聚合 |
|----------|-----------------|----------|
| 模型资产 Provider | [01–03 Provider](../../../product-design/nexmux/prd-diagram/模型资产/Provider/) | `ProviderService` |
| 模型资产 Model | [01–03 Model](../../../product-design/nexmux/prd-diagram/模型资产/Model/) | `ModelService` |
| 模型路由 | [01–03 路由](../../../product-design/nexmux/prd-diagram/模型路由/) | `RouteService` |
| 访问治理 Client | [01–03 客户端](../../../product-design/nexmux/prd-diagram/访问治理/Client/) | `ClientService` |
| 运行管理 | [01 调用日志](../../../product-design/nexmux/prd-diagram/运行管理/01-调用日志列表与详情.md) | `RequestLogQueryService` |
| 调试台 | [01–02 调试台](../../../product-design/nexmux/prd-diagram/调试台/) | `DebugService` |
| 系统管理 | [02 审计](../../../product-design/nexmux/prd-diagram/系统管理/02-审计.md) | `AuditQueryService` |

凭证说明：[AppKey与凭证说明](../../../product-design/nexmux/prd-diagram/模型资产/AppKey与凭证说明.md)。

## 4. P0 核心业务流程

### 4.1 Route 发布

```text
编辑 Route 草稿 → 发布前检查（Binding 健康、Client 授权可选提示）
→ 拷贝 route_spec → published_route_spec，status = PUBLISHED
→ 写 config_audit_log →（异步）通知 Runtime 拉取 mux_route.published_route_spec
```

快照 JSON 须包含：alias、ingress、策略类型（FIXED / WEIGHTED / ROUND_ROBIN）、候选 logical model 列表及权重、过滤不健康 Binding 标志（P0 调度前过滤）。

### 4.2 影响分析（高风险操作）

在以下操作前调用 `DependencyImpactAnalyzer`（core 内具体类）：

- Model / Provider / Binding 停用
- Route 停用或发布
- Client 停用
- Provider 凭证变更

返回引用链：Route、Client grant、最近 Request 样本（只读提示）。失败则 `NMX` FAILED_PRECONDITION。

### 4.3 调试台

| 能力 | 行为 |
|------|------|
| 模型调试 | 直连 Provider Binding 测试，不经过 Route |
| 客户端调试 | 模拟 Client AppKey + Route 全链路；请求/响应受保留策略截断 |

审计：调试请求记为 `TEST` 类审计或独立 debug 访问日志（P0 至少记 operator + target + request_id）。

## 5. P0 API 契约附录

基路径：`/api/d`。以下为核心端点；完整字段对齐线框「表单字段」章节。

### 5.1 Provider

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/providers` | 分页列表 |
| GET | `/providers/{providerId}` | 详情（不含明文 secret） |
| POST | `/providers` | 创建 |
| PUT | `/providers/{providerId}` | 更新 |
| POST | `/providers/{providerId}/enable` | 启停 |
| POST | `/providers/{providerId}/connection-test` | 连接测试 |

### 5.2 Model

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/models` | 列表 |
| GET | `/models/{modelId}` | 详情含 bindings、默认定价 |
| POST | `/models` | 创建 / 目录导入 |
| PUT | `/models/{modelId}` | 更新 |
| POST | `/models/{modelId}/enable` | 启停 |
| POST | `/models/{modelId}/invoke-test` | 调用测试 |

### 5.3 Route

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/routes` | 列表 |
| GET | `/routes/{routeId}` | 详情含引用关系 |
| POST | `/routes` | 创建 |
| PUT | `/routes/{routeId}` | 更新草稿 |
| POST | `/routes/{routeId}/publish` | 发布（无历史版本 API） |

### 5.4 Client

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/clients` | 列表 |
| GET | `/clients/{clientId}` | 详情含 quota/rate-limit 区块 |
| POST | `/clients` | 创建（返回一次性 AppKey 明文） |
| PUT | `/clients/{clientId}` | 更新策略 |
| POST | `/clients/{clientId}/rotate-app-key` | 轮换 |
| PUT | `/clients/{clientId}/route-grants` | Route 授权 |

### 5.5 只读运营

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/request-logs` | 列表筛选 |
| GET | `/request-logs/{requestId}` | 详情含 route trace |
| GET | `/audit-logs` | 配置审计列表 |
| GET | `/audit-logs/{auditId}` | Diff 详情 |

### 5.6 Debug

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/debug/model-invoke` | 模型调试 |
| POST | `/debug/client-invoke` | 客户端全链路 |

端点超过 7 个/资源属预期；实现按资源分包并做契约测试分文件。

## 6. 权限 key（Console 页面）

与 PRD §4 三类角色对齐，permission key 前缀 **`nexmux.`**：

| Key | 操作 |
|-----|------|
| `nexmux.provider.view` | Provider 查看 |
| `nexmux.provider.manage` | Provider 写 + 连接测试 |
| `nexmux.model.manage` | Model 写 + 调用测试 |
| `nexmux.route.publish` | Route 发布 |
| `nexmux.client.secret` | AppKey 轮换 |
| `nexmux.ops.logs.view` | 调用日志 |
| `nexmux.audit.view` | 审计 |
| `nexmux.debug.execute` | 调试台 |

菜单、`pageKey`、路由与 Entity 对照见 [nexmux-console-entry-plugin-design.md](./nexmux-console-entry-plugin-design.md)；权限 key 与上表一致。

## 7. 配置项（`nexus/nexmux.yaml`）

源码：`nexmux-spring/src/main/resources/nexus/nexmux.yaml`；发行包建议 `config/nexus/nexmux.yaml`。键约束与禁止项见 [nexmux-yaml-configuration-design.md](./nexmux-yaml-configuration-design.md) §5。

```yaml
nexmux:
  console:
    api-base-path: /api/d
  client:
    app-key-grace-seconds: 300
  debug:
    max-response-body-bytes: 65536
  runtime:
    snapshot-poll-interval-seconds: 30   # Runtime 侧消费，Console 仅写快照
```

## 8. P0 测试范围（develop 入口）

建议实现顺序（测试先行）：

1. `NexmuxStatusCodeContractTest`
2. `RouteEntityContractTest` + `RoutePublishServiceTest`
3. `ProviderEndpointContractTest`
4. `RoutePublishEndpointContractTest`（Jersey）
5. `ClientRotateAppKeyServiceTest`

**集成**：`nexmux-spring` 启动 H2，走通「创建 Provider → Model → Route 发布 → Client 授权」脚本测试（可后续补 IT）。

## 9. 与 Runtime 的 P0 联调最小契约

Console 交付：

- Runtime 只读 `mux_route.published_route_spec`（`status = PUBLISHED`）。
- Client `app_key_hash` 校验元数据（盐、算法标识）。

Runtime 交付（`inbound`，独立进程）：

- 消费 `published_route_spec` 当前行。
- 写 `mux_request_log` 最小字段：`request_id`, `client_id`, `route_id`, `status`, `latency_ms`, `error_code`。

详细字段对齐 [调用日志线框](../../../product-design/nexmux/prd-diagram/运行管理/01-调用日志列表与详情.md)。

## 10. 开放问题（P0）

| # | 问题 | 备注 |
|---|------|------|
| 1 | H2 开发是否合并 console + ops 表 | 默认单库，表前缀区分 |
| 2 | AppKey 明文仅创建/轮换响应一次 | 与安全评审同步 |
| 3 | 内置模型目录导入数据源 | 静态 JSON classpath vs 管理 API |
