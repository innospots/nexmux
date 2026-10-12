#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""从 pi-ai 模型 catalog（+ 可选 OctaFuse 覆盖层）生成 nexmux 提供商/模型 H2 初始化脚本。

数据来源（任选其一，按优先级）：
  1. pi 仓库生成的 catalog：  packages/ai/src/providers/data/*.json
     （在 pi 仓库执行 `npm run generate-models` 后生成）
  2. 已安装 pi-ai 发行包内置 catalog：
     @earendil-works/pi-coding-agent/node_modules/@earendil-works/pi-ai/dist/providers/data/*.json

覆盖层（可选，见下方 OCTAFUSE 说明）：从 OctaFuse gateway 的 provider/model preset
补齐 pi-ai 未收录的国内厂商（volcengine / aliyun / tencent / stepfun / meituan，仅 LLM；
生图/语音需厂商原生协议，暂不引入），并把已收厂商的模型行补上中文描述、双币定价、
发布日与模态。

输出：nexmux-spring/src/main/resources/nexus/schema/nexmux-catalog-data-h2.sql
  - mux_provider / mux_model 种子数据
  - 使用 H2 MERGE INTO ... WHEN NOT MATCHED THEN INSERT，只插不更新
    （已落库的表行不会被本脚本覆盖；改种子值后需清库或手工迁移才生效）

用法：
  python3 scripts/generate-pi-catalog-h2-sql.py [pi-repo-root] [data-dir] [--octafuse-lib DIR]

  # 带 OctaFuse 覆盖层
  git clone --depth 1 https://github.com/OctaFuse/octafuse-gateway.git /tmp/octafuse-gateway
  python3 scripts/generate-pi-catalog-h2-sql.py --octafuse-lib /tmp/octafuse-gateway/packages/admin/lib

  # 不带覆盖层（等价于旧行为）
  python3 scripts/generate-pi-catalog-h2-sql.py --no-octafuse
"""
import argparse
import hashlib
import json
import glob
import os
import re
import sys

DEFAULT_PI_REPO = "/Users/mac/worker/code/ai-example/pi/pi"
INSTALLED_DATA = ("/Users/mac/.local/share/fnm/node-versions/v25.8.1/installation/lib/node_modules/"
                  "@earendil-works/pi-coding-agent/node_modules/@earendil-works/pi-ai/dist/providers/data")

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

# ── OctaFuse 覆盖层 ──────────────────────────────────────────────
# OctaFuse gateway（https://github.com/OctaFuse/octafuse-gateway）为 AGPL-3.0，
# 本仓库为 Apache-2.0：其 preset JSON **不并入本仓库**，生成时从外部 clone 读取。
# 目录应为 packages/admin/lib（含 provider-import-presets.json 与 model-presets/）。
OCTAFUSE_LIB_ENV = "NEXMUX_OCTAFUSE_LIB"

# 需要从 OctaFuse 引入的**新增** provider：vendor_key -> 模型 preset 文件
OCTAFUSE_PROVIDER_MODELS = {
    "volcengine": ["bytedance.json", "bytedance-image.json"],
    "aliyun": ["aliyun.json", "aliyun-image.json"],
    "tencent": ["tencent.json"],
    "stepfun": ["stepfun.json"],
    "meituan": ["meituan.json"],
}

# 允许引入的 mux model_type（None = 不限制）。
# volcengine / aliyun 的生图与语音走厂商原生协议（volcengine images / dashscope），
# mux 尚未建模对应 default_protocol，故只引入 LLM；其余厂商 preset 本身只有 LLM。
OCTAFUSE_ALLOWED_TYPES = {
    "volcengine": {"LLM"},
    "aliyun": {"LLM"},
    "tencent": {"LLM"},
    "stepfun": {"LLM"},
    "meituan": {"LLM"},
}

# 已有 pi-ai provider ↔ OctaFuse vendor 对齐：用于给存量模型行补充描述/定价/模态
OCTAFUSE_VENDOR_PROVIDERS = {
    "deepseek": ["deepseek"],
    "anthropic": ["anthropic"],
    "openai": ["openai"],
    "google": ["google"],
    "xai": ["xai"],
    "minimax": ["minimax", "minimax-cn"],
    "moonshot": ["moonshotai", "moonshotai-cn", "kimi-coding"],
    "zhipu": ["zai", "zai-coding-cn"],
    "xiaomi": ["xiaomi", "xiaomi-token-plan-cn", "xiaomi-token-plan-ams", "xiaomi-token-plan-sgp"],
    "aliyun": ["qwen-token-plan", "qwen-token-plan-cn", "qwen-token-plan-individual"],
}

# OctaFuse pricing 分档键 -> nexmux pricing_spec（ModelViewAssembler.tiersOf）分档键
OCTAFUSE_TIER_KEYS = (
    ("input_price", "input"),
    ("output_price", "output"),
    ("cache_read_price", "cacheRead"),
    ("cache_write_price", "cacheWrite"),
)
# nexmux 分档未建模、但需原样保留（生图/语音分档价）
OCTAFUSE_TIER_EXTRA_KEYS = (
    "image_input_price",
    "image_input_cache_price",
    "image_output_price",
    "audio_input_price",
    "audio_output_price",
)

OCTAFUSE_TAG = "octafuse-preset"


def provider_display_names(pi_repo):
    """从 pi 仓库 providers/*.ts 的 createProvider({name: ...}) 解析展示名。"""
    names = dict(EXTRA_NAMES)
    for f in glob.glob(os.path.join(pi_repo, "packages/ai/src/providers/*.ts")):
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


# ── OctaFuse：读取与转换 ──────────────────────────────────────────

def load_octafuse(lib_dir):
    """读取 OctaFuse preset：返回 (vendor_key -> provider preset, [model preset])。"""
    presets_path = os.path.join(lib_dir, "provider-import-presets.json")
    model_dir = os.path.join(lib_dir, "model-presets")
    if not os.path.isfile(presets_path) or not os.path.isdir(model_dir):
        raise SystemExit(f"octafuse lib not found (need provider-import-presets.json + "
                         f"model-presets/): {lib_dir}")
    raw_presets = json.load(open(presets_path, encoding="utf-8"))
    presets = {}
    for p in raw_presets:
        # 同名 vendor_key 有多条（官方 / Coding Plan / Agent Plan / 国际站）：取首条=主站
        presets.setdefault(p["vendor_key"], p)
    models = []
    for f in sorted(glob.glob(os.path.join(model_dir, "*.json"))):
        for m in json.load(open(f, encoding="utf-8")):
            m["_file"] = os.path.basename(f)
            models.append(m)
    return presets, models


def octafuse_strip_endpoint(url):
    """endpoint 全 URL -> base_url（去掉能力后缀）。"""
    for suffix in ("/chat/completions", "/responses", "/images/generations", "/messages"):
        if url.endswith(suffix):
            return url[: -len(suffix)]
    return url


def octafuse_protocol(preset):
    """OctaFuse endpoints -> mux default_protocol。"""
    endpoints = preset.get("endpoints") or {}
    openai = endpoints.get("openai") or {}
    eps = openai.get("endpoints") or {}
    if openai.get("base") or "chat" in eps:
        return "openai-completions"
    if "responses" in eps:
        return "openai-responses"
    if "anthropic" in endpoints:
        return "anthropic-messages"
    return "openai-completions"


def octafuse_base_url(preset):
    """OctaFuse endpoints -> mux base_url。"""
    endpoints = preset.get("endpoints") or {}
    openai = endpoints.get("openai") or {}
    if openai.get("base"):
        return openai["base"]
    for url in (openai.get("endpoints") or {}).values():
        if url:
            return octafuse_strip_endpoint(url)
    for proto in ("anthropic", "gemini", "dashscope", "volcengine", "minimax"):
        block = endpoints.get(proto) or {}
        if block.get("base"):
            return block["base"]
    return None


# mux model_type -> pi-ai 原始 type（emit 统一走 model_type() 映射）
MUX_TO_RAW_TYPE = {"LLM": "chat", "Image": "image", "Audio": "audio", "Embedding": "embedding"}


def octafuse_model_type(preset):
    """按输出/输入模态推断 mux model_type，再回落到 pi-ai 原始 type（OctaFuse 无显式 type）。"""
    modalities = preset.get("modalities") or {}
    inputs = set(modalities.get("input") or [])
    outputs = set(modalities.get("output") or [])
    model_id = (preset.get("id") or "").lower()
    if "image" in outputs:
        mux_type = "Image"
    elif "audio" in outputs:
        mux_type = "Audio"
    elif "audio" in inputs and "text" in outputs:
        # 输入音频、输出文本：ASR/转写类归 Audio，omni 对话类仍归 LLM
        mux_type = "Audio" if any(k in model_id for k in ("asr", "transcri", "whisper", "audio")) else "LLM"
    elif "embed" in model_id:
        mux_type = "Embedding"
    else:
        mux_type = "LLM"
    return MUX_TO_RAW_TYPE[mux_type]


def octafuse_unit(block, mode_key):
    """计费模式 -> pricing_spec.unit（模式本身已含 per_ 前缀时不重复拼接）。"""
    mode = str(block.get(mode_key) or "").strip()
    if not mode:
        return "per_million_tokens"
    return mode if mode.startswith("per_") else "per_" + mode


def octafuse_capability_tags(preset):
    modalities = preset.get("modalities") or {}
    inputs = set(modalities.get("input") or [])
    tags = []
    if "image" in inputs:
        tags.append("vision")
    if "audio" in inputs:
        tags.append("audio")
    return json.dumps(tags, ensure_ascii=False)


def octafuse_tiers(currency_block):
    """OctaFuse 分档 -> nexmux 分档（upto/input/output/cacheRead/cacheWrite + 保留生图语音价）。"""
    out = []
    for tier in currency_block.get("tiers") or []:
        item = {}
        if tier.get("upto") is not None:
            item["upto"] = tier["upto"]
        for src, dst in OCTAFUSE_TIER_KEYS:
            item[dst] = tier.get(src) or 0
        for key in OCTAFUSE_TIER_EXTRA_KEYS:
            if tier.get(key) is not None:
                item[key] = tier[key]
        out.append(item)
    return out


def octafuse_pricing_spec(preset):
    """OctaFuse 双币 pricing -> nexmux pricing_spec（USD 为生效价，CNY 原样保留在 prices）。

    nexmux 现为单币模型（PlatformSettingService.currency 默认 USD），故取 usd 作为
    生效字段；cny 与 usd 的原始分档/分时结构完整存放在 `prices` 下，供后续多币种渲染。
    """
    pricing = preset.get("pricing") or {}
    usd = pricing.get("usd") or {}
    cny = pricing.get("cny") or {}
    usd_tiers = octafuse_tiers(usd)

    spec = {"currency": "USD"}
    if usd_tiers:
        spec["unit"] = "per_million_tokens"
        first = usd_tiers[0]
        spec["input"] = first.get("input", 0)
        spec["output"] = first.get("output", 0)
        spec["cacheRead"] = first.get("cacheRead", 0)
        spec["cacheWrite"] = first.get("cacheWrite", 0)
        spec["tiers"] = usd_tiers
    if usd.get("image_billing_mode"):
        spec.setdefault("unit", octafuse_unit(usd, "image_billing_mode"))
        spec["imageBillingMode"] = usd["image_billing_mode"]
        if usd.get("image"):
            spec["image"] = usd["image"]
    if usd.get("audio_billing_mode"):
        spec.setdefault("unit", octafuse_unit(usd, "audio_billing_mode"))
        spec["audioBillingMode"] = usd["audio_billing_mode"]
        if usd.get("audio"):
            spec["audio"] = usd["audio"]

    multipliers = []
    for rule in usd.get("schedule") or []:
        multipliers.append({
            "days": ",".join(str(d) for d in (rule.get("days") or [])),
            "start": rule.get("start"),
            "end": rule.get("end"),
            "rate": rule.get("factor"),
        })
    if multipliers:
        spec["timeMultipliers"] = multipliers
    if cny:
        spec["prices"] = {"usd": usd, "cny": cny}
    return json.dumps(spec, ensure_ascii=False)


def octafuse_metadata(preset):
    """i18n + 出处，存入 mux_model.metadata_spec（控制台目前仅透传该列）。"""
    i18n = preset.get("i18n") or {}
    metadata = {
        "source": OCTAFUSE_TAG,
        "i18n": {"en": i18n.get("en"), "zh": i18n.get("zh")},
    }
    if preset.get("suggested_provider_model_name"):
        metadata["suggestedProviderModelName"] = preset["suggested_provider_model_name"]
    return json.dumps(metadata, ensure_ascii=False)


def octafuse_description(preset):
    """控制台为中文界面：description 取中文，缺失回退英文。"""
    i18n = preset.get("i18n") or {}
    return i18n.get("zh") or i18n.get("en") or preset.get("description")


def octafuse_model_to_row(preset, provider_code):
    """OctaFuse model preset -> 与 pi-ai 行同构的 model dict。"""
    modalities = preset.get("modalities") or {}
    inputs = modalities.get("input") or ["text"]
    outputs = modalities.get("output") or ["text"]
    tags = [OCTAFUSE_TAG, provider_code]
    return {
        "code": model_code(provider_code, preset["id"]),
        "name": preset.get("display_name") or preset["id"],
        "type": octafuse_model_type(preset),
        "provider": provider_code,
        "provider_model_id": preset["id"],
        "protocol": None,          # 由 provider 默认协议回填
        "context": preset.get("context_window"),
        "max_out": preset.get("max_tokens"),
        "input_mod": ",".join(inputs),
        "output_mod": ",".join(outputs),
        "cap_tags": octafuse_capability_tags(preset),
        "tags": json.dumps(tags, ensure_ascii=False),
        "pricing": octafuse_pricing_spec(preset),
        "description": octafuse_description(preset),
        "metadata": octafuse_metadata(preset),
        "release_date": preset.get("released"),
        "raw": preset,
    }


def apply_octafuse_overlay(models, presets, model_presets):
    """给存量 pi-ai 模型行补充 OctaFuse 的中文描述/双币定价/上下文/模态/发布日。"""
    index = {}
    for m in models:
        index.setdefault((m["provider"].lower(), (m["provider_model_id"] or "").lower()), m)

    changed = 0
    for preset in model_presets:
        vendor = preset.get("vendor")
        if vendor not in OCTAFUSE_VENDOR_PROVIDERS:
            continue
        row = None
        for provider_code in OCTAFUSE_VENDOR_PROVIDERS[vendor]:
            row = index.get((provider_code.lower(), preset["id"].lower()))
            if row:
                break
        if not row:
            continue

        row["description"] = octafuse_description(preset)
        row["metadata"] = octafuse_metadata(preset)
        row["pricing"] = octafuse_pricing_spec(preset)
        row["release_date"] = preset.get("released") or row.get("release_date")
        if preset.get("context_window") is not None:
            row["context"] = preset["context_window"]
        if preset.get("max_tokens") is not None:
            row["max_out"] = preset["max_tokens"]
        modalities = preset.get("modalities") or {}
        if modalities.get("input"):
            row["input_mod"] = ",".join(modalities["input"])
        if modalities.get("output"):
            row["output_mod"] = ",".join(modalities["output"])
        if preset.get("display_name"):
            row["name"] = preset["display_name"]
        tags = json.loads(row.get("tags") or "[]")
        if OCTAFUSE_TAG not in tags:
            tags.append(OCTAFUSE_TAG)
        row["tags"] = json.dumps(tags, ensure_ascii=False)
        changed += 1
    return changed


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


def build_pi_catalog(names, data_dir):
    providers = {}   # code -> dict（保持 pi-ai 文件顺序）
    models = []      # list of dict（按 provider 文件 + catalog 顺序）
    seen_codes = set()

    files = sorted(glob.glob(os.path.join(data_dir, "*.json")))
    if not files:
        raise SystemExit(f"catalog data not found in {data_dir}")

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
                    "description": None,
                    "metadata": None,
                    "release_date": None,
                    "raw": m,
                })
        providers[prov] = {
            "code": prov,
            "name": names.get(prov, prov.replace("-", " ").title()),
            "type": PROVIDER_TYPE.get(prov, "COMMERCIAL"),
            "protocol": modal(apis),
            "base_url": modal(base_urls) or AZURE_BASE_URL_PLACEHOLDER,
            "description": "Imported from pi-ai model catalog: " + names.get(prov, prov.replace("-", " ").title()),
        }
    return providers, models


def add_octafuse_providers(providers, models, presets, model_presets, seen_codes):
    """追加 OctaFuse 独有 provider 及其模型行（按 OCTAFUSE_ALLOWED_TYPES 过滤类型）。"""
    added_providers, added_models, skipped_models = 0, 0, 0
    by_file = {}
    for preset in model_presets:
        by_file.setdefault(preset["_file"], []).append(preset)

    for vendor, files in OCTAFUSE_PROVIDER_MODELS.items():
        preset = presets.get(vendor)
        if not preset:
            print(f"warn: octafuse provider preset missing for {vendor}", file=sys.stderr)
            continue
        name = preset.get("name") or vendor.replace("-", " ").title()
        protocol = octafuse_protocol(preset)
        base_url = octafuse_base_url(preset)
        if not base_url:
            print(f"warn: octafuse base_url missing for {vendor}", file=sys.stderr)
        providers[vendor] = {
            "code": vendor,
            "name": name,
            "type": "COMMERCIAL",
            "protocol": protocol,
            "base_url": base_url or "https://localhost",
            "description": "Imported from octafuse provider preset: " + name,
        }
        added_providers += 1

        allowed = OCTAFUSE_ALLOWED_TYPES.get(vendor)
        for file_name in files:
            for mp in by_file.get(file_name, []):
                row = octafuse_model_to_row(mp, vendor)
                if allowed is not None and model_type(row["type"]) not in allowed:
                    skipped_models += 1
                    continue
                if row["code"] in seen_codes:
                    continue
                seen_codes.add(row["code"])
                row["protocol"] = protocol
                models.append(row)
                added_models += 1
    return added_providers, added_models, skipped_models


def main():
    parser = argparse.ArgumentParser(description="生成 nexmux 提供商/模型 H2 种子数据")
    parser.add_argument("pi_repo", nargs="?", default=DEFAULT_PI_REPO,
                        help=f"pi 仓库根目录（默认 {DEFAULT_PI_REPO}）")
    parser.add_argument("data_dir", nargs="?", default=None,
                        help="pi-ai catalog 目录（默认取 pi_repo 生成物或已安装发行包）")
    parser.add_argument("--octafuse-lib", default=os.environ.get(OCTAFUSE_LIB_ENV),
                        help=f"OctaFuse packages/admin/lib 目录（默认取环境变量 {OCTAFUSE_LIB_ENV}）")
    parser.add_argument("--no-octafuse", action="store_true", help="不启用 OctaFuse 覆盖层")
    args = parser.parse_args()

    repo_data = os.path.join(args.pi_repo, "packages/ai/src/providers/data")
    data_dir = args.data_dir or (repo_data if os.path.isdir(repo_data) else INSTALLED_DATA)

    names = provider_display_names(args.pi_repo)
    providers, models = build_pi_catalog(names, data_dir)
    seen_codes = {m["code"] for m in models}

    overlay_note = "未启用（--no-octafuse）"
    enriched, added_providers, added_models, skipped_models = 0, 0, 0, 0
    octafuse_dir = None
    if not args.no_octafuse and args.octafuse_lib:
        octafuse_dir = os.path.abspath(os.path.expanduser(args.octafuse_lib))
        presets, model_presets = load_octafuse(octafuse_dir)
        enriched = apply_octafuse_overlay(models, presets, model_presets)
        added_providers, added_models, skipped_models = add_octafuse_providers(
                providers, models, presets, model_presets, seen_codes)
        overlay_note = (f"已启用：{octafuse_dir}"
                        f"（补全存量模型 {enriched} 行；新增 provider {added_providers} 个、"
                        f"模型 {added_models} 个；按类型白名单跳过 {skipped_models} 个）")
    elif not args.no_octafuse:
        overlay_note = f"跳过：未设置 --octafuse-lib / {OCTAFUSE_LIB_ENV}"

    lines = []
    w = lines.append
    w("-- Nexmux 提供商/模型 catalog 种子数据（H2）")
    w("-- 数据来源一：pi-ai model catalog（pi 仓库 packages/ai，catalog 数据源自 models.dev）")
    if octafuse_dir:
        w("-- 数据来源二（覆盖层）：OctaFuse gateway provider/model preset（AGPL-3.0，外部读取不并入本仓库）")
    w(f"-- 生成命令：python3 scripts/generate-pi-catalog-h2-sql.py"
      + (f" --octafuse-lib {octafuse_dir}" if octafuse_dir else ""))
    w(f"-- catalog 目录：{data_dir}")
    w(f"-- OctaFuse 覆盖层：{overlay_note}")
    w(f"-- 统计：{len(providers)} 个提供商，{len(models)} 个模型")
    w("-- 说明：MERGE ... WHEN NOT MATCHED THEN INSERT，只插不更新；")
    w("--       控制台对种子行的修改（状态/密钥/参数等）重启后保留；")
    w("--       因此改种子值（含覆盖层补充的描述/定价）对已落库的行不生效，需清库或手工迁移。")
    w("--       provider_id/model_id 由编码 md5 派生（pv*/md* 前缀），保证多次生成主键稳定。")
    w("")

    for prov in providers.values():
        pid = row_id("pv", prov["code"])
        w(f"-- provider: {prov['code']} ({prov['name']})")
        # ④ 种子供应商默认 DISABLED：未配置凭证前不可调度，控制台配置后手动启用
        emit_insert_only(w, "mux_provider", "provider_id", PROVIDER_COLS, [
            sql_str(pid), sql_str(prov["code"]), sql_str(prov["name"]), sql_str(prov["type"]), sql_str(prov["protocol"]),
            sql_str(prov["description"]), sql_str(prov["code"]), sql_str(prov["code"]), "NULL", sql_str(prov["base_url"]),
            "NULL", "60000", "NULL", "NULL", "NULL", sql_str("DISABLED"),
            "CURRENT_TIMESTAMP", "CURRENT_TIMESTAMP", sql_str("system"), sql_str("system"),
        ])

    for m in models:
        mid = row_id("md", m["code"])
        pid = row_id("pv", m["provider"])
        # ④ 种子模型默认 DRAFT：与控制台 ModelService.create 初始状态一致，审核后启用
        emit_insert_only(w, "mux_model", "model_id", MODEL_COLS, [
            sql_str(mid), sql_str(m["code"]), sql_str(m["name"]), sql_str(model_type(m["type"])), sql_str("DRAFT"),
            "NULL", sql_str(m.get("release_date")), sql_num(m["context"]), sql_num(m["max_out"]),
            sql_str(m["input_mod"]), sql_str(m["output_mod"]), sql_str(m["cap_tags"]), sql_str(m["tags"]),
            sql_str(m.get("description")), sql_str(m.get("metadata")), "NULL",
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
    print(f"octafuse overlay: {overlay_note}")


if __name__ == "__main__":
    main()
