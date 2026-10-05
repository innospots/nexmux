#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""从 pi-ai 模型 catalog 生成 nexmux 提供商/模型 H2 初始化脚本。

数据来源（任选其一，按优先级）：
  1. pi 仓库生成的 catalog：  packages/ai/src/providers/data/*.json
     （在 pi 仓库执行 `npm run generate-models` 后生成）
  2. 已安装 pi-ai 发行包内置 catalog：
     @earendil-works/pi-coding-agent/node_modules/@earendil-works/pi-ai/dist/providers/data/*.json

输出：nexmux-spring/src/main/resources/nexus/schema/nexmux-catalog-data-h2.sql
  - mux_provider / mux_model 种子数据
  - 使用 H2 MERGE INTO ... KEY(...) 幂等写入，可随 spring.sql.init.mode=always 重复执行

用法：
  python3 nexmux-spring/bin/../../../scripts/generate-pi-catalog-h2-sql.py [pi-repo-root] [data-dir]
"""
import hashlib
import json
import glob
import os
import re
import sys

PI_REPO = sys.argv[1] if len(sys.argv) > 1 else "/Users/mac/worker/code/ai-example/pi/pi"
INSTALLED_DATA = ("/Users/mac/.local/share/fnm/node-versions/v25.8.1/installation/lib/node_modules/"
                  "@earendil-works/pi-coding-agent/node_modules/@earendil-works/pi-ai/dist/providers/data")

REPO_DATA = os.path.join(PI_REPO, "packages/ai/src/providers/data")
DATA_DIR = sys.argv[2] if len(sys.argv) > 2 else (REPO_DATA if os.path.isdir(REPO_DATA) else INSTALLED_DATA)

OUT_SQL = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                       "../nexmux-spring/src/main/resources/nexus/schema/nexmux-catalog-data-h2.sql")

# pi provider -> mux provider_type（COMMERCIAL / CLOUD / PRIVATE / CUSTOM）
PROVIDER_TYPE = {
    "amazon-bedrock": "CLOUD",
    "azure-openai-responses": "CLOUD",
    "google-vertex": "CLOUD",
    "cloudflare-workers-ai": "CLOUD",
    "radius": "CUSTOM",      # pi 私有中继网关（api: pi-messages）
    "typesafe": "CUSTOM",    # pi 内置分类/判别提供方
}

# 未能从源码解析到 display name 的补充
EXTRA_NAMES = {
    "radius": "Radius Gateway",
}

AZURE_BASE_URL_PLACEHOLDER = "https://{resource}.openai.azure.com"


def provider_display_names():
    """从 pi 仓库 providers/*.ts 的 createProvider({name: ...}) 解析展示名。"""
    names = dict(EXTRA_NAMES)
    for f in glob.glob(os.path.join(PI_REPO, "packages/ai/src/providers/*.ts")):
        if f.endswith(".models.ts"):
            continue
        src = open(f, encoding="utf-8").read()
        m = re.search(r'createProvider\(\{[^}]*?name:\s*"([^"]+)"', src, re.S)
        if m:
            names[os.path.basename(f)[:-3]] = m.group(1)
    return names


def sql_str(value):
    if value is None:
        return "NULL"
    return "'" + str(value).replace("'", "''") + "'"


def sql_num(value):
    return "NULL" if value is None else str(value)


def row_id(prefix, code):
    return prefix + hashlib.md5(code.encode("utf-8")).hexdigest()[:30]


def model_code(provider, model_id):
    code = f"{provider}/{model_id}"
    if len(code) > 64:
        code = code[:55] + "-" + hashlib.md5(code.encode("utf-8")).hexdigest()[:8]
    return code


def model_type(t):
    return {"chat": "LLM", "classifier": "LLM", "image": "Image",
            "audio": "Audio", "embedding": "Embedding"}.get(t, "LLM")


def capability_tags(m):
    tags = []
    if m.get("reasoning"):
        tags.append("reasoning")
    if "image" in (m.get("input") or []):
        tags.append("vision")
    if m.get("type") == "classifier":
        tags.append("classifier")
    return json.dumps(tags, ensure_ascii=False)


def pricing_spec(m):
    cost = m.get("cost") or {}
    spec = {
        "currency": "USD",
        "unit": "per_million_tokens",
        "input": cost.get("input", 0),
        "output": cost.get("output", 0),
        "cacheRead": cost.get("cacheRead", 0),
        "cacheWrite": cost.get("cacheWrite", 0),
    }
    if cost.get("tiers"):
        spec["tiers"] = cost["tiers"]
    return json.dumps(spec, ensure_ascii=False)


def modal(values):
    """取列表中出现频次最高的值。"""
    freq = {}
    for v in values:
        if v:
            freq[v] = freq.get(v, 0) + 1
    return max(freq, key=freq.get) if freq else None


def emit_insert_only(w, table, key_col, cols, values):
    """只插不更新：已存在的种子行（含控制台修改）跳过。"""
    w(f"MERGE INTO {table} AS t USING (VALUES (")
    w("    " + ", ".join(values))
    w(f")) AS s({', '.join(cols)})")
    w(f"ON t.{key_col} = s.{key_col}")
    w(f"WHEN NOT MATCHED THEN INSERT ({', '.join(cols)})")
    w("    VALUES (" + ", ".join(f"s.{c}" for c in cols) + ");")
    w("")


PROVIDER_COLS = [
    "provider_id", "provider_code", "display_name", "provider_type", "default_protocol",
    "description", "vendor_code", "source_vendor_code", "icon_uri", "base_url",
    "region", "timeout_ms", "proxy", "capability_extension", "extension_config", "status",
    "created_at", "updated_at", "created_by", "updated_by",
]

MODEL_COLS = [
    "model_id", "model_code", "display_name", "model_type", "status",
    "icon_uri", "release_date", "context_length", "max_output_tokens",
    "input_modalities", "output_modalities", "capability_tags", "tags",
    "description", "metadata_spec", "default_params",
    "primary_provider_id", "primary_provider_model_id", "primary_upstream_protocol",
    "primary_binding_weight", "primary_cost_multiplier", "primary_reference_multiplier",
    "primary_binding_enabled", "extra_bindings", "pricing_spec",
    "created_at", "updated_at", "created_by", "updated_by",
]


def main():
    names = provider_display_names()
    files = sorted(glob.glob(os.path.join(DATA_DIR, "*.json")))
    if not files:
        raise SystemExit(f"catalog data not found in {DATA_DIR}")

    providers = {}   # code -> dict
    models = []      # list of dict（按 provider 文件 + catalog 顺序）
    seen_codes = set()

    for f in files:
        prov = os.path.basename(f)[:-5]
        catalog = json.load(open(f, encoding="utf-8"))
        apis, base_urls = [], []
        for api, entries in catalog.items():
            for _, m in entries.items():
                apis.append(m.get("api") or api)
                base_urls.append(m.get("baseUrl"))
                t = m.get("type", "chat")
                code = model_code(prov, m["id"])
                if code in seen_codes:
                    continue
                seen_codes.add(code)
                models.append({
                    "code": code,
                    "name": m["name"],
                    "type": t,
                    "provider": prov,
                    "provider_model_id": m["id"],
                    "protocol": m.get("api") or api,
                    "context": m.get("contextWindow"),
                    "max_out": m.get("maxTokens"),
                    "input_mod": ",".join(m.get("input") or ["text"]),
                    "output_mod": ",".join(m.get("output") or (["image"] if t == "image" else ["text"])),
                    "cap_tags": capability_tags(m),
                    "tags": json.dumps(["pi-ai-catalog", prov]),
                    "pricing": pricing_spec(m),
                    "raw": m,
                })
        providers[prov] = {
            "code": prov,
            "name": names.get(prov, prov.replace("-", " ").title()),
            "type": PROVIDER_TYPE.get(prov, "COMMERCIAL"),
            "protocol": modal(apis),
            "base_url": modal(base_urls) or AZURE_BASE_URL_PLACEHOLDER,
        }

    lines = []
    w = lines.append
    w("-- Nexmux 提供商/模型 catalog 种子数据（H2）")
    w("-- 数据来源：pi-ai model catalog（pi 仓库 packages/ai，catalog 数据源自 models.dev）")
    w(f"-- 生成命令：python3 scripts/generate-pi-catalog-h2-sql.py")
    w(f"-- catalog 目录：{DATA_DIR}")
    w(f"-- 统计：{len(providers)} 个提供商，{len(models)} 个模型")
    w("-- 说明：MERGE ... WHEN NOT MATCHED THEN INSERT，只插不更新；")
    w("--       控制台对种子行的修改（状态/密钥/参数等）重启后保留。")
    w("--       provider_id/model_id 由编码 md5 派生（pv*/md* 前缀），保证多次生成主键稳定。")
    w("")

    for prov in providers.values():
        pid = row_id("pv", prov["code"])
        w(f"-- provider: {prov['code']} ({prov['name']})")
        # ④ 种子供应商默认 DISABLED：未配置凭证前不可调度，控制台配置后手动启用
        emit_insert_only(w, "mux_provider", "provider_id", PROVIDER_COLS, [
            sql_str(pid), sql_str(prov["code"]), sql_str(prov["name"]), sql_str(prov["type"]), sql_str(prov["protocol"]),
            sql_str("Imported from pi-ai model catalog: " + prov["name"]), sql_str(prov["code"]), sql_str(prov["code"]), "NULL", sql_str(prov["base_url"]),
            "NULL", "60000", "NULL", "NULL", "NULL", sql_str("DISABLED"),
            "CURRENT_TIMESTAMP", "CURRENT_TIMESTAMP", sql_str("system"), sql_str("system"),
        ])

    for m in models:
        mid = row_id("md", m["code"])
        pid = row_id("pv", m["provider"])
        # ④ 种子模型默认 DRAFT：与控制台 ModelService.create 初始状态一致，审核后启用
        emit_insert_only(w, "mux_model", "model_id", MODEL_COLS, [
            sql_str(mid), sql_str(m["code"]), sql_str(m["name"]), sql_str(model_type(m["type"])), sql_str("DRAFT"),
            "NULL", "NULL", sql_num(m["context"]), sql_num(m["max_out"]),
            sql_str(m["input_mod"]), sql_str(m["output_mod"]), sql_str(m["cap_tags"]), sql_str(m["tags"]),
            "NULL", "NULL", "NULL",
            sql_str(pid), sql_str(m["provider_model_id"]), sql_str(m["protocol"]),
            "100", "1.0000", "1.0000",
            "TRUE", sql_str("[]"), sql_str(m["pricing"]),
            "CURRENT_TIMESTAMP", "CURRENT_TIMESTAMP", sql_str("system"), sql_str("system"),
        ])

    out = os.path.normpath(OUT_SQL)
    with open(out, "w", encoding="utf-8") as fh:
        fh.write("\n".join(lines) + "\n")
    print(f"written: {out}")
    print(f"providers: {len(providers)}, models: {len(models)}")


if __name__ == "__main__":
    main()
