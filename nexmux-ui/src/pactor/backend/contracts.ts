/**
 * UI 契约类型（页面 DSL 绑定的数据形状）。
 *
 * 抽取自原 src/pactor/mocks/*.ts 的类型定义（mock 已按设计 §10 里程碑退役删除）：
 * BFF adapter 的输出契约，与页面 YAML 的 ${data.*} 绑定一一对应。
 * 修改本文件 = 修改接口契约，须同步核对页面 YAML 绑定与 adapter 黄金测试。
 */

// ── from providers-mock ──
export type ProviderStatus = "enabled" | "disabled";
/** configured=已配置；pending=已创建但密钥未保存（模板导入待补齐）；none=未配置密钥 */
export type KeyStatus = "configured" | "pending" | "none";

export interface ProviderModel {
  code: string;
  name: string;
  type: string;
  providerModelId: string;
  pricing: string;
  status: "ONLINE" | "OFFLINE";
  routes: number;
}
export interface ProviderRecord {
  code: string;
  name: string;
  /** 同名多实例区分展示名，如 DeepSeek (2) */
  displayName: string;
  /** Icon 白名单图标名 */
  icon: string;
  providerType: string;
  protocol: string;
  /** 类型/协议快筛聚合键（内置模板族） */
  family: "openai" | "anthropic" | "gemini" | "dashscope";
  status: ProviderStatus;
  keyStatus: KeyStatus;
  baseUrl: string;
  region: string;
  timeout: number;
  proxy: string;
  maskedKey: string;
  lastTest: { time: string; ok: boolean; detail: string } | null;
  capabilities: string[];
  routes: { total: number; enabled: number };
  /** 路由摘要（预计算：表达式不适合拼接复合文案） */
  routeSummary: string;
  models: ProviderModel[];
  modelCount: number;
  /** 启用状态模型数（getProvider 派生；停用确认弹层「影响范围」用） */
  enabledModelCount?: number;
  description: string;
  capabilityExtension: string;
  createdAt: string;
  updatedAt: string;
  operator: string;
}
export interface ProviderListParams {
  keyword?: string;
  /** all | enabled | disabled | pending | none */
  status?: string;
  types?: string[];
}
export interface ProviderCounts {
  all: { enabled: number; total: number };
  status: Record<"enabled" | "disabled" | "pending" | "none", number>;
  types: Record<string, { enabled: number; total: number }>;
}
export interface ProviderChipOption {
  value: string;
  text: string;
  dot?: string;
}
export interface ProviderListResult {
  items: ProviderRecord[];
  total: number;
  counts: ProviderCounts;
  /** 快筛 Chip 组：status 单选（含「全部」）/ types 多选（无「全部」，不选即全部） */
  filters: { status: ProviderChipOption[]; types: ProviderChipOption[] };
}
export interface PullModelRow {
  id: string;
  name: string;
  local: string;
}
// ── from models-mock ──
export type ModelStatus = "enabled" | "disabled";
export type ModelType = "LLM" | "Embedding" | "Image" | "Audio";

/** 标准价格分档：upto=null 表示无上限档（∞） */
export interface ModelTier {
  upto: number | null;
  input: number;
  output: number;
  cacheRead: number | null;
  cacheWrite: number | null;
}
export interface TimeMultiplier {
  days: string;
  start: string;
  end: string;
  rate: number;
}
export interface SupplierTimeRate {
  days: string;
  start: string;
  end: string;
  cost: number;
  billing: number;
}
export interface SupplierModel {
  providerCode: string;
  providerName: string;
  /** 继承 Provider 品牌图标（Icon 白名单图标名） */
  providerIcon: string;
  providerModelId: string;
  weight: number;
  /** 供应商成本倍率 C（相对定价 Tab 标准价） */
  costRate: number;
  /** 模型计费倍率 M（相对定价 Tab 标准价） */
  billingRate: number;
  status: ModelStatus;
  /** 时段倍率；null = 未配置，使用默认 C / M */
  timeRates: SupplierTimeRate[] | null;
  /** 继承 Provider 密钥状态 */
  keyConfigured: boolean;
  lastProbe: { time: string; ok: boolean } | null;
}
export interface RouteRef {
  name: string;
  alias: string;
  type: string;
  weight: number;
  status: "启用" | "停用";
  health: "HEALTHY" | "UNHEALTHY";
}
export interface PriceVersion {
  version: string;
  current: boolean;
  effective: string;
  summary: string;
}
export interface ModelParams {
  temperature: string;
  topP: string;
  topK: string;
  maxTokens: string;
  frequencyPenalty: string;
  presencePenalty: string;
  reasoningEffort: string;
  seed: string;
  stopSequences: string;
  responseFormat: string;
  capabilities: string[];
}
export interface ModelRecord {
  code: string;
  name: string;
  providerCode: string;
  providerName: string;
  providerIcon: string;
  type: ModelType;
  typeLabel: string;
  releaseDate: string;
  contextWindow: number;
  /** 0 表示未设置（展示 —） */
  maxTokens: number;
  inputModalities: string[];
  outputModalities: string[];
  tags: string[];
  description: string;
  /** 元数据（JSON 文本） */
  metadata: string;
  status: ModelStatus;
  tiers: ModelTier[];
  timeMultipliers: TimeMultiplier[];
  currency: string;
  priceVersion: string;
  priceEffective: string;
  priceVersions: PriceVersion[];
  suppliers: SupplierModel[];
  routes: { total: number; active: number };
  routeRefs: RouteRef[];
  params: ModelParams;
  createdAt: string;
  updatedAt: string;
  operator: string;
  // ── 预计算摘要（表达式不适合拼接复合文案） ──
  /** 规格摘要：如 `256K · 32K`（上下文 · 最大输出） */
  specSummary: string;
  /** 定价摘要：默认档 如 `$0.2 / $1.6 /M`（输出为 0 时展示 —） */
  pricingSummary: string;
  /** 多档标识：如 `4档`；单档为空串 */
  tierLabel: string;
  /** 路由摘要：`N 条路由 · M 条活跃` 或 `未关联路由` */
  routeSummary: string;
  /** 模态预览：如 `T+I+V → T` */
  modalityPreview: string;
  inputModalitiesText: string;
  outputModalitiesText: string;
  tagsText: string;
  /** Provider 摘要：`阿里云百炼（主） · 共 N 个供应商模型` */
  providerSummary: string;
  /** 主供应商上游 Model ID（权重最高的启用实例） */
  primaryUpstreamId: string;
  /** 启用实例权重合计（展示文案） */
  supplierWeightTotal: string;
  /** 最近更新短日期（MM-DD） */
  shortDate: string;
}
export interface ModelListParams {
  /** all | LLM | Embedding | Image | Audio */
  type?: string;
  /** 厂商多选（Provider Code 列表） */
  providers?: string[];
}
export interface ModelCounts {
  all: { enabled: number; total: number };
  types: Record<ModelType, number>;
  providers: Record<string, number>;
}
export interface ModelChipOption {
  value: string;
  text: string;
  dot?: string;
}
export interface ModelListResult {
  items: ModelRecord[];
  total: number;
  counts: ModelCounts;
  /** 快筛 Chip 组：type 单选（含「全部」），providers 多选（含「全部」，点击清空） */
  filters: { type: ModelChipOption[]; providers: ModelChipOption[] };
}
// ── from routes-mock ──
export type RouteStatus = "enabled" | "disabled" | "draft";
export type RouteType = "single" | "pool" | "conditional";
export type RouteStrategy =
  | "WEIGHTED"
  | "HASH"
  | "PRIORITY"
  | "ROUND_ROBIN"
  | "RANDOM"
  | "FIXED";

/** 路由下的模型行（详情/编辑「模型与路由策略」表） */
export interface RouteModelEntry {
  modelCode: string;
  modelName: string;
  /** 主供应商摘要，如 `阿里云百炼 · qwen-vl-plus-latest` */
  providerSummary: string;
  weight: number;
  priority: "P0" | "P1" | "P2";
  /** 供应成本倍率 C；null = 留空继承 Model「提供商」配置 */
  costRate: number | null;
  /** 用户计费倍率 M；null = 留空继承 */
  billingRate: number | null;
  status: "enabled" | "disabled";
}
export interface RouteRecord {
  code: string;
  name: string;
  type: RouteType;
  typeLabel: string;
  /** 逻辑模型 Alias（对外暴露的模型名）；空串 = 未设置 */
  alias: string;
  tags: string[];
  description: string;
  status: RouteStatus;
  statusLabel: string;
  /** 请求路径，如 /v1/chat/completions */
  path: string;
  /** 协议格式（Ingress Protocol），如 OpenAI / Chat Completions */
  protocol: string;
  /** Ingress Operation，随协议格式带出，如 chat.completions */
  operation: string;
  strategy: RouteStrategy;
  strategyLabel: string;
  /** 粘性 Session：同会话固定到同一模型实例 */
  stickySession: boolean;
  /** 健康过滤：仅调度 HEALTHY 实例（默认开） */
  healthFilter: boolean;
  models: RouteModelEntry[];
  createdAt: string;
  updatedAt: string;
  operator: string;
  // ── 预计算摘要（表达式不适合拼接复合文案） ──
  /** 入口摘要：`/v1/chat/completions · OpenAI / Chat Completions` */
  entrySummary: string;
  /** 策略摘要：`3 模型 · 加权轮询` */
  policySummary: string;
  /** 策略完整文案：`加权轮询 (WEIGHTED)` */
  strategySummary: string;
  tagsText: string;
  /** 启用模型行数 */
  enabledModelCount: number;
  /** 启用项权重合计 */
  weightTotal: number;
  /** 最近更新短日期（MM-DD） */
  shortDate: string;
}
export interface RouteListParams {
  /** all | enabled | disabled | draft */
  status?: string;
  /** all | single | pool | conditional */
  type?: string;
  /** 标签多选 */
  tags?: string[];
}
export interface RouteCounts {
  all: { enabled: number; total: number };
  status: Record<RouteStatus, number>;
  types: Record<RouteType, { enabled: number; total: number }>;
  /** 键为标签的表达式安全形式（连字符转下划线，如 low-cost → low_cost） */
  tags: Record<string, number>;
}
export interface RouteChipOption {
  value: string;
  text: string;
  dot?: string;
}
export interface RouteListResult {
  items: RouteRecord[];
  total: number;
  counts: RouteCounts;
  /** 快筛 Chip 组：status / type 单选（含「全部」），tags 多选（含「全部」，点击清空） */
  filters: {
    status: RouteChipOption[];
    type: RouteChipOption[];
    tags: RouteChipOption[];
  };
}
// ── from client-templates-mock ──
export type ClientTemplateStatus = "enabled" | "disabled";
export type ValidityType = "days" | "fixed" | "never";
export type QuotaType = "lifetime" | "cyclic";
export type QuotaMetric =
  | "REQUEST"
  | "INPUT_TOKEN"
  | "OUTPUT_TOKEN"
  | "TOTAL_TOKEN"
  | "COST"
  | "CONCURRENT";
export type QuotaMode = "soft" | "hard";
export type CycleUnit = "hour" | "day" | "month";
export type RateLimitType = "RPM" | "TPM" | "CONCURRENT";
export type RuleStatus = "enabled" | "disabled";

/** 授权路由快照：引用 routes-mock 中已启用路由的展示字段（code/name/alias/typeLabel/path） */
export interface AuthRoute {
  code: string;
  name: string;
  alias: string;
  typeLabel: string;
  path: string;
}
export interface QuotaRule {
  /** 规则名称，策略内唯一 */
  name: string;
  quotaType: QuotaType;
  /** 总配额 / 周期循环配额 */
  quotaTypeLabel: string;
  metric: QuotaMetric;
  metricLabel: string;
  limit: number;
  /** 千分位上限文案（表达式不适合做数字格式化，derive 预计算） */
  limitText: string;
  mode: QuotaMode;
  /** Soft Limit / Hard Limit */
  modeLabel: string;
  /** 循环周期（仅 quotaType=cyclic）：每 X 小时 / 每 X 天 / 每月（自然月） */
  cycleUnit?: CycleUnit;
  cycleValue?: number;
  /** 如「每 24 小时」「每 7 天」「每月（自然月）」 */
  cycleLabel?: string;
  status: RuleStatus;
}
export interface RateLimitRule {
  name: string;
  limitType: RateLimitType;
  /** 如「RPM（每分钟请求数）」 */
  limitTypeLabel: string;
  limit: number;
  /** 千分位上限文案 */
  limitText: string;
  /** 突发容量（RPM/TPM 可选；Concurrent 无） */
  burst?: number;
  /** true = 全部授权路由；false 时 scopeRoutes 为指定路由 code 列表 */
  scopeAll: boolean;
  scopeRoutes?: string[];
  /** 「全部授权路由」或路由名列表 */
  scopeLabel: string;
  status: RuleStatus;
}
export interface ClientTemplateRecord {
  code: string;
  name: string;
  tags: string[];
  description: string;
  status: ClientTemplateStatus;
  statusLabel: string;
  /** 有效期规则：创建后 N 天 / 固定日期 / 永不过期 */
  validityType: ValidityType;
  validityDays?: number;
  validityFixedDate?: string;
  /** 如「创建后 365 天」「固定至 2027-12-31」「永不过期」 */
  validityLabel: string;
  authRoutes: AuthRoute[];
  quotas: QuotaRule[];
  rateLimits: RateLimitRule[];
  /** 基于本模板创建的客户端数 */
  createdClientCount: number;
  createdAt: string;
  updatedAt: string;
  operator: string;
  // ── 预计算摘要（表达式不适合拼接复合文案） ──
  /** 授权路由数 */
  routeCount: number;
  /** 配额摘要，如「总+日循环」 */
  quotaSummary: string;
  /** 限流摘要，如「1000 RPM」 */
  rateLimitSummary: string;
  tagsText: string;
  /** 策略摘要，如「3 路由 · 总+日循环 · 1000 RPM」 */
  policySummary: string;
}
export interface ClientTemplateListParams {
  /** all | enabled | disabled */
  status?: string;
  /** 名称 / 标识模糊匹配 */
  keyword?: string;
  /** 标签多选（取交集） */
  tags?: string[];
}
export interface ClientTemplateCounts {
  all: { enabled: number; total: number };
  status: Record<ClientTemplateStatus, number>;
  /** 键为标签的表达式安全形式（连字符转下划线，如 team-default → team_default） */
  tags: Record<string, number>;
}
export interface ClientTemplateChipOption {
  value: string;
  text: string;
  dot?: string;
}
export interface ClientTemplateListResult {
  items: ClientTemplateRecord[];
  total: number;
  counts: ClientTemplateCounts;
  /** 快筛 Chip 组：status 单选（含「全部」），tags 多选（含「全部」，点击清空） */
  filters: {
    status: ClientTemplateChipOption[];
    tags: ClientTemplateChipOption[];
  };
}
// ── from clients-mock ──
export type ClientStatus = "enabled" | "disabled" | "expired";

/** 客户端配额规则：在模板规则之上带运行用量（Runtime 维护，控制台近实时展示） */
export interface ClientQuotaRule extends QuotaRule {
  /** 已用量 */
  usageUsed?: number;
  /** 千分位已用量文案 */
  usageUsedText?: string;
  /** 用量百分比（Progress 组件 value 直接绑定） */
  usagePercent?: number;
  /** 下次重置时间（仅周期循环），如「今日 24:00」 */
  nextResetAt?: string;
}
export interface ClientRecord {
  code: string;
  name: string;
  tags: string[];
  description: string;
  status: ClientStatus;
  /** 启用 / 停用 / 已过期 */
  statusLabel: string;
  /** 到期时间（绝对时间），如 2027-01-01 23:59:59 */
  expiresAt: string;
  /** AppKey ID，如 gw-ak-7f3a2b1c */
  appKeyId: string;
  /** Secret 上次轮换时间 */
  secretLastRotatedAt: string;
  /** 来源模板（空白创建时为空） */
  templateCode?: string;
  templateName?: string;
  authRoutes: AuthRoute[];
  quotas: ClientQuotaRule[];
  rateLimits: RateLimitRule[];
  createdAt: string;
  updatedAt: string;
  operator: string;
  // ── 预计算摘要（表达式不适合拼接复合文案） ──
  routeCount: number;
  quotaSummary: string;
  rateLimitSummary: string;
  tagsText: string;
  /** Header 摘要：`gw-ak-7f3a2b1c · 到期 2027-01-01 · 3 路由 · 1000 RPM` */
  appKeySummary: string;
}
export interface ClientListParams {
  /** all | enabled | disabled | expired */
  status?: string;
  /** 名称模糊匹配 */
  name?: string;
  /** 标识模糊匹配 */
  code?: string;
  /** 标签多选（取交集） */
  tags?: string[];
}
export interface ClientCounts {
  all: { enabled: number; total: number };
  status: Record<ClientStatus, number>;
  /** 键为标签的表达式安全形式（连字符转下划线） */
  tags: Record<string, number>;
}
export interface ClientChipOption {
  value: string;
  text: string;
  dot?: string;
}
export interface ClientListResult {
  items: ClientRecord[];
  total: number;
  counts: ClientCounts;
  /** 快筛 Chip 组：tags 多选（含「全部」，点击清空） */
  filters: { tags: ClientChipOption[] };
}
// ── from call-logs-mock ──
export type CallLogStatus =
  | "success"
  | "error"
  | "unfinished"
  | "cancelled"
  | "other";

/** 路由追踪决策链单步：输入 / 判断 / 输出 / 耗时 / 原因 */
export interface TraceStep {
  name: string;
  input: string;
  decision: string;
  output: string;
  duration: string;
  reason: string;
}
export interface CallLogError {
  code: string;
  type: string;
  /** 上游 message */
  message: string;
}
export interface PromptResponse {
  policy: string;
  policyNote: string;
  /** null = 按策略未留存 */
  prompt: string | null;
  response: string | null;
}
export interface CallLogRecord {
  id: string;
  status: CallLogStatus;
  statusLabel: string;
  /** 2026-09-18 14:32:18 UTC */
  timeUtc: string;
  /** 09-18 14:32:18 UTC */
  timeShort: string;
  latencyMs: number;
  /** 320ms */
  latencyText: string;
  userEmail: string;
  clientCode: string;
  clientName: string;
  appKeyId: string;
  channel: string;
  routeTag: string;
  application: string;
  /** 上下文标签摘要（紧凑展示），如 web · agent */
  tagsText: string;
  routeCode: string;
  routeName: string;
  routeGroup: string;
  protocol: string;
  modelCode: string;
  modelName: string;
  providerCode: string;
  providerName: string;
  inputTokens: number | null;
  outputTokens: number | null;
  cacheReadTokens: number;
  cacheWriteTokens: number;
  /** 用量紧凑格式，如 2.1K/1.8K；无输出为 520/— */
  usageText: string;
  inputTokensText: string;
  outputTokensText: string;
  cacheReadText: string;
  cacheWriteText: string;
  totalTokensText: string;
  costUsd: number | null;
  billingUsd: number | null;
  marginUsd: number | null;
  costText: string;
  billingText: string;
  /** 失败或未计费显示 — */
  marginText: string;
  standardPriceText: string;
  pricingSnapshot: string;
  httpStatusText: string;
  // ── Ingress ──
  ingressMethod: string;
  ingressPath: string;
  authResult: string;
  clientIp: string;
  // ── 路由结果 ──
  alias: string;
  strategyText: string;
  // ── Upstream / 性能 ──
  upstreamEndpoint: string;
  upstreamModel: string;
  upstreamStatusText: string;
  upstreamLatencyText: string;
  ttftText: string;
  retries: number;
  retriesText: string;
  traceId: string;
  // ── 错误 / 路由追踪 / Prompt ──
  error: CallLogError | null;
  trace: TraceStep[];
  pr: PromptResponse;
}
export interface CallLogListParams {
  /** 时间范围（ISO 日期对，含端点；缺省不过滤） */
  start?: string;
  end?: string;
  /** all | success | error | unfinished | cancelled | other */
  status?: string;
  model?: string;
  provider?: string;
  protocol?: string;
  routeGroup?: string;
  /** 用户 Email 模糊 */
  email?: string;
  /** AppKey ID 模糊 */
  appKey?: string;
}
export interface CallLogListResult {
  items: CallLogRecord[];
  /** 全量条数（不随筛选变化） */
  total: number;
  /** 当前筛选命中数（千分位预计算，供「总计: N 个请求」展示） */
  filteredText: string;
}
// ── from costs-mock ──
export interface CostRecord {
  /** 与调用日志成功（已计费）记录的 request id 一致，如 req-9f2a1c4d */
  id: string;
  /** 距当前时间的分钟数（time/shortTime 由此派生，随运行日漂移，
   *  保证默认时间范围（近 7 天）始终命中数据） */
  minutesAgo: number;
  /** 完整时间，如 2026-09-19 08:05:12（由 minutesAgo 派生） */
  time: string;
  /** 列表短时间（MM-DD HH:mm，由 minutesAgo 派生） */
  shortTime: string;
  application: string;
  clientName: string;
  appKeyId: string;
  alias: string;
  modelCode: string;
  modelName: string;
  providerCode: string;
  providerName: string;
  currency: string;
  // ── 用量 ──
  inputTokens: number;
  outputTokens: number;
  /** 0 表示无缓存用量（列表 Cache 列展示 —） */
  cacheReadTokens: number;
  // ── 定价快照（pricing_snapshot，只读） ──
  priceVersion: string;
  priceEffective: string;
  /** 命中档位 UPTO 上限；null = 无上限档（∞） */
  tierUpto: number | null;
  /** 档位单价（$/1M tokens） */
  tierInputPrice: number;
  tierOutputPrice: number;
  tierCacheReadPrice: number | null;
  /** 命中的时段倍率（未命中为 1.0） */
  multiplier: number;
  /** 时段倍率规则文案，如「每天 00:00–08:00 ×0.8」；未命中为说明文字 */
  multiplierRule: string;
  // ── 预计算展示字段 ──
  /** 档位短标签，如 ≤32K / ≤128K / ∞ */
  tierLabel: string;
  /** 倍率短标签，如 0.8x */
  multiplierText: string;
  inputCost: string;
  outputCost: string;
  /** 无缓存用量时为 — */
  cacheCost: string;
  totalCost: string;
  /** Header 摘要：时间 · 客户端（AppKey）· 模型 · 供应商 */
  contextSummary: string;
  /** 档位完整文案，如 UPTO ≤ 32,000 / UPTO ∞（无上限档） */
  tierDetail: string;
  /** 档位单价摘要：输入 $0.2/M · 输出 $1.6/M · 缓存读 — */
  tierPriceText: string;
  inputTokensText: string;
  outputTokensText: string;
  cacheReadTokensText: string;
  /** 费用计算过程，如 2,400 × $0.2 × 0.8 ÷ 1M = $0.0004 */
  inputCalcText: string;
  outputCalcText: string;
  cacheCalcText: string;
}
export interface CostListParams {
  /** Application 模糊 */
  application?: string;
  /** AppKey ID 模糊 */
  appKey?: string;
  /** Alias 模糊 */
  alias?: string;
  /** all | Model Code */
  model?: string;
  /** all | Provider Code */
  provider?: string;
  /** 时间范围（ISO 日期对，含端点；缺省不过滤） */
  start?: string;
  end?: string;
}
export interface CostListResult {
  items: CostRecord[];
  total: number;
}
// ── from audit-mock ──
export interface AuditDiffLine {
  type: "add" | "remove" | "same";
  text: string;
}
export interface AuditContextRow {
  label: string;
  value: string;
}
export interface AuditRecord {
  id: string;
  /** ISO 日期（yyyy-MM-dd），时间范围过滤用 */
  date: string;
  /** 完整时间戳（UTC+8 展示口径） */
  timeUtc: string;
  /** 列表短格式 MM-DD HH:mm */
  timeShort: string;
  operator: string;
  /** 操作展示名（修改 / 创建AppKey / Rotate Secret …） */
  operation: string;
  /** 操作类型过滤值 */
  operationType: string;
  /** 资源类型展示名（Route Policy / API Client …） */
  resourceType: string;
  /** 资源类型过滤值 */
  resourceTypeKey: string;
  resourceId: string;
  ip: string;
  requestId: string;
  userAgent: string;
  /** null = 该操作无此侧快照（创建无变更前 / 删除无变更后） */
  beforeJson: string | null;
  afterJson: string | null;
  diffLines: AuditDiffLine[];
  context: AuditContextRow[];
}
export interface AuditQuery {
  start?: string;
  end?: string;
  operator?: string;
  operation?: string;
  resourceType?: string;
  resourceId?: string;
}
// ── from ops-usage-mock ──
export interface OpsUsageQuery {
  /** ISO 日期对（DatePicker range 值，同仪表盘） */
  start?: string;
  end?: string;
}
export interface UsageDisplay {
  requests: string;
  inputCompact: string;
  inputRaw: string;
  outputCompact: string;
  outputRaw: string;
  cacheHit: string;
  standard: string;
  billed: string;
  cost: string;
  avgBilled: string;
  successRate: string;
  avgLatency: string;
  ttft: string;
  avgUpstream: string;
  tps: string;
  failoverRate: string;
  avgAttempts: string;
}
export interface UsageSummary {
  standard: string;
  billed: string;
  cost: string;
}
export interface ModelUsageRow extends UsageDisplay {
  key: string;
  model: string;
  route: string;
}
export interface ProviderUsageRow extends UsageDisplay {
  key: string;
  provider: string;
}
export interface ClientUsageRow extends UsageDisplay {
  key: string;
  name: string;
  code: string;
  appKeyId: string;
  quotaUsage: string;
}
/** 可靠性展示行（无 Token / 缓存列，错误数随范围缩放） */
export interface QualityDisplay {
  requests: string;
  successRate: string;
  avgLatency: string;
  avgUpstream: string;
  failoverRate: string;
  avgAttempts: string;
  standard: string;
  billed: string;
  cost: string;
}

export interface ProviderQualityRow extends QualityDisplay {
  key: string;
  provider: string;
  /** 失败请求数（随范围缩放） */
  errors: string;
}
export interface ModelProviderCompareRow extends QualityDisplay {
  key: string;
  model: string;
  provider: string;
}
export interface RecentErrorRow {
  id: string;
  /** UTC 时间（MM-DD HH:mm:ss） */
  time: string;
  /** 模型 / 供应商 */
  target: string;
  error: string;
}
// ── from dashboard-mock ──
export interface TrendPoint {
  t: string;
  requests: number;
  tokens: number;
  cost: number;
  latency: number;
}
export interface DistributionItem {
  name: string;
  percent: number;
}
export interface DashboardStats {
  requests: string;
  activeClients: number;
  tokens: string;
  successRate: string;
  failures: string;
  errorRate: string;
  avgLatency: number;
  cost: string;
  avgCost: string;
}
export interface DashboardData {
  realtime: { rpm: number; tpm: string };
  /** Chart yKeys 需要数组，而表达式不支持数组字面量 → 预置映射按指标取 */
  metricKeys: Record<string, string[]>;
  stats: DashboardStats;
  trend: TrendPoint[];
  models: DistributionItem[];
  clients: DistributionItem[];
}
// ── from model-debug-mock ──
export interface ConnectionTestResult {
  ok: boolean;
  latencyMs: number;
  detail: string;
}
export interface ModelDebugParams {
  temperature?: number;
  topP?: number;
  maxTokens?: number;
  reasoningEffort?: string;
  streaming: boolean;
  tools: boolean;
}
export interface ModelDebugResult {
  reply: string;
  tokensIn: number;
  tokensOut: number;
  ttftMs: number;
  latencyMs: number;
  status: number;
  requestId: string;
}
// ── from client-debug-mock ──
export interface DebugIdentity {
  clientCode: string;
  appKeyId: string;
  routeCode: string;
  channel: string;
  routeTag: string;
  user: string;
}
export interface ClientDebugRound {
  id: string;
  /** 第 N 轮（从 1 起） */
  seq: number;
  /** 实际发往 Gateway 的 HTTP Body（pretty JSON 文本） */
  requestJson: string;
  /** 请求报文压缩形态（「压缩」切换） */
  requestCompact: string;
  /** Gateway 返回的完整 Response Body（流式合并后的最终 JSON，pretty 文本） */
  responseJson: string;
  /** 返回报文压缩形态 */
  responseCompact: string;
  status: number;
  latencyMs: number;
  ttftMs: number;
  /** 命中路由组（RouteTag 命中；未指定时取路由首个标签） */
  routeGroup: string;
  /** 命中 Target（模型 · 供应商摘要） */
  target: string;
  tokensIn: number;
  tokensOut: number;
  /** 配额扣减预览文案 */
  quotaPreview: string;
  /** 限流检查结果文案 */
  rateLimitResult: string;
  requestId: string;
  /** assistant 回复全文（流式模拟的目标文本） */
  reply: string;
}
