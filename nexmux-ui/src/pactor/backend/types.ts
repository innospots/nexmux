/**
 * nexmux-spring 后端数据结构（服务端 only）。
 *
 * 权威来源：后端 OpenAPI（/openapi/specs/nexmux-kernel）的 components.schemas，
 * 字段口径见 docs/design/nexmux-ui-data-api-inventory.md §1。
 * 各 adapter 一律从这里 import，不再各自手工镜像 VO。
 *
 * 约定：
 * - required 仅含 spec 中声明必填的字段，其余一律可选（后端可缺省/为 null）；
 * - 枚举值（status / keyStatus / modelType 等）保持 string，取值约束写在注释里；
 * - 日期时间均为 ISO 文本（date-time），页面展示格式由 adapter 派生。
 */

/* ── Provider ─────────────────────────────────────────────── */

/** 模型服务提供方概要（GET /providers 行） */
export interface ProviderVo {
  /** 提供方 ID（技术主键） */
  providerId: string;
  /** 唯一编码（稳定业务键） */
  providerCode: string;
  displayName: string;
  /** COMMERCIAL / CLOUD / PRIVATE / CUSTOM / OPENAI… */
  providerType?: string;
  defaultProtocol?: string;
  vendorCode?: string;
  sourceVendorCode?: string;
  iconUri?: string;
  baseUrl?: string;
  region?: string;
  timeoutMs?: number;
  /** ENABLED / DISABLED */
  status?: string;
  /** configured / pending / none（由凭证表派生） */
  keyStatus?: string;
  /** 主绑定引用该提供方的模型数 */
  modelCount?: number;
}

/** 提供方下的模型行 */
export interface ProviderModelRowVo {
  modelCode: string;
  displayName: string;
  modelType?: string;
  /** 上游模型 ID */
  providerModelId?: string;
  /** ENABLED / DISABLED */
  status?: string;
}

/** 提供方详情（GET /providers/by-code/{code}） */
export interface ProviderDetailVo extends Omit<ProviderVo, "modelCount"> {
  proxy?: string;
  description?: string;
  /** 能力扩展（JSON 文本） */
  capabilityExtension?: string;
  /** 密钥版本；无凭证为 null */
  keyVersion?: number;
  /** 密钥最近轮换时间 */
  rotatedAt?: string;
  /** 主绑定在该提供方下的模型行 */
  models?: ProviderModelRowVo[];
  createdAt?: string;
  updatedAt?: string;
  updatedBy?: string;
}

/** 提供方连通性测试结果 */
export interface ConnectionTestVo {
  ok: boolean;
  latencyMs: number;
  detail?: string;
}

/** 上游拉取的模型行 */
export interface UpstreamModelVo {
  providerModelId: string;
  /** 本地是否已建模 */
  existsLocally: boolean;
}

export interface ProviderCreateRequest {
  providerCode: string;
  displayName: string;
  providerType?: string;
  defaultProtocol?: string;
  vendorCode?: string;
  sourceVendorCode?: string;
  iconUri?: string;
  baseUrl?: string;
  region?: string;
  timeoutMs?: number;
}

export interface ProviderUpdateRequest {
  displayName?: string;
  baseUrl?: string;
  region?: string;
  timeoutMs?: number;
}

/* ── Model ────────────────────────────────────────────────── */

/** 模型配置概要（GET /models 行；modelCode 形如 openai/o4-mini） */
export interface ModelVo {
  modelId: string;
  modelCode: string;
  displayName: string;
  /** DRAFT / ENABLED / DISABLED */
  status?: string;
  /** LLM / Embedding / Image / Audio */
  modelType?: string;
  contextLength?: number;
  maxOutputTokens?: number;
  primaryProviderId?: string;
  /** 定价整包（JSON 文本） */
  pricingSpec?: string;
  /** 扩展绑定（JSON 数组文本，ModelBindingItem[]） */
  extraBindings?: string;
}

/** 模型详情（GET /models/by-code/{modelCode}） */
export interface ModelDetailVo extends ModelVo {
  iconUri?: string;
  releaseDate?: string;
  /** 输入模态（逗号分隔） */
  inputModalities?: string;
  /** 输出模态（逗号分隔） */
  outputModalities?: string;
  /** 能力标签（JSON 数组文本） */
  capabilityTags?: string;
  /** 标签（JSON 数组文本） */
  tags?: string;
  description?: string;
  /** 元数据（JSON 文本） */
  metadataSpec?: string;
  /** 默认参数（JSON 文本） */
  defaultParams?: string;
  primaryProviderModelId?: string;
  primaryUpstreamProtocol?: string;
  primaryBindingWeight?: number;
  /** 主绑定成本倍率 C */
  primaryCostMultiplier?: number;
  /** 主绑定参考倍率 M */
  primaryReferenceMultiplier?: number;
  primaryBindingEnabled?: boolean;
  createdAt?: string;
  updatedAt?: string;
  updatedBy?: string;
}

export interface ModelCreateRequest {
  modelCode: string;
  displayName: string;
  modelType?: string;
  primaryProviderCode: string;
  primaryProviderModelId?: string;
  primaryUpstreamProtocol?: string;
  contextLength?: number;
  maxOutputTokens?: number;
  inputModalities?: string[];
  outputModalities?: string[];
  tags?: string[];
  description?: string;
}

export interface ModelUpdateRequest {
  displayName?: string;
  description?: string;
  contextLength?: number;
  maxOutputTokens?: number;
  inputModalities?: string[];
  outputModalities?: string[];
  tags?: string[];
}

/* ── Route ────────────────────────────────────────────────── */

/** 路由候选模型与权重（RouteSpec.candidates 行） */
export interface RouteCandidate {
  modelCode: string;
  weight: number;
  /** 启用态；null/缺省 = 启用 */
  enabled?: boolean;
  /** P0 / P1 / P2 */
  priority?: string;
  /** 供应成本倍率 C；null = 继承 Model */
  costRate?: number;
  /** 用户计费倍率 M；null = 继承 Model */
  billingRate?: number;
}

/** 路由规格（schema v1） */
export interface RouteSpec {
  alias?: string;
  /** 入口标识（请求路径） */
  ingress?: string;
  /** FIXED / WEIGHTED / ROUND_ROBIN / PRIORITY / HASH / RANDOM */
  strategyType?: string;
  candidates?: RouteCandidate[];
  filterUnhealthyBindings?: boolean;
  schemaVersion?: number;
  description?: string;
  protocol?: string;
  tags?: string[];
  stickySession?: boolean;
}

/** 路由概要（GET /routes 行） */
export interface RouteVo {
  routeId: string;
  routeCode: string;
  /** 对外逻辑模型名 */
  alias: string;
  displayName: string;
  /** DRAFT / PUBLISHED / DISABLED */
  status?: string;
  routeSpec?: RouteSpec;
  publishedAt?: string;
}

/** 路由详情（GET /routes/by-code/{routeCode}） */
export interface RouteDetailVo extends RouteVo {
  /** 发布快照；未发布为 null */
  publishedRouteSpec?: RouteSpec;
  publishedBy?: string;
  createdAt?: string;
  updatedAt?: string;
  updatedBy?: string;
}

export interface RouteCreateRequest {
  routeCode: string;
  alias: string;
  displayName: string;
  description?: string;
  /** 空 = /v1/chat/completions */
  path?: string;
  protocol?: string;
  /** 空 = FIXED */
  strategyType?: string;
  stickySession?: boolean;
  /** 空 = true */
  healthFilter?: boolean;
  tags?: string[];
  models?: RouteCandidate[];
}

export interface RouteUpdateRequest {
  displayName?: string;
  routeSpec?: RouteSpec;
}

export interface RoutePublishRequest {
  changeSummary?: string;
  operatorId?: string;
}

/* ── Client ───────────────────────────────────────────────── */

/** 接入客户端概要（GET /clients 行） */
export interface ClientVo {
  clientId: string;
  clientCode: string;
  displayName: string;
  /** ENABLED / DISABLED */
  status?: string;
  authorizedRouteIds?: string[];
  /** 配额规格（QuotaRule[] JSON 原文） */
  quotaSpec?: string;
  /** 限流规格（RateLimitRule[] JSON 原文） */
  rateLimitSpec?: string;
  appKeyVersion?: number;
  /** AppKey 展示 ID（gw-ak- + 哈希前 8 位） */
  appKeyId?: string;
  /** 标签（JSON 数组文本） */
  tags?: string;
  sourceTemplateId?: string;
  expiresAt?: string;
  appKeyRotatedAt?: string;
}

/** 接入客户端详情（by-code；tags 为解析后数组；不含 AppKey 明文与完整哈希） */
export interface ClientDetailVo extends Omit<ClientVo, "tags"> {
  tags?: string[];
  createdAt?: string;
  updatedAt?: string;
  updatedBy?: string;
}

export interface ClientCreateRequest {
  clientCode: string;
  displayName: string;
  tags?: string[];
  /** 模板创建：复制授权/配额/限流；未知模板 → NMX130001 */
  sourceTemplateCode?: string;
  /** 空白创建时（模板创建被模板覆盖） */
  routeIds?: string[];
  /** 空白创建时（ISO 文本） */
  expiresAt?: string;
}

/** 创建客户端结果（明文 AppKey 仅此一次） */
export interface ClientCreateResultVo {
  client: ClientVo;
  appKeyPlaintext: string;
}

export interface ClientUpdateRequest {
  displayName?: string;
  quotaSpec?: string;
  rateLimitSpec?: string;
  tags?: string[];
}

/** 轮换 AppKey 结果 */
export interface ClientRotateResultVo {
  clientId: string;
  appKeyPlaintext: string;
  appKeyVersion: number;
}

export interface ClientRouteGrantsRequest {
  routeIds?: string[];
}

/* ── ClientTemplate ───────────────────────────────────────── */

/** 客户端模板概要（GET /client-templates 行） */
export interface ClientTemplateVo {
  templateId: string;
  templateCode: string;
  displayName: string;
  /** ENABLED / DISABLED */
  status?: string;
  /** 标签（JSON 数组文本） */
  tags?: string;
  /** days / fixed / never */
  validityType?: string;
  validityDays?: number;
  /** fixed 时的固定到期日 */
  validTo?: string;
  authorizedRouteIds?: string[];
  quotaSpec?: string;
  rateLimitSpec?: string;
  /** 按 source_template_id 实时统计 */
  createdClientCount?: number;
}

/** 客户端模板详情（by-code；tags 为解析后数组） */
export interface ClientTemplateDetailVo extends Omit<ClientTemplateVo, "tags"> {
  description?: string;
  tags?: string[];
  createdAt?: string;
  updatedAt?: string;
  updatedBy?: string;
}

export interface ClientTemplateCreateRequest {
  templateCode: string;
  displayName: string;
  description?: string;
  tags?: string[];
  validityType?: string;
  /** days 时必填 */
  validityDays?: number;
  /** fixed 时必填（ISO 日期） */
  validityFixedDate?: string;
  routeIds?: string[];
  quotaSpec?: string;
  rateLimitSpec?: string;
}

export interface ClientTemplateUpdateRequest {
  displayName?: string;
  description?: string;
  tags?: string[];
  validityType?: string;
  validityDays?: number;
  validityFixedDate?: string;
  routeIds?: string[];
  quotaSpec?: string;
  rateLimitSpec?: string;
}

/* ── 调用日志 / 审计 / 成本 ────────────────────────────────── */

/** 网关请求日志概要 */
export interface RequestLogVo {
  requestId: string;
  clientId?: string;
  routeId?: string;
  modelCode?: string;
  status?: string;
  latencyMs?: number;
  errorCode?: string;
  routeTrace?: string;
  requestedAt?: string;
  appKeyId?: string;
  userEmail?: string;
  providerCode?: string;
  protocol?: string;
  routeGroup?: string;
  application?: string;
  alias?: string;
  httpStatus?: number;
  inputTokens?: number;
  outputTokens?: number;
  cacheReadTokens?: number;
  cacheWriteTokens?: number;
  costUsd?: number;
  billingUsd?: number;
}

/** 网关请求日志详情 */
export interface RequestLogDetailVo extends RequestLogVo {
  /** 首 token 耗时（毫秒） */
  ttftMs?: number;
  upstreamModel?: string;
  upstreamEndpoint?: string;
  upstreamStatus?: string;
  upstreamLatencyMs?: number;
  retries?: number;
  clientIp?: string;
  ingressMethod?: string;
  ingressPath?: string;
  traceId?: string;
  errorType?: string;
  /** 定价快照（JSON 原文） */
  pricingSnapshot?: string;
  /** Prompt/Response 快照（JSON 原文，受保留策略截断） */
  promptSnapshot?: string;
  createdAt?: string;
}

/** 配置变更审计日志概要 */
export interface AuditLogVo {
  auditId: string;
  resourceType?: string;
  resourceId?: string;
  operation?: string;
  changeSummary?: string;
  changeDiff?: string;
  operatorId?: string;
  clientIp?: string;
  requestId?: string;
  userAgent?: string;
  createdAt?: string;
}

/** 配置变更审计日志详情（changeDiff 为 JSON 原文：信封 {before, after} 或单快照） */
export type AuditLogDetailVo = AuditLogVo;

/** 成本明细记录 */
export interface CostRecordVo {
  requestId: string;
  requestedAt?: string;
  application?: string;
  clientId?: string;
  appKeyId?: string;
  alias?: string;
  modelCode?: string;
  providerCode?: string;
  inputTokens?: number;
  outputTokens?: number;
  cacheReadTokens?: number;
  costUsd?: number;
  billingUsd?: number;
  /** 定价快照（JSON 原文：档位/倍率/价格版本） */
  pricingSnapshot?: string;
}
