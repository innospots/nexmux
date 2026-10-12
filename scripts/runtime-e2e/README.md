# Nexmux Model Runtime E2E 测试手册

目标：对运行中的 Model Runtime（`runtime` profile，`:8083`）逐项验证**路由配置项**与**客户端配置项**的真实行为。
本手册每条用例给出确切命令与预期结果；全部用例基于固定 fixture（见 `env.sh` 头注释）。

## 0. 环境准备（一次性）

```bash
export E2E_REPO=/Users/mac/worker/code/my-ai/innospots/nexmux
export E2E_APP_DIR=/tmp/nexmux-e2e/nexmux-spring
export JAVA_HOME=$(/usr/libexec/java_home)

# 1. 解包发行包
mkdir -p /tmp/nexmux-e2e && tar -xzf $E2E_REPO/dist/nexmux-spring.tar.gz -C /tmp/nexmux-e2e

# 2. 加速快照刷新/探活（追加到解包后的 config/application-local.yaml 末尾）
cat >> $E2E_APP_DIR/config/application-local.yaml <<'EOF'
nexmux:
  runtime:
    snapshot-poll-interval-seconds: 2
    health-probe-interval-seconds: 3
    health-probe-timeout-ms: 1000
    quota-resync-interval-seconds: 3
EOF

# 3. 起上游桩（保持运行）
nohup python3 $E2E_REPO/scripts/runtime-e2e/upstream-stub.py 18099 > /tmp/nexmux-e2e/upstream.log 2>&1 &

# 4. 起 runtime（保持运行）
cd $E2E_APP_DIR && ./bin/startup.sh "local,runtime"

# 5. 加载工具函数并种 fixture
source $E2E_REPO/scripts/runtime-e2e/env.sh
sleep 15   # 等 runtime 启动
seed_fixtures
```

通用请求体（变量别名即路由）：`BODY='{"model":"e2e-route","messages":[{"role":"user","content":"hi"}]}'`

**每次改路由/客户端配置后必须 `wait_refresh`**（快照 2s 刷新）。

> ⚠️ **数据库共用陷阱**（ADR-0002 本地单库形态）：`application-local.yaml` 的 datasource 是相对路径 `./out/db`（相对进程 CWD）。按本节从 `$E2E_APP_DIR` 启动 runtime 时，它使用的是**独立库文件**，与开发Console（:8082，从仓库根启动）**不共享**——D1–D4 仅在数据库层验证。若要让 Console 调用日志页看到 E2E 的 RUNTIME 行，须走 §6 联动验证。

## 1. 路由配置项用例

| # | 配置项 | 设置命令（reset_route 参数） | 操作 | 预期 |
|---|--------|------------------------------|------|------|
| R1 | 策略 FIXED | `{"alias":"e2e-route","ingress":"/v1/chat/completions","strategyType":"FIXED","candidates":[{"modelCode":"e2e/model-a","weight":100},{"modelCode":"e2e/model-b","weight":100}],"schemaVersion":1}` | `call "$BODY"` ×3 | 全部 200 且 content 均含 `stub-a`（恒取首候选） |
| R2 | 策略 WEIGHTED | 同上但 `strategyType:"WEIGHTED"` 权重 100/0 | ×5 | 全部命中 `stub-a`；再改权重 0/100 → 全部命中 `stub-b` |
| R3 | 策略 ROUND_ROBIN | `strategyType:"ROUND_ROBIN"`，权重 1/1 | ×4 | 命中依次交替 a,b,a,b |
| R4 | 策略 RANDOM | `strategyType:"RANDOM"`，权重 50/50 | ×10 | stub-a 与 stub-b 都至少出现 1 次 |
| R5 | 策略 HASH | `strategyType:"HASH"`，权重 50/50 | body 加 `"user":"u1"`，×5 | 5 次命中同一模型；换 `"user":"u2"` 亦各自恒定 |
| R6 | 候选优先级 | `strategyType:"FIXED"`，candidates=[{a,weight100,priority:"P1"},{b,weight100,priority:"P0"}] | ×3 | 全部命中 `stub-b`（P0 层优先）；**同级 priority 时取声明序首候选**（a/b 同为 P0 → 恒 a，属实现定义语义） |
| R7 | 粘性 Session | `strategyType:"WEIGHTED"` 权重 50/50 + `"stickySession":true` | body 加 `"user":"u9"`，×6 | 全部命中同一模型 |
| R8 | 健康过滤 | candidates=[{e2e/model-dead,50},{e2e/model-a,50}]，`"filterUnhealthyBindings":true` | 等探活 3 轮（~10s）后 ×6 | 全部命中 `stub-a` 无 502；同 spec 改 `false` → 命中 model-dead 的请求**真实调上游并失败**：502 `NMX060001` + 落 ERROR 日志（PRD §19.7 语义）；命中 a 的请求 200 |
| R9 | 候选启停 | FIXED，candidates=[{a,100,enabled:false},{b,100}] | ×3 | 全部命中 `stub-b` |
| R10 | 别名不存在 | 保持 R1 | `call '{"model":"ghost","messages":[]}'` | 404 `NMX130002` |
| R11 | 路由未发布 | `h2sql "UPDATE mux_route SET status='DRAFT', published_route_spec=NULL WHERE route_id='rt-e2e'"` | `call "$BODY"` | 404 `NMX130002`（未发布即不可见）；测完用 R1 spec 恢复 |

> R8 注意：健康状态在 Runtime 进程内存中，从 UNAVAILABLE 恢复需探活成功 1 轮；若用例顺序颠倒请先重启 runtime 进程（`./bin/shutdown.sh` 后再 startup）重置内存健康视图。

## 2. 客户端配置项用例

前置：路由恢复为 R1 spec；每项前 `reset_client` 按需设置（参数：quota / rate / status / expires / routes）。

| # | 配置项 | 设置 | 操作 | 预期 |
|---|--------|------|------|------|
| C1 | AppKey 鉴权 | `reset_client - -` | `call "$BODY" NONE`；`call "$BODY" gw-sk-wrong`；`call "$BODY"` | 401 `NMX040002`；401；200 |
| C2 | 客户端停用 | `reset_client - - DISABLED` | `call "$BODY"` | 401 `NMX040002` |
| C3 | 有效期 | `reset_client - - ENABLED '2020-01-01 00:00:00'` | `call "$BODY"` | 401 `NMX040002`（已过期） |
| C4 | 路由授权 | `reset_client - - ENABLED - '["rt-other"]'` | `call "$BODY"` | 403 `NMX040003` |
| C5 | 配额 lifetime/hard/REQUEST | `reset_client '[{"name":"q","quotaType":"lifetime","metric":"REQUEST","limit":2,"mode":"hard","status":"enabled"}]' -` | `call "$BODY"` ×3 | 前 2 次 200，第 3 次 429 `NMX100001` |
| C6 | 配额 soft | 同上但 `limit:0, mode:"soft"` | ×2 | 200；`last_trace` 含 `softQuotaTriggered` |
| C7 | 配额 cyclic(day) | C5 spec 改 `quotaType:"cyclic","cycleUnit":"day", limit:1` | ×2 | 第 2 次 429 |
| C8 | 配额 TOTAL_TOKEN | `metric:"TOTAL_TOKEN", limit:20, mode:"hard"`（每次调用消耗 18） | ×3 | 第 1、2 次 200（事后回填语义：事前比较 used<limit，无在途预留；第 2 次前 used=18<20 放行），第 3 次 429（used=36≥20） |
| C9 | 配额规则停用 | C5 spec 改 `status:"disabled"` | ×3 | 全部 200 |
| C10 | 限流 RPM | `reset_client - '[{"name":"r","limitType":"RPM","limit":2,"scopeAll":true,"status":"enabled"}]'` | ×3 | 第 3 次 429 `NMX100002` |
| C11 | 限流 burst | 同上 `limit:1,"burst":1` | ×3 | 前 2 次 200，第 3 次 429 |
| C12 | 限流按路由 scope | `limit:0, scopeAll:false, scopeRoutes:["rt-other"]` | ×2 | 全部 200（不作用于 rt-e2e） |
| C13 | 限流规则停用 | C10 spec 改 `status:"disabled"` | ×3 | 全部 200 |
| C14 | 并发 CONCURRENT | `{"name":"c","limitType":"CONCURRENT","limit":1,...}` | 两个 `call` 并行（`&` 同时发） | 一个 200 一个 429（上游桩较快，若都 200 可接受并标注「窗口过短未命中并发」） |

## 3. 观测性断言（贯穿用例）

> **跨用例计数约束**（实现语义，执行时留意）：
> - 配额桶键 = clientId + 规则(name|metric|quotaType) + 窗口起点，**不含 mode**；同名规则跨用例（如 C5→C6）共享计数，建议各用例用不同 name 或重启 runtime。
> - 限流分钟窗口为固定窗且先自增后比较（被拒请求也计数）；C10→C11 同分钟执行会互相污染，跨分钟或换规则名。
> - `reset_client` 清 `mux_request_log` 但**不重置进程内配额/限流计数与健康视图**；如需干净状态重启 runtime 进程。

| # | 断言 | 命令 |
|---|------|------|
| D1 | 成功调用落 `mux_request_log`：`source=RUNTIME`、`status=SUCCESS`、`input_tokens=12`、`output_tokens=6`、`cost_usd=0.0003` | `last_log` |
| D2 | `route_trace` 含 `strategy` / `candidateModelCode` / `poolSize` / `healthFilterApplied` / `stickyApplied` / `binding` | `last_trace` |
| D3 | 失败调用（429 除外，前置拒绝不落日志）也落 `ERROR` 行（如 R8 false 分支的 502） | `last_log` |
| D4 | 探活结果落 `mux_binding_health`（R8 后 `SELECT provider_id, health_status FROM mux_binding_health`：pv-e2e-dead=UNAVAILABLE；注意探活按周期 upsert，`reset_logs` 删行后需等一个探活周期才回写） | h2sql |

## 4. 报告格式

按 `| 用例 | 结果(PASS/FAIL/SKIP) | 实际观察 | 备注 |` 输出表格，附环境（runtime 端口、commit）；FAIL 项附请求/响应原文与 `last_log`/`last_trace` 输出。

## 5. 清理

```bash
cd $E2E_APP_DIR && ./bin/shutdown.sh
pkill -f upstream-stub.py
rm -rf /tmp/nexmux-e2e
```

## 6. Console 联动验证（可选，验证「Console 能看到 Runtime 日志」的最后一环）

把 E2E runtime 挂到开发 Console 同一个库（会向开发库临时写入 e2e- 前缀 fixture 与 1–2 行 RUNTIME 日志，验证后必须 `cleanup_fixtures` 精确清除）：

```bash
export E2E_REPO=/Users/mac/worker/code/my-ai/innospots/nexmux
export E2E_APP_DIR=/tmp/nexmux-e2e/nexmux-spring
export JAVA_HOME=$(/usr/libexec/java_home)
# 指向开发库（Console :8082 正在使用的文件；若 Console 从别处启动请相应调整）
export SHARED_DB='jdbc:h2:file:/Users/mac/worker/code/my-ai/innospots/nexmux/out/db/nexus-platform-console;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;AUTO_SERVER=TRUE'
export E2E_DB_URL_OVERRIDE="$SHARED_DB"

mkdir -p /tmp/nexmux-e2e && tar -xzf $E2E_REPO/dist/nexmux-spring.tar.gz -C /tmp/nexmux-e2e
# 追加 §0 第 2 步加速配置后：
export SPRING_DATASOURCE_URL="$SHARED_DB"
nohup python3 $E2E_REPO/scripts/runtime-e2e/upstream-stub.py 18099 > /tmp/nexmux-e2e/upstream.log 2>&1 &
cd $E2E_APP_DIR && ./bin/startup.sh "local,runtime"

source $E2E_REPO/scripts/runtime-e2e/env.sh
sleep 15 && seed_fixtures && wait_refresh 5

# 发一次真实调用
call '{"model":"e2e-route","messages":[{"role":"user","content":"hi"}]}'

# D5：Console API 层断言（不再是数据库直查）
# 注意：/call-logs 仅支持 start/end/status 等过滤（无分页参数），不传会全量返回
curl -s "http://localhost:8082/api/d/call-logs?start=$(date +%F)&end=$(date +%F)" | grep -o 'rq-[a-z0-9]*' | head -2
# 预期：返回中出现 rq- 前缀（RUNTIME）行，clientCode=e2e-app；:9011 调用日志页同可见
# 注：runtime 进程 spring.sql.init.mode=never（DDL/种子归 Console）；E2E 须先起 Console 建库（含 mux_binding_health）

# 验证后精确清理（必须）
cleanup_fixtures
```
