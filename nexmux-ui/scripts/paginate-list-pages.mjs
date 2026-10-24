#!/usr/bin/env node
/**
 * 列表页分页化改造（一次性转换，保留作工具备查）：
 * - state 增加 page；dataSource 参数增加 page/pageSize
 * - 含「setState + reload(该数据源)」的事件列表自动补 page=1 重置（筛选/搜索回第一页）
 * - 卡片页：Grid 中 N 个硬编码 *CardLink 槽位（items[0..N]）→ 单个 For 循环组件
 *   （模板取槽位 0，`data.<ds>.items[N]` 深替换为 row 作用域），Grid 后插入 Pagination
 * - 表格页（client-list）：Table 保持 pagination: false，表卡后插入分页 Pagination
 * - 「显示 x / y」计数文案 → 「共 N 条」（total 语义随分页改为筛选后总数）
 *
 * 用法：node scripts/paginate-list-pages.mjs
 */
import { readFileSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import { parseDocument, stringify } from "yaml";

const YAML_DIR = new URL("../src/server/yaml", import.meta.url).pathname;

const PAGES = [
  { file: "provider/provider-list.yaml", ds: "providers", link: "ProviderCardLink", pageSize: 12 },
  { file: "model/model-list.yaml", ds: "models", link: "ModelCardLink", pageSize: 12 },
  { file: "route/route-list.yaml", ds: "routes", link: "RouteCardLink", pageSize: 12 },
  { file: "client/client-template-list.yaml", ds: "templates", link: "TemplateCardLink", pageSize: 12 },
  { file: "client/client-list.yaml", ds: "clients", pageSize: 10 },
];

/** 深度遍历 DSL 节点树 */
function walk(node, visit) {
  if (Array.isArray(node)) return node.forEach((n) => walk(n, visit));
  if (node && typeof node === "object") {
    visit(node);
    for (const v of Object.values(node)) walk(v, visit);
  }
}

/** 深替换字符串值中的 items[N] 引用为 row 作用域 */
function rebindRow(node, ds) {
  const re = new RegExp(`data\\.${ds}\\.items\\[\\d+\\]`, "g");
  if (typeof node === "string") return node.replace(re, "row");
  if (Array.isArray(node)) return node.map((v) => rebindRow(v, ds));
  if (node && typeof node === "object") {
    return Object.fromEntries(
      Object.entries(node).map(([k, v]) => [k, rebindRow(v, ds)]),
    );
  }
  return node;
}

/** 筛选/搜索事件（setState + reload 同列表）补 page=1 重置 */
function addPageReset(body, ds) {
  let count = 0;
  walk(body, (node) => {
    if (!node.events || typeof node.events !== "object") return;
    for (const actions of Object.values(node.events)) {
      if (!Array.isArray(actions)) continue;
      const hasSetState = actions.some((a) => a?.action === "setState");
      const reloadIdx = actions.findIndex(
        (a) => a?.action === "reload" && a?.params?.dataSource === ds,
      );
      const already = actions.some(
        (a) => a?.action === "setState" && a?.params && "page" in a.params,
      );
      if (hasSetState && reloadIdx >= 0 && !already) {
        actions.splice(reloadIdx, 0, { action: "setState", params: { page: 1 } });
        count++;
      }
    }
  });
  return count;
}

/** 在 parentChildren 中定位含谓词命中的直接子节点下标 */
function findChild(parentChildren, match) {
  for (let i = 0; i < (parentChildren ?? []).length; i++) {
    let hit = false;
    walk(parentChildren[i], (n) => {
      if (match(n)) hit = true;
    });
    if (hit) return i;
  }
  return -1;
}

function paginationNode(ds, pageSize) {
  return {
    type: "Pagination",
    props: {
      total: `\${data.${ds}.total}`,
      pageSize,
      page: "${state.page}",
      className: "mt-4",
    },
    events: {
      onChange: [
        { action: "setState", params: { page: "${event}" } },
        { action: "reload", params: { dataSource: ds } },
      ],
    },
  };
}

let converted = 0;
for (const cfg of PAGES) {
  const path = join(YAML_DIR, cfg.file);
  const doc = parseDocument(readFileSync(path, "utf8"));
  if (doc.errors.length > 0) {
    console.error(`${cfg.file} 解析失败: ${doc.errors[0].message.split("\n")[0]}`);
    process.exit(1);
  }
  const dsl = doc.toJS();
  const dsCfg = dsl.dataSources?.[cfg.ds];
  if (!dsl.state || !dsCfg?.request) {
    console.error(`${cfg.file}: 缺 state 或 dataSources.${cfg.ds}.request`);
    process.exit(1);
  }

  dsl.state.page = 1;
  dsCfg.request.params.page = "${state.page}";
  dsCfg.request.params.pageSize = cfg.pageSize;

  const resets = addPageReset(dsl.body, cfg.ds);

  // 计数文案：显示 x / y → 共 N 条
  let textFixed = 0;
  walk(dsl.body, (n) => {
    const t = n?.props?.text;
    if (typeof t === "string" && t.includes(`\${data.${cfg.ds}.items.length}`)) {
      n.props.text = `共 \${data.${cfg.ds}.total} 条`;
      textFixed++;
    }
  });

  if (cfg.link) {
    // 卡片页：Grid 内 *CardLink 槽位 → For 模板
    const pageChildren = dsl.body.children;
    const gridIdx = findChild(pageChildren, (n) => n?.type === cfg.link);
    if (gridIdx < 0) {
      console.error(`${cfg.file}: 未找到含 ${cfg.link} 的 Grid`);
      process.exit(1);
    }
    const grid = pageChildren[gridIdx];
    const slots = grid.children.filter((c) => c?.type === cfg.link);
    const template = rebindRow(slots[0], cfg.ds);
    delete template.when;
    grid.children = [
      ...grid.children.filter((c) => c?.type !== cfg.link),
      {
        type: "For",
        props: { items: `\${data.${cfg.ds}.items}`, keyField: "code" },
        children: [template],
      },
    ];
    pageChildren.splice(gridIdx + 1, 0, paginationNode(cfg.ds, cfg.pageSize));
    console.log(
      `${cfg.file}: ${slots.length} 槽位 → For 模板；page 重置 ${resets} 处；文案 ${textFixed} 处`,
    );
  } else {
    // 表格页：表卡后插入分页
    const pageChildren = dsl.body.children;
    const cardIdx = findChild(pageChildren, (n) => n?.type === "Table");
    if (cardIdx < 0) {
      console.error(`${cfg.file}: 未找到含 Table 的 Card`);
      process.exit(1);
    }
    pageChildren.splice(cardIdx + 1, 0, paginationNode(cfg.ds, cfg.pageSize));
    console.log(`${cfg.file}: 表格分页插入；page 重置 ${resets} 处；文案 ${textFixed} 处`);
  }

  writeFileSync(path, stringify(dsl), "utf8");
  converted++;
}
console.log(`已完成 ${converted} 个页面的分页化改造`);
