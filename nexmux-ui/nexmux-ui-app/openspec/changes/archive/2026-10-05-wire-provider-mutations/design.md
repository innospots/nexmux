# Design: wire-provider-mutations

## Context

供应商域读取链路已完成真实后端对接（list/detail/pull-models 经 `/api/data/providers` → BFF 适配器 → `nexmux-kernel`）。写链路基础设施已存在：`app/api/data/[...parts]/route.ts` 的 `handleMutation` 把 `POST/PUT /api/data/providers[...]` 映射到 `pactorMutations`（`src/pactor/backend/services-real.ts`），再进 `provider-adapter.ts`；mock 模式下写通道统一返回 503 `{error: "当前为演示模式（mock），写操作不可用"}`。

缺的是**页面 DSL 侧的请求编排**：create/edit 表单 onSubmit、列表/详情的启停确认、连接测试按钮目前全部只弹演示 message。表单字段名已对齐后端契约（`providerCode`/`displayName`/`defaultProtocol`/`timeoutMs`）。

后端写契约事实（OpenAPI 核实，`/openapi/specs/nexmux-kernel`）：

- `ProviderCreateRequest`：`providerCode`+`displayName` 必填；另有 `providerType`/`defaultProtocol`/`vendorCode`/`sourceVendorCode`/`iconUri`/`baseUrl`/`region`/`timeoutMs`。**无** `status`/`description`/`proxy`/`capabilityExtension`/`apiKey`
- `ProviderUpdateRequest`：仅 `displayName`/`baseUrl`/`region`/`timeoutMs`
- 启停：`POST /providers/{id}/enable|disable`（无 body）；连接测试：`POST /providers/{id}/connection-test`（无 body，返回 `{ok, latencyMs, detail}`）
- **无** `DELETE /providers/{id}`；**无** Provider 凭证（apiKey）写入接口

产品决策（用户指示）：**无后端接口的操作先对接到 BFF 代理层，默认返回成功**，页面交互闭环先行；后端接口就绪后只改代理层。

## Goals / Non-Goals

**Goals:**

- create/edit 表单 onSubmit 通过 DSL `request` 动作真实调用 BFF 写通道，成功刷新/跳转，失败透传后端错误文案
- 列表卡片与详情页的启停确认接通 enable/disable 接口并刷新数据
- 详情页「测试连接」接通 connection-test 并展示真实结果
- 详情页新增删除入口，经 BFF 代理桩默认成功
- 编辑表单整包提交：真实可更新字段转发后端，`status` 拆分调 enable/disable，其余字段（`description`/`proxy`/`capabilityExtension`/`apiKey`）代理层默认成功
- BFF 写通道支持 `DELETE`；代理桩响应标注 `stub: true`
- mock 模式下：纯桩操作正常返回成功；依赖真实后端的操作 503 降级提示，不崩溃

**Non-Goals:**

- 代理桩的真实持久化（桩就是默认成功，不写 mock 数据、不写库）
- 拉取模型的「确认拉取」批量建 Model（Model 域，后端有真实接口，另行提案）
- 其他域（model/route/client…）的写对接
- BFF 写通道与适配器的重构（仅小修补：vendorCode 回填、DELETE 支持、字段拆分、桩映射）

## Decisions

### D1: 写请求用 DSL 标准 `request` 动作编排，不新增自定义 action

onSubmit/onClick 事件链中直接声明 `action: request`（method/url/body），成功后续接 `reload`/`navigate`/`message`。备选「为每个写操作注册自定义 action」被否：BFF 写通道已是统一入口，DSL 标准动作足够表达。

### D2: 表单整包提交，字段拆分收敛在 BFF 代理层

表单 onSubmit 的 `request` body 直接整包 `${event}`（Form 提交的全部字段）。BFF 的 `PUT providers` 处理器按契约拆分：`displayName`/`baseUrl`/`region`/`timeoutMs` → 后端 PUT；`status` 与当前详情状态比对，变化时 → 后端 enable/disable；其余键（`description`/`proxy`/`capabilityExtension`/`apiKey`）落入代理桩默认成功。`POST providers` 同理：`ProviderCreateRequest` 字段转发后端，`apiKey` 等落桩。备选「DSL 侧显式枚举契约字段 + 不支持字段禁用」被否：把契约知识压进 DSL，且禁用字段破坏「编辑页即可见即可改」的交互预期；拆分逻辑放 BFF 后，后端补接口时只改 BFF 一处。

### D3: `vendorCode` 缺省由 BFF 适配层回填 `providerCode`

新建表单不增加 vendorCode 输入框（catalog 40+ 厂商选择属模板导入流程）；`createProvider` 适配器在 `vendorCode` 为空时回填 `providerCode`，`sourceVendorCode` 同理。

### D4: 代理桩集中为一张映射表，响应标注 `stub: true`

`pactorMutations` 旁新增桩映射（如 `DELETE providers`、以及字段级落桩在处理器内部完成）。桩处理器统一返回 `{ stub: true, ok: true }`；`stub` 标记供前端/调试识别「该操作未真正持久化」，也为后端接口就绪后的替换提供检索点。备选「散在各处理器里硬编码」被否：集中一张表才能一眼看清哪些操作是假成功。

### D5: 删除入口放详情页操作菜单，确认后走代理桩

详情页「···」下拉菜单（现为演示项）替换为「删除」，点击弹确认弹层（警示：影响范围 Model/Route 统计，复用 detail 已有数据），确认后 `request DELETE /api/data/providers/{code}` → 桩成功 → message（注明「演示删除，未真正移除」）→ 跳回列表。列表页卡片不加删除（误触风险，详情页二次确认足够）。

### D6: 选项值域改为后端枚举码，中文留 label

`providerType` 选项 `value` 改为 `COMMERCIAL`/`CLOUD`/`PRIVATE`/`CUSTOM`；`defaultProtocol` 选项 `value` 改为后端协议码（以 catalog 既有数据为准，如 `openai-completions`）。消除编辑页回填显示原始码、提交中文值的双向错配。

### D7: 错误与降级统一靠「失败分支 message 透传后端 error」；桩操作豁免 mock 闸门

BFF 已把 `BackendError` 映射为 `{error, code}` + HTTP 状态码。mock 模式下：纯桩操作（`DELETE providers` 等不触碰后端的）**不**走 503 闸门，直接返回成功（演示闭环）；依赖真实后端的操作（create/edit 转发部分、enable/disable、connection-test）保持 503 降级提示。DSL 侧无需区分，失败分支统一展示后端/通道返回的 `error` 文案。

> 实现注记（任务 1.1 验证结论）：框架默认 fetch 适配器在非 2xx 时抛通用错误（不含响应体），
> `PactorHttpHooks.response` 钩子在非 2xx 时不被调用，故采用备选方案——BFF 写通道错误
> 统一返回 **200 + `{code, message, data: null}` 信封**，前端经 `src/pactor/http-hooks.ts`
> 的响应钩子（要求 code/message/data 三键同时存在，避免误伤含 `code` 字段的业务载荷）
> 抛出真实文案，DSL 以 `${actions.<id>.error.message}` 读取。

## Risks / Trade-offs

- [代理桩默认成功 = 用户以为已删除/已保存密钥，实际未持久化] → 桩响应标注 `stub: true`；删除成功的 message 文案注明演示性质；桩映射表集中可检索，后端接口就绪后逐条替换
- [DSL `request` 动作的失败分支/响应读取能力未在当前页面用过] → 以连接测试（无表单依赖）为试点先行验证；若能力不足，按 D7 备选在 BFF 收敛错误或补薄自定义 action
- [编辑表单整包提交后，status 拆分调 enable/disable 与 PUT 的时序/部分失败（PUT 成功但 enable 失败）] → BFF 处理器内顺序执行：先 PUT 可更新字段，再按需启停；任一步失败即返回该错误，文案指明失败环节
- [后端对透传字段的校验严格度未验证（如 Select 枚举值 422）] → 联调阶段以真实后端逐一验证每个写操作
- [mock 模式下桩删除返回成功但列表数据不变（mock 数据不动）] → 删除成功 message 注明「演示」；读取链路行为不变

## Migration Plan

无数据迁移。变更仅涉及 nexmux-ui 的 DSL 与 BFF 层；后端接口就绪后，将桩映射表中对应条目替换为真实适配器调用即完成迁移，页面 DSL 不变。

## Open Questions

- `defaultProtocol` 的完整后端枚举清单（实现时从 catalog 种子数据 `distinct defaultProtocol` 提取固化）
- `description`/`proxy`/`capabilityExtension`/凭证写入的后端排期（决定桩条目的存续期）
- 删除后端接口若未来提供，是否有「存在引用时禁止删除」约束（影响确认弹层文案，届时随真实对接一并处理）
