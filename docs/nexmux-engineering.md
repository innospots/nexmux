# Nexmux 工程结构指南

> 面向新加入的开发者。本文说明模块职责、包边界、核心数据流、持久化与构建方式；产品运行见 [运行与部署](./nexmux-deployment.md)，业务接入见 [模型接入指南](./nexmux-integration-guide.md)。

## 1. 工程原则

1. **平台依赖挂接，不复制平台源码**：平台能力通过 `innospots-nexus-*` 依赖提供。
2. **四个产品模块，不擅自增删**：`nexmux-bom`、`nexmux-kernel`、`nexmux-spring`、`nexmux-ui`。
3. **业务服务纯 Java**：业务规则在 `nexmux-kernel`，Spring 仅负责装配。
4. **Console 与 Runtime 分进程**：同一 artifact，独立 profile 与 JVM；不互相进行在线调用。
5. **配置与运行分责**：Console 写配置，Runtime 读取已发布配置并写运行数据。
6. **不返回伪成功**：未实现的能力明确失败，不以空实现、空数据或成功占位代替。

## 2. Maven 与依赖关系

```text
innospots-nexus-parent
        │
        ▼
     nexmux
        │ dependencyManagement
        ▼
   nexmux-bom ───────► innospots-nexus-bom

nexmux-kernel ───────► innospots-nexus-platform
        ▲
        │
nexmux-spring ───────┴─── innospots-nexus-spring-platform + H2(runtime)
```

- 根 `pom.xml` 直接继承 `innospots-nexus-parent`，并 import `nexmux-bom`。
- `nexmux-bom` 继承 `innospots-nexus-bom`，登记本产品 artifact。
- `kernel` / `spring` 的 parent 仅为根 `nexmux`，不得重复连接 Nexus parent 或重复 import BOM。
- 模块 POM 不内联 JAR 版本，版本统一由 BOM 管理。

## 3. 包结构与所有权

### 3.1 `core`：领域能力与业务规则

```text
com.innospots.nexmux.core
├── provider/       Provider、凭证、连接测试、上游模型目录
├── model/          Model、默认参数、Binding、定价与生命周期
├── route/          Route、候选、发布、版本、回滚与选择器
├── access/         Client、AppKey、模板、配额与限流策略
├── ops/            请求日志、成本、Pricing Snapshot 与保留清理
├── stats/          Dashboard、用量、可靠性与聚合投影
├── audit/          配置审计与 Diff 查询
├── settings/       全局服务设置与近实时缓存
├── debug/          Console 调试台真实上游调用
└── shared/         状态码、JSON、凭证加密、引用影响分析与通用值对象
```

典型调用链：

```text
console.endpoint → core.service → core.dao
```

业务校验和生命周期规则归 `core.service`；端点仅负责 HTTP 契约和投影，不直接操作 DAO。

### 3.2 `console`：管理接口与 UI 投影

```text
com.innospots.nexmux.console
├── endpoint/       /api/d/** JAX-RS 资源
├── provider/       Provider 列表、详情与选项投影
├── model/          Model、参数、Binding、定价与版本投影
├── route/          Route、发布状态与运行版本投影
├── access/         Client/Template 与策略投影
├── ops/            调用日志、成本与审计详情投影
├── stats/          用量与 Dashboard 展示格式化
├── openapi/        构建期 OpenAPI 定义
└── permission/     权限资源常量
```

Console 层只做接口适配、计数、筛选 Chip、派生文案与展示格式化，不承载业务规则。

### 3.3 `inbound`：真实 Runtime 数据面

```text
com.innospots.nexmux.inbound
├── ingress/        /v1/** 协议入口、错误形状、Request ID 对齐
├── pipeline/       鉴权、授权、配额、限流、选路、上游调用与计量
├── protocol/       OpenAI Compatible Adapter、流式与操作级调用
└── runtime/        路由/目录快照、主动探活、健康与日志保留调度
```

非流式调用链：

```text
RuntimeIngressEndpoint
  → InvocationPipeline
    → AppKeyVerifier
    → PublishedRouteCache + Client 授权
    → QuotaGate + RateLimitGate
    → CandidateSelector + ModelBindingSelector
    → UpstreamChatAdapter
    → RequestLogWriter + 运行日志
```

`inbound → core` 允许；`inbound → console` 禁止。Runtime 请求热路径不逐条查询配置表，配置来自内存快照。

### 3.4 `nexmux-spring`：可运行组装

- `NexmuxPlatformServer`：Spring Boot 主类。
- `NexmuxKernelConfiguration`：Console 侧集中装配。
- `NexmuxRuntimeConfiguration`：`runtime` profile 的 Runtime 专用装配。
- `bin/package.xml`：assembly 描述，生成 `dist/nexmux-spring.tar.gz`。
- `schema/nexmux/nexmux-h2.sql`：产品表结构与幂等迁移。
- `schema/nexmux/nexmux-catalog-data-h2.sql`：Provider/Model catalog 种子。

禁止把业务规则塞进 Spring `@Configuration`；配置类只做 Bean wiring。

### 3.5 `nexmux-ui`：管理控制台

```text
nexmux-ui/
├── app/                      # Next.js 路由与 API 转发
├── src/server/yaml/          # sitemap、页面与布局 DSL
├── src/pactor/               # 自定义动作、调试台、组件与契约
├── public/                   # 皮肤与静态资源
├── scripts/                  # 构建与维护脚本
└── openspec/                 # UI 变更记录
```

UI 使用 Pactor DSL 页面，不为单个模型类型写专用前端。`NEXT_PUBLIC_API_BASE_URL` 是构建期内联值，改变后必须重新构建。

## 4. 核心数据对象

| 对象/表 | 所有者 | 用途 |
|---|---|---|
| `mux_provider` | Console | 上游地址、协议、Region、Timeout、Proxy、状态 |
| `mux_provider_credential` | Console 写 / Runtime 读 | 上游凭证密文、版本与轮换时间 |
| `mux_model` | Console | Capability、默认参数、主/扩展 Binding、定价 |
| `mux_route` | Console | 草稿、已发布规格、Alias、发布时间、`config_revision` |
| `mux_client` | Console | AppKey、授权、配额、限流、有效期、策略修订 |
| `mux_client_template` | Console | 可复制的授权/配额/限流与有效期模板 |
| `mux_platform_setting` | Console | 全局服务设置 |
| `mux_config_audit_log` | Console | 配置操作、操作人、前后快照与 Diff |
| `mux_request_log` | Runtime 写 / Console 读 | 请求、usage、金额、Trace、Prompt/Response 策略 |
| `mux_binding_health` | Runtime 写 / Console 读 | 主动探活、健康状态、延迟与错误 |

金额字段：

- `standard_usd`：标准参考金额；
- `billing_usd`：内部参考消费金额；
- `cost_usd`：供应商成本估算。

这些金额只用于内部分析，不是客户账单、应收或结算凭证。

## 5. 配置发布与快照

### Route

```text
编辑 route_spec
  → 校验候选与引用
  → 写入 published_route_spec
  → config_revision + 1
  → 写配置审计
  → Runtime 按周期加载
```

- 草稿和停用 Route 不进入 Runtime。
- 发布失败不替换旧运行快照。
- 回滚读取历史发布审计中的规格，恢复后生成新的修订，而不是修改历史。

### Client 策略

路由授权、配额和限流作为同一份策略更新，递增 `config_revision`，并在审计中记录变更前后快照。模板只在创建 Client 时复制，不回溯修改既有 Client。

### 其他配置

- Model 停用与 Binding 健康过滤是两个独立条件，停用 Model 恒不参与选路。
- Provider/Model/Client 修改经 Runtime 快照生效。
- Prompt/Response 策略与超时设置通过近实时设置缓存读取，不必等待完整路由快照。
- Console 停止后，Runtime 继续使用当前内存快照。

## 6. 协议与错误语义

| 场景 | 行为 |
|---|---|
| AppKey 缺失、无效、Client 停用或过期 | 401 `NMX040002` |
| Route 未授权 | 403 `NMX040003` |
| Alias 未发布或 Ingress 不匹配 | 404 `NMX130002` |
| 无可用候选/Binding | 503 `NMX020005` |
| 配额超限 | 429 `NMX100001` |
| 限流触发 | 429 `NMX100002` + `Retry-After` |
| 上游 4xx | 原样透传状态与错误体 |
| 上游 5xx、网络、超时 | 502 `NMX060001` |
| 不支持 Tools 或上游协议 | 400 `NMX010001` |

Runtime 的 `x-request-id` 与 `mux_request_log.request_id` 一致。Console 中可用该 ID 下钻日志、成本和 Route Trace。

## 7. 测试组织

### 后端

- `*ContractTest`：端点、实体、状态码和 OpenAPI 契约。
- `*ServiceTest`：生命周期、校验、持久化与审计。
- `InvocationPipelineTest`：鉴权、路由、治理、能力、错误和计量。
- `QuotaGateTest` / `RateLimitGateTest`：窗口、指标、Burst、并发和 Clock。
- `RequestLogWriterTest`：分档、缓存、时段倍率、Pricing Snapshot 与 sample。

当前后端测试总数：**340**。

### 端测

[Console → Runtime 端测计划](./nexmux-console-runtime-e2e-plan.md) 以 100 个编号覆盖：

- 基础接入与真实 DeepSeek smoke；
- Provider、Model/Binding/参数/定价；
- Route、Client/Template、配额限流；
- 全局设置、日志金额与运行韧性。

端测使用独立解包目录和临时数据库，避免“只构建未部署”及共享数据污染。每项记录 PASS/FAIL/BLOCKED、证据和清理结果。

## 8. 构建与发布边界

```bash
# 平台依赖
cd ../innospots-nexus && mvn clean install -DskipTests

# 编译、测试、发行包
cd ../nexmux
mvn validate clean test
mvn package -pl nexmux-spring -am -DskipTests

# 前端
cd nexmux-ui && pnpm install --frozen-lockfile && pnpm build
```

注意：Maven 生成 tar 与运行解包是两个步骤。**修改代码后必须使用本次新 tar 的解包目录重启进程**，不能只看 `package` 成功就认定运行实例已更新。

Fresh 库先启动 Console 执行 DDL 和种子，再启动 Runtime。库结构变更通过 `CREATE/ALTER IF NOT EXISTS` 幂等迁移。

## 9. 文档修改约定

- 新能力先更新设计、接口与测试范围，再修改实现。
- 配置项文档必须同时说明：保存位置、读取方、默认值、生效时间和失败行为。
- 不把“页面存在”写成“功能已实现”，不把“单测通过”写成“端测通过”。
- 运行文档中的端口、路径、状态码和配置键必须与代码一致。
- 计划项、已知限制和生产风险不得隐藏在实现细节中。
