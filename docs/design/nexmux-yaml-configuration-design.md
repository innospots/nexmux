# Nexmux YAML 配置与 PageDsl 规范

## 1. 文档定位

- **状态**：已采纳（与 `innospots-nexus-plugin` PageDsl、`ConsoleModuleDeclaration` 现行契约对齐）
- **日期**：2026-09-27
- **归属**：`nexmux-kernel`（PageDsl）、`nexmux-spring`（Spring / Nexus 宿主配置）
- **关联**：
  - [nexmux-ui-spec-pages-design.md](./nexmux-ui-spec-pages-design.md) — 页面方案与线框映射
  - [nexmux-console-entry-plugin-design.md](./nexmux-console-entry-plugin-design.md) — Entry、`pagePath`、权限
  - [nexmux-p0-console-design.md](./nexmux-p0-console-design.md) — `nexmux.*` 业务配置键
  - [ADR-0001](./adr/0001-console-runtime-configuration-boundary.md) — Console / Runtime 配置边界
  - Nexus：`PageDslConfig`、`ClasspathPageDslLoader`、`ConsoleCatalogSyncService`

本文件是 **本仓库所有 YAML 的单一规范入口**；实现与评审以本节约束为准。

## 2. YAML 分类总览

| 类别 | 路径（源码） | 职责 | 键前缀 / 身份 |
|------|----------------|------|----------------|
| **A. Boot 引导** | `nexmux-spring/src/main/resources/application.yaml` | 应用名、Jersey、`spring.config.import`、数据源、**schema 脚本列表** | `spring.*`、`server.*` |
| **B. Nexus 宿主** | `nexmux-spring/src/main/resources/nexus/console-*.yaml` | 鉴权、Web 过滤器、OpenAPI、**必选插件 ID** | `nexus.*` |
| **C. 产品业务** | `nexmux-spring/src/main/resources/nexus/nexmux.yaml` | Console API 基路径、AppKey 宽限期、调试截断、Runtime 轮询（仅声明） | `nexmux.*` |
| **D. PageDsl / UiSpec** | `nexmux-kernel/src/main/resources/ui-pages/{domainKey}/{moduleKey}/{pageKey}.yaml` | 控制台页面契约、数据源与权限声明 | `dsl` + `page.id` |

```text
禁止在本仓库新增业务含义的 *.properties（含 MyBatis / Spring 业务键）。
业务与宿主分项配置一律使用 YAML；本地覆盖使用 optional:file:./config/nexus/...（与 application.yaml 注释一致）。
```

## 3. 分类 A：`application.yaml` 约束

| 项 | 规则 |
|----|------|
| 职责边界 | 仅 **引导与基础设施**：`spring.application.name`、`spring.jersey`、`spring.datasource`、`spring.sql.init` |
| 配置导入 | 通过 `spring.config.import` 引入 `classpath:nexus/console-*.yaml` 与 `classpath:nexus/nexmux.yaml`；**不得**把 Nexmux 业务键写入本文件 |
| Schema | `spring.sql.init.schema-locations` 逗号分隔；须包含 `plugin-installation-h2.sql`、`console-catalog-h2.sql`、**`nexmux-h2.sql`**（产品表） |
| 覆盖 | 生产/本地可使用 `optional:file:./config/nexus/<同名文件>` 覆盖 classpath 分项，**不**复制整棵配置进 `application.yaml` |

## 4. 分类 B：`nexus/console-*.yaml` 约束

| 文件 | 绑定用途 |
|------|----------|
| `console-auth.yaml` | 登录、令牌、PLATFORM/TENANT 鉴权入口 |
| `console-credential.yaml` | 凭证域 Dao / 服务装配 |
| `console-web.yaml` | Jersey 过滤器、CORS、`X-Nexus-Page-Key` 页面权限、`permit-all-patterns` |
| `console-openapi.yaml` | OpenAPI / Scalar |
| `console-plugins.yaml` | `nexus.plugins.required` 必须包含 `com.innospots.nexmux.console` |

**约束**：

- 仅描述 **Nexus 控制台宿主**；不定义 `mux_*` 表、不定义 `/api/d` 资源字段。
- 与 Nexmux 产品相关的只有 **插件必选列表** 与 Web 层对 `X-Nexus-Page-Key` 的通用行为（见 `console-web.yaml` 注释）。
- Workspace 会话由宿主写入 `SessionContext`；**不**在 YAML 中为 Nexmux 配置表增加 `tenant_id` / `workspace_id` 隔离项。

## 5. 分类 C：`nexus/nexmux.yaml` 约束

部署时建议同步到发行包 **`config/nexus/nexmux.yaml`**（与 P0 设计 §7 一致）。

| 键 | 类型 | 说明 | 消费方 |
|----|------|------|--------|
| `nexmux.console.api-base-path` | string | 管理 API 前缀，固定 `/api/d` | Console 文档、PageDsl `meta.apiBase`、实现 `@Path` |
| `nexmux.client.app-key-grace-seconds` | int | AppKey 轮换宽限期（秒） | `ClientService` |
| `nexmux.debug.max-response-body-bytes` | int | 调试台响应体上限 | `DebugService` |
| `nexmux.runtime.snapshot-poll-interval-seconds` | int | Runtime 拉取 `published_route_spec` 间隔 | **仅 Runtime/inbound**；Console 只写库 |

**约束**：

- 所有键以 **`nexmux.`** 为根；新增键须在本表与 P0 设计附录同步登记。
- **不得**在此文件配置 H2 URL、JDBC 驱动（归属 `application.yaml`）。
- **不得**在此文件重复 `nexus.plugins`（归属 `console-plugins.yaml`）。
- Runtime 专用键在 Console 进程内可绑定为配置 Bean，但 **不得**在 Console 请求链路中依赖其触发推理。

## 6. 分类 D：PageDsl（UiSpec）约束

### 6.1 Classpath 路径（硬约束）

Nexus 加载路径由 `PageDslConfig.resourcePath` 决定：

```text
ui-pages/{domainKey}/{moduleKey}/{pageKey}.yaml
```

**术语（勿混用）**

| 概念 | 键 | 说明 |
|------|-----|------|
| **产品领域** | `domainKey` = `nexmux` | 全产品 PageDsl 与 `ConsoleModuleDeclaration.domainKey` 固定值；**不是**菜单里的「模块」 |
| **控制台模块** | `moduleKey` = `provider` / `model` / … | 与侧栏业务块、Java `core.{provider,model,...}` 对齐；**`nexmux` 不作为 moduleKey** |
| **Entry 插件** | `pluginId` = `com.innospots.nexmux.console` | 一个插件可贡献 **多个** `ConsoleModuleDeclaration`（每个自带 `moduleKey`） |

| `moduleKey` | 页面前缀示例 | PageDsl 目录 |
|-------------|--------------|--------------|
| `provider` | `nexmux-provider-*` | `ui-pages/nexmux/provider/` |
| `model` | `nexmux-model-*` | `ui-pages/nexmux/model/` |
| `route` | `nexmux-route-*` | `ui-pages/nexmux/route/` |
| `client` | `nexmux-client-*` | `ui-pages/nexmux/client/` |
| `ops` | `nexmux-request-log-*` | `ui-pages/nexmux/ops/` |
| `debug` | `nexmux-debug-*` | `ui-pages/nexmux/debug/` |
| `audit` | `nexmux-audit-*` | `ui-pages/nexmux/audit/` |

`pageKey` 见 [ui-spec §5](./nexmux-ui-spec-pages-design.md)；文件名（不含后缀）= `page.id`。

示例：

```text
nexmux-kernel/src/main/resources/ui-pages/nexmux/provider/nexmux-provider-list.yaml
```

**禁止**：

- 使用 `ui-pages/nexmux/nexmux/{pageKey}.yaml`（把产品名误当作 `moduleKey`）。
- 使用 `ui-pages/nexmux/{pageKey}.yaml`（缺少 `moduleKey` 段，加载失败）。
- 使用 `.yml` 作为新产品页面后缀（平台允许 `.yml`，本仓库 **统一 `.yaml`**）。
- `page.id` 与 `pageKey` / 文件名不一致。

### 6.2 与 Entry / SPA 路由的关系

| 概念 | 规范 |
|------|------|
| `UiSpecPageDeclaration.pagePath` | 与 Nexus 统一：`/page/{domainKey}/{moduleKey}/{pageKey}`（Nexmux 即 `/page/nexmux/provider/nexmux-provider-list` 等） |
| PageDsl `meta.routePath` | 与 Entry `pagePath` **逐字一致**（勿使用顶层 `route:`，解析器不识别） |
| 管理 REST | `meta.apiBase` = `nexmux.console.api-base-path`（默认 `/api/d`） |
| 页面权限 HTTP 头 | `X-Nexus-Page-Key: {page.id}` |

内置与产品均使用 `ConsoleModuleDescriptor.PAGE_ROUTE_PREFIX`（`/page`）+ `/{domainKey}/{moduleKey}/{pageKey}`；Nexmux 的 `domainKey=nexmux`。

### 6.3 文档结构与解析

| 项 | 规则 |
|----|------|
| `dsl` | 字面量 `'1.0'`（与 Nexus 一致） |
| 未知字段 | 默认 **严格**：`PageDslConfig.failOnUnknownProperties=true`，拼写错误即启动/同步失败 |
| 最小 P0 壳 | 须含 `page`、`meta`、`route`；`dataSources` / `actions` 可为 `{}`；`body` 可省略或 `null` |
| `page.permission` | 与 [P0 §6](./nexmux-p0-console-design.md) 的 `nexmux.*` key 一致 |
| `regions` | Nexmux 文档扩展段；解析器可忽略，**不得**与 PageDsl 标准段键名冲突 |

### 6.4 数据绑定与领域命名

PageDsl 中 HTTP 字段名、state 键与 API JSON **对齐 Java record/Vo**（camelCase），并遵守领域层约束：

| 禁止 | 改用 |
|------|------|
| `*_json` / `diff_json` | `pricingSpec`、`changeDiff`、`extraBindings`、`authorizedRouteIds` 等 |
| 在 YAML 内嵌 SQL | `dataSources` 仅 `http` / `service` / `static` |
| 相对 URL 省略 api 基址 | 集合接口写 `/providers` 等，由 `meta.apiBase` 拼接 |

CLOB 列在 API 层以 **结构化对象** 暴露；PageDsl 不定义「原始 JSON 列」编辑键名。

## 7. 一致性检查清单

| # | 检查 |
|---|------|
| 1 | 每个 P0 页面在 `ui-pages/nexmux/{moduleKey}/{pageKey}.yaml` 存在，且 `ClasspathPageDslLoader.load("nexmux", moduleKey, pageKey)` 成功 |
| 2 | 每个 `ConsoleModuleDeclaration`：`domainKey=nexmux`；`moduleKey` 为上表之一（**非** `nexmux`） |
| 3 | `route.path` = Entry `pagePath`；路径参数名 = Entity 稳定键（`providerCode`、`modelCode`、`alias`、`clientCode`、`requestId`、`auditId`） |
| 4 | `meta.apiBase` = `nexmux.yaml` 中 `nexmux.console.api-base-path` |
| 5 | 业务配置只出现在 `nexus/nexmux.yaml`，不在 `application.yaml` 或 `console-*.yaml` 堆叠 |
| 6 | 无新增 `*.properties` 业务配置 |

## 8. 测试挂钩

| 测试类 | 断言 |
|--------|------|
| `NexmuxConsolePagePathTest` | 全部 `pagePath` 以 `/page/nexmux` 开头且无 query |
| `NexmuxPageDslClasspathTest` | 对每个 P0 页面调用 `load("nexmux", moduleKey, pageKey)`，`moduleKey` 与上表一致 |
| `NexmuxPageRouteAlignmentTest`（建议） | yaml `route.path` 与 Entry 表一致 |

## 9. 变更流程

1. 改 **API 或配置键** → 同步 `nexmux.yaml`、P0 §7、相关 PageDsl `meta` / `dataSources`。
2. 改 **页面路由** → 同步 Entry `NexmuxConsoleCatalog`、`ui-pages` 下 yaml、`nexmux-ui-spec-pages-design.md` §5。
3. 改 **PageDsl 标准段** → 先对齐 Nexus `innospots-nexus-plugin` 解析器与契约测试，再改本产品 yaml。
