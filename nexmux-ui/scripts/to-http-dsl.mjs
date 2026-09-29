#!/usr/bin/env node
/**
 * 将 src/server/yaml/pages/*.page.yaml 中的 service 数据源改为 http 数据源：
 *   { type: service, service: provider.list, params: {...} }
 *   → { type: http, request: { method: GET, url: /api/data/providers, params: {...} } }
 *
 * URL 约定（app/api/data/[...parts]/route.ts 按同一张表路由到 mock 实现；
 * 接入真实后端时整体替换 URL 前缀或改写该路由表）：
 * - 列表 / 统计类：查询参数原样保留（params 序列化为 query string）
 * - 详情 / 子资源类：code / id 提升为路径段，params 随之移除
 */
import { readFileSync, readdirSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import { parseDocument, stringify } from "yaml";

const PAGES_DIR = new URL("../src/server/yaml/pages", import.meta.url).pathname;

/** service 名 → URL（${route.params.*} 表达式在客户端渲染期求值） */
const SERVICE_URLS = {
  "dashboard.query": "/api/data/dashboard",
  "provider.list": "/api/data/providers",
  "provider.detail": "/api/data/providers/${route.params.code}",
  "provider.pullModels": "/api/data/providers/${route.params.code}/pull-models",
  "model.list": "/api/data/models",
  "model.detail": "/api/data/models/${route.params.code}",
  "route.list": "/api/data/routes",
  "route.detail": "/api/data/routes/${route.params.code}",
  "client.list": "/api/data/clients",
  "client.detail": "/api/data/clients/${route.params.code}",
  "clientTemplate.list": "/api/data/client-templates",
  "clientTemplate.detail": "/api/data/client-templates/${route.params.code}",
  "clientTemplate.createdClients":
    "/api/data/client-templates/${route.params.code}/created-clients",
  "callLog.list": "/api/data/call-logs",
  "callLog.detail": "/api/data/call-logs/${route.params.id}",
  "cost.list": "/api/data/costs",
  "cost.detail": "/api/data/costs/${route.params.id}",
  "audit.list": "/api/data/audit",
  "audit.detail": "/api/data/audit/${route.params.id}",
  "audit.operators": "/api/data/audit-operators",
  "ops.modelUsage": "/api/data/ops/model-usage",
  "ops.providerUsage": "/api/data/ops/provider-usage",
  "ops.clientUsage": "/api/data/ops/client-usage",
  "ops.availability": "/api/data/ops/provider-availability",
};

let converted = 0;
for (const file of readdirSync(PAGES_DIR).filter((f) => f.endsWith(".page.yaml"))) {
  const path = join(PAGES_DIR, file);
  const doc = parseDocument(readFileSync(path, "utf8"));
  if (doc.errors.length > 0) {
    console.error(`${file} 解析失败: ${doc.errors[0].message.split("\n")[0]}`);
    process.exit(1);
  }
  const dsl = doc.toJS();
  let changed = false;
  for (const ds of Object.values(dsl.dataSources ?? {})) {
    if (ds?.type !== "service") continue;
    const url = SERVICE_URLS[ds.service];
    if (!url) {
      console.error(`${file}: 未知 service ${ds.service}`);
      process.exit(1);
    }
    const pathParam = url.includes("${route.params.");
    ds.type = "http";
    delete ds.service;
    // 路径段已携带 code / id，params 不再需要
    ds.request = {
      method: "GET",
      url,
      ...(pathParam ? {} : ds.params ? { params: ds.params } : {}),
    };
    delete ds.params;
    changed = true;
  }
  if (changed) {
    writeFileSync(path, stringify(dsl), "utf8");
    converted++;
  }
}
console.log(`已将 ${converted} 个页面的 service 数据源改为 http`);
