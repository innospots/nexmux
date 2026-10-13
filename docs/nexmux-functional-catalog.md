# Nexmux Model Console 功能页面清单

> **文档定位**：`nexmux-ui` 当前实现的功能页面速查表（菜单 / 路径 / 用途 / 主要操作 / 主要字段·列 / 数据来源）。
> 使用说明与操作细节见 [功能说明书](./nexmux-functional-guide.md)。
>
> 数据来源为页面 `dataSources` 的服务端契约路径（经转发层 `app/api/d/[...parts]` 直连 Console）。
> 「状态」列：**可用** = 当前版本已开放；**计划中** = 产品已定义、当前未开放。

## 1. 菜单与页面总表

| 一级菜单 | 页面 | 路径 | 用途 | 主要操作 | 数据来源 |
|----------|------|------|------|----------|----------|
| 首页 | 仪表盘 | `/` | 运营概览：核心指标、趋势、用量构成 | 时间范围、刷新、下钻用量 | `GET /api/d/stats-dashboard` |
| 模型资产 | 供应商 | `/providers` | 上游供应商端点与 API Key（卡片列表） | 搜索、状态 / 类型筛选、导入、新建、启停、编辑、填写密钥 | `GET /api/d/providers` |
| 模型资产 | 新增 Provider | `/providers/new` | 定义供应商并配置上游连接与 API Key | 测试连接、保存、取消 | —（提交） |
| 模型资产 | Provider 详情 | `/providers/:code` | 基本信息、连接凭证、关联模型、引用关系 | 编辑、启停、查看模型、拉取上游模型、创建 Model 草稿、删除、复制引用关系 | `GET /api/d/providers/{code}`；`GET /api/d/providers/{code}/pull-models` |
| 模型资产 | 编辑 Provider | `/providers/:code/edit` | 同上（编辑态） | 测试连接、保存、返回列表 | `GET /api/d/providers/{code}` |
| 模型资产 | 模型 | `/models` | 对客户端暴露的逻辑模型（卡片列表） | 搜索、类型 / 厂商筛选、导入、新建、启停、编辑 | `GET /api/d/models` |
| 模型资产 | 新增模型 | `/models/new` | 基本信息、默认参数、分档定价 | 保存、取消 | `GET /api/d/provider-options` |
| 模型资产 | 模型详情 | `/models/:code` | 基本信息、默认参数、定价、提供商绑定、路由引用 | 编辑、启停、模型调用测试、查看路由、导出、复制 Code | `GET /api/d/models/{code}` |
| 模型资产 | 编辑模型 | `/models/:code/edit` | 同上（编辑态） | 保存、删除模型、返回列表 | `GET /api/d/models/{code}`；`GET /api/d/provider-options` |
| 模型路由 | 路由 | `/routes` | 对外请求路径、协议与调度策略（卡片列表） | 状态 / 类型 / 标签筛选、新建、启停、编辑 | `GET /api/d/routes` |
| 模型路由 | 新增路由 | `/routes/new` | 基本信息、入口配置、模型与路由策略 | 查看协议说明、添加模型、模拟调用、保存草稿、保存并启用 | `GET /api/d/model-options` |
| 模型路由 | 路由详情 | `/routes/:code` | 单页纵向三区块（无 Tab） | 编辑、启停、模拟调用、复制路由标识、导出配置 JSON | `GET /api/d/routes/{code}` |
| 模型路由 | 编辑路由 | `/routes/:code/edit` | 同上（编辑态） | 添加模型、模拟调用、保存草稿 / 保存并启用、删除路由 | `GET /api/d/routes/{code}`；`GET /api/d/model-options` |
| 访问治理 | 客户端 | `/clients` | 调用实例与 AppKey（表格） | 标签 / 关键字筛选、新建客户端、从模板创建、启停、编辑、Rotate Secret、调试客户端 | `GET /api/d/clients` |
| 访问治理 | 新建客户端 | `/clients/new` | ① 基本信息 ② AppKey ③ 路由授权 ④ 配额 ⑤ 限流 | 添加路由 / 配额 / 限流、保存并启用、取消 | —（提交） |
| 访问治理 | 客户端详情 | `/clients/:code` | 单页分区（无 Tab） | 编辑、启停、Rotate Secret、复制 AppKey ID、导出配置、查看模板、返回列表 | `GET /api/d/clients/{code}` |
| 访问治理 | 编辑客户端 | `/clients/:code/edit` | 同上（编辑态） | 添加路由 / 配额 / 限流、保存并启用、删除客户端 | `GET /api/d/clients/{code}` |
| 访问治理 | 客户端模板 | `/client-templates` | 预置路由 / 配额 / 限流（卡片列表） | 状态 / 标签 / 搜索、新建模板、启停、编辑 | `GET /api/d/client-templates` |
| 访问治理 | 新建客户端模板 | `/client-templates/new` | ① 基本信息 ③ 路由授权 ④ 配额 ⑤ 限流（无 AppKey） | 添加路由 / 配额 / 限流、保存并启用 | —（提交） |
| 访问治理 | 客户端模板详情 | `/client-templates/:code` | 同上 + 已创建客户端 | 编辑、创建客户端、复制模板标识、导出配置 JSON、返回列表 | `GET /api/d/client-templates/{code}`；`GET /api/d/client-templates/{code}/created-clients` |
| 访问治理 | 编辑客户端模板 | `/client-templates/:code/edit` | 同上（编辑态） | 添加路由 / 配额 / 限流、保存并启用、删除模板 | `GET /api/d/client-templates/{code}` |
| 运行管理 | 调用日志 | `/call-logs` | Gateway 请求历史 | 时间 / 状态 / 模型 / 供应商 / 协议 / 路由组 / 用户 / AppKey 筛选、清除筛选 | `GET /api/d/call-logs`；`GET /api/d/request-log-model-options` |
| 运行管理 | 调用日志详情 | `/call-logs/:id` | 11 个 Tab 的完整运行记录 | 在客户端调试复现、跳转客户端用量、跳转成本明细、返回列表 | `GET /api/d/call-logs/{id}` |
| 运行管理 | 模型用量 | `/model-usage` | 按模型与路由组的用量与成本 | 时间范围、Token 显示、导出 CSV | `GET /api/d/stats-model-usage`；`GET /api/d/service-settings` |
| 运行管理 | 供应商用量 | `/provider-usage` | 按上游供应商统计用量与成本 | 时间范围、Token 显示、导出 CSV | `GET /api/d/stats-provider-usage`；`GET /api/d/service-settings` |
| 运行管理 | 客户端用量 | `/client-usage` | 按客户端与 AppKey 的用量与成本 | 时间范围、Token 显示、导出 CSV | `GET /api/d/stats-client-usage`；`GET /api/d/service-settings` |
| 运行管理 | 可靠性 | `/provider-availability` | 供应商与路由质量 | 时间范围；查看全部错误日志 | `GET /api/d/stats-provider-availability` |
| 运行管理 | 成本明细 | `/costs` | 按单次请求的成本与定价快照 | 时间 / Application / AppKey / Alias / Model / Provider 筛选 | `GET /api/d/costs`；`GET /api/d/request-log-model-options` |
| 运行管理 | 成本明细详情 | `/costs/:id` | 证据链、定价快照、用量与费用拆分 | 查看调用日志、查看 Model 定价 / 版本历史、返回列表 | `GET /api/d/costs/{id}` |
| 调试台 | 客户端调试 | `/client-debug` | 模拟业务侧真实调用，验证鉴权 / 路由 / 配额 | 选择客户端 / AppKey / 授权路由 / Channel / RouteTag / User、发送消息、查看请求 / 返回报文、查看调用日志 | `POST /api/d/debug/client-invoke` 等 |
| 调试台 | 模型调试 | `/model-debug` | 直连供应商模型验证连通性与效果 | 选择供应商 / 模型、连接测试、System Prompt、模型参数、Streaming、Tools、发送消息 | `GET /api/d/provider-options`；`POST /api/d/debug/model-invoke` |
| 系统管理 | 服务设置 | `/service-settings` | 平台全局参数 | 保存、取消 | `GET /api/d/service-settings` |
| 系统管理 | 审计 | `/audit` | 管理操作留痕 | 时间 / 操作人 / 操作类型 / 资源类型 / Resource ID 筛选、清除筛选 | `GET /api/d/audit-logs`；`GET /api/d/audit-operators` |
| 系统管理 | 审计详情 | `/audit/:id` | 变更前 / 变更后 / Diff / 操作上下文 | 返回审计列表 | `GET /api/d/audit-logs/{id}` |
| （公开） | 登录 | `/login` | 管理后台登录 | 用户名 / 密码登录 | `POST /api/auth/login`（宿主） |

> 详情 / 新增 / 编辑页不出现在侧栏菜单，经列表页跳转进入；隐藏页通过 `activeMenu` 保持所属菜单高亮。

## 2. 关键字段与列速查

### 2.1 列表页

| 页面 | 主要列 / 卡片字段 | 主要筛选 |
|------|-------------------|----------|
| 供应商 | 名称、路由摘要、密钥状态、能力标签、状态 | 名称 / ID / 端点 / 能力关键字；状态；类型 |
| 模型 | 名称 / Code、类型、上下文·输出、定价摘要、路由摘要、状态 | 编码 / 名称；类型；厂商 |
| 路由 | 名称 / Code、Alias、类型、Ingress 摘要、Tags、目标摘要、状态 | 状态；类型；标签 |
| 客户端 | 名称、标识、标签、授权路由、配额、限流、状态 | 标签；名称；标识；状态 |
| 客户端模板 | 名称 / Code、Tags、有效期、授权路由、配额、限流、已创建客户端数、状态 | 状态；标签；关键字 |
| 调用日志 | 状态 · 时间、用户 · 系统、路由、标签、用量、成本 ($)、毛利 ($) | 时间、状态、模型、供应商、协议、路由组、用户 Email、AppKey ID |
| 模型用量 | 模型、路由组、请求数、输入 / 输出 Token、缓存命中率、标准、计费、成本、平均计费/请求、成功率、平均延迟、TTFT、平均上游、Token/秒、Failover 率、平均尝试 | 时间范围；Token 显示 |
| 供应商用量 | 供应商名称 + 同上指标 | 时间范围；Token 显示 |
| 客户端用量 | 客户端、AppKey ID + 同上指标 + 配额用量 | 时间范围；Token 显示 |
| 可靠性 | 供应商质量（供应商 + 指标）；按模型对比供应商（模型 + 指标）；近期错误（时间、模型 / 供应商、错误） | 时间范围 |
| 成本明细 | Request ID、Time、Model、In Cost、Out Cost、Cache、Total、档位、倍率、Currency | 时间、Application、AppKey、Alias、Model、Provider |
| 审计 | 时间、操作人、操作、资源类型、Resource ID、IP | 时间、操作人、操作类型、资源类型、Resource ID |

### 2.2 详情页 Tab / 分区

| 页面 | 结构 |
|------|------|
| Provider 详情 | Tab：基本信息、连接与凭证、关联模型、引用关系 |
| Model 详情 | Tab：基本信息、默认参数、定价、提供商、路由引用 |
| Route 详情 | 单页三区块：① 基本信息 ② 入口配置 ③ 模型与路由策略 |
| Client 详情 | 单页五区块：① 基本信息 ② AppKey ③ 路由授权 ④ 配额策略 ⑤ 限流策略 |
| Client Template 详情 | 单页四区块：① 基本信息 ③ 路由授权 ④ 配额策略 ⑤ 限流策略 + 已创建客户端 |
| 调用日志详情 | Tab：基本信息、调用主体、Ingress、路由结果、Upstream、性能、用量、成本、错误、路由追踪、Prompt/Response |
| 成本明细详情 | 分区：证据链、定价快照（只读）、用量与费用拆分 |
| 审计详情 | Tab：变更前、变更后、Diff、操作上下文 |

### 2.3 表单字段

| 表单 | 字段 |
|------|------|
| Provider | Provider Code、Provider Name、Provider Type、Default Protocol、Status、Description、Base URL、Region、API Key、Timeout (ms)、Proxy、Capability Extension / JSON 模式 |
| Model | Model Code、Model Name、Provider / Provider Model Name、Model Type、Status、上下文窗口、Max Tokens、INPUT/OUTPUT 模态、Tags、Description、元数据 (JSON)；默认参数：temperature、top_p、top_k、max_tokens、frequency_penalty、presence_penalty、reasoning_effort、seed、stop_sequences、response_format；定价：UPTO / INPUT / OUTPUT / CACHE READ / CACHE WRITE、时段倍率、Currency |
| Route | 路由名称、路由标识、类型、逻辑模型 Alias、标签、描述、请求路径、协议格式、Ingress Operation、模型路由策略、粘性 Session、健康过滤；模型行：Model / 权重 / 优先级 / C / M / 状态 |
| Client / Template | 名称、标识、有效期、标签、描述；路由授权；配额规则（规则名称 / 配额类型 / 计量指标 / 上限 / 循环周期 / 模式 / 状态）；限流规则（限流类型 / 上限 / Burst / 作用范围） |
| 服务设置 | 控制台时区、默认语言、平台显示名称、计费币种、金额小数位、Token 默认显示、日期格式、时间格式、Prompt/Response 保存策略、调用日志保留天数、默认请求超时、上游连接超时、允许调试调用、调试日配额 |

## 3. 枚举取值速查

| 维度 | 取值 |
|------|------|
| Provider Type | 商业 / 云服务 / 私有 / 自定义 |
| 上游协议（Provider Default Protocol） | OpenAI / Chat Completions、OpenAI / Responses、Anthropic / Messages、Google / Generative AI (Gemini)、Google / Vertex AI、Azure OpenAI / Responses、AWS Bedrock / Converse Stream、Mistral / Conversations、OpenAI Codex / Responses、Pi / Messages |
| Ingress 协议（Route 协议格式） | OpenAI / Chat Completions、OpenAI / Responses、Anthropic / Messages、OpenAI / Embeddings |
| Model Type | 大语言模型 (LLM)、文生图 (Image)、音频 (Audio)、Embedding |
| 路由类型 | 单模型、多模型池、条件路由（计划中）、灰度发布（计划中）、智能路由（计划中） |
| 路由策略 | 固定 (FIXED)、轮询 (ROUND_ROBIN)、加权轮询 (WEIGHTED)、哈希分配 (HASH)、优先级 (PRIORITY)、随机 (RANDOM) |
| 候选优先级 | P0 / P1 / P2 |
| 配额类型 | 总配额、周期循环配额（每 24 小时 / 每 7 天 / 每月自然月） |
| 配额计量指标 | Request、Input Token、Output Token、Total Token、Cost、Concurrent |
| 配额模式 | Soft Limit、Hard Limit |
| 限流类型 | RPM（每分钟请求数）、TPM（每分钟 Token 数）、Concurrent |
| 供应商密钥状态 | 已配置 / 待填密钥 / 无密钥 |
| 状态 | 启用 / 停用 / 草稿（Route）/ 已取消 等 |
| 服务设置枚举 | 语言 zh-CN / en-US；币种 USD / CNY / EUR；Token 显示 紧凑 (K/M/B) / 完整数字；时间格式 12h / 24h；Prompt/Response 策略 不保存 / 脱敏保存 / 抽样 / 完整 |

## 4. 皮肤与外观

| 项 | 取值 |
|----|------|
| 主题皮肤 | 晨雾奶芙（默认）、苔径深林、宣纸墨韵 |
| 明暗模式 | 浅色 / 深色 / 跟随系统 |
| 语言 | 中文 / English |

---

*清单随实现滚动更新；如与运行页面不一致，以运行页面为准。*
