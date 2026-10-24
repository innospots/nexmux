# Nexmux 运行与部署

> 适用版本：`0.1.0-SNAPSHOT`（发行包 `dist/nexmux-spring.tar.gz`）。
> 架构决策见 [design/adr/0002-runtime-deployment-form.md](./design/adr/0002-runtime-deployment-form.md)；
> Runtime 契约见 [design/nexmux-runtime-design.md](./design/nexmux-runtime-design.md)。

## 1. 拓扑

```text
┌─────────────────────┐         ┌─────────────────────┐
│  Console 进程        │         │  Runtime 进程        │
│  profile: prod       │         │  profile: prod,runtime│
│  端口 8082           │         │  端口 8083           │
│  /api/d/** 管理端点  │         │  /v1/** Ingress      │
│  （Bearer 会话鉴权） │         │  （AppKey 鉴权）      │
└─────────┬───────────┘         └─────────┬───────────┘
          │ 配置表可写 / 运行表只读         │ 运行表可写 / 配置表只读（快照缓存）
          └──────────────┬────────────────┘
                         ▼
              H2 文件库（默认，单实例）
              /var/lib/nexmux/nexus-platform-console.mv.db
```

- 同一 artifact 两个 JVM 进程；**互不默认启动对方面**（`@Profile` 隔离）。
- 本地/小规模：单 H2 文件库（`AUTO_SERVER=TRUE` 支持双 JVM 共写）。
- 生产建议：Runtime 账号对运行表（`mux_request_log` / `mux_binding_health`）可写、配置表只读；
  Console 账号反之（表级写责分离，ADR-0002 §决策 2）。

## 2. 启动

```bash
tar -xzf nexmux-spring.tar.gz && cd nexmux-spring

# 必填：控制台令牌密钥（16 字节）
export CONSOLE_TOKEN_SECRET='<16-byte-secret>'

# 可选但生产强烈建议：Provider 上游凭证落库加密密钥（AES-GCM，任意长原文）
# 不配置则凭证明文落库（启动告警）；配置后新写/轮换自动加密，存量明文行兼容读取、
# 下次保存即升级为密文。两进程必须同值；更换密钥前须先用旧密钥轮换全部凭证
export NEXMUX_CREDENTIAL_SECRET='<random-long-secret>'

# 可选：库路径（默认 /var/lib/nexmux/nexus-platform-console）
export DB_URL='jdbc:h2:file:/var/lib/nexmux/nexus-platform-console;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;AUTO_SERVER=TRUE'

# Console（8082）
./bin/startup.sh "prod"

# Runtime（8083）
./bin/startup.sh "prod,runtime"
```

**启动顺序**：fresh 库必须先起 Console（DDL 与种子数据由 Console 进程执行；Runtime 的 `spring.sql.init.mode=never`，只消费快照）。

可选端口覆盖（默认值见括号）：`CONSOLE_PORT`（8082）、`RUNTIME_PORT`（8083）、`CONSOLE_MANAGEMENT_PORT`（19090）、`RUNTIME_MANAGEMENT_PORT`（19091）。

**配置覆盖**：`config/` 目录对两个进程共享（`startup.sh` 挂载 `optional:file:config/`），`config/nexus/*.yaml` 会同时影响两进程；角色级覆盖使用 `config/application-runtime.yaml`（仅 Runtime 进程加载）。

注意：宿主认证装配无 profile 守卫，**prod 下 Console 与 Runtime 都必须注入 `CONSOLE_TOKEN_SECRET`**（同值）。

停止：`./bin/shutdown.sh`（各进程独立 PID 文件）。

## 3. Profile 矩阵

| profile | 用途 | 数据源 | 安全 |
|---------|------|--------|------|
| `local`（默认） | 本地开发 Console | `./out/db`（相对 CWD） | security 关闭 + dev-session |
| `local,runtime` | 本地 Runtime | 同上（跨目录启动须 `SPRING_DATASOURCE_URL` 绝对路径覆盖） | `/v1/**` 免会话鉴权 |
| `prod` | 生产 Console | `DB_URL`（绝对路径） | security 强制开启 |
| `prod,runtime` | 生产 Runtime | 同上 | 同上 + AppKey 鉴权 |

所有 runtime 组合：`spring.sql.init.mode=never`（DDL/种子归 Console），且 prod 下必须注入 `CONSOLE_TOKEN_SECRET`。

## 4. Runtime API 与运行版本

| 端点 | 当前能力 |
|---|---|
| `GET /v1/models` | 返回当前 AppKey 已授权且已发布的 Alias |
| `POST /v1/chat/completions` | OpenAI Chat Completions，非流式与 SSE |
| `POST /v1/responses` | OpenAI Responses，非流式 |
| `POST /v1/messages` | Anthropic Messages，非流式 |
| `POST /v1/embeddings` | OpenAI Embeddings |

- `model` 始终是 Route Alias，不是上游 Provider Model ID。
- Responses、Messages、Embeddings 的 Streaming 尚未开放；`stream=true` 会明确返回 400。
- Route 每次发布递增 `config_revision`，版本接口为 `GET /api/d/routes/{code}/versions`，回滚为 `POST /api/d/routes/{code}/rollback?revision=N`。
- 回滚使用历史发布规格并生成新修订，不修改历史审计；发布失败保留当前运行版本。
- Client 授权、配额、限流作为同一策略版本更新，版本查询为 `POST /api/d/clients/{id}/publish`。

## 5. 关键配置（`config/nexus/nexmux.yaml`）

| 键 | 默认 | 说明 |
|----|------|------|
| `nexmux.runtime.snapshot-poll-interval-seconds` | 30 | Runtime 配置快照重载周期 |
| `nexmux.runtime.health-probe-interval-seconds` | 60 | Binding 主动探活周期 |
| `nexmux.runtime.health-probe-timeout-ms` | 5000 | 探测超时 |
| `nexmux.runtime.health-unhealthy-threshold` | 3 | 连续失败判 UNAVAILABLE |
| `nexmux.runtime.health-degraded-latency-ms` | 10000 | 延迟超此判 DEGRADED |
| `nexmux.runtime.quota-resync-interval-seconds` | 300 | 配额计数与库校准周期 |
| `nexmux.runtime.log-retention-initial-delay-minutes` | 10 | 日志清理首次执行延迟，测试可设 0 |
| `nexmux.runtime.log-retention-period-minutes` | 1440 | 日志清理周期，默认每日 |

调试台限域（Console 服务设置页或 `mux_platform_setting` 表）：`debugInvokeEnabled`、`debugDailyQuota`。

## 6. 健康检查与监控（仅内网/本机）

- actuator 绑定回环地址（`management.server.address=127.0.0.1`）+ 独立管理端口：
  Console `127.0.0.1:19090`、Runtime `127.0.0.1:19091`（runtime profile 覆盖；可分别用 `CONSOLE_MANAGEMENT_PORT` / `RUNTIME_MANAGEMENT_PORT` env 再改）。
  **业务端口（8082/18082/8083）不再暴露 `/actuator/**`**，公网不可达。
- `GET /actuator/health`（liveness/readiness 已启用）；Prometheus：`/actuator/prometheus`。

## 7. 升级与迁移

- 库结构演进幂等：新包启动自动执行 `CREATE/ALTER IF NOT EXISTS`（H2），无需手工迁移。
- 配置变更热生效边界：Runtime 侧 Provider/Model/Route/Client 改动在快照周期内生效（默认 30s）；
  Route 须「发布」才对 Runtime 可见（草稿不影响线上）。

## 8. 已知限制与生产边界

- **nexmux-ui 的 `NEXT_PUBLIC_*` 为构建期内联变量**：改动 API 上游地址必须「改环境变量 + 重新 `pnpm build` + 重启」，仅改 systemd unit 重启无效（部署实测教训）。

- 配额/限流为 Runtime **实例级**计数（多实例非全局精确；多实例部署前评审 ADR-0002 退出条件）。
- AppKey 轮换后旧 key 立即失效（宽限期未实现）。
- `/v1/chat/completions` 支持 SSE；`/v1/responses`、`/v1/messages`、`/v1/embeddings` 当前仅非流式。
- 当前上游 Adapter 为 OpenAI Compatible；其他上游协议在调用前明确拒绝。
- 多实例配额与限流为实例级语义，不是分布式一致。
- 连接超时的毫秒级计时需在无透明代理环境验收；保留 IP 被透明网关代理时不能作为有效超时用例。
- 供应商凭证支持落库加密（`NEXMUX_CREDENTIAL_SECRET`，AES-GCM，`aesgcm:v1` 前缀密文）；未配置时明文存储并启动告警——生产必配，并保证库文件与备份的访问控制。
- 调试台穿透会产生真实上游调用与费用：生产建议 `debugInvokeEnabled=false` 或收紧配额。
- 本地双进程共用 H2（`AUTO_SERVER`）：进程被强杀（如超时杀进程组）后 `.lock.db` 残留会使另一进程的 AUTO_SERVER TCP 握手 EOF——停全部进程、删 `out/db/*.lock.db` 后重启。系统 SOCKS 代理不可用时需加 `-Djava.net.useSystemProxies=false`（H2 AUTO_SERVER 与上游调用都会走路径代理）。

## 9. 发布检查单

- [ ] `CONSOLE_TOKEN_SECRET` 已注入（非示例值）
- [ ] `DB_URL` 为绝对路径，两进程指向同一库文件
- [ ] Console `security.enabled=true` 生效（未登录访问 `/api/d/providers` 返回 401）
- [ ] Runtime 未暴露 `/api/d/**`（应 404）；Console 未暴露 `/v1/**`（应 404）
- [ ] `/actuator/health` 两进程 UP（本机 127.0.0.1:19090 / 19091；业务端口访问 actuator 应 404）
- [ ] 调试台口径已评审（debugInvokeEnabled / debugDailyQuota）
- [ ] 库文件备份策略就位（H2 文件库）
