#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""从 pi-ai catalog（+ 可选 OctaFuse 覆盖层）生成 nexmux provider-models YAML 资产。

输出目录：nexmux-kernel/src/main/resources/provider-models/
  - provider-presets.yaml
  - models/<providerCode>.yaml

用法：
  python3 scripts/generate-provider-model-presets-yaml.py --data-dir DIR [--octafuse-lib DIR]
"""
from __future__ import annotations

import argparse
import importlib.util
import json
import os
import sys
from collections import defaultdict
from datetime import datetime, timezone

try:
    import yaml
except ImportError:
    raise SystemExit("PyYAML required: pip install pyyaml")

ROOT = os.path.dirname(os.path.abspath(__file__))
OUT_DIR = os.path.normpath(os.path.join(ROOT, "../nexmux-kernel/src/main/resources/provider-models"))
MODELS_DIR = os.path.join(OUT_DIR, "models")

# 复用 catalog 构建逻辑（与 H2 脚本同源）
_CATALOG = os.path.join(ROOT, "generate-pi-catalog-h2-sql.py")
_spec = importlib.util.spec_from_file_location("catalog_sql", _CATALOG)
catalog = importlib.util.module_from_spec(_spec)
assert _spec.loader is not None
_spec.loader.exec_module(catalog)

INSTALLED_DATA = catalog.INSTALLED_DATA

# BCP 47：OctaFuse 使用 zh，平台 I18nObject 推荐 zh-CN（zh 仍可作为回退键）
LOCALE_ALIASES = {"zh": "zh-CN"}


def locale_key(loc):
    loc = (loc or "").strip()
    return LOCALE_ALIASES.get(loc, loc)


def i18n_map(values):
    """过滤空值并保持 locale 顺序。"""
    if not values:
        return None
    out = {}
    for loc, text in values.items():
        if text is None:
            continue
        t = str(text).strip()
        if t:
            out[locale_key(loc)] = t
    return out or None


def i18n_single(text, locale="en"):
    if text is None:
        return None
    t = str(text).strip()
    if not t:
        return None
    return {locale_key(locale): t}


def i18n_from_octafuse_block(block):
    """OctaFuse model i18n（各语言为描述字符串）或 provider catalog i18n（各语言为 name/description 对象）。"""
    out = {}
    for loc, val in (block or {}).items():
        if isinstance(val, str):
            t = val.strip()
            if t:
                out[locale_key(loc)] = t
        elif isinstance(val, dict):
            text = val.get("description") or val.get("name")
            if text and str(text).strip():
                out[locale_key(loc)] = str(text).strip()
    return out


def load_provider_import_presets(lib_dir):
    path = os.path.join(lib_dir, "provider-import-presets.json")
    if not os.path.isfile(path):
        return []
    return json.load(open(path, encoding="utf-8"))


def find_provider_import_preset(provider_code, raw_presets):
    for preset in raw_presets:
        if preset.get("vendor_key") == provider_code:
            return preset
    for vendor, codes in catalog.OCTAFUSE_VENDOR_PROVIDERS.items():
        if provider_code in codes:
            for preset in raw_presets:
                if preset.get("vendor_key") == vendor:
                    return preset
    return None


def provider_i18n_fields(prov, raw_presets):
    preset = find_provider_import_preset(prov["code"], raw_presets)
    display = None
    desc = None
    if preset:
        cat = (preset.get("catalog") or {}).get("i18n") or {}
        names = {}
        descs = {}
        for loc, block in cat.items():
            if not isinstance(block, dict):
                continue
            k = locale_key(loc)
            if block.get("name"):
                names[k] = block["name"]
            if block.get("description"):
                descs[k] = block["description"]
        display = i18n_map(names)
        desc = i18n_map(descs)
    if display is None:
        display = i18n_single(prov.get("name"))
    if desc is None and prov.get("description"):
        desc = i18n_single(prov.get("description"))
    return display, desc


def model_display_i18n(m):
    name = m.get("name")
    return i18n_single(name)


def model_description_i18n(m):
    texts = {}
    raw = m.get("raw")
    if isinstance(raw, dict) and raw.get("i18n"):
        texts.update(i18n_from_octafuse_block(raw["i18n"]))
    meta = parse_json_field(m.get("metadata"))
    if isinstance(meta, dict):
        texts.update(i18n_from_octafuse_block(meta.get("i18n")))
    plain = m.get("description")
    if plain and str(plain).strip() and "en" not in texts and "zh-CN" not in texts:
        texts["en"] = str(plain).strip()
    return i18n_map(texts)


def model_metadata_without_promoted_i18n(m):
    meta = parse_json_field(m.get("metadata"))
    if not isinstance(meta, dict):
        return meta
    cleaned = dict(meta)
    cleaned.pop("i18n", None)
    return cleaned if cleaned else None


def parse_json_field(value):
  """pricing / tags / capability_tags / metadata 在内存里可能是 JSON 字符串。"""
  if value is None or value == "":
    return None
  if isinstance(value, (dict, list)):
    return value
  try:
    return json.loads(value)
  except json.JSONDecodeError:
    return value


def split_modalities(text):
  if not text:
    return ["text"]
  return [part.strip() for part in str(text).split(",") if part.strip()]


def provider_doc(providers, models, raw_presets):
  items = []
  counts = defaultdict(int)
  for m in models:
    counts[m["provider"]] += 1
  for prov in sorted(providers.values(), key=lambda p: p["code"]):
    display, desc = provider_i18n_fields(prov, raw_presets)
    items.append({
        "providerCode": prov["code"],
        "displayName": display,
        "providerType": prov["type"],
        "defaultProtocol": prov["protocol"],
        "vendorCode": prov["code"],
        "sourceVendorCode": prov["code"],
        "baseUrl": prov["base_url"],
        "timeoutMs": 60000,
        "description": desc,
        "modelCount": counts.get(prov["code"], 0),
    })
  return {
      "apiVersion": "nexmux.provider-presets/v1",
      "updatedAt": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
      "providerCount": len(items),
      "modelCount": len(models),
      "providers": items,
  }


def model_entry(m):
  tags = parse_json_field(m.get("tags"))
  if isinstance(tags, str):
    tags = split_modalities(tags)
  return {
      "modelCode": m["code"],
      "displayName": model_display_i18n(m),
      "modelType": catalog.model_type(m["type"]),
      "providerModelId": m["provider_model_id"],
      "primaryUpstreamProtocol": m.get("protocol"),
      "contextLength": m.get("context"),
      "maxOutputTokens": m.get("max_out"),
      "inputModalities": split_modalities(m.get("input_mod")),
      "outputModalities": split_modalities(m.get("output_mod")),
      "capabilityTags": parse_json_field(m.get("cap_tags")),
      "tags": tags if isinstance(tags, list) else [],
      "description": model_description_i18n(m),
      "metadataSpec": model_metadata_without_promoted_i18n(m),
      "pricingSpec": parse_json_field(m.get("pricing")),
      "releaseDate": m.get("release_date"),
  }


def write_yaml(path, data):
  os.makedirs(os.path.dirname(path), exist_ok=True)
  with open(path, "w", encoding="utf-8") as fh:
    yaml.safe_dump(
        data,
        fh,
        allow_unicode=True,
        sort_keys=False,
        default_flow_style=False,
        width=120,
    )


def main():
  parser = argparse.ArgumentParser(description="生成 provider-models YAML 资产")
  parser.add_argument("--data-dir", default=None, help="pi-ai catalog 目录")
  parser.add_argument("--octafuse-lib", default=os.environ.get(catalog.OCTAFUSE_LIB_ENV),
                      help="OctaFuse packages/admin/lib")
  parser.add_argument("--no-octafuse", action="store_true")
  args = parser.parse_args()

  data_dir = args.data_dir
  if not data_dir:
    repo_data = os.path.join(catalog.DEFAULT_PI_REPO, "packages/ai/src/providers/data")
    data_dir = repo_data if os.path.isdir(repo_data) else INSTALLED_DATA
  if not os.path.isdir(data_dir):
    raise SystemExit(f"catalog data not found: {data_dir}")

  names = catalog.provider_display_names(catalog.DEFAULT_PI_REPO)
  providers, models = catalog.build_pi_catalog(names, data_dir)
  seen_codes = {m["code"] for m in models}

  raw_presets = []
  if not args.no_octafuse and args.octafuse_lib:
    octafuse_dir = os.path.abspath(os.path.expanduser(args.octafuse_lib))
    raw_presets = load_provider_import_presets(octafuse_dir)
    presets, model_presets = catalog.load_octafuse(octafuse_dir)
    catalog.apply_octafuse_overlay(models, presets, model_presets)
    catalog.add_octafuse_providers(providers, models, presets, model_presets, seen_codes)

  by_provider = defaultdict(list)
  for m in models:
    by_provider[m["provider"]].append(model_entry(m))

  if os.path.isdir(MODELS_DIR):
    for fn in os.listdir(MODELS_DIR):
      if fn.endswith(".yaml"):
        os.remove(os.path.join(MODELS_DIR, fn))

  write_yaml(os.path.join(OUT_DIR, "provider-presets.yaml"), provider_doc(providers, models, raw_presets))
  for code in sorted(by_provider):
    write_yaml(
        os.path.join(MODELS_DIR, f"{code}.yaml"),
        {
            "apiVersion": "nexmux.model-presets/v1",
            "providerCode": code,
            "modelCount": len(by_provider[code]),
            "models": by_provider[code],
        },
    )

  print(f"written: {OUT_DIR}")
  print(f"providers: {len(providers)}, models: {len(models)}, files: {len(by_provider)}")


if __name__ == "__main__":
  main()