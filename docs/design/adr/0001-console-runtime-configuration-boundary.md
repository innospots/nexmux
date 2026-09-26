# ADR-0001：Model Console 与 Model Runtime 配置边界

## 状态

已接受（与产品 PRD 一致；Runtime 工程形态待二期 project 评审）

## 日期

2026-09-26

## 背景

**Nexmux** 将系统划分为 **Model Console**（控制面）与 **Model Runtime**（数据面）。Console 定义策略，Runtime 执行鉴权、路由、转发与计量。本仓库当前仅包含 Console 可运行组装（`nexmux-spring`），须避免把高频运行时状态写入 Console 业务库。

依据：[需求说明书 §2](../../../../product-design/gateweave/模型管理系统产品功能需求说明书.md)、[控制台设计 §24](../../../../product-design/gateweave/模型管理系统-管理控制台产品功能设计文档.md)。

## 决策

1. **配置权威源**：Provider / Model / Route / Client / Pricing 等**配置类实体**持久化在 Console 侧关系库（由 `nexmux-kernel` 领域层拥有表与 Dao）。
2. **发布物**：Route 发布时将 `route_spec` 拷贝到 **`mux_route.published_route_spec`**；Runtime 只消费该列（`status = PUBLISHED`）。无独立版本表；历史追溯靠审计日志。
3. **运行数据**：Request Log、Usage、Cost、Metric、Route Trace 由 Runtime **写入**运行库或时序/日志存储；Console **只读查询**（PRD §24.2）。
4. **禁止路径**：模型推理请求链路 **不得** 同步依赖 Console HTTP API；Runtime 使用本地 **Credential Cache / Runtime Config Cache**（PRD §24.3）。
5. **仓库边界**：P0 不新增 Maven 模块；Runtime 代码可置于 `com.innospots.nexmux.inbound`，但 **不** 与 `nexmux-spring` 同进程默认启动。独立 `nexmux-runtime`（或等价 artifact）在首版 Runtime 联调前通过 `java:project` 立项。

## 后果

### 正面

- Console 故障不阻断已发布配置下的调用（PRD §3.1）。
- 配置审计与运行观测职责清晰。

### 负面 / 成本

- 需实现发布流水线、快照版本与 Runtime 热更新协议。
- 联调需两个部署单元（或 profile 分离的本地进程）。

## 备选方案（未采纳）

| 方案 | 原因 |
|------|------|
| Console 与 Runtime 单库实时读写 | 违反控制面/数据面分离；高频计数与熔断状态污染配置库 |
| Runtime 每次请求查 Console | 延迟与可用性风险；与 PRD 明示原则冲突 |
| 将 Runtime 并入 `nexmux-spring` | 混淆运维边界，不利于独立扩缩容 |

## 关联

- [nexmux-architecture-design.md](../nexmux-architecture-design.md)
- [nexmux-p0-console-design.md](../nexmux-p0-console-design.md)
