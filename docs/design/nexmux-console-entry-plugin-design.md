# Nexmux 控制台 Entry 插件设计

## 1. 文档定位

- **状态**：定稿（当前实现，2026-10-24 核验）
- **日期**：2026-09-26（最近修订：2026-10-24）
- **归属**：`nexmux-kernel` → `com.innospots.nexmux.console.entry` / `console.catalog`
- **关联**：
  - 产品信息架构 §5：[管理控制台产品功能设计文档](../../../product-design/nexmux/模型管理系统-管理控制台产品功能设计文档.md)
  - 线框索引：[prd-diagram/README.md](../../../product-design/nexmux/prd-diagram/README.md)
  - 领域实体：[nexmux-domain-layer-design.md](./nexmux-domain-layer-design.md)
  - 权限 key：[nexmux-p0-console-design.md](../archive/nexmux-p0-console-design.md) §6
- **规范参考**：Nexus `ConsoleModuleDescriptor`、`ConsolePluginContribution`、`MenuDeclaration`（`innospots-nexus-console` / `innospots-nexus-plugin`）

## 2. 概念边界

| 概念 | 归属 | 说明 |
|------|------|------|
| **Entity**（`mux_*`） | `core.*.domain.entity` | 业务配置与运行只读数据；**不由插件定义** |
| **Entry 插件** | `console.entry` | 向 Nexus 宿主注册 **模块 / 菜单 / 页面路由 / 权限资源**；同步到 `nx_console_catalog_resource` |
| **管理 API** | `console.endpoint` | Jersey；路径 `/api/d/*` |
| **前端** | `nexmux-ui` | 消费菜单与 `pagePath`；列表/详情/编辑 UX 对齐线框 |

```text
PluginDefinition (com.innospots.nexmux.console)
    → ConsolePluginContribution (console@1)
        → ConsoleModuleDeclaration[]   # domainKey=nexmux；moduleKey=provider|model|…
            → UiSpecPageDeclaration[]    # 本模块页面
            → MenuDeclaration[]        # 本模块菜单项（与 page 同 moduleKey）
    → 启动后 CatalogSync → nx_console_catalog_resource
```

**不**在 nexmux 仓库复制 `innospots-nexus-console` 内置 entry；**不**为每个 Entity 单独建 Maven 模块。P0 采用 **一个产品 Entry 插件** + **一棵菜单树**（满足原型 IA，避免插件碎片化）。

## 3. 插件身份与装配

| 项 | 值 |
|----|-----|
| `pluginId` | `com.innospots.nexmux.console` |
| `domainKey` | `nexmux`（**产品领域**；PageDsl `ui-pages/nexmux/...`；与内置 `nexus` 区分） |
| `moduleKey` | `provider` / `model` / `route` / `client` / `ops` / `debug` / `audit`（**菜单业务模块**；kebab：`[a-z][a-z0-9-]*`；**不是** `nexmux`） |
| `securityRealm` | `PLATFORM`（平台级；与「无 Tenant/Workspace 配置隔离」一致） |
| Java 类 | `com.innospots.nexmux.console.entry.NexmuxConsoleEntryPlugin` implements `Plugin` |
| SPI | `nexmux-kernel/src/main/resources/META-INF/services/com.innospots.nexus.core.plugin.contract.Plugin` 登记上述类 |
| 宿主必选插件 | `nexmux-spring` 的 `nexus/console-plugins.yaml` 在现有 `com.innospots.nexus.console.*` 之外 **增加** `com.innospots.nexmux.console`（与 kernel 同 classpath） |

实现方式：单类 `NexmuxConsoleEntryPlugin` — `ConsoleModuleDescriptor` 声明页面键，`ConsoleModuleDescriptor.pagePath` 生成路由，目录菜单用 `MenuDeclaration`；与 `ui-pages/nexmux/{moduleKey}/` 对齐。

## 4. 命名约定

| 类型 | 规则 | 示例 |
|------|------|------|
| `menuKey` | kebab-case，前缀 `nexmux-` | `nexmux-asset-provider` |
| `pageKey` | kebab-case，前缀 `nexmux-`；列表类默认菜单入口 `nexmux-{resourceKey}-list`（见 `NexmuxConsolePageConvention`）；调试台 `nexmux-debug-{client\|model}` | `nexmux-provider-list` |
| 默认菜单入口 | 每模块 `entryPageKey` = 菜单叶子绑定的 `pageKey`，且为 `ConsoleModuleDeclaration.pages` **第一项** | `nexmux-provider-list` |
| `pagePath` | 与 Nexus 一致：`/page/{domainKey}/{moduleKey}/{pageKey}`（`ConsoleModuleDescriptor.pagePath`） | `/page/nexmux/provider/nexmux-provider-list` |
| `permission` | 与 page 或 action 绑定；前缀 `nexmux.` | `nexmux.provider.view` |
| `datasourceKey` | 列表页代理 API 时用；`nexmux.{resource}` | `nexmux.providers` → `GET /api/d/providers` |

`resourceKey`（同步进 catalog）：`module:nexmux`、`page:nexmux.{pageKey}`、`menu:{menuKey}`（由 Nexus 同步规则生成，实现侧勿手写落库）。

## 5. 菜单与页面对照（产品 §5 + 线框）

侧栏 **orderIndex** 按 10 递增；图标为 Nexus 菜单 `icon` 字符串（与线框 Logo/模块语义对齐，可后续换 icon font）。

### 5.1 菜单树（P0 可点击；P1/P2 可 `disabled` 或隐藏）

```text
nexmux（MODULE，不在侧栏单独显示时可作为根 module）
├── 首页                          [P1]  nexmux-home
├── 模型资产（directory）
│   ├── Provider                    [P0]  nexmux-asset-provider
│   └── 模型                          [P0]  nexmux-asset-model
├── 模型路由（directory）
│   └── 路由                          [P0]  nexmux-route
├── 访问治理（directory）
│   ├── 客户端                        [P0]  nexmux-access-client
│   └── 客户端模板                    [P1]  nexmux-access-client-template
├── 运行管理（directory）
│   ├── 调用日志                      [P0]  nexmux-ops-request-log
│   ├── 模型用量                      [P1]  nexmux-ops-model-usage
│   ├── 供应商用量                    [P1]  nexmux-ops-provider-usage
│   ├── 客户端用量                    [P1]  nexmux-ops-client-usage
│   └── 供应商可用性                  [P1]  nexmux-ops-provider-sla
├── 调试台（directory）
│   ├── 客户端调试                    [P0]  nexmux-debug-client
│   └── 模型调试                      [P0]  nexmux-debug-model
└── 系统管理（directory）
    ├── 服务设置                      [P1]  nexmux-system-settings
    └── 审计                          [P0]  nexmux-system-audit
```

### 5.2 P0 页面路由与线框

| menuKey | pageKey | pagePath（SPA） | 线框 | 主要 Entity |
|---------|---------|-----------------|------|-------------|
| nexmux-asset-provider | nexmux-provider-list | `/page/nexmux/provider` | [Provider 列表](../../../product-design/nexmux/prd-diagram/模型资产/Provider/01-Provider列表页.md) | `ProviderEntity` |
| — | nexmux-provider-detail | `/page/nexmux/provider/{providerCode}` | [02 详情](../../../product-design/nexmux/prd-diagram/模型资产/Provider/02-Provider详情页.md) | 同上 |
| — | nexmux-provider-edit | `/page/nexmux/provider/{providerCode}/edit` | [03 编辑](../../../product-design/nexmux/prd-diagram/模型资产/Provider/03-Provider新增编辑页.md) | 同上 |
| nexmux-asset-model | nexmux-model-list | `/page/nexmux/model` | [Model 列表](../../../product-design/nexmux/prd-diagram/模型资产/Model/01-Model列表页.md) | `ModelEntity` |
| — | nexmux-model-detail | `/page/nexmux/model/{modelCode}` | [02 详情](../../../product-design/nexmux/prd-diagram/模型资产/Model/02-Model详情页.md) | 同上（含 `pricing_spec` Tab） |
| — | nexmux-model-edit | `/page/nexmux/model/{modelCode}/edit` | [03 编辑/导入](../../../product-design/nexmux/prd-diagram/模型资产/Model/03-Model新增编辑页.md) | 同上 |
| nexmux-route | nexmux-route-list | `/page/nexmux/route` | [路由列表](../../../product-design/nexmux/prd-diagram/模型路由/01-路由列表页.md) | `RouteEntity` |
| — | nexmux-route-detail | `/page/nexmux/route/{alias}` | [02 详情](../../../product-design/nexmux/prd-diagram/模型路由/02-路由详情页.md) | 同上 |
| — | nexmux-route-edit | `/page/nexmux/route/{alias}/edit` | [03 编辑](../../../product-design/nexmux/prd-diagram/模型路由/03-路由新增编辑页.md) | 同上 |
| nexmux-access-client | nexmux-client-list | `/page/nexmux/access/client` | [Client 列表](../../../product-design/nexmux/prd-diagram/访问治理/Client/01-客户端列表页.md) | `ClientEntity` |
| — | nexmux-client-detail | `/page/nexmux/access/client/{clientCode}` | [02 详情](../../../product-design/nexmux/prd-diagram/访问治理/Client/02-客户端详情页.md) | 同上 |
| — | nexmux-client-edit | `/page/nexmux/access/client/{clientCode}/edit` | [03 编辑](../../../product-design/nexmux/prd-diagram/访问治理/Client/03-客户端新增编辑页.md) | 同上 |
| nexmux-ops-request-log | nexmux-request-log-list | `/page/nexmux/ops/request-log` | [调用日志](../../../product-design/nexmux/prd-diagram/运行管理/01-调用日志列表与详情.md) | `RequestLogEntity` |
| — | nexmux-request-log-detail | `/page/nexmux/ops/request-log/{requestId}` | 同上 | 同上 |
| nexmux-debug-client | nexmux-debug-client | `/page/nexmux/debug/client` | [客户端调试](../../../product-design/nexmux/prd-diagram/调试台/01-客户端调试.md) | —（调 API） |
| nexmux-debug-model | nexmux-debug-model | `/page/nexmux/debug/model` | [模型调试](../../../product-design/nexmux/prd-diagram/调试台/02-模型调试.md) | — |
| nexmux-system-audit | nexmux-audit-list | `/page/nexmux/system/audit` | [审计](../../../product-design/nexmux/prd-diagram/系统管理/02-审计.md) | `ConfigAuditLogEntity` |
| — | nexmux-audit-detail | `/page/nexmux/system/audit/{auditId}` | 同上 | 同上 |

详情/编辑页 **不** 各占侧栏菜单项；仅声明 `UiSpecPageDeclaration` 供路由与 `ConsolePagePermissionFilter`（`X-Nexus-Page-Key`）使用。列表页 `pageKey` 作为默认菜单入口。

## 6. Entity ↔ API ↔ 权限 ↔ Catalog

| Entity | 稳定键参数 | 管理 API 前缀 | 列表页 permission | 写/发布类 permission |
|--------|-----------|---------------|-------------------|----------------------|
| ProviderEntity | `providerCode` | `/providers` | `nexmux.provider.view` | `nexmux.provider.manage` |
| ModelEntity | `modelCode` | `/models` | `nexmux.provider.view`（复用资产查看）或 `nexmux.model.view`（若拆分） | `nexmux.model.manage` |
| RouteEntity | `alias` | `/routes` | `nexmux.route.view` | `nexmux.route.manage` + `nexmux.route.publish` |
| ClientEntity | `clientCode` | `/clients` | `nexmux.provider.view` | `nexmux.client.secret`（轮换）+ 策略编辑 |
| RequestLogEntity | `requestId` | `/request-logs` | `nexmux.ops.logs.view` | — |
| ConfigAuditLogEntity | `auditId` | `/audit-logs` | `nexmux.audit.view` | — |
| ClientTemplateEntity | `templateCode` | `/client-templates` | `nexmux.client.view` | `nexmux.client.manage` |
| DebugInvoke | `modelCode` / `clientCode` | `/debug` | `nexmux.debug.invoke` | 同左 |

**页面级鉴权**：列表/详情请求携带 `X-Nexus-Page-Key: {pageKey}`；与 `nexmux.console.web` 配置一致（见 `nexmux-spring` `console-web.yaml`）。

**权限资源登记**：Entry 插件启动后，通过 Nexus **permission 插件** 的 resource sync 机制登记上表 key（实现阶段在 `console.permission` 包提供 `NexmuxPermissionResources` 常量 + 同步贡献，或启动时调用既有 Sync API）；**不**新建 `mux_permission` 表。

## 7. UiSpec / PageDsl

> **2026-10-12 起本节作废**：页面 DSL 已收敛为 nexmux-ui 单一来源（Pactor DSL），kernel 不再持有 `ui-pages/` PageDsl 镜像，`ClasspathPageDslLoader` 契约不再适用。Entry 插件仅保留菜单与页面键声明（供 Nexus Console 导航挂载）。历史内容见 git 历史。

页面方案结构、布局模式（列表 / Tab 详情 / 纵向区块 / 表单 / 调试台）、P0 yaml 清单与最小壳模板见 **[nexmux-ui-spec-pages-design.md](./nexmux-ui-spec-pages-design.md)**。

与 Nexus `ClasspathPageDslLoader` 对齐：每个页面须在 **`ui-pages/nexmux/{moduleKey}/{pageKey}.yaml`** 存在且 `page.id` 一致（见 [nexmux-yaml-configuration-design.md](./nexmux-yaml-configuration-design.md) §6）。

## 8. 契约骨架（Entry 插件）

```java
public final class NexmuxConsoleEntryPlugin implements Plugin {

    public static final String PLUGIN_ID = "com.innospots.nexmux.console";
    public static final String DOMAIN_KEY = "nexmux";
    public static final String MODULE_PROVIDER = "provider";

    @Override
    public PluginDefinition definition() {
        return PluginDefinition.builder(PLUGIN_ID)
                .displayName(I18nObject.of("en", "Nexmux", "zh", "Nexmux 模型管理"))
                .description(I18nObject.of("en", "Model console for Nexmux.", "zh", "Nexmux 模型管理控制台。"))
                .version("1.0.0")
                .tags(Tags.from(Map.of("scope", "console", "product", "nexmux")))
                .contribute(NexmuxConsoleContribution.get())
                .build();
    }
}
```

`NexmuxConsoleContribution.get()` 返回多个 `ConsoleModuleDeclaration`（`domainKey=nexmux`，`moduleKey=provider|model|…`），各模块含本域页面与菜单目录片段。

## 9. 与领域实体设计的关系

| 产品对象 | Entity 表 | Entry 暴露 |
|----------|-----------|------------|
| Provider | `mux_provider` | 菜单 + 列表/详情/编辑 pageKey |
| Model | `mux_model` | 同上；详情 Tab（定价/提供商）为 **同页** 前端分区，不增 menu |
| Route | `mux_route` | 单页表单编辑（线框无 Tab） |
| Client | `mux_client` | 列表 + 单页分区（配额/限流/授权） |
| Client Template | `mux_client_template` | P1 菜单项 |
| 调用日志 / 审计 | `mux_request_log` / `mux_config_audit_log` | 只读页 |

配置变更审计 **不** 单独做 entry；走 `nexmux-system-audit`。

## 10. 测试范围

| 测试 | 断言 |
|------|------|
| `NexmuxConsoleEntryPluginTest` | `definition()` 含 `ConsolePluginContribution`；`domainKey=nexmux`；含 `provider`/`model` 等 `moduleKey` |
| `NexmuxConsoleModuleEntryContractTest` | 每模块 `entryPageKey` 为 `pages` 首项；菜单叶子 `pageKey` 属于本模块；列表入口命名符合 `NexmuxConsolePageConvention` |
| `NexmuxConsoleCatalogMenuTest` | P0 menuKey 集合与 §5.1 一致；目录/页面互斥 |
| `NexmuxConsolePagePathTest` | 所有 `pagePath` 以 `/page/nexmux` 开头且无 query |
| `NexmuxEntityPageMappingTest` | §6 表每个 P0 Entity 至少一个 pageKey |

## 11. 开放问题

| # | 问题 | 建议 |
|---|------|------|
| 1 | Model 查看权限是否独立 `nexmux.model.view` | P0 可与 `nexmux.provider.view` 合并为 `nexmux.asset.view` |
| 2 | P1 菜单是否预注册但 `status=DISABLED` | 是，便于 UI 占位与权限预置 |
| 3 | PageDsl 与纯 SPA 的最终策略 | 见 [ui-spec 文档](./nexmux-ui-spec-pages-design.md) §12 |
