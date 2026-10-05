/**
 * Model 域适配器：nexmux 后端 VO → UI 契约形状（ModelRecord / ModelListResult）。
 *
 * 与 provider-adapter 同分工：后端给「数据可得性」（entity 平铺 + JSON 原文），
 * 派生文案 / 供应商组装 / 路由引用统计 / counts/filters 全部在 BFF。
 * 数据结构依据：docs/design/nexmux-domain-layer-design.md §5（ModelBindingItem /
 * ModelPricingSpec 同构 JSON）。
 */

import type {
  ModelListResult,
  ModelParams,
  ModelRecord,
  ModelTier,
  PriceVersion,
  RouteRef,
  SupplierModel,
  SupplierTimeRate,
  TimeMultiplier,
} from "../contracts";
import { backendClient, fetchAll, type PageResult } from "../client";

// ── 后端 VO 镜像 ──────────────────────────────────────────────

interface ModelVo {
  modelId: string;
  modelCode: string;
  displayName: string;
  status: string;
  modelType: string;
  contextLength?: number;
  maxOutputTokens?: number;
  primaryProviderId?: string;
  pricingSpec?: string;
  extraBindings?: string;
}

interface ModelDetailVo extends ModelVo {
  iconUri?: string;
  releaseDate?: string;
  inputModalities?: string;
  outputModalities?: string;
  capabilityTags?: string;
  tags?: string;
  description?: string;
  metadataSpec?: string;
  defaultParams?: string;
  primaryProviderModelId?: string;
  primaryUpstreamProtocol?: string;
  primaryBindingWeight?: number;
  primaryCostMultiplier?: number;
  primaryReferenceMultiplier?: number;
  primaryBindingEnabled?: boolean;
  createdAt?: string;
  updatedAt?: string;
  updatedBy?: string;
}

interface ProviderVo {
  providerId: string;
  providerCode: string;
  displayName: string;
  vendorCode: string;
  keyStatus: string;
}

interface RouteVo {
  alias: string;
  displayName: string;
  status: string;
  routeSpec?: { candidates?: { modelCode: string; weight: number }[] };
}

/** ModelBindingItem（domain-layer-design §5.1） */
interface BindingItem {
  providerId: string;
  providerModelId?: string;
  upstreamProtocol?: string;
  weight?: number;
  costMultiplier?: number;
  referenceMultiplier?: number;
  timeRateRules?: SupplierTimeRate[] | null;
  enabled?: boolean;
  healthStatus?: string;
}

/** ModelPricingSpec 同构 JSON；catalog 种子为平铺单价（unit 标记），两种兼容 */
interface PricingSpec {
  tiers?: ModelTier[];
  timeMultipliers?: TimeMultiplier[];
  currency?: string;
  priceVersion?: string;
  priceEffective?: string;
  priceVersions?: PriceVersion[];
  /** catalog 平铺单价字段（unit=per_million_tokens 时） */
  unit?: string;
  input?: number;
  output?: number;
  cacheRead?: number | null;
  cacheWrite?: number | null;
}

/** 种子 modelCode 含 /（openai/o4-mini）：页面路径与 /api/data 段不支持，
 *  BFF 对外暴露时用 ~ 替代，入站还原（页面 URL 形态 /models/openai~o4-mini）。 */
export function toUiCode(modelCode: string): string {
  return modelCode.replace(/\//g, "~");
}

function fromUiCode(uiCode: string): string {
  return uiCode.replace(/~/g, "/");
}

/** by-code 路径：保留 / 分段（后端 {modelCode: .+} 多段匹配；Tomcat 拒绝 %2F） */
function byCodePath(uiCode: string): string {
  return `/api/nexmux/models/by-code/${fromUiCode(uiCode)
    .split("/")
    .map(encodeURIComponent)
    .join("/")}`;
}

// ── 常量（与 models-mock 逐字对齐） ────────────────────────────

const TYPE_LABELS: Record<string, string> = {
  LLM: "大语言模型 (LLM)",
  Embedding: "Embedding",
  Image: "文生图 (Image)",
  Audio: "音频 (Audio)",
};

const MODEL_TYPE_OPTIONS = [
  { value: "LLM", label: "大语言模型" },
  { value: "Image", label: "文生图" },
  { value: "Audio", label: "音频" },
  { value: "Embedding", label: "Embedding" },
];

const VENDOR_ICONS: Record<string, string> = {
  openai: "zap",
  anthropic: "brain",
  gemini: "star",
  dashscope: "cloud",
};

// ── 派生工具（对齐 models-mock derive） ───────────────────────

function formatTokens(n: number): string {
  if (n >= 1_000_000) {
    const v = n / 1_000_000;
    return `${v % 1 === 0 ? v : v.toFixed(1)}M`;
  }
  if (n >= 1_000) {
    const v = n / 1_000;
    return `${v % 1 === 0 ? v : v.toFixed(1)}K`;
  }
  return String(n);
}

function modalityLetter(m: string): string {
  return (
    ({ text: "T", image: "I", audio: "A", video: "V", file: "F" } as Record<string, string>)[m] ??
    m[0].toUpperCase()
  );
}

function parseJson<T>(text: string | undefined, fallback: T): T {
  if (!text) return fallback;
  try {
    return JSON.parse(text) as T;
  } catch {
    return fallback;
  }
}

function csvList(text?: string): string[] {
  return text ? text.split(",").map((s) => s.trim()).filter(Boolean) : [];
}

function timeText(iso?: string): string {
  return iso ? iso.slice(0, 16).replace("T", " ") : "";
}

/** pricingSpec → 标准分档数组（兼容 catalog 平铺单价格式） */
function tiersOf(pricing: PricingSpec): ModelTier[] {
  if (pricing.tiers?.length) return pricing.tiers;
  if (pricing.unit || pricing.input != null) {
    return [
      {
        upto: null,
        input: pricing.input ?? 0,
        output: pricing.output ?? 0,
        cacheRead: pricing.cacheRead ?? null,
        cacheWrite: pricing.cacheWrite ?? null,
      },
    ];
  }
  return [];
}

// ── 关联数据（providers / routes 全量内存 join） ──────────────

interface ModelRelations {
  providersById: Map<string, ProviderVo>;
  routes: RouteVo[];
}

async function loadRelations(): Promise<ModelRelations> {
  const [providers, routes] = await Promise.all([
    fetchAll<ProviderVo>("/api/nexmux/providers"),
    fetchAll<RouteVo>("/api/nexmux/routes"),
  ]);
  return {
    providersById: new Map(providers.map((p) => [p.providerId, p])),
    routes,
  };
}

function supplierOf(
  provider: ProviderVo | undefined,
  binding: {
    providerModelId?: string;
    weight?: number;
    costMultiplier?: number;
    referenceMultiplier?: number;
    timeRateRules?: SupplierTimeRate[] | null;
    enabled?: boolean;
  },
): SupplierModel {
  return {
    providerCode: provider?.providerCode ?? "—",
    providerName: provider?.displayName ?? "未知供应商",
    providerIcon: VENDOR_ICONS[provider?.vendorCode ?? ""] ?? "zap",
    providerModelId: binding.providerModelId ?? "—",
    weight: binding.weight ?? 0,
    costRate: binding.costMultiplier ?? 1,
    billingRate: binding.referenceMultiplier ?? 1,
    status: binding.enabled === false ? "disabled" : "enabled",
    timeRates: binding.timeRateRules ?? null,
    keyConfigured: provider?.keyStatus === "configured",
    lastProbe: null,
  };
}

function routeRefsOf(modelCode: string, routes: RouteVo[]): RouteRef[] {
  return routes
    .filter((r) => (r.routeSpec?.candidates ?? []).some((c) => c.modelCode === modelCode))
    .map((r) => {
      const candidates = r.routeSpec?.candidates ?? [];
      return {
        name: r.displayName,
        alias: r.alias,
        type: candidates.length > 1 ? "多模型池" : "单模型",
        weight: candidates.find((c) => c.modelCode === modelCode)?.weight ?? 0,
        status: (r.status === "PUBLISHED" ? "启用" : "停用") as RouteRef["status"],
        health: "HEALTHY" as const,
      };
    });
}

// ── VO → ModelRecord ──────────────────────────────────────────

function toRecord(
  vo: ModelVo | ModelDetailVo,
  relations: ModelRelations,
  detail?: ModelDetailVo,
): ModelRecord {
  const isDetail = (v: ModelVo | ModelDetailVo): v is ModelDetailVo => "extraBindings" in v && "createdAt" in v;
  const d = detail ?? (isDetail(vo) ? vo : undefined);

  const pricing = parseJson<PricingSpec>(vo.pricingSpec, {});
  const tiers = tiersOf(pricing);
  const extraBindings = parseJson<BindingItem[]>(vo.extraBindings, []);

  const suppliers: SupplierModel[] = [];
  if (d) {
    suppliers.push(
      supplierOf(relations.providersById.get(d.primaryProviderId ?? ""), {
        providerModelId: d.primaryProviderModelId,
        weight: d.primaryBindingWeight,
        costMultiplier: d.primaryCostMultiplier,
        referenceMultiplier: d.primaryReferenceMultiplier,
        enabled: d.primaryBindingEnabled !== false,
      }),
    );
  } else if (vo.primaryProviderId) {
    suppliers.push(
      supplierOf(relations.providersById.get(vo.primaryProviderId), { weight: 100, enabled: true }),
    );
  }
  for (const binding of extraBindings) {
    suppliers.push(supplierOf(relations.providersById.get(binding.providerId), binding));
  }

  const routeRefs = routeRefsOf(vo.modelCode, relations.routes);
  const routes = {
    total: routeRefs.length,
    active: routeRefs.filter((r) => r.status === "启用").length,
  };

  const inputModalities = csvList(d?.inputModalities);
  const outputModalities = csvList(d?.outputModalities);
  const tags = parseJson<string[]>(d?.tags, []);
  const capabilityTags = parseJson<string[]>(d?.capabilityTags, []);
  const defaultParams = parseJson<Record<string, string>>(d?.defaultParams, {});
  const params: ModelParams = {
    temperature: defaultParams.temperature ?? "—",
    topP: defaultParams.topP ?? "—",
    topK: defaultParams.topK ?? "—",
    maxTokens: defaultParams.maxTokens ?? "—",
    frequencyPenalty: defaultParams.frequencyPenalty ?? "—",
    presencePenalty: defaultParams.presencePenalty ?? "—",
    reasoningEffort: defaultParams.reasoningEffort ?? "—",
    seed: defaultParams.seed ?? "—",
    stopSequences: defaultParams.stopSequences ?? "—",
    responseFormat: defaultParams.responseFormat ?? "—",
    capabilities: capabilityTags,
  };

  const firstTier = tiers[0];
  const enabledSuppliers = suppliers.filter((s) => s.status === "enabled");
  const primary = enabledSuppliers[0] ?? suppliers[0];
  const contextWindow = vo.contextLength ?? 0;
  const maxTokens = vo.maxOutputTokens ?? 0;
  const updatedAt = timeText(d?.updatedAt);

  return {
    code: toUiCode(vo.modelCode),
    name: vo.displayName,
    providerCode: primary?.providerCode ?? "",
    providerName: primary?.providerName ?? "",
    providerIcon: primary?.providerIcon ?? "zap",
    type: (vo.modelType || "LLM") as ModelRecord["type"],
    typeLabel: TYPE_LABELS[vo.modelType] ?? vo.modelType,
    releaseDate: d?.releaseDate ?? "",
    contextWindow,
    maxTokens,
    inputModalities,
    outputModalities,
    tags,
    description: d?.description ?? "",
    metadata: d?.metadataSpec ?? "",
    status: (vo.status === "ENABLED" ? "enabled" : "disabled") as ModelRecord["status"],
    tiers,
    timeMultipliers: pricing.timeMultipliers ?? [],
    currency: pricing.currency ?? "USD",
    priceVersion: pricing.priceVersion ?? "",
    priceEffective: pricing.priceEffective ?? "",
    priceVersions: pricing.priceVersions ?? [],
    suppliers,
    routes,
    routeRefs,
    params,
    createdAt: timeText(d?.createdAt),
    updatedAt,
    operator: d?.updatedBy ?? "—",
    specSummary: `${formatTokens(contextWindow)} · ${maxTokens > 0 ? formatTokens(maxTokens) : "—"}`,
    pricingSummary: firstTier
      ? `$${firstTier.input} / $${firstTier.output > 0 ? firstTier.output : "—"} /M`
      : "未定价",
    tierLabel: tiers.length > 1 ? `${tiers.length}档` : "",
    routeSummary: routes.total === 0 ? "未关联路由" : `${routes.total} 条路由 · ${routes.active} 条活跃`,
    modalityPreview: `${inputModalities.map(modalityLetter).join("+")} → ${outputModalities.map(modalityLetter).join("+")}`,
    inputModalitiesText: inputModalities.join(" · "),
    outputModalitiesText: outputModalities.join(" · "),
    tagsText: tags.join(" · "),
    providerSummary: primary
      ? `${primary.providerName}（主） · 共 ${suppliers.length} 个供应商模型`
      : "未绑定供应商模型",
    primaryUpstreamId: primary?.providerModelId ?? "—",
    supplierWeightTotal: String(enabledSuppliers.reduce((sum, s) => sum + s.weight, 0)),
    shortDate: updatedAt.slice(5, 10),
  };
}

// ── counts / filters（构成口径） ───────────────────────────────

function computeCounts(
  items: ModelRecord[],
  providers: ProviderVo[],
): ModelListResult["counts"] {
  return {
    all: {
      enabled: items.filter((m) => m.status === "enabled").length,
      total: items.length,
    },
    types: {
      LLM: items.filter((m) => m.type === "LLM").length,
      Embedding: items.filter((m) => m.type === "Embedding").length,
      Image: items.filter((m) => m.type === "Image").length,
      Audio: items.filter((m) => m.type === "Audio").length,
    },
    providers: providers.reduce<Record<string, number>>((acc, p) => {
      acc[p.providerCode] = items.filter((m) => m.providerCode === p.providerCode).length;
      return acc;
    }, {}),
  };
}

function computeFilters(
  counts: ModelListResult["counts"],
  providers: ProviderVo[],
): ModelListResult["filters"] {
  return {
    type: [
      { value: "all", text: `全部 ${counts.all.total}` },
      ...MODEL_TYPE_OPTIONS.map((o) => ({
        value: o.value,
        text: `${o.label} ${counts.types[o.value as keyof typeof counts.types] ?? 0}`,
      })),
    ],
    providers: [
      { value: "all", text: `全部 ${counts.all.total}` },
      ...providers.map((p) => ({
        value: p.providerCode,
        text: `${p.displayName} ${counts.providers[p.providerCode] ?? 0}`,
      })),
    ],
  };
}

// ── 对外服务 ───────────────────────────────────────────────────

export async function listModels(params: {
  type?: string;
  providers?: string[];
}): Promise<ModelListResult> {
  const type = params.type && params.type !== "all" ? params.type : undefined;
  const [page, relations, allForCounts] = await Promise.all([
    backendClient.get<PageResult<ModelVo>>("/api/nexmux/models", {
      pageNo: 1,
      pageSize: 5000,
      type,
      providers: params.providers?.length ? params.providers.join(",") : undefined,
    }),
    loadRelations(),
    fetchAll<ModelVo>("/api/nexmux/models"),
  ]);

  const items = page.records.map((vo) => toRecord(vo, relations));
  const providerList = [...relations.providersById.values()];
  const counts = computeCounts(
    allForCounts.map((vo) => toRecord(vo, relations)),
    providerList,
  );
  return { items, total: allForCounts.length, counts, filters: computeFilters(counts, providerList) };
}

export async function getModelByCode(code: string): Promise<ModelRecord | null> {
  const [detail, relations] = await Promise.all([
    backendClient.get<ModelDetailVo>(byCodePath(code)),
    loadRelations(),
  ]);
  return toRecord(detail, relations, detail);
}

// ── 写操作 ─────────────────────────────────────────────────────

async function modelIdOf(uiCode: string): Promise<string> {
  const detail = await backendClient.get<ModelDetailVo>(byCodePath(uiCode));
  return detail.modelId;
}

export async function createModel(body: Record<string, unknown>): Promise<ModelVo> {
  return backendClient.post<ModelVo>("/api/nexmux/models", body);
}

export async function updateModel(code: string, body: Record<string, unknown>): Promise<ModelVo> {
  return backendClient.put<ModelVo>(`/api/nexmux/models/${await modelIdOf(code)}`, body);
}

export async function setModelEnabled(code: string, enabled: boolean): Promise<ModelVo> {
  const id = await modelIdOf(code);
  return backendClient.post<ModelVo>(`/api/nexmux/models/${id}/${enabled ? "enable" : "disable"}`);
}
