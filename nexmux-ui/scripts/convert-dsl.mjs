#!/usr/bin/env node
/**
 * 将 model-console 提取的静态页面 DSL（public/dsl/*.json）转换为本项目的
 * 页面 YAML（src/server/yaml/pages/<id>.page.yaml）。
 *
 * 转换规则：
 * - 字符串中的路由参数占位 `:code` / `:id` → `${route.params.code}` / `${route.params.id}`
 * - 其余结构原样保留（组件 / 表达式 / 事件 / 数据源均与 DSL 1.0 兼容）
 *
 * 用法：node scripts/convert-dsl.mjs [model-console 仓库路径]
 */
import { readFileSync, readdirSync, writeFileSync, mkdirSync } from "node:fs";
import { join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { stringify } from "yaml";

const projectRoot = resolve(fileURLToPath(import.meta.url), "../..");
const modelConsole =
  process.argv[2] ?? resolve(projectRoot, "../../model-console");
const SRC_DIR = join(modelConsole, "public/dsl");
const OUT_DIR = join(projectRoot, "src/server/yaml/pages");

/** 页面 id → 路由路径（sitemap.yaml 的 pages 须与此一致）；login 用项目自带登录页，不转换 */
export const PAGE_ROUTES = {
  dashboard: "/",
  "provider-list": "/providers",
  "provider-create": "/providers/new",
  "provider-detail": "/providers/:code",
  "model-list": "/models",
  "model-create": "/models/new",
  "model-detail": "/models/:code",
  "route-list": "/routes",
  "route-create": "/routes/new",
  "route-detail": "/routes/:code",
  "client-list": "/clients",
  "client-create": "/clients/new",
  "client-detail": "/clients/:code",
  "client-template-list": "/client-templates",
  "client-template-create": "/client-templates/new",
  "client-template-detail": "/client-templates/:code",
  "call-log-list": "/call-logs",
  "call-log-detail": "/call-logs/:id",
  "cost-list": "/costs",
  "cost-detail": "/costs/:id",
  "audit-list": "/audit",
  "audit-detail": "/audit/:id",
  "model-usage": "/model-usage",
  "provider-usage": "/provider-usage",
  "client-usage": "/client-usage",
  "provider-availability": "/provider-availability",
  "client-debug": "/client-debug",
  "model-debug": "/model-debug",
  "service-settings": "/service-settings",
};

/** 编辑页为运行时记录驱动（buildFormPage('edit', record)），无法静态转换，
 * 由 *-edit.page.yaml 手工维护（create 结构 + detail 数据源回填 initialValues）；
 * edit-404 的内容已内联进各编辑页的 notFound 分支，均不转换 */
const SKIP = new Set(["login", "shell"]);

/** 路由参数占位 → route.params 表达式 */
function replaceRouteParams(value) {
  return value
    .replaceAll(":code", "${route.params.code}")
    .replaceAll(":id", "${route.params.id}");
}

function walk(node) {
  if (typeof node === "string") return replaceRouteParams(node);
  if (Array.isArray(node)) return node.map(walk);
  if (node && typeof node === "object") {
    return Object.fromEntries(
      Object.entries(node).map(([k, v]) => [k, walk(v)]),
    );
  }
  return node;
}

mkdirSync(OUT_DIR, { recursive: true });

let converted = 0;
for (const file of readdirSync(SRC_DIR).filter((f) => f.endsWith(".json"))) {
  const dsl = JSON.parse(readFileSync(join(SRC_DIR, file), "utf8"));
  const id = dsl.page?.id;
  if (!id || SKIP.has(id) || id.endsWith("-edit-404")) continue;
  if (!PAGE_ROUTES[id]) {
    console.error(`未配置路由的页面: ${id}（${file}）`);
    process.exit(1);
  }
  const out = join(OUT_DIR, `${id}.page.yaml`);
  writeFileSync(out, stringify(walk(dsl)), "utf8");
  converted++;
}
console.log(`已转换 ${converted} 个页面 → ${OUT_DIR}`);
