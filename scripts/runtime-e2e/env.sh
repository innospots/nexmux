#!/usr/bin/env bash
# Nexmux Runtime E2E 环境工具函数。用法：source 本文件后调用函数。
#
# 前置：
#   export E2E_APP_DIR=<解包后的 nexmux-spring 目录>   # 内含 bin/ config/ lib/
#   export E2E_REPO=<nexmux 仓库根>                     # 本脚本所在仓库
#
# 固定设施（fixture）：
#   上游桩        http://127.0.0.1:18099/v1（upstream-stub.py；model 回显 = 命中断言依据）
#   死上游        http://127.0.0.1:18098/v1（无进程监听，用于不健康场景）
#   Provider      pv-e2e（18099, ENABLED, sk-e2e） / pv-e2e-dead（18098, ENABLED, sk-e2e-dead）
#   Model         e2e/model-a → pv-e2e:stub-a；e2e/model-b → pv-e2e:stub-b；e2e/model-dead → pv-e2e-dead:stub-x
#   Route         rt-e2e / alias=e2e-route / ingress=/v1/chat/completions（spec 由 reset_route 注入）
#   Client        cl-e2e / client_code=e2e-app / AppKey 明文 gw-sk-e2e（spec 由 reset_client 注入）
#
# Runtime 地址：http://127.0.0.1:8083（profile=local,runtime）
#
# ⚠️ 数据库共用陷阱（ADR-0002 本地单库形态）：application-local.yaml 的 datasource 是
# 相对路径 ./out/db（相对进程 CWD）。从不同目录启动 runtime 会拿到**另一个库文件**，
# Console（:8082）将永远看不到 E2E 的运行日志。需要联动验证时，用
# E2E_DB_URL_OVERRIDE + SPRING_DATASOURCE_URL 把两个进程指向同一绝对路径库文件
# （H2 AUTO_SERVER=TRUE 支持多 JVM 共写）。

E2E_DB_URL="${E2E_DB_URL_OVERRIDE:-jdbc:h2:file:${E2E_APP_DIR}/out/db/nexus-platform-console;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;AUTO_SERVER=TRUE}"
E2E_H2_JAR=$(ls "${E2E_APP_DIR}"/lib/h2-*.jar | head -1)
E2E_RUNTIME="http://127.0.0.1:8083"
E2E_KEY="gw-sk-e2e"
E2E_KEY_HASH="1o+5fAtxXna++V3TlrkarNrQbAj6E3wueb1eD1JCMyk="
E2E_KEY_SALT="e2e-salt-0001"

h2sql() {
  "$JAVA_HOME/bin/java" -cp "$E2E_H2_JAR" org.h2.tools.Shell \
    -url "$E2E_DB_URL" -user sa -sql "$1" 2>&1 | grep -v "^$" | tail -3
}

# 一次性种入 provider / model fixture（幂等：先删后插）
seed_fixtures() {
  h2sql "
DELETE FROM mux_request_log WHERE client_id='cl-e2e';
DELETE FROM mux_route WHERE route_id='rt-e2e';
DELETE FROM mux_client WHERE client_id='cl-e2e';
DELETE FROM mux_model WHERE model_id IN ('md-e2e-a','md-e2e-b','md-e2e-dead');
DELETE FROM mux_provider_credential WHERE provider_id IN ('pv-e2e','pv-e2e-dead');
DELETE FROM mux_provider WHERE provider_id IN ('pv-e2e','pv-e2e-dead');
DELETE FROM mux_binding_health WHERE provider_id IN ('pv-e2e','pv-e2e-dead');
INSERT INTO mux_provider (provider_id, provider_code, display_name, provider_type, default_protocol, description, vendor_code, source_vendor_code, base_url, timeout_ms, status, created_at, updated_at, created_by, updated_by)
VALUES ('pv-e2e','e2e','E2E Stub','CLOUD','openai-completions','e2e','e2e','e2e','http://127.0.0.1:18099/v1',5000,'ENABLED',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'e2e','e2e');
INSERT INTO mux_provider (provider_id, provider_code, display_name, provider_type, default_protocol, description, vendor_code, source_vendor_code, base_url, timeout_ms, status, created_at, updated_at, created_by, updated_by)
VALUES ('pv-e2e-dead','e2e-dead','E2E Dead','CLOUD','openai-completions','e2e dead','e2e','e2e','http://127.0.0.1:18098/v1',1000,'ENABLED',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'e2e','e2e');
INSERT INTO mux_provider_credential (credential_id, provider_id, credential_mode, ciphertext, key_version, created_at, updated_at, created_by, updated_by)
VALUES ('cr-e2e','pv-e2e','API_KEY','sk-e2e',1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'e2e','e2e');
INSERT INTO mux_provider_credential (credential_id, provider_id, credential_mode, ciphertext, key_version, created_at, updated_at, created_by, updated_by)
VALUES ('cr-e2e-dead','pv-e2e-dead','API_KEY','sk-e2e-dead',1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'e2e','e2e');
INSERT INTO mux_model (model_id, model_code, display_name, model_type, status, primary_provider_id, primary_provider_model_id, primary_binding_weight, primary_binding_enabled, extra_bindings, pricing_spec, created_at, updated_at, created_by, updated_by)
VALUES ('md-e2e-a','e2e/model-a','E2E Model A','CHAT','ENABLED','pv-e2e','stub-a',100,TRUE,'[]','{\"currency\":\"USD\",\"input\":10,\"output\":30}',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'e2e','e2e');
INSERT INTO mux_model (model_id, model_code, display_name, model_type, status, primary_provider_id, primary_provider_model_id, primary_binding_weight, primary_binding_enabled, extra_bindings, pricing_spec, created_at, updated_at, created_by, updated_by)
VALUES ('md-e2e-b','e2e/model-b','E2E Model B','CHAT','ENABLED','pv-e2e','stub-b',100,TRUE,'[]','{\"currency\":\"USD\",\"input\":10,\"output\":30}',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'e2e','e2e');
INSERT INTO mux_model (model_id, model_code, display_name, model_type, status, primary_provider_id, primary_provider_model_id, primary_binding_weight, primary_binding_enabled, extra_bindings, pricing_spec, created_at, updated_at, created_by, updated_by)
VALUES ('md-e2e-dead','e2e/model-dead','E2E Model Dead','CHAT','ENABLED','pv-e2e-dead','stub-x',100,TRUE,'[]',NULL,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'e2e','e2e');
"
}

# 重置路由发布快照。$1 = published_route_spec JSON 字符串（单行）。
reset_route() {
  local spec="$1"
  h2sql "DELETE FROM mux_route WHERE route_id='rt-e2e';
INSERT INTO mux_route (route_id, alias, route_code, display_name, status, route_spec, published_route_spec, published_at, published_by, created_at, updated_at, created_by, updated_by)
VALUES ('rt-e2e','e2e-route','e2e-route','E2E Route','PUBLISHED','{}','${spec}',CURRENT_TIMESTAMP,'e2e',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'e2e','e2e');"
}

# 重置客户端。$1=quota_spec（'-' 为 NULL）；$2=rate_limit_spec（'-' 为 NULL）；
# $3=status（默认 ENABLED）；$4=expires_at（'-' 为 NULL，否则如 '2020-01-01 00:00:00'）；
# $5=authorized_route_ids JSON（默认 ["rt-e2e"]）
reset_client() {
  local quota="$1" rate="$2" status="${3:-ENABLED}" expires="${4:--}" routes="${5:-[\"rt-e2e\"]}"
  [ "$quota" = "-" ] && quota="NULL" || quota="'${quota}'"
  [ "$rate" = "-" ] && rate="NULL" || rate="'${rate}'"
  [ "$expires" = "-" ] && expires="NULL" || expires="'${expires}'"
  h2sql "DELETE FROM mux_client WHERE client_id='cl-e2e';
INSERT INTO mux_client (client_id, client_code, display_name, status, app_key_hash, app_key_salt, app_key_version, authorized_route_ids, quota_spec, rate_limit_spec, expires_at, created_at, updated_at, created_by, updated_by)
VALUES ('cl-e2e','e2e-app','E2E App','${status}','${E2E_KEY_HASH}','${E2E_KEY_SALT}',1,'${routes}',${quota},${rate},${expires},CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'e2e','e2e');"
  h2sql "DELETE FROM mux_request_log WHERE client_id='cl-e2e';"
}

# 清 E2E 运行数据（request_log / binding_health）
reset_logs() {
  h2sql "DELETE FROM mux_request_log WHERE client_id='cl-e2e';
DELETE FROM mux_binding_health WHERE provider_id IN ('pv-e2e','pv-e2e-dead');"
}

# 精确清除全部 E2E fixture（含日志与健康行）；Console 联动验证后必须执行
cleanup_fixtures() {
  h2sql "DELETE FROM mux_request_log WHERE client_id='cl-e2e' OR request_id LIKE 'rq-%' AND route_id='rt-e2e';
DELETE FROM mux_binding_health WHERE provider_id IN ('pv-e2e','pv-e2e-dead');
DELETE FROM mux_route WHERE route_id='rt-e2e';
DELETE FROM mux_client WHERE client_id='cl-e2e';
DELETE FROM mux_model WHERE model_id IN ('md-e2e-a','md-e2e-b','md-e2e-dead');
DELETE FROM mux_provider_credential WHERE provider_id IN ('pv-e2e','pv-e2e-dead');
DELETE FROM mux_provider WHERE provider_id IN ('pv-e2e','pv-e2e-dead');"
}

# 等待快照刷新（配置已调到 2s 时传 3 即可）
wait_refresh() { sleep "${1:-4}"; }

# 调用 runtime。$1=body JSON；$2=key（缺省 gw-sk-e2e；'NONE' 表示不带凭证头）
# 输出：<http_code> <body 第一行>
call() {
  local body="$1" key="${2:-$E2E_KEY}"
  local args=(-s -X POST "${E2E_RUNTIME}/v1/chat/completions" -H "Content-Type: application/json" -d "$body")
  if [ "$key" != "NONE" ]; then
    args+=(-H "Authorization: Bearer ${key}")
  fi
  local out resp
  resp=$(mktemp /tmp/e2e-resp.XXXXXX)
  out=$(curl -s -o "$resp" -w "%{http_code}" "${args[@]}")
  echo "HTTP $out $(head -c 300 "$resp" | tr '\n' ' ')"
  rm -f "$resp"
}

# 查最近一条 E2E 调用日志的关键列
last_log() {
  h2sql "SELECT request_id, source, status, error_code, http_status, input_tokens, output_tokens, cost_usd FROM mux_request_log WHERE client_id='cl-e2e' ORDER BY requested_at DESC LIMIT 1" 2>/dev/null | tail -4
}

# 查最近一条日志的 route_trace
last_trace() {
  h2sql "SELECT route_trace FROM mux_request_log WHERE client_id='cl-e2e' ORDER BY requested_at DESC LIMIT 1" 2>/dev/null | tail -2
}

# E2E 调用日志行数
log_count() {
  h2sql "SELECT COUNT(*) FROM mux_request_log WHERE client_id='cl-e2e'" 2>/dev/null | tail -2
}
