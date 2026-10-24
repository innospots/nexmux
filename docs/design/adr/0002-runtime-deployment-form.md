# ADR-0002：Model Runtime 工程形态与运行库共用

## 状态

已接受（grill-me 2026-10-10 确认）；**修订 ADR-0001 决策 5 的工程形态部分**

## 日期

2026-10-10

## 背景

ADR-0001 决策 5 预留「独立 `nexmux-runtime`（或等价 artifact）在首版 Runtime 联调前通过 `java:project` 立项」，并否决「将 Runtime 并入 `nexmux-spring`」。但根 `AGENTS.md` 限定本仓库**仅四个 Maven 模块**（勿增删），新增 `nexmux-runtime` 模块须先突破该约束。Runtime 落地前需在「模块形态」与「运行库拓扑」两点上收口。

## 决策

1. **工程形态**：不新增 Maven 模块。Runtime 代码归属 `nexmux-kernel` 的 `com.innospots.nexmux.inbound` 包；进程形态为 **`nexmux-spring` 同一 artifact 以独立 Spring profile（`runtime`）启动的独立 JVM 进程**（独立端口，默认 8083）。
   - Console 进程：`--spring.profiles.active=local`（现状不变，Console 端点 beans 标注 `@Profile("!runtime")`）。
   - Runtime 进程：`--spring.profiles.active=local,runtime`，仅装配 `inbound` 相关 beans 与 `/v1/**` Ingress 端点。
   - 这**不是** ADR-0001 否决的「并入同进程默认启动」：两面仍是独立进程、独立端口、独立装配；共享的只是 artifact 与仓库。
2. **运行库拓扑**：Console 与 Runtime **共用同一数据库实例**，按**表级写责分离**：
   - 配置表（`mux_provider` / `mux_model` / `mux_route` / `mux_client` / `mux_client_template` / `mux_config_audit_log` / `mux_platform_setting`）仅 Console 写。
   - 运行表（`mux_request_log` / `mux_binding_health`）仅 Runtime 写（Console 调试台过渡期写入例外，一律 `source=DEBUG`）。
   - Console 对运行表只读；生产部署建议使用只读账号（配置侧注释已落 `application-local.yaml`）。

## 后果

### 正面

- 不触碰 AGENTS.md 四模块约束，无需 `java:project` 新模块评审；构建与依赖管理成本不变。
- 进程级隔离保留 ADR-0001 的核心收益：Console 进程停止/发布不影响 Runtime 已缓存配置的调用；Runtime 可独立扩缩容（多实例起多个 runtime profile 进程）。
- 共用库使本地与中小规模部署的运维成本最低；表级写责分离保留了未来拆库的演进路径（运行表 DDL 已在脚本中标注归属）。

### 负面 / 成本

- 数据库实例成为两面共同的可用性依赖（共用库的固有限制，ADR-0001 否决单库的理由在「同进程实时读写污染配置库」，本决策以表级写责分离 + 低频运行写入缓解）。
- profile 装配边界依赖纪律（`@Profile` 标注），需以端点契约测试守护：runtime 进程不得暴露 `/api/d/**`，console 进程不得暴露 `/v1/**`。
- 配额/限流计数为进程内实例级语义，多实例部署时不保证全局精确。

### 退出条件（触发其一即重评拆库/拆模块）

- 运行表写入频率或数据量影响配置库性能（观测到锁竞争、IO 饱和）。
- 需要 Runtime 多实例 + 全局精确配额/限流。
- 合规要求运行数据与配置数据物理隔离。

## 备选方案（未采纳）

| 方案 | 原因 |
|------|------|
| 新增 `nexmux-runtime` Maven 模块 + 独立 artifact | 违反 AGENTS.md「仅四模块，勿增删」；当前体量下构建收益不抵成本 |
| Console 与 Runtime 同进程默认启动 | ADR-0001 已否决：混淆运维边界，推理故障域波及控制面 |
| 独立运行库实例 | 现阶段运维成本过高；表级写责分离已保留演进路径 |

## 关联

- [ADR-0001 Console / Runtime 配置边界](./0001-console-runtime-configuration-boundary.md)（决策 5 工程形态部分由本 ADR 修订）
- [nexmux-runtime-design.md](../nexmux-runtime-design.md)（§1 grill-me 决策 1/2、§5.1、§9）
