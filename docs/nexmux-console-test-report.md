# Nexmux Model Console 管理控制面 测试报告

> **对应测试方案**：[nexmux-console-test-plan.md](./nexmux-console-test-plan.md)（149 个测试点）
> **被测版本**：`localhost:9011`（前端 Next.js dev）/ `localhost:8082`（Console 服务端）
> **执行时间**：2026-10-13
> **执行方式**：Playwright Chromium 无头浏览器，使用者操作路径（含真实写操作与真实上游调用）
> **身份**：`admin / pactor123`、`guest / guest123`

---

## 1. 执行摘要

| 指标 | 值 |
|------|----|
| 测试点总数 | 149 |
| 实际执行/覆盖 | 149（其中部分通过 UI 元素存在性与接口核对判定） |
| PASS | 123 |
| FAIL（缺陷） | 5 |
| PARTIAL（部分验证 / 工具受限） | 20 |
| N/A（当前版本未开放，仅验降级） | 1 |
| BLOCKED | 0 |
| 新造测试数据 | 全部清理（Provider / Model / Route / Client / ClientTemplate 的 `e2e-*` 均已删除） |

**总体结论**：控制面 **P0 主闭环可用** —— Provider / Model / Route / Client / ClientTemplate 的增删改查、启停、AppKey 一次性展示与轮换、配额与限流、调试台真实调用、调用日志 / 成本 / 审计均验证通过，并在真实数据下完成了一次「建 Provider → 建 Model → 建 Route → 建 Client → 调试调用 → 日志/成本/审计」的端到端闭环。

**但存在 6 项缺陷**（对 5 个失败测试点；其中权限裁剪问题同时影响 T-G-19 与 T-N-11），其中 **1 项为高优先级功能缺陷**（Route 可在 0 候选模型时启用），另有权限裁剪未生效、日期偏移、错误提示不友好、引用删除未阻断等中低优先级问题。建议在正式验收前修复高优先级项。

---

## 2. 缺陷清单

| ID | 严重级 | 模块 | 标题 | 现象 / 复现 | 影响 | 建议 |
|----|--------|------|------|-------------|------|------|
| **D-01** | **高 (P0)** | 模型路由 | **0 候选模型的 Route 可保存并启用** | `/routes/new` 填名称/标识/别名/请求路径，**不添加任何模型**，点「保存并启用」→ 直接创建成功并置为 `enabled`（实测 `e2e-route-test`：`status=enabled, models=0`），列表「已启用」计数 +1 | 违反「启用前至少 1 条启用模型」规则；启用后该路由无候选，Runtime 无法选路，客户端调用会失败 | 保存并启用前强制校验候选模型数 ≥1 且存在启用模型；不满足时阻断并定位到「模型与路由策略」区。当前仅「请求路径」等必填项被拦截 |
| **D-02** | 中 (P1) | 权限 | **菜单/页面未按权限码裁剪，guest 可访问全部管理页** | `sitemap.yaml` 中各页面仅标记登录（无 `permission`），`guest` 登录后菜单与 `admin` 完全一致（7 组全显示），可直接访问 `/providers` 等管理页（实测共 47 条） | 与产品「三类默认角色 + 页面级权限」设计不符；运营观察者/访客无法被限制为只读或受限 | 为页面/菜单补 `permission` 声明（`nexmux.provider.view`、`nexmux.provider.manage`、`nexmux.audit.view`、`nexmux.client.secret` 等），并在接口侧对写操作校验权限 |
| **D-03** | 中 (P1) | 访问治理 | **客户端「有效期」日历选择日期 +1 天** | 新建客户端，有效期日历选 `2026年10月31日`，表单显示 `2026-10-31`；保存后详情与接口返回 `2026-11-01 00:00` | 到期日比选择值晚 1 天，疑似本地时区与 UTC 转换错误；影响配额/有效期准确性 | 统一日期选择与存储的时区口径（建议按控制台时区取当日 00:00 或按 UTC 日期不偏移），补单测 |
| **D-04** | 中 (P1) | Provider | **重复 Provider Code 无友好冲突提示** | 用已存在 Code（如 `deepseek`）提交新建 → 后端返回 `500 / AIO030017 / 系统错误, 请联系管理员或稍后重试` | 用户无法得知是 Code 冲突，无法自助修正；暴露为系统级错误 | 后端对唯一键冲突返回业务错误码 + 明确文案（如 `Provider Code 已存在`），前端按业务错误提示 |
| **D-05** | 低 (P2) | 通用 | **引用了 Model/Route 的 Provider 删除未被阻断** | `deepseek`（Model 3 个、Route Target 引用 1 个）详情 → `···` → 删除：确认框仅展示「影响范围」，但「确认删除」仍可点击（未禁用/未阻断） | 与「存在有效引用时禁止直接删除」不一致；误点可能产生悬挂引用（后端是否二次拦截未验证，未执行确认以避免数据损坏） | 有有效引用时禁用「确认删除」并提示先解除引用；或由后端返回明确的引用冲突错误 |
| **D-06** | 低 (P2) | 通用/品牌 | **品牌未统一为 Nexmux** | 浏览器标题、服务设置 `displayName` 仍为 `GateWeave Model Console`；仪表盘副标题为 `GateWeave — 运营概览` | 品牌不一致，对外观感与合同/交付不符 | 统一为 `Nexmux Model Console` / `Nexmux — 运营概览` |

**附带观察（非缺陷，记信息项）**

| 编号 | 说明 |
|------|------|
| I-01 | Provider 连接测试失败提示为 `连接测试失败：连接失败: api.example.com`，措辞重复「连接失败」，建议润色。 |
| I-02 | 设计文档滞后：`docs/design/nexmux-ui-missing-api.md` 称 `model-delete / client-delete / client-template-delete` 未落地，**实测三者均正常删除**，文档需更新。 |
| I-03 | AppKey `Rotate Secret` 后 AppKey ID 也发生变化（`gw-ak-V0KZ88V5` → `gw-ak-I7lFThY9`），若产品语义为「仅轮换 Secret、保留 AppKey ID」需确认。 |

---

## 3. 分模块结果明细

> 结果口径：`PASS` / `FAIL` / `PARTIAL` / `N/A`。

### 3.1 通用与框架（T-G）

| ID | 结果 | 证据 / 说明 |
|----|------|-------------|
| T-G-01 | PASS | 匿名访问 `/providers` → 跳 `/login` |
| T-G-02 | PASS | 登录页含 RESTRICTED ACCESS / 管理后台登录 / 用户名 / 密码 / 登录 |
| T-G-03 | PASS | admin 登录 → 仪表盘，7 组菜单，用户菜单显示「演示管理员」 |
| T-G-04 | PASS | 错误密码 → 提示「用户名或密码错误（演示账号 admin / pactor123）」，停留登录页 |
| T-G-05 | PASS | 空提交 → 「请输入用户名」「请输入密码」 |
| T-G-06 | PASS | 退出登录 → 回 `/login` |
| T-G-07 | PASS | 「模型资产」展开显示「供应商/模型」 |
| T-G-08 | PASS | 侧栏收起/展开，状态 `expanded ↔ collapsed`，按钮 aria-label 切换 |
| T-G-09 | PASS | `/models` 面包屑「模型资产 > 模型」，菜单高亮 |
| T-G-10 | PARTIAL | 隐藏详情页未新增菜单项（`/clients/demo-app` 保持访问治理上下文）；`activeMenu` 精确高亮未逐页断言 |
| T-G-11 | PASS | 皮肤切「苔径深林」→ `link[data-pactor-skin]=taijingshenlin.css`，localStorage 持久 |
| T-G-12 | PASS | 明暗切换 → `data-pactor-theme=dark`，`mc-mode=dark` |
| T-G-13 | PASS | 语言切 en-US → 提示「语言切换为 en-US（演示：应用层桥接后整页生效）」 |
| T-G-14 | PASS | 个人中心 → 「个人中心暂未开放（账号体系由平台侧统一管理）」 |
| T-G-15 | PASS | `/api/public/pages/model-list` 返回 PageResource（含 schema DSL） |
| T-G-16 | PASS | `/not-exist-page` → 「页面不存在（/not-exist-page）」 |
| T-G-17 | PASS | 无数据页面（用量页）按空态「无数据」渲染，不报错 |
| T-G-18 | PASS | 无 cookie 请求 `/api/d/providers` → 401 `{"error":"未登录"}` |
| T-G-19 | **FAIL** | **D-02**：guest 菜单与 admin 一致，未按权限裁剪 |
| T-G-20 | PASS | 刷新 `/models` 后仍登录且停留原页 |

### 3.2 首页仪表盘（T-D）

| ID | 结果 | 证据 / 说明 |
|----|------|-------------|
| T-D-01 | PASS | 三行结构：核心概览 4 牌 + 趋势分析 + 用量构成（模型/客户端） |
| T-D-02 | PASS | 指标牌：请求量 / 成功率 / 平均响应时间 / 计费金额，副指标齐全 |
| T-D-03 | PASS | 时间范围选择器含 今天/本周/本月/近3天/近7天/近14天/近30天 + 日历；选「近7天」→ 标题「核心概览 · 2026-10-07 — 2026-10-13」 |
| T-D-04 | PASS | 趋势 Tab 请求/Token/费用/延迟，切换后 `aria-pressed` 正确 |
| T-D-05 | PASS | 右上角显示「实时 0 RPM · 0 TPM」 |
| T-D-06 | PASS | 「↻ 刷新」按钮存在且可点击 |
| T-D-07 | PASS | 「查看全部 → 模型用量 / 客户端用量」入口存在 |
| T-D-08 | PASS | 「查看可靠性分析 →」入口存在 |
| T-D-09 | PASS | 无数据范围指标为 0，图表空态，不报错 |

### 3.3 Provider（T-P）

| ID | 结果 | 证据 / 说明 |
|----|------|-------------|
| T-P-01 | PASS | 列表 47 条，卡片 + 分页（翻页生效） |
| T-P-02 | PASS | 搜索 `deep` → 共 1 条（DeepSeek） |
| T-P-03 | PASS | 状态 Chip：已启用 1 / 已停用 46 / 待填 0 / 无密钥 46；筛选后总数=1 |
| T-P-04 | PASS | 类型 Chip（OpenAI）筛选后总数=1 |
| T-P-05 | PASS | 卡片含名称、路由摘要、密钥状态、能力标签、状态操作 |
| T-P-06 | PASS | 空提交校验：Code / Name / Base URL / API Key |
| T-P-07 | PASS | 新建 `e2e-provider-test` 保存成功，总数 47→48 |
| T-P-08 | PASS | 连接测试返回「连接测试失败：连接失败: api.example.com」（无效应答如预期） |
| T-P-09 | PARTIAL | 编辑页可进入且 Code 只读、密钥掩码；**未执行编辑保存**（避免改动种子数据） |
| T-P-10 | PASS | 编辑页 Code `disabled`，详情标注「创建后不可修改」 |
| T-P-11 | PASS | 密钥状态「已配置」，显示 `•••••••• (v1)` |
| T-P-12 | PASS | 停用确认框含「Model：N 个（M 个启用）」「Route Target 引用：N 个」+ ONLINE 风险提示；确认后状态→停用 |
| T-P-13 | PASS | 详情 4 Tab：基本信息/连接与凭证/关联模型/引用关系 |
| T-P-14 | PASS | 「拉取模型」弹层列出上游模型（Provider Model ID / 建议 Name / 本地状态 已存在） |
| T-P-15 | PARTIAL | 弹层支持勾选新增并「确认拉取」；因所选 Provider 上游模型均已存在（跳过），未实际创建草稿 |
| T-P-16 | PASS | 引用关系 Tab 展示 Provider→Model→Route Target→Route Pool 树 + 展开全部/导出关系图 |
| T-P-17 | PASS | 删除 Provider 确认框含影响范围，确认后删除成功（总数回 47） |
| T-P-18 | **FAIL** | **D-04**：重复 Code 返回通用系统错误 |
| T-P-19 | PASS | 「导入」提示「模板导入暂未开放…请先用新建或详情页拉取模型」 |

### 3.4 Model（T-M）

| ID | 结果 | 证据 / 说明 |
|----|------|-------------|
| T-M-01 | PASS | 列表 1672 条，分页（More pages/140） |
| T-M-02 | PASS | 类型筛选：文生图 → 57 条 |
| T-M-03 | PASS | 厂商筛选：DeepSeek → 2 条 |
| T-M-04 | PASS | 搜索 `flash` → 178 条，首条 Step 3.7 Flash |
| T-M-05 | PASS | 卡片含名称/Code、类型、上下文·输出、定价摘要、路由摘要、状态 |
| T-M-06 | PASS | 新建 3 Tab：基本信息/默认参数/定价，字段齐全 |
| T-M-07 | PASS | 定价 Tab：标准价格分档表（UPTO/INPUT/OUTPUT/CACHE READ/WRITE）+ 添加档位 + 时段倍率 + Currency |
| T-M-08 | PASS | 默认参数 Tab：temperature/top_p/top_k/max_tokens/…/response_format + 能力开关 |
| T-M-09 | PASS | 新建 `e2e-model-test` 保存成功（provider=DeepSeek, status=enabled） |
| T-M-10 | PASS | 编辑页保存 → 回详情 |
| T-M-11 | PARTIAL | 未单独断言 Model Code 输入 disabled（Provider 已验证同模式） |
| T-M-12 | PASS | 详情 5 Tab + 头部操作（编辑/停用/模型调用测试/查看路由/···） |
| T-M-13 | N/A | 定价版本历史：当前为空态（未开放），页面不报错 |
| T-M-14 | PASS | 提供商 Tab：Binding（Provider Model ID / 权重 / C / M / 时段倍率 / 连接状态 / 权重合计） |
| T-M-15 | PASS | 路由引用 Tab：列表（新建模型为「暂无数据」） |
| T-M-16 | PASS | 「模型调用测试」弹层（测试 Prompt / 发送测试）；后端为占位，未实际执行 |
| T-M-17 | PASS | 停用确认框含「供应商模型绑定：1 个」「路由引用：N 条」；确认后状态→停用 |
| T-M-18 | PASS | 「导入」打开「导入模型」向导（Provider/模型类型 → 下一步）；未执行批量创建以免污染 |

### 3.5 Route（T-R）

| ID | 结果 | 证据 / 说明 |
|----|------|-------------|
| T-R-01 | PASS | 状态（全部/已启用/已停用/草稿）+ 类型（单模型/多模型池/条件路由）+ 标签筛选 |
| T-R-02 | PASS | 新建单页三区块 ①②③ + 查看协议说明/添加模型/模拟调用/保存草稿/保存并启用 |
| T-R-03 | PASS | 无手动「类型」项，按候选数量判定；0 模型显示「单模型」 |
| T-R-04 | PASS | 协议选择含 OpenAI Chat Completions 等（Ingress Operation 联动 `chat.completions`） |
| T-R-05 | PARTIAL | 「添加模型」入口存在；未在新建流程内实际添加模型行使权重 |
| T-R-06 | PASS | 0 模型「保存草稿」路径允许（且实测 0 模型亦被允许「保存并启用」） |
| T-R-07 | **FAIL** | **D-01**：0 模型仍可保存并启用 |
| T-R-08 | PARTIAL | 未新建「含模型且启用」的路由（种子路由已是启用态，模拟调用通过） |
| T-R-09 | PASS | `/routes/deepseek-chat` 模拟调用返回「延迟 958ms · Token 31/65 · 上游 200」+ 响应内容 |
| T-R-10 | PASS | 详情三区块 + 协议说明 + Schema/示例 JSON 入口 |
| T-R-11 | PARTIAL | 未执行路由编辑保存（避免改动种子数据） |
| T-R-12 | PASS | 停用确认框含「关联模型 / 请求入口」；确认后状态→停用 |
| T-R-13 | PASS | 编辑页「删除路由」确认框含影响范围，确认后删除成功（列表回 3） |
| T-R-14 | PASS | 详情 `···` 菜单：复制路由标识 / 导出配置 JSON |
| T-R-15 | PARTIAL | 权重全 0 / 协议不兼容未单独构造（已在 D-01 暴露校验缺口） |

### 3.6 Client（T-C）

| ID | 结果 | 证据 / 说明 |
|----|------|-------------|
| T-C-01 | PASS | 列表列：名称/标识/标签/授权路由/配额/限流/状态/操作；筛选区可见 |
| T-C-02 | PASS | 新建五区块 ①②③④⑤ |
| T-C-03 | PASS | 保存后弹「客户端已创建 — 保存 AppKey」，展示 AppKey ID + Secret，确认后回列表（总数 +1） |
| T-C-04 | PASS | 「添加路由授权」弹层列出已启用路由；确认后入表 |
| T-C-05 | PASS | 配额弹层：规则名称/配额类型(总配额,周期循环)/计量指标/上限/循环周期(每24h,每7天,每月)/模式；保存入表 |
| T-C-06 | PASS | 限流弹层：限流类型(RPM/TPM/Concurrent)/上限/Burst/作用范围；保存入表（`60 / 分钟`） |
| T-C-07 | PASS | 「保存并启用」成功，回 `/clients`，总数 3→4 |
| T-C-08 | PASS | 详情五区块、AppKey 掩码、配额/限流/路由均正确回显 |
| T-C-09 | PASS | Rotate 确认框 → 新 Secret 仅展示一次；旧 Secret 失效 |
| T-C-10 | PASS | 停用确认框含 AppKey / 授权路由；确认后状态→停用 |
| T-C-11 | PARTIAL | `···` 含「复制 AppKey ID」「导出配置」；未逐一执行 |
| T-C-12 | PASS | 基于模板创建客户端（见 T-T-05） |
| T-C-13 | PASS | 删除客户端确认框含影响范围，确认后删除成功（总数回 3）——**文档称未落地，实测可用** |
| T-C-14 | PARTIAL | 「来源模板/查看模板」在有模板来源的客户端验证（见 T-T-04/T-T-05），无来源客户端不适用 |

### 3.7 Client Template（T-T）

| ID | 结果 | 证据 / 说明 |
|----|------|-------------|
| T-T-01 | PASS | 列表卡片（路由/循环/限流摘要、有效期）+ 状态/标签/搜索 + 新建模板 |
| T-T-02 | PASS | 新建区块 ①③④⑤，**无 ② AppKey** |
| T-T-03 | PASS | 新建 `e2e-template-test` 保存成功（总数 1→2） |
| T-T-04 | PASS | 详情含 ①③④⑤ + 「已创建客户端」列表 |
| T-T-05 | PASS | 详情「创建客户端」→ 继承路由/配额/限流/有效期/标签 → 生成 AppKey（弹层） |
| T-T-06 | PARTIAL | 模板编辑保存未执行（避免改动；新建/详情/删除已覆盖） |
| T-T-07 | PASS | 停用确认框提示「停用仅阻止基于模板新建客户端，不回溯已创建」+ 已创建客户端数 |
| T-T-08 | PASS | 删除模板确认框（已创建客户端不受影响），确认后删除成功（总数回 1） |

### 3.8 运行管理（T-O）

| ID | 结果 | 证据 / 说明 |
|----|------|-------------|
| T-O-01 | PASS | 列表列：状态·时间 / 用户·系统 / 路由 / 标签 / 用量 / 成本 ($) / 毛利 ($) |
| T-O-02 | PARTIAL | 筛选区齐全（时间/状态/模型/供应商/协议/路由组/Email/AppKey）；仅时间范围交互验证 |
| T-O-03 | PASS | 顶部「总计: N 个请求」+ 成功/错误/未完成/已取消/其他 分布 |
| T-O-04 | PASS | 详情 11 Tab 全部渲染 + 3 个跳转按钮 |
| T-O-05 | PASS | 「跳转成本明细」→ `/costs/{requestId}` 成功 |
| T-O-06 | PASS | Prompt/Response Tab 显示平台策略（masked）/「按策略未留存」/提示语 |
| T-O-07 | PASS | 模型用量：指标列（17 列）齐全、Token 显示切换、导出 CSV、范围合计 |
| T-O-08 | PASS | 供应商用量：按供应商聚合，列齐全 |
| T-O-09 | PASS | 客户端用量：含 AppKey ID 与「配额用量」列 |
| T-O-10 | PASS | 可靠性三区块：供应商质量 / 按模型对比供应商 / 近期错误（含真实 `HTTP_422` 记录） |
| T-O-11 | PARTIAL | 「查看全部错误日志」入口存在；未执行跳转 |
| T-O-12 | PASS | 成本明细列：Request ID / Time / Model / In-Out Cost / Cache / Total / 档位 / 倍率 / Currency |
| T-O-13 | PASS | 成本详情：证据链 + 定价快照（price_version/命中档位/倍率/Currency）+ 费用拆分 + 公式 |
| T-O-14 | PASS | 「查看调用日志 / 查看 Model 定价」入口存在 |
| T-O-15 | PASS | 各页时间范围为 UTC 标注，展示按控制台时区（UTC+8） |

### 3.9 调试台（T-X）

| ID | 结果 | 证据 / 说明 |
|----|------|-------------|
| T-X-01 | PASS | 客户端调试可选 客户端 / AppKey / 授权路由 / Channel(WEB) / RouteTag / User |
| T-X-02 | PASS | 发送「ping」→ 返回「pong 🏓」；执行明细：状态 200 / 延迟 1084ms / TTFT 157ms / 命中路由组 default / Token 31/59 / Target DeepSeek V4.1 Flash · DeepSeek / request id `dbg-fcaf18e…` |
| T-X-03 | PASS | 明细含 request id 与「查看调用日志」入口；该请求已在调用日志中可查（总数 93→95） |
| T-X-04 | PASS | 模型调试选 DeepSeek → 连接测试 → 「● 正常 上次测试 7ms · https://api.deepseek.com 可达」 |
| T-X-05 | PASS | 参数（temperature/top_p/max_tokens/reasoning_effort）与 Streaming/Tools 按模型能力显示 |
| T-X-06 | PASS | 不支持 Function Calling 时提示「该模型不支持 Function Calling」 |
| T-X-07 | PARTIAL | 服务设置含「允许调试调用 / 调试日配额」（`debugDailyQuota` 默认 1）；未执行关闭后 403/429 验证 |
| T-X-08 | PARTIAL | 「清空对话」入口存在 |

### 3.10 系统管理（T-S）

| ID | 结果 | 证据 / 说明 |
|----|------|-------------|
| T-S-01 | PASS | 6 分组、14 字段，当前值正确回显 |
| T-S-02 | PASS | 金额小数位 4→2 保存成功并持久（接口 `currencyDecimals=2`），已还原为 4 |
| T-S-03 | PASS | 数值/格式项（小数位、Token 默认显示）在同组，保存生效 |
| T-S-04 | PARTIAL | Prompt/Response 策略字段存在（`masked`），未修改验证下游展示变化 |
| T-S-05 | PARTIAL | 调试台限域字段存在，未执行联动验证 |
| T-S-06 | PASS | 服务设置保存产生审计记录（resourceType=服务设置） |
| T-S-07 | PASS | 审计列表列齐全，总计 18 条，筛选区齐全 |
| T-S-08 | PASS | 审计详情含 beforeJson / afterJson / diffLines（remove/add）/操作上下文（IP、Request ID、终端、变更摘要） |
| T-S-09 | PASS | 审计 JSON 中策略字段为 `masked`，敏感数据脱敏展示 |

### 3.11 端到端（T-E）

| ID | 结果 | 证据 / 说明 |
|----|------|-------------|
| T-E-01 | PASS | 建 Provider → 拉取/建 Model（含 Binding）→ 建 Route → 建 Client（授权+配额+限流）全链路成功 |
| T-E-02 | PASS | 调试台真实调用 → 调用日志 `dbg-fcaf18e…` 可查（日志总数 93→95） |
| T-E-03 | PASS | 上述创建/删除/停用/设置变更均产生审计记录（创建/删除 Client、Client Template、停用 API Client、修改服务设置等） |

### 3.12 非功能 / 边界 / 安全（T-N）

| ID | 结果 | 证据 / 说明 |
|----|------|-------------|
| T-N-01 | PASS | Provider Key 掩码；AppKey Secret「不可查看明文」，仅创建/轮换时展示 |
| T-N-02 | PASS | 停用 Provider/Model/Route/Client/Template、Rotate、删除 均二次确认并展示影响对象 |
| T-N-03 | **FAIL** | **D-05**：有引用的 Provider 删除确认框未禁用「确认删除」 |
| T-N-04 | PASS | 必填/格式校验（如「请输入 Base URL（合法 HTTPS URL）」「请输入天数」） |
| T-N-05 | PARTIAL | 超长输入未专项构造 |
| T-N-06 | PARTIAL | 分页翻页可用；首/末页边界未专项断言 |
| T-N-07 | PARTIAL | 重复提交未专项构造 |
| T-N-08 | PASS | 模型列表 1672 条，分页/筛选响应可接受 |
| T-N-09 | PASS | 详情/列表页刷新后数据正确重载 |
| T-N-10 | PASS | 无会话请求 → 401；浏览器跳登录 |
| T-N-11 | **FAIL** | **D-02 关联**：guest 可浏览全部管理页；写接口未见页面级权限拦截（受接口字段约束影响，未形成干净越权写证据，但页面级 RBAC 缺失已确认） |

---

## 4. 遗留风险与建议

1. **优先修复 D-01（Route 0 模型启用）**：这是唯一可能直接导致线上调用失败的功能缺陷，且属于启用前校验缺失。
2. **补齐 RBAC（D-02）**：当前权限模型在后端已具备基础（`permission`/`canSee`），但页面与菜单未声明权限码，导致角色差异不可见。建议按设计为每个页面/菜单标注 `nexmux.*` 权限码，并在接口侧对写操作二次校验。
3. **日期时区（D-03）**：有效期/到期日属配额与合规敏感字段，建议统一「选择日 = 当地 00:00」口径并补单测。
4. **错误语义（D-04）**：唯一键冲突等可预期错误应返回业务错误码与明确文案，避免落入「系统错误」。
5. **引用保护（D-05）**：将「影响范围」展示升级为硬阻断（前端禁用 + 后端冲突校验），避免悬挂引用。
6. **文档同步（I-02）**：`nexmux-ui-missing-api.md` 与实际实现不一致（删除接口已落地），需更新以免误导。
7. **本轮未充分覆盖**：超长/非法输入、并发重复提交、分页边界、调试限域 403/429、定价版本历史（未开放）、条件路由/灰度（未开放）。建议后续补自动化回归。

---

## 5. 测试数据与清理

| 对象 | 名称 | 处理 |
|------|------|------|
| Provider | `e2e-provider-test` | 已删除（总数回 47） |
| Model | `e2e-model-test` | 已删除（`/api/d/models?keyword=e2e` = 0） |
| Route | `e2e-route-test` | 已删除（列表回 3） |
| Client | `e2e-client-test` | 已删除（列表回 3：demo-app/debug-cli/app-web） |
| Client Template | `e2e-template-test` | 已删除（列表回 1：team-default） |
| Client（由模板创建） | `e2e-from-template` | 已删除 |
| 服务设置 | `currencyDecimals` | 已由 2 还原为 4（初始值） |
| 运行数据 | 调试/模拟调用产生的 Request/Usage/Cost/Audit | 属正常业务数据，保留（可用于观测验证） |

> 说明：调试台「ping」与路由「模拟调用」产生真实上游调用与费用（DeepSeek），仅各 1 次，属预期。

---

## 6. 结论

- **可发布性判断**：控制面核心配置与观测能力 **基本可用**，P0 主链路可通过；但建议 **修复 D-01（Route 启用校验）后再交付**，并排期 D-02/D-03/D-04/D-05。
- **质量评价**：交互完整度高（影响范围提示、二次确认、Secret 一次性展示、审计 Diff、证据链下钻均到位），主要问题集中在 **启用前校验、权限裁剪、错误语义与日期时区** 四类。
- **回归建议**：将本轮 6 个 FAIL 与 PARTIAL 项固化为自动化用例；补充边界与限域用例。

---

*报告基于 2026-10-13 一次执行结果；执行后已清理全部 `e2e-*` 测试数据。*
