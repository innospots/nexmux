# Spec: provider-mutations

供应商域写操作的服务端接口对接（含代理层默认成功桩）。

## ADDED Requirements

### Requirement: 新建 Provider 提交真实创建请求

新建 Provider 表单（provider-create）提交时 SHALL 通过 DSL `request` 动作调用 `POST /api/data/providers`。BFF 代理层 SHALL 将契约字段（`providerCode`/`displayName`/`providerType`/`defaultProtocol`/`vendorCode`/`sourceVendorCode`/`baseUrl`/`region`/`timeoutMs`）转发后端创建接口；契约外字段（如 `apiKey`）SHALL 落入代理桩默认成功。创建成功 SHALL 提示成功并跳回供应商列表，列表刷新后 MUST 可见新建记录。

#### Scenario: 提交合法表单创建成功

- **WHEN** 用户填写必填项（Provider Code、Provider Name、Base URL 等）并点击「保存」
- **THEN** 页面向 `POST /api/data/providers` 发起请求，成功后展示成功消息并导航至 `/providers`，列表中包含新 Provider

#### Scenario: vendorCode 缺省回填

- **WHEN** 表单未单独提供 `vendorCode`
- **THEN** BFF 适配层以 `providerCode` 值回填 `vendorCode`（`sourceVendorCode` 空时同 `vendorCode`）后再调用后端

#### Scenario: 业务冲突错误透传

- **WHEN** 后端返回错误（如 providerCode 重复，NMX070001/409）
- **THEN** 页面展示后端错误消息，不跳转、不提示成功

### Requirement: 编辑 Provider 经代理层字段拆分提交

编辑 Provider 表单（provider-edit）提交时 SHALL 整包调用 `PUT /api/data/providers/{code}`。BFF 代理层 SHALL 拆分处理：`displayName`/`baseUrl`/`region`/`timeoutMs` 转发后端 PUT；`status` 与当前状态比对有变化时调用后端 enable/disable 接口；`description`/`proxy`/`capabilityExtension`/`apiKey` 落入代理桩默认成功。所有字段 SHALL 保持可编辑（不仅 4 个可更新字段）。成功 SHALL 跳转回详情页并展示更新后的数据；任一真实后端环节失败 SHALL 返回该环节错误且不提示成功。

#### Scenario: 修改可更新字段保存成功

- **WHEN** 用户修改 Provider Name / Base URL / Region / Timeout 并点击「保存」
- **THEN** 页面发起 `PUT /api/data/providers/{code}`，成功后导航至 `/providers/{code}` 且详情页显示新值

#### Scenario: 表单内切换状态触发启停接口

- **WHEN** 用户在编辑表单将 Status 从「启用」改为「停用」并保存
- **THEN** BFF 除转发可更新字段外，调用 `POST /providers/{id}/disable`，成功后详情页展示停用状态

#### Scenario: 契约外字段走代理桩

- **WHEN** 用户修改 Description / Proxy / Capability Extension 并保存
- **THEN** 请求整体返回成功（这些字段由代理层默认成功，不落库），其余真实字段正常生效

#### Scenario: 真实环节失败整体报错

- **WHEN** 后端 PUT 或 enable/disable 任一环节返回错误
- **THEN** 页面展示指明失败环节的错误消息，不跳转、不提示成功

### Requirement: 启用/停用接通后端接口

列表卡片与详情页的启用/停用确认流程 SHALL 在用户确认后调用 `POST /api/data/providers/{code}/enable` 或 `/disable`，成功后 MUST 刷新当前数据（列表 reload / 详情 reload）使状态展示与后端一致；演示文案（如「静态页面不落库」）MUST 移除。

#### Scenario: 停用确认后状态生效

- **WHEN** 用户在列表卡片上点击已启用 Provider 的停用按钮并在确认弹层中点击「确认停用」
- **THEN** 页面调用 disable 接口，成功后列表刷新，该 Provider 展示为停用状态

#### Scenario: 取消确认不发起请求

- **WHEN** 用户在启停确认弹层点击「取消」
- **THEN** 不发起任何写请求，状态不变

### Requirement: 删除 Provider 经代理桩默认成功

详情页操作菜单 SHALL 提供「删除」入口，确认弹层 MUST 展示影响范围（Model 数 / Route 引用数）后方可确认。确认后 SHALL 调用 `DELETE /api/data/providers/{code}`；因后端无删除接口，BFF 代理层 SHALL 默认返回成功并标注 `stub: true`。页面收到成功后 SHALL 提示删除成功（注明演示性质）并跳回列表。

#### Scenario: 确认删除走代理桩

- **WHEN** 用户在详情页操作菜单选择「删除」，在确认弹层查看影响范围后点击确认
- **THEN** 页面调用 `DELETE /api/data/providers/{code}`，代理层返回 `{stub: true, ok: true}`，页面提示删除成功（注明演示）并导航至 `/providers`

#### Scenario: 取消删除不发起请求

- **WHEN** 用户在删除确认弹层点击「取消」
- **THEN** 不发起任何写请求，停留详情页

### Requirement: 连接测试接通后端接口

详情页「连接与凭证」标签下的「测试连接」按钮 SHALL 调用 `POST /api/data/providers/{code}/connection-test`，并根据返回的 `ok`/`latencyMs`/`detail` 展示真实结果（成功含耗时，失败含原因）。

#### Scenario: 连接测试成功

- **WHEN** 用户点击「测试连接」且后端返回 `ok=true`
- **THEN** 页面展示成功消息，包含真实 `latencyMs`

#### Scenario: 连接测试失败

- **WHEN** 后端返回 `ok=false`（如未配置 API Key）
- **THEN** 页面展示失败消息，包含后端 `detail` 原因

### Requirement: 写失败的错误透传

所有写操作失败时 SHALL 以 message 展示 BFF 返回的错误文案（`BackendError` 的 `error` 字段），MUST NOT 在失败时展示成功提示或执行成功分支的刷新/跳转。

#### Scenario: 后端校验失败

- **WHEN** 任一写请求返回 4xx/5xx（含 `{error, code}` 错误体）
- **THEN** 页面以错误消息展示后端 `error` 文案，页面停留当前上下文

### Requirement: mock 演示模式优雅降级

mock 模式下，纯代理桩操作（如删除）SHALL 直接返回成功（演示闭环）；依赖真实后端的写操作（create/edit 转发、启停、连接测试）SHALL 收到 503 并展示演示模式提示文案。两种情况下页面 MUST NOT 出现未捕获异常或白屏。

#### Scenario: 演示模式下提交依赖真实后端的表单

- **WHEN** 未配置真实后端时用户在新建/编辑页点击「保存」
- **THEN** 页面展示「当前为演示模式（mock），写操作不可用」语义的提示，不发生页面错误

#### Scenario: 演示模式下删除走桩成功

- **WHEN** 未配置真实后端时用户在详情页确认删除
- **THEN** 代理桩返回成功，页面提示删除成功（注明演示）并跳回列表

### Requirement: 表单选项值域对齐后端枚举

`providerType` 选项值 SHALL 使用后端枚举码（`COMMERCIAL`/`CLOUD`/`PRIVATE`/`CUSTOM`，中文保留在 label）；`defaultProtocol` 选项值 SHALL 使用后端协议码（以 catalog 既有数据为准）。编辑页回填的枚举值 MUST 能命中选项，不再显示原始码文本。

#### Scenario: 编辑页枚举正常回显

- **WHEN** 用户打开一个 `providerType=COMMERCIAL`、`defaultProtocol=openai-completions` 的 Provider 编辑页
- **THEN** 对应 Select 正常显示中文 label，提交时送出枚举码
