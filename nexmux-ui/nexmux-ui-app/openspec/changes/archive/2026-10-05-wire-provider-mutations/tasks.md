# Tasks: wire-provider-mutations

## 1. 前置验证（先行排除设计风险）

- [x] 1.1 验证 DSL `request` 动作的失败分支与响应/错误读取能力：以详情页「测试连接」按钮为试点，发起 `POST /api/data/providers/{code}/connection-test`，确认成功可读 `ok`/`latencyMs`、失败可取后端 `error` 文案（参考 pactor `references/dsl-authoring.md`）；若能力不足，按 design D7 备选收敛
- [x] 1.2 从 catalog 种子数据提取 `defaultProtocol` 全部枚举值（如 `openai-completions` 等），固化为表单选项值清单

## 2. BFF 代理层改造

- [x] 2.1 `app/api/data/[...parts]/route.ts`：增加 `DELETE` 导出；桩操作（不触碰真实后端的写请求）豁免 mock 503 闸门
- [x] 2.2 `src/pactor/backend/services-real.ts`：新增桩映射表（集中登记无后端接口的写操作，如 `DELETE providers`），统一返回 `{ stub: true, ok: true }`
- [x] 2.3 `src/pactor/backend/services-real.ts` 的 `PUT providers` 处理器实现字段拆分：`displayName`/`baseUrl`/`region`/`timeoutMs` → `updateProvider`；`status` 变化 → `setProviderEnabled`；其余键落桩成功；真实环节顺序执行、任一失败即返回指明环节的错误
- [x] 2.4 `src/pactor/backend/adapters/provider-adapter.ts` 的 `createProvider`：`vendorCode` 为空时回填 `providerCode`，`sourceVendorCode` 为空时回填 `vendorCode`；`POST providers` 透传体中的契约外字段（如 `apiKey`）落桩忽略
- [x] 2.5 更新/补充适配器与拆分逻辑的 vitest 用例（vendorCode 回填、PUT 字段拆分、桩响应形状）

## 3. 新建页（provider-create.yaml）

- [x] 3.1 `providerType` 选项值改为 `COMMERCIAL`/`CLOUD`/`PRIVATE`/`CUSTOM`（label 保留中文）；`defaultProtocol` 选项值改为 1.2 的协议码
- [x] 3.2 `onSubmit` 改为 `request` 动作 `POST /api/data/providers`，body 整包 `${event}`；成功 → message + `navigate /providers`；失败 → message 透传后端 `error`
- [x] 3.3 `apiKey` 字段说明文案调整为「当前版本密钥不落库（代理层默认成功）」语义

## 4. 编辑页（provider-edit.yaml）

- [x] 4.1 `providerType`/`defaultProtocol` 选项值域同 3.1 改为枚举码（保证回填命中选项）
- [x] 4.2 `onSubmit` 改为 `request` 动作 `PUT /api/data/providers/{code}`，body 整包 `${event}`；成功 → message + `navigate /providers/{code}`；失败 → message 透传后端 `error`；移除旧的「确认停用」confirm 演示编排（status 拆分由 BFF 处理）
- [x] 4.3 各字段保持可编辑；`apiKey` 说明文案同 3.3

## 5. 启用/停用（provider-list.yaml + provider-detail.yaml）

- [x] 5.1 列表卡片启停：确认弹层「确认停用/启用」点击后 `request` 调用 `POST /api/data/providers/{code}/enable|disable`，成功 → `reload` providers 数据源；失败 → message 透传错误；移除「演示：静态页面不落库」文案
- [x] 5.2 详情页头部启停按钮同样接入，成功后 `reload` detail 数据源，移除演示文案

## 6. 删除入口（provider-detail.yaml）

- [x] 6.1 详情页「···」下拉菜单的演示项替换为「删除」：确认弹层展示影响范围（`modelCount`/`routes.total`），确认后 `request DELETE /api/data/providers/{code}`，成功 → message（注明演示删除）+ `navigate /providers`；失败 → message 透传错误

## 7. 连接测试（provider-detail.yaml）

- [x] 7.1 「连接与凭证」标签的「测试连接」按钮接入 `POST /api/data/providers/{code}/connection-test`：成功展示含真实 `latencyMs` 的消息，失败展示后端 `detail`/`error`；移除「演示」文案

## 8. 验证

- [x] 8.1 mock 模式（默认）：四个页面正常渲染；删除走桩成功并提示；create/edit/启停/连接测试展示「演示模式（mock），写操作不可用」语义提示；控制台无未捕获错误
- [x] 8.2 真实后端模式（`NEXMUX_BACKEND_BASE_URL`）：端到端验证 create → 列表可见、edit → 详情回显新值（含 status 拆分启停）、enable/disable → 状态切换、connection-test → 真实结果、delete → 桩成功；providerCode 重复时展示 409 错误文案
- [x] 8.3 回归：列表/详情读取链路（筛选、分页、卡片渲染）无破坏；`npx tsc --noEmit`、`pnpm test`、`pnpm lint`（除 theme-switcher 存量外）通过
- [x] 8.4 注意：每轮 YAML 改动后需对 `src/server/sitemap.ts` 做真实内容改动以触发 dev server 重载（模块级缓存）
