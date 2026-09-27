# Nexmux UiSpec（PageDsl）页面方案结构

## 1. 文档定位

- **状态**：已采纳（与 [nexmux-yaml-configuration-design.md](./nexmux-yaml-configuration-design.md) 同步）
- **日期**：2026-09-27
- **归属**：`nexmux-kernel/src/main/resources/ui-pages/nexmux/{moduleKey}/`（`domainKey=nexmux`；`moduleKey` 为 provider / model / …）
- **关联**：
  - 产品统一详情 §17–§18：[管理控制台产品功能设计文档](../../../product-design/gateweave/模型管理系统-管理控制台产品功能设计文档.md)
  - 线框通用说明：[prd-diagram/README.md](../../../product-design/gateweave/prd-diagram/README.md)
  - 路由与 pageKey：[nexmux-console-entry-plugin-design.md](./nexmux-console-entry-plugin-design.md)
  - 领域与 API：[nexmux-domain-layer-design.md](./nexmux-domain-layer-design.md)
- **规范**：Nexus PageDsl `dsl: '1.0'`（参考 `innospots-nexus-plugin` 测试用例与 `innospots-nexus-console` 内置 `ui-pages`）

UiSpec 描述 **页面信息架构与数据绑定契约**；视觉与交互细节以产品线框为准。实现可逐步从「壳 + 数据源」补到完整 `body` 组件树。

## 2. 文件与身份

Classpath 路径由 Nexus `PageDslConfig` 固定为 **`ui-pages/{domainKey}/{moduleKey}/{pageKey}.yaml`**（详见 [YAML 规范 §6](./nexmux-yaml-configuration-design.md)）。

| 项 | 约定 |
|----|------|
| `domainKey` | `nexmux`（产品领域；**不是**菜单「模块」、**不是**内置 `nexus`） |
| `moduleKey` | `provider` / `model` / `route` / `client` / `ops` / `debug` / `audit`（与 `NexmuxConsoleCatalog.MODULE_*` 一致） |
| 目录 | `ui-pages/nexmux/{moduleKey}/`（例：`ui-pages/nexmux/provider/`） |
| 文件名 | `{pageKey}.yaml`，与 Entry 中 `UiSpecPageDeclaration.pageKey` **完全一致** |
| `page.id` | 同 `pageKey`（加载器校验失败即 CONFIG_ERROR） |
| `page.name` | camelCase，由 pageKey 推导（如 `nexmux-provider-list` → `nexmuxProviderList`） |
| 后缀 | **仅** `.yaml`（本仓库不新增 `.yml` 页面） |
| API 基址 | `meta.apiBase: /api/nexmux`（与 `nexus/nexmux.yaml` → `nexmux.console.api-base-path` 一致） |
| 页面路由 | `meta.routePath` 与 Entry `pagePath` 一致（以 **`/page/nexmux`** 开头；**不用** `/console`） |
| 请求头 | 数据请求携带 `X-Nexus-Page-Key: ${page.id}` |

```text
nexmux-kernel/src/main/resources/ui-pages/nexmux/
├── provider/nexmux-provider-list.yaml
├── provider/nexmux-provider-detail.yaml
├── model/nexmux-model-list.yaml
├── route/…
├── client/…
├── ops/…
├── debug/…
└── audit/…
```

**禁止**：`ui-pages/nexmux/nexmux/`（误把产品名当 `moduleKey`）；`ui-pages/nexmux/{pageKey}.yaml`（缺少 `moduleKey` 段）。

## 3. 产品页面方案（布局模式）

产品线框约定三种主形态（[prd-diagram 通用说明](../../../product-design/gateweave/prd-diagram/README.md)）：

| 方案 ID | 适用对象 | 产品形态 | PageDsl `page.type` | 结构要点 |
|---------|----------|----------|----------------------|----------|
| **S-LIST** | Provider、Model、Route、Client、日志、审计 | 列表 / 卡片网格 | `list` | 筛选区 + 主列表 + 行操作；Route/Provider 可为卡片 |
| **S-DETAIL-TAB** | Provider、Model | Header + Overview + **Tab** | `detail` + `layout: tabs` | Tab 对齐线框（Model：基本信息/默认参数/定价/提供商/路由引用） |
| **S-DETAIL-STACK** | Route | **单页纵向**区块，无 Tab | `detail` + `layout: stack` | ①基本信息 ②入口 ③模型与策略 … |
| **S-DETAIL-PANEL** | Client、Client Template | **单页分区**（非 Tab） | `detail` + `layout: panels` | 基本信息 / 授权 / 配额 / 限流 / AppKey 等纵向 Panel |
| **S-FORM** | 各资源新增/编辑 | 表单页 | `form` | 分组字段 + 底部主操作；编辑页稳定键只读 |
| **S-WORKBENCH** | 调试台 | 左右栏或上下栏 | `general` | 请求编辑区 + 响应区；受保留策略截断 |
| **S-READONLY** | 调用日志详情、审计 Diff | 只读 | `detail` + `layout: readonly` | 无保存；支持复制 RequestId、跳转关联对象 |

所有详情页共享产品 **§17 信息层级**（按需裁剪，不强行凑 Tab）：

```text
Header        → Name / Code / Status / Primary Actions
Overview      → 摘要指标、关联对象计数（可选）
Content       → Configuration | Relationships | Runtime | Audit（适用项）
```

- **Relationships**：引用关系组件（§18），用于停用/删除前影响分析入口。
- **Runtime**：最近探测、发布态、published_route_spec 摘要等。
- **Audit / Change History**：配置类对象展示变更；深度 Diff 链到 `nexmux-audit-detail`。

## 4. PageDsl 逻辑分区（推荐）

每个 yaml 按固定区块组织，便于评审与生成器扩展：

```yaml
dsl: '1.0'

page:          # id / title / type / layout / permission
meta:          # apiBase, product, priority P0|P1, wireframeRef
meta.routePath: # 与 Entry pagePath 一致（PageDsl 根对象无 route 段，勿写顶层 route:）
state:         # 列表筛选、分页、选中主键、表单草稿
dataSources:   # http → /api/nexmux/...
actions:       # search / save / publish / enable / testConnection ...
regions:       # 声明式区域清单（对应线框区块名，body 可后补）
lifecycle:     # onInit / onShow
body:          # Page 组件树（P0 可先 minimal）
```

**regions**（Nexmux 扩展段，文档约定；解析器可忽略）：只列区块名与绑定数据源，不写 UI 实现。

```yaml
regions:
  - id: header
    slots: [title, status, primaryActions]
  - id: overview
    slots: [metrics, relationsSummary]
  - id: content
    slots: [main]   # tabs | stack | panels 由 layout 决定
```

## 5. P0 页面清单与方案结构

`pagePath` 与 [Entry 文档](./nexmux-console-entry-plugin-design.md) 一致。

### 5.1 Provider

| pageKey | 方案 | pagePath | 线框 | regions（content） |
|---------|------|----------|------|---------------------|
| nexmux-provider-list | S-LIST | `/page/nexmux/provider` | [列表](../../../product-design/gateweave/prd-diagram/模型资产/Provider/01-Provider列表页.md) | 搜索、类型 Chip、卡片网格、添加卡 |
| nexmux-provider-detail | S-DETAIL-TAB | `.../provider/{providerCode}` | [详情](../../../product-design/gateweave/prd-diagram/模型资产/Provider/02-Provider详情页.md) | Tab：概览、连接与凭证、能力扩展、关系、审计 |
| nexmux-provider-edit | S-FORM | `.../provider/{providerCode}/edit` | [编辑](../../../product-design/gateweave/prd-diagram/模型资产/Provider/03-Provider新增编辑页.md) | 基本信息、连接与凭证、能力扩展；`providerCode` 新增可编辑 |

**dataSources 示例**：`providerDetail` → `GET /providers/{providerCode}`；`testConnection` → `POST /providers/{id}/connection-test`。

### 5.2 Model

| pageKey | 方案 | regions（Tab / 主区） |
|---------|------|------------------------|
| nexmux-model-list | S-LIST | 类型 Chip、厂商 Chip、卡片（Logo、定价摘要、路由摘要） |
| nexmux-model-detail | S-DETAIL-TAB | **基本信息** · **默认参数** · **定价** · **提供商** · **路由引用**（P1：参数预设 Tab 占位 disabled） |
| nexmux-model-edit | S-FORM | 基本信息 + 提供商区（主绑定 + extra_bindings 编辑）+ 导入向导（可选步骤） |

定价 Tab 绑定 `pricing_spec` 单字段编辑（表单表格 + 时段规则列表 UI，持久化一次提交）。

### 5.3 Route

| pageKey | 方案 | regions（stack 顺序） |
|---------|------|------------------------|
| nexmux-route-list | S-LIST | 表格/卡片：alias、Ingress、策略、发布态 |
| nexmux-route-detail | S-DETAIL-STACK | ①基本信息 ②入口配置 ③模型与策略 ④关系 ⑤发布与 Runtime |
| nexmux-route-edit | S-FORM | 同详情区块，可编辑；**发布**为独立 action（非保存草稿） |

### 5.4 Client

| pageKey | 方案 | regions（panels） |
|---------|------|-------------------|
| nexmux-client-list | S-LIST | 表格：clientCode、授权 Route 数、状态 |
| nexmux-client-detail | S-DETAIL-PANEL | 基本信息 · Route 授权 · 配额 · 限流 · AppKey（掩码）· 关系 |
| nexmux-client-edit | S-FORM | 同上；创建成功一次性展示 AppKey（`ClientAppKeyRevealVo`） |

### 5.5 运行管理 · 调试台 · 系统

| pageKey | 方案 | 说明 |
|---------|------|------|
| nexmux-request-log-list | S-LIST | 时间、Client、Route、状态筛选 |
| nexmux-request-log-detail | S-READONLY | Route trace、上游摘要、错误码 |
| nexmux-debug-client | S-WORKBENCH | 选 Client + Route，发调试请求 |
| nexmux-debug-model | S-WORKBENCH | 选 providerId + providerModelId 直连测试 |
| nexmux-audit-list | S-LIST | 资源类型、操作者、时间 |
| nexmux-audit-detail | S-READONLY | `change_diff` 展示 |

### 5.6 P1 占位（预置 yaml 壳）

| pageKey | 方案 | 备注 |
|---------|------|------|
| nexmux-home | S-WORKBENCH | Dashboard 四指标 + 趋势 + 分布 |
| nexmux-access-client-template-* | S-LIST / S-DETAIL-PANEL | 同 Client 分区 |
| nexmux-ops-model-usage 等 | S-LIST | 只读聚合，P1 再接 API |

`meta.status: placeholder` 表示菜单可见但 UI 显示「后续版本」。

## 6. 列表页标准结构（S-LIST）

```text
┌ PageHeader（面包屑 + 标题 + 全局操作：新建/导入）
├ FilterBar（搜索、Chip 筛选、显示 N/M）
├ Toolbar（批量操作，可选）
├ DataView（Table | CardGrid）
└ FooterPagination
```

**state 最小集**：`keyword`, `filters`, `page`, `pageSize`, `total`。  
**dataSources**：`list` → `GET` 集合接口；`meta` 静态枚举（Provider 类型、Model 类型等）可 `type: static`。

## 7. 表单页标准结构（S-FORM）

```text
┌ PageHeader（新建/编辑标题）
├ FormSections（与线框分组一致）
│   ├ 基本信息
│   ├ 业务配置
│   └ 敏感字段（API Key：掩码/留空不修改）
├ ImpactHint（高风险字段变更前，可选调 impact API）
└ FormActions（取消 | 保存 | 保存并返回）
```

稳定业务键（`providerCode`、`modelCode`、`alias`、`clientCode`）在编辑页 **只读**；与 [domain-layer](./nexmux-domain-layer-design.md) 一致。

## 8. 详情 Tab 结构（S-DETAIL-TAB · Model 示例）

与 [Model 详情线框](../../../product-design/gateweave/prd-diagram/模型资产/Model/02-Model详情页.md) 对齐：

| Tab key | 绑定数据 | 主要字段来源 |
|---------|----------|--------------|
| basic | `modelDetail` | Entity 基本信息列 + icon |
| defaultParams | `modelDetail.defaultParams` | 默认参数 |
| pricing | `modelDetail.pricingSpec` | `pricing_spec` |
| bindings | `modelDetail` + 解析 bindings | 主绑定列 + `extra_bindings` |
| routeRefs | `GET /models/{id}/route-references` | 只读关联 |

Provider 详情 Tab：**概览 · 连接与凭证 · 能力扩展 · 关系 · 审计**（无定价 Tab）。

## 9. 最小 PageDsl 壳（契约占位）

P0 可先提交仅含 `page` + `meta`（含 `routePath`），`body` 为空，供 catalog/PageDsl 加载测试：

```yaml
dsl: '1.0'

page:
  id: nexmux-provider-list
  name: nexmuxProviderList
  title: 提供商
  description: 提供商目录与连接管理。
  type: list
  permission: nexmux.provider.view

meta:
  apiBase: /api/nexmux
  priority: P0
  layoutScheme: S-LIST
  wireframe: product-design/gateweave/prd-diagram/模型资产/Provider/01-Provider列表页.md

  routePath: /page/nexmux/provider/nexmux-provider-list

state:
  page: 1
  pageSize: 20

dataSources: {}

body: null
```

完整列表页逐步补齐 `dataSources` / `body`，参照 Nexus `customer-list.yaml` 模式，URL 改为 `meta.apiBase` 下路径。

## 10. 与 Entry / Entity 的一致性检查

| 检查项 | 规则 |
|--------|------|
| classpath | `ui-pages/nexmux/{moduleKey}/{pageKey}.yaml` 可加载且 `page.id` 匹配 |
| `domainKey` | 恒为 `nexmux` |
| `moduleKey` | 与页面所属业务模块一致（见 §2 表），**非** `nexmux` |
| pageKey 集合 | Entry §5.2 每个 P0 路由均有同名 yaml |
| page.id | 等于 pageKey |
| meta.routePath | 等于 Entry `pagePath` |
| 主键路由参数 | 与 Entity 稳定键名一致：`providerCode`、`modelCode`、`alias`、`clientCode`、`requestId`、`auditId` |
| 写操作 permission | `actions` 内 `permission` 与 [P0 §6](./nexmux-p0-console-design.md) 一致 |
| 字段命名 | 绑定 API 使用 camelCase；**禁止** `*_json` 键名（对齐 [domain-layer](./nexmux-domain-layer-design.md) G15） |

## 11. 测试与交付

| 项 | 说明 |
|----|------|
| `NexmuxPageDslClasspathTest` | `ClasspathPageDslLoader.load("nexmux", moduleKey, pageKey)`；`moduleKey` 与 §2 表一致 |
| `NexmuxPageRouteAlignmentTest` | yaml `route.path` 与 Entry 表一致 |
| 交付顺序 | 先 list + detail 壳 → 再接 dataSources → 最后 body 组件 |

完整 YAML 分类与 Spring 分项约束见 [nexmux-yaml-configuration-design.md](./nexmux-yaml-configuration-design.md)。

## 12. 开放问题

| # | 问题 | 建议 |
|---|------|------|
| 1 | `regions` 是否进入 PageDsl 正式 schema | P0 仅文档约定；实现用注释块亦可 |
| 2 | 卡片列表 vs 表格 | Provider/Model 用卡片（线框）；Route/Client 用表格 |
| 3 | nexmux-ui 纯 React 是否仍维护 yaml | **是** — 至少保留壳 yaml 满足 catalog 同步与 PageDsl 契约测试 |
