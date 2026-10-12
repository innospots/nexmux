# Nexmux 设计文档目录

本目录存放 **Nexmux**（企业级模型管理系统）工程的设计决策与契约，供 `java:develop` 施工。

**入口**：从本文件进入；子目录 `design/` 存放 L1/L2 方案与 ADR。

## 产品需求来源（只读引用）

产品文档当前存放在 `product-design/gateweave/` 目录（历史路径名；**产品名称为 Nexmux**）。

| 文档 | 路径 |
|------|------|
| 需求说明书 V2.1 | [`../../product-design/gateweave/模型管理系统产品功能需求说明书.md`](../../product-design/gateweave/模型管理系统产品功能需求说明书.md) |
| 管理控制台设计 V1.0 | [`../../product-design/gateweave/模型管理系统-管理控制台产品功能设计文档.md`](../../product-design/gateweave/模型管理系统-管理控制台产品功能设计文档.md) |
| 线框图目录 | [`../../product-design/gateweave/prd-diagram/README.md`](../../product-design/gateweave/prd-diagram/README.md) |

可运行控制面组装：**nexmux-spring**（Model Console）。

## 本仓库设计文档索引

| 级别 | 文档 | 说明 |
|------|------|------|
| L2 | [nexmux-architecture-design.md](./design/nexmux-architecture-design.md) | 控制面 / 数据面、模块归属、词汇、分阶段路线 |
| L2 | [nexmux-domain-layer-design.md](./design/nexmux-domain-layer-design.md) | Entity / Request / Vo / Value 分包与 P0 类型骨架 |
| L2 | [nexmux-console-entry-plugin-design.md](./design/nexmux-console-entry-plugin-design.md) | 控制台 Entry 插件、菜单/页面与 Entity 对照 |
| L2 | [nexmux-yaml-configuration-design.md](./design/nexmux-yaml-configuration-design.md) | **YAML 总规范**：Spring 分项、`nexmux.yaml`、PageDsl 路径与禁止项 |
| L2 | [nexmux-ui-spec-pages-design.md](./design/nexmux-ui-spec-pages-design.md) | UiSpec/PageDsl 页面方案结构（S-LIST / Tab / Stack 等） |
| L1 | [nexmux-p0-console-design.md](./design/nexmux-p0-console-design.md) | P0 可调用闭环：域划分、表意契约、测试范围 |
| L2 | [nexmux-ui-data-api-design.md](./design/nexmux-ui-data-api-design.md) | UI 数据接口：BFF 适配层、后端端点补全、mux_request_log 扩列、实施批次 |
| L2 | [nexmux-runtime-design.md](./design/nexmux-runtime-design.md) | **Model Runtime（数据面）P0**：profile 进程形态、Ingress、调用流水线、探活、计量全量、状态码与测试范围 |
| 参考 | [nexmux-ui-data-api-inventory.md](./design/nexmux-ui-data-api-inventory.md) | **已实现**数据接口清单（字段级输入输出；随批次滚动更新） |
| L3 | [0001-console-runtime-configuration-boundary.md](./design/adr/0001-console-runtime-configuration-boundary.md) | Console 与 Runtime 配置边界 ADR |
| L3 | [0002-runtime-deployment-form.md](./design/adr/0002-runtime-deployment-form.md) | Runtime 工程形态（独立 profile 进程）与运行库共用（修订 ADR-0001 决策 5） |

命名约定（`java:design`）：

- 域方案：`*-design.md`，置于 `docs/design/`
- 单项决策：`docs/design/adr/NNNN-<kebab-slug>.md`
- 总目录：本文件 `nexmux-design-catalog.md`（不使用 `README` 作为文档名）

## 工程约束

实现须遵守仓库根 [`AGENTS.md`](../AGENTS.md)：仅 `nexmux-bom` / `nexmux-kernel` / `nexmux-spring` / `nexmux-ui` 四模块，平台能力通过 `innospots-nexus-*` 依赖挂接。

## 设计前审查（grill-me 摘要）

| 项 | 结论 / 待确认 |
|----|----------------|
| 平台级 vs Nexus Workspace | Nexmux **不做 Tenant/Workspace 资源隔离**；Nexus Console 默认带 workspace 会话。实现须在 **平台域（PLATFORM realm）** 下挂载菜单与 API，配置表**不含** `tenant_id` / `workspace_id` 隔离列（与 PRD §3.7 一致）。 |
| Runtime 落仓 | Runtime 设计已立项（[nexmux-runtime-design.md](./design/nexmux-runtime-design.md)）：不新增 Maven 模块，`nexmux-spring` 独立 `runtime` profile 进程启动，代码归属 `nexmux-kernel.inbound`（见 ADR-0002）。 |
| 身份与权限 | 不建设用户/角色页；复用 Nexus 控制台鉴权 + 外部 IAM；Nexmux 定义 **页面级 permission key**（前缀 `nexmux.`）与三类默认角色映射（PRD §4）。 |
| 状态码模块码 | 产品域使用 **`NMX`**，异常统一 `NexusException` + `StatusCode`（`innospots-nexus-base`）。 |

完整 grill-me 回合建议在方案评审前由产品/架构确认上表「待确认」项，并将结论回写设计文档开放问题节。
