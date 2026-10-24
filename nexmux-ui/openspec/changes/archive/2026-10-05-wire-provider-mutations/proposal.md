# Proposal: wire-provider-mutations

## Why

供应商（Provider）页面的新建、编辑、启停、删除、连接测试等操作目前全部是演示桩：表单提交只弹 message 不调用接口，启停确认后提示「演示：静态页面不落库」。BFF 写通道（`POST/PUT /api/data/providers/**` → `pactorMutations` → 后端适配器）与表单字段命名（`providerCode`/`displayName`/`defaultProtocol`/`timeoutMs`）已就绪，但页面 DSL 尚未真正发起写请求，供应商域无法完成任何管理操作。对于后端尚无接口的操作（删除、API Key 保存、部分字段更新），先行对接到 BFF 代理层并默认返回成功，使页面交互闭环先行打通，后端接口就绪后只改代理层。

## What Changes

- 新建 Provider 表单（`provider-create.yaml`）`onSubmit` 改为真实调用 `POST /api/data/providers`，成功跳回列表，失败透传后端错误消息
- 编辑 Provider 表单（`provider-edit.yaml`）`onSubmit` 改为真实调用 `PUT /api/data/providers/{code}`；表单整包提交，由 BFF 代理层拆分：`ProviderUpdateRequest` 字段（`displayName`/`baseUrl`/`region`/`timeoutMs`）转发后端，`status` 拆分调用 enable/disable 接口，其余字段（`description`/`proxy`/`capabilityExtension`/`apiKey`）代理层默认返回成功（不落库）
- **新增删除 Provider 入口**（详情页操作菜单）：确认后调用 `DELETE /api/data/providers/{code}`，由 BFF 代理层默认返回成功（后端无 `DELETE` 接口，已在 OpenAPI 核实），成功后跳回列表
- 列表页与详情页的启用/停用确认接通 `POST /api/data/providers/{code}/enable|disable`（后端真实接口），成功后刷新数据
- 详情页「测试连接」接通 `POST /api/data/providers/{code}/connection-test`，展示真实 `ok`/`latencyMs`/`detail`
- `providerType`/`defaultProtocol` 的选项值从展示文案改为后端枚举码（如 `COMMERCIAL`/`openai-completions`），消除值域错配
- BFF 写通道增加 `DELETE` 方法支持；代理层对「无后端接口的写操作」提供默认成功桩，并在响应中标注 `stub: true` 以便后续替换
- 演示模式（mock）下：纯代理桩操作（删除、apiKey 等）直接返回成功；依赖真实后端的操作（create/edit 真实字段、启停、连接测试）保持 503 降级提示

**不做**：

- 拉取模型（pull-models）的「确认拉取」批量创建 Model——属 Model 域写操作，且后端有真实接口，另行提案对接
- 其他域（model/route/client 等）的写对接
- BFF 写通道、适配器函数的重构（已存在，仅允许小修补：vendorCode 回填、DELETE 支持、桩映射）

## Capabilities

### New Capabilities

- `provider-mutations`: 供应商域写操作对接——新建、编辑（代理层字段拆分）、启用/停用、删除（代理桩）、连接测试的请求编排、错误映射与成功后刷新；表单选项值域对齐后端枚举
- `proxy-stub-mutations`: BFF 代理层对无后端接口写操作的默认成功桩——桩映射表、`stub: true` 响应标注、与真实后端写通道的分发关系、mock 模式行为

### Modified Capabilities

（无既有 specs，全部为新增）

## Impact

- **页面 DSL**：`src/server/yaml/provider/provider-create.yaml`、`provider-edit.yaml`、`provider-list.yaml`、`provider-detail.yaml`（onSubmit/onClick 事件编排由演示 message 改为 `request` 动作 + 条件刷新；详情页操作菜单新增删除项）
- **BFF 层**：`app/api/data/[...parts]/route.ts`（增加 `DELETE` 导出 + 桩操作绕过 mock 503 闸门）、`src/pactor/backend/services-real.ts` 的 `pactorMutations`（增加 `DELETE providers`、字段拆分逻辑、桩映射）
- **适配器**：`src/pactor/backend/adapters/provider-adapter.ts`（`createProvider` 补 vendorCode 回填；其余复用）
- **后端契约**（只读依据）：`docs/design/nexmux-ui-data-api-inventory.md` §1.1、`http://localhost:8082/openapi/specs/nexmux-kernel`
- **不影响**：读取链路（list/detail/pull-models 已对接）；其他域页面
