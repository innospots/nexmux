# Nexmux 生产运行手册

> 面向值班与运维人员。开发环境启动见 [运行与部署](./nexmux-deployment.md)，业务错误码见 [模型接入指南](./nexmux-integration-guide.md)。

## 1. 进程与端口

| 组件 | 默认业务端口 | 默认管理端口 | 关键路径 |
|---|---:|---:|---|
| Console | 8082 | 19090 | `/api/d/**`、`/actuator/health`（仅管理口） |
| Runtime | 8083 | 19091 | `/v1/**`、`/actuator/health`（仅管理口） |
| UI | 9011 | — | 控制台页面 |

检查：

```bash
curl http://localhost:8082/api/d/service-settings
curl http://localhost:8083/v1/models
curl http://localhost:19090/actuator/health
curl http://localhost:19091/actuator/health
```

Runtime 无 AppKey 请求 `/v1/models` 应返回 401，而不是进程不可用。管理口仅允许内网/回环访问。

## 2. 启停与升级

```bash
cd /path/to/nexmux-spring
./bin/startup.sh "prod"
./bin/startup.sh "prod,runtime"
./bin/shutdown.sh
```

- fresh 库必须先起 Console，再启动 Runtime。
- 升级前停止两个进程，备份数据库、配置和旧发行包。
- 新包必须从本次 tar 的解包目录启动，不能仅覆盖 JAR 名称就假定加载了新类。
- 启动后检查进程、端口、schema、AppKey、已发布 Alias 和一次真实调用，再恢复流量。

## 3. 配置变更

- Route 必须先发布；修改草稿不影响当前运行版本。
- 默认快照刷新为 30 秒。变更未生效时先等待一个周期，再检查 published spec 和进程 CWD/DB URL 是否一致。
- Route 回滚使用历史版本并生成新修订；Client 策略更新递增 `config_revision`。
- 全局超时和 Prompt 策略采用近实时设置缓存，不要求重启，但需避免多个进程配置不一致。
- 生产配置密钥通过受控注入更新，不写入发行包或启动脚本。

## 4. 备份与恢复

### 备份

1. 停止 Console 与 Runtime，等待 PID 退出。
2. 备份数据库文件、WAL/锁文件、`config/`、发行包和外部密钥元数据（不备份明文密钥到普通文档）。
3. 记录版本、commit、数据库路径、备份时间和校验值。

### 恢复

1. 停止所有相关进程，先恢复数据库与配置到隔离目录。
2. 使用匹配版本启动 Console，验证 schema、配置和审计，再启动 Runtime。
3. 执行只读检查和一次受控调用，确认日志、金额和 Route Trace。
4. 验证完成后恢复端口与流量，保留原备份直到观察期结束。

若强杀后出现 H2 `AUTO_SERVER` 握手 EOF，停止全部进程并删除对应 `*.lock.db` 后再启动，不要同时删除数据文件。

## 5. 日常巡检

- Runtime 是否持续可用、错误率与上游延迟是否异常。
- `mux_binding_health` 是否出现持续 `UNAVAILABLE` 或 `DEGRADED`。
- `mux_request_log` 是否增长正常，`logRetentionDays` 是否符合合规要求，清理日志是否报告异常。
- 是否存在无可用候选、凭证失败、快照刷新失败或配额校准失败。
- 标准金额、内部参考消费金额与供应商成本是否口径一致，价格版本/Binding 是否可追溯。
- 磁盘、线程、H2 锁文件、数据库连接与备份是否健康。

## 6. 常见故障处置

| 现象 | 优先检查 | 处理 |
|---|---|---|
| 调用 401 | AppKey、Client 状态/有效期、请求头 | 使用正确 AppKey，必要时轮换并更新调用方 |
| 调用 403 | Client 的 Route 授权 | 重新授权并等待快照 |
| 调用 404 | Alias、Route 状态、Ingress Path | 使用 `/v1/models` 核对已发布 Alias 与路径 |
| 调用 503 | Model/候选/Binding 状态、健康 | 检查 Model 停用、Binding 健康、Provider 状态 |
| 502 | Base URL、Proxy、凭证、超时、上游 5xx | 用 `x-request-id` 对齐日志，区分上游与网关故障 |
| 修改不生效 | published spec、快照周期、DB URL、旧解包目录 | 先确认配置源一致，再等待刷新或重启正确实例 |
| Request ID 查不到日志 | 响应头与日志 ID、时间范围 | 使用最新 Runtime 包，按 ID 和时间联合定位 |
| 金额不一致 | 命中档、缓存价格、时段倍率、C/M | 查 Pricing Snapshot、standard/billing/cost 三类金额 |
| 代理错误码异常 | 透明代理 vs 真实连接超时 | 不把代理 HTTP 错误当超时；在无透明代理环境复测 |
| UI 空数据或假成功 | 后端可达性、转发层、真实状态码 | 直接请求 Console `/api/d/**`，不要只信空态或 `{ok:true}` |

## 7. 扩缩容与多实例

- 当前配额与限流为**实例级**计数，不能将多实例结果解释为全局精确限制。
- 扩容前评审 ADR-0002 的共用库退出条件、数据库写责、快照一致性和分布式计数需求。
- 多实例使用同一数据库和密钥配置；检查每个实例的 snapshot、健康探活与 Request ID。
- 灰度发布时先验证单实例全链路，再增加实例，不在未评估前直接做全局严格限流。

## 8. 安全运维

- 生产启用外部 IAM、HTTPS、`CONSOLE_TOKEN_SECRET` 与 `NEXMUX_CREDENTIAL_SECRET`。
- 密钥轮换保持 Console/Runtime 同步；AppKey 轮换后旧 Key 立即失效。
- Debug 默认应关闭或限制日配额，避免真实上游费用与数据出网。
- `promptPolicy=full/sample` 需评审 Prompt/Response 保存范围、访问权限与保留期限。
- 管理口、actuator、数据库和备份只允许受控网络访问。
