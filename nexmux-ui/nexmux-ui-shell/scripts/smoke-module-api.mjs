#!/usr/bin/env node
/**
 * 模块 API 运行时冒烟：验证 /api/modules/nexmux/* 分发契约。
 * 用法：SHELL_BASE_URL=http://127.0.0.1:9020 node scripts/smoke-module-api.mjs
 */
const BASE = process.env.SHELL_BASE_URL ?? "http://127.0.0.1:9020";
const DATA = `${BASE}/api/modules/nexmux/d/providers`;

const failures = [];

function check(name, condition, detail = "") {
  if (condition) {
    console.log(`  ✓ ${name}`);
  } else {
    failures.push(`${name}${detail ? `（${detail}）` : ""}`);
    console.log(`  ✗ ${name}${detail ? `（${detail}）` : ""}`);
  }
}

async function main() {
  console.log(`[smoke] ${BASE}`);

  const providers = await fetch(DATA);
  check("未登录 GET /d/providers → 401", providers.status === 401, `status=${providers.status}`);

  const head = await fetch(DATA, { method: "HEAD" });
  check("HEAD 回退 GET → 401", head.status === 401, `status=${head.status}`);

  const options = await fetch(DATA, { method: "OPTIONS" });
  const allow = options.headers.get("allow") ?? "";
  check("OPTIONS → 204", options.status === 204, `status=${options.status}`);
  check("Allow 含 GET", allow.split(",").map((item) => item.trim()).includes("GET"), allow);

  const unknownModule = await fetch(`${BASE}/api/modules/missing/d/providers`);
  check("未知模块 → 404", unknownModule.status === 404, `status=${unknownModule.status}`);

  const unknownPath = await fetch(`${BASE}/api/modules/nexmux/nope`);
  check("前缀外路径 → 404", unknownPath.status === 404, `status=${unknownPath.status}`);

  if (failures.length > 0) {
    console.error(`\n${failures.length} failed`);
    process.exit(1);
  }
  console.log("\nsmoke ok");
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
