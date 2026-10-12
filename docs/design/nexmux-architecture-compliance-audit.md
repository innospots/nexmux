# Nexmux 架构一致性审计：Console / Runtime 边界

## 1. 文档定位

- **状态**：已提出（待修复）
- **日期**：2026-10-10
- **审计对象**：`nexmux-kernel`、`nexmux-spring`、`nexmux-ui`
- **架构基线**：
  - [ADR-0001 Console / Runtime 配置边界](./adr/0001-console-runtime-configuration-boundary.md)
  - [nexmux-architecture-design.md](./nexmux-architecture-design.md)（§2、§4、§5.2、§6、§8）
- **关联**：[nexmux-p0-console-design.md](./nexmux-p0-console-design.md)、[nexmux-domain-layer-design.md](./nexmux-domain-layer-design.md)

### 审计范围与方法

以 ADR-0001 与架构文档为基线，对以下规则逐条做代码检索与运行验证：

1. 配置权威源在 Console 侧关系库，由 `nexmux-kernel` 领域层拥有表与 Dao。
2. 发布物：Route 发布时 `route_spec` → `published_route_spec`；**Runtime 只消费该列**（`status = PUBLISHED`）。
3. 运行数据（Request Log / Usage / Cost / Metric / Route Trace）由 **Runtime 写入**；Console **只读查询**。
4. 推理链路 **不得** 同步依赖 Console；Runtime 使用本地 Credential Cache / Runtime Config Cache。
5. 依赖方向：`console.endpoint → core.*`（禁止 endpoint 直调 Dao）；`core` 不依赖 `console` / `inbound`；`inbound` 不依赖 `console`。
6. Runtime 代码归属 `com.innospots.nexmux.inbound`，**不**与 `nexmux-spring` 同进程默认启动。

---

## 2. 结论摘要

主干分层**合规**（依赖方向、endpoint→dao、发布快照消费、状态码均正确），但存在 **4 处明确的 Console / Runtime 边界不符**、2 处边界模糊/文档滞后、1 处双 UI 运行时重复实现。

| 编号 | 级别 | 问题 |
|---|---|---|
| A1 | 高 | Console 侧写入 Runtime 表 `mux_request_log` |
| A2 | 高 | Console 侧承担推理与上游出网，并由 `nexmux-spring` 默认装配暴露 |
| A3 | 中 | Console 侧直接解密/使用上游凭证 |
| A4 | 中 | 运行表 DDL 与库/账号未与配置分离（单库同账号可写） |
| B1 | 中 | 调试台是「Console 内模拟 Runtime」，且选路逻辑与 Runtime 不一致 |
| B2 | 低 | 架构 §5.2 `core` 域清单缺 `debug`（文档滞后） |
| E1 | 中 | 页面 DSL 两套来源（kernel `ui-pages` vs nexmux-ui `yaml`），两套 UI 运行栈 |

---

## 3. 明确不符（需修复）

### A1. Console 侧写入 Runtime 表 `mux_request_log`

- **架构依据**：ADR-0001 §3「Request Log、Usage、Cost、Metric、Route Trace 由 Runtime **写入**运行库或时序/日志存储；Console **只读查询**」；架构 §8「运行库表（`mux_request_log` 等）由 Runtime 写入；Console 只读 Dao」。
- **现状证据**：
  - `nexmux-kernel/src/main/java/com/innospots/nexmux/core/debug/service/DebugService.java:516` `writeRequestLog(...)`，`:586` `requestLogDao.insert(entity)`。
  - 触发路径：`modelInvoke`、`clientInvoke`、`clientInvokeStream`（调试台四条链路全部落运行表）。
- **影响**：调用日志 / 用量 / 成本 / 仪表盘 / 供应商可用性页面的数据全部来自 Console 调试写入，而非真实运行流量；数据面写入职责落在控制面。
- **修复方向**：
  1. 运行数据写入从 Console 迁出（由 Runtime 或独立写入器负责）；
  2. 或在 Runtime 就绪前，明确把调试写入标记为 `source=DEBUG`，并在 Console 侧以只读账号接运行库（见 A4）。

### A2. Console 侧做推理 + 上游出网

- **架构依据**：ADR-0001 §1「Console 定义策略，Runtime 执行鉴权/路由/转发/计量」、§4「模型推理请求链路**不得**同步依赖 Console」；架构 §2 排除「在 `nexmux-spring` 内默认启动 Runtime 推理服务」。
- **现状证据**：
  - `DebugService.java:235 / :281` 以 `HttpURLConnection` 调 `{baseUrl}/chat/completions`（含 SSE 流式）。
  - `nexmux-kernel/.../core/provider/service/ProviderService.java:229` `connectionTest(...)`，`:282-284` HTTP 调 `{baseUrl}/models`。
  - `nexmux-spring/src/main/java/com/innospots/nexmux/spring/config/NexmuxKernelConfiguration.java:161`（`DebugService` Bean）、`:296`（`DebugEndpoint` Bean）——Console 进程装载并对外暴露推理能力。
- **影响**：控制面进程具备推理能力并对外暴露；"调试"会产生真实上游流量与费用；违反控制面/数据面分离。
- **修复方向**：把调试穿透收敛为「数据面能力」——要么由 `inbound` Runtime 提供调试端点并让 Console 只调用它，要么对 Console 内推理做严格限域（仅调试、可关闭、有配额与审计）。

### A3. Console 侧直接解密/使用上游凭证

- **架构依据**：ADR-0001 §4「Runtime 使用本地 **Credential Cache / Runtime Config Cache**」；Console 只保存密文配置。
- **现状证据**：`DebugService.java:235 / :281 / :415` 取 `ProviderCredentialEntity.ciphertext` 直接作为 `Bearer` 使用。
- **影响**：凭证使用面下沉到控制面；与「凭证只由数据面消费」的边界冲突。
- **修复方向**：凭证解密与使用统一放在 Runtime（或统一凭证服务），Console 只做引用与轮换。

### A4. 运行表 DDL 与库/账号未与配置分离

- **架构依据**：架构 §8「运行库表由 Runtime 写入；Console 只读 Dao，连接只读数据源或同一库只读账号」；ADR-0001 备选方案明确**否决**「Console 与 Runtime 单库实时读写」。
- **现状证据**：
  - `mux_request_log` 建表/索引位于 Console 侧 `nexmux-spring/src/main/resources/nexus/schema/nexmux-h2.sql:180`（及 `:224+` 的增量列）。
  - `nexmux-spring/src/main/resources/application-local.yaml:5-8`：配置库与运行库**同库、同账号 `sa`、可写**。
- **影响**：控制面/数据面未分离；高频运行状态与配置同库同权限，ADR 已明确否决该模式。
- **修复方向**：运行表 DDL 归属数据面；Console 侧改用只读数据源/只读账号；本地开发如需单库，至少在文档与配置中体现"只读"约束。

---

## 4. 边界模糊 / 文档滞后

### B1. 调试台 = 「Console 内模拟 Runtime」，且选路逻辑与 Runtime 不一致

- 架构 §6.1 将 `Debug（模型调试 / 客户端全链路调试）` 明确列为 Console API 组，`DebugService` 也引"设计 §4.3"——**不算擅自越界**，属有意设计。
- 但其行为（鉴权 → 选路由 → 选候选 → 转发上游 → 写运行日志）就是 Runtime 职责；且候选选择为：

  ```java
  // DebugService.resolveClientTarget()
  var candidate = spec.candidates().stream()
          .filter(c -> c.enabled() == null || c.enabled())
          .findFirst();
  ```

  忽略 `strategyType / weight / priority / stickySession / filterUnhealthyBindings`。
- **后果**：出现"调试通过 ≠ 上线一致"；配置项（策略/权重/优先级/粘性/健康过滤）在调试链路中**不生效**（实测：六种策略选择结果完全相同）。
- **修复方向**：抽出共享 `CandidateSelector`（Runtime 与调试台同一实现）；在此之前，至少在前端标注"调试绕过路由策略"。

### B2. 架构文档滞后：`core` 域清单缺 `debug`

- 架构 §5.2 列出 `core` 域为 provider/model/route/access/audit/ops/settings/stats/shared；实际存在 `core/debug/{service, domain/{request, vo}}`。
- **修复方向**：同步 §5.2 清单，或在领域文档中登记 `debug` 域归属与职责边界。

### E1. 页面 DSL 两套来源（跨层重复）

- 架构 §8 规定 PageDsl 位于 `nexmux-kernel/src/main/resources/ui-pages/nexmux/{moduleKey}/*.yaml`（Nexus PageDsl）。
- 实际运行（:9011）使用 `nexmux-ui/src/server/yaml/**`（Pactor DSL + Next 转发层）。
- 两套内容重复、口径不一（例如时间范围默认值在两份中都硬编码）。
- **修复方向**：确定单一来源；若短期保留双实现，需明确同步机制与差异清单。

---

## 5. 合规清单（已核对，无问题）

| 规则 | 结果 |
|---|---|
| `core` 不依赖 `console` | ✅（无 `import com.innospots.nexmux.console`） |
| `inbound` 不依赖 `console` | ✅（`inbound` 目前仅 `package-info.java`） |
| `console.endpoint` 不直调 Dao | ✅（全部经 Service） |
| 只消费 `published_route_spec`（`status = PUBLISHED`） | ✅ `DebugService.resolveClientTarget()` 有 `PUBLISHED && publishedRouteSpec != null` 校验 |
| 未发布请求失败码登记 | ✅ `NexmuxStatusCode.ROUTE_NOT_PUBLISHED("0002")`，与架构 §7 一致 |
| 管理 API 无 URL 版本段（`/api/d`） | ✅ |
| `nexmux-spring` 仅 Boot + assembly | ✅ `NexmuxKernelConfiguration` 只 new Service/Dao，无业务逻辑 |
| `inbound` 预留 | ⚠️ 仅 `package-info.java`；「P0 契约先行」尚未落地骨架 |

---

## 6. 修复优先级与验收标准

### P0

1. **明确调试台口径**（A2/A3/B1）：确定"调试台属于 Console 还是数据面模拟"。若保留在 Console：
   - 抽出共享 `CandidateSelector`，调试与 Runtime 复用；
   - 页面对"绕过策略"给出显式提示；
   - 对 Console 内推理做限域（可关闭开关、配额、审计）。
2. **运行数据写入与权限**（A1/A4）：`mux_request_log` 写入迁出 Console（或标注来源并改只读账号）；DDL 归属数据面。

### P1

3. **`inbound` 落地 Runtime 骨架**（`inbound/runtime` 配置快照缓存 + `inbound/protocol` 适配），使 PRD §25.1「可调用闭环」成立。
4. **统一页面 DSL 来源**（E1），消除两套 UI 运行栈。

### P2

5. 同步架构 §5.2 `core` 域清单（B2）。

### 复测验收（修复完成后逐条验证）

- [ ] A1：Console 侧不再写 `mux_request_log`（或写入被显式标记为 DEBUG 且 Console 使用只读账号）。
- [ ] A2/A3：Console 进程不直接解密凭证、不直接调上游（推理与凭证使用由数据面承担，或限域开关可见）。
- [ ] A4：运行库与配置库权限分离（至少文档 + 配置体现只读）。
- [ ] B1：调试台与 Runtime 使用同一 `CandidateSelector`；策略/权重/优先级/粘性/健康过滤在调试链路中可观测地生效。
- [ ] P1：`inbound` 存在可运行的 Runtime 骨架，并只消费 `published_route_spec`。
- [ ] E1：页面 DSL 单一来源（或给出双实现同步机制）。
- [ ] 回归：合规清单 8 项仍全部通过；`tsc + eslint`、`mvn clean compile` 通过。

---

## 7. 附：证据索引

| 编号 | 文件 : 行 |
|---|---|
| A1 | `nexmux-kernel/.../core/debug/service/DebugService.java:516,586` |
| A2 | `DebugService.java:235,281`；`core/provider/service/ProviderService.java:229,282-284`；`nexmux-spring/.../NexmuxKernelConfiguration.java:161,296` |
| A3 | `DebugService.java:235,281,415` |
| A4 | `nexmux-spring/src/main/resources/nexus/schema/nexmux-h2.sql:180,224`；`application-local.yaml:5-8` |
| B1 | `DebugService.java:159-204`（`resolveClientTarget`） |
| B2 | `docs/design/nexmux-architecture-design.md` §5.2 |
| E1 | `nexmux-kernel/src/main/resources/ui-pages/nexmux/**` vs `nexmux-ui/src/server/yaml/**` |

---

## 8. 复测记录（2026-10-10，修复后）

修复方：agent-9EAGYZ。复测环境：重建发行包 `mvn package -pl nexmux-spring -am -DskipTests` → 解包 `out/nexmux-spring` → 重启 :8082；:9011 dev server 重启。

### 通过

| 项 | 结果 |
|---|---|
| B1 选路真实生效 | ✅ `CandidateSelector` 生效：FIXED/PRIORITY/WEIGHTED/HASH/ROUND_ROBIN/RANDOM 结果各异；优先级分层、权重、粘性会话均可观测（RANDOM+sticky off → 6/6 混合；RANDOM+sticky on → 12/12 定选）；`route_trace` 含 `candidateModelCode/poolSize/healthFilterApplied/stickyApplied` |
| A1 运行数据标记 | ✅ 新写入行 `source=DEBUG`，旧行 `null` |
| 调试限域（语义） | ✅ `debugInvokeEnabled=false` → envelope `code=NMX020003`；`debugDailyQuota` 超限 → `NMX020004` |
| 调试限域（UI） | ✅ client-debug / model-debug 页 Alert、服务设置「调试台限域」卡片（:9011 重启后生效） |
| P1-1 inbound 骨架 | ✅ `PublishedRouteCache` 只读 `status=PUBLISHED AND published_route_spec IS NOT NULL`；`RuntimeRouter` / `UpstreamChatAdapter` / `OpenAiCompatibleChatAdapter` 就位，凭证在后两者消费 |
| P1-2 / P2 文档 | ✅ §5.2 core 域补 `debug`/`selection`；§6.1 调试台口径；运行库落地口径；页面 DSL 双实现口径；§7 补 `0003/0004` |
| 合规回归 | ✅ `core→console`=0、`inbound→console`=0、`console.endpoint→dao`=0 |
| 构建/静态检查 | ✅ `mvn package` BUILD SUCCESS；`tsc --noEmit` ✅；`eslint src` 0 problems（theme-switcher 已修） |
| 单测 | ✅ CandidateSelectorTest 13 + DebugServiceTest 18 + PublishedRouteCacheTest 5 + OpenAiCompatibleChatAdapterTest 2 = 38，0 失败 |

### 未通过（需修）

| 项 | 结果 |
|---|---|
| **HTTP 状态码未生效** | ❌ 声明 403/429，实际 **HTTP 500**（envelope `code` 正确）。系统性：`GET /api/d/routes/does-not-exist` → `code=NMX130001` 但 HTTP **500**（应 404）。根因在平台侧：`NexusException` 只携带 `code` 字符串，不携带 `StatusCode`；`ConsoleJaxRsExceptionSupport.resolveHttpStatus()` 用平台 `NexusStatusCode.findByFullCode(code)` 查表，平台模块码为 `AIO`，`NMX*` 永不匹配 → `.orElse(SYSTEM_ERROR)` → 500。nexmux 侧 `NexmuxStatusCode` 已声明 403/429 但未被使用。 |

修复方向（建议）：

1. 平台 `NexusException.build(StatusCode)` 时保存 `statusCode.httpStatusCode()`，mapper 优先使用；
2. 或 mapper 按 `module()` 解析（支持 `NMX`/`PLG` 等非 `AIO` 模块）；
3. 或 nexus 侧提供独立 `@Provider` ExceptionMapper 覆盖。

> 注意 1/2 属 `innospots-nexus`（平台）范围，需确认改动归属。

### 观察（非阻塞）

- `source` 未在 `GET /api/d/call-logs/{id}` 暴露，UI 无法展示 `source=DEBUG`。
- `DebugService` 仍自带 `HttpURLConnection`/`postChatCompletions`/`streamInvoke` 与凭证消费，未复用 `inbound/protocol/OpenAiCompatibleChatAdapter` → 两套 HTTP 实现，有漂移风险。
- 健康过滤 `healthFilterApplied=false`（无健康数据源），本地无法验证剔除行为。
- `ProviderService.connectionTest` 仍在 Console 出网，属管理面连通性探测（PRD §7），建议在文档中显式登记为**已接受例外**。
