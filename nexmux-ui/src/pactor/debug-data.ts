/**
 * 调试页浏览器侧数据源：全部经数据接口转发层（/api/d/*）取服务端数据。
 * 与页面 actions（src/pactor/actions.ts）配套；错误以异常上抛，
 * 由 action handler 转为页面错误消息展示。
 *
 * 路径即契约：/api/d/* 路径即服务端接口路径（docs/design/
 * nexmux-ui-missing-api.md §〇）；接口未实现时转发层回退空数据，
 * 相关选项为空、写操作按成功占位处理。
 */

// ── 页面契约类型（/api/d 响应形状） ─────────────────────────────

export interface ProviderModel {
  code: string;
  name: string;
  type: string;
  providerModelId: string;
  pricing: string;
  status: "ONLINE" | "OFFLINE";
  routes: number;
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

export interface AuthRoute {
  code: string;
  name: string;
  alias: string;
  typeLabel: string;
  path: string;
}

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

// ── http 基础 ──────────────────────────────────────────────────

async function callJson<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, {
    headers: { "content-type": "application/json" },
    ...init,
  });
  if (!response.ok) {
    const body = await response.json().catch(() => ({}));
    throw new Error((body as { error?: string }).error ?? `HTTP ${response.status}`);
  }
  return response.json() as Promise<T>;
}

// ── 选项数据 ───────────────────────────────────────────────────

export async function modelOptionsOf(
  providerCode: string,
): Promise<{ value: string; label: string }[]> {
  const detail = await callJson<{ models?: ProviderModel[] }>(
    `/api/d/providers/${encodeURIComponent(providerCode)}`,
  );
  return (detail.models ?? [])
    .filter((m) => m.status === "ONLINE")
    .map((m) => ({ value: m.code, label: `${m.name}（${m.providerModelId}）` }));
}

interface ModelDetailForDebug {
  params?: Partial<ModelParams>;
}

export async function capabilitiesOf(modelCode: string): Promise<string[]> {
  const detail = await callJson<ModelDetailForDebug>(
    `/api/d/models/${encodeURIComponent(modelCode)}`,
  );
  return detail.params?.capabilities ?? [];
}

export async function supportsReasoning(modelCode: string): Promise<boolean> {
  const detail = await callJson<ModelDetailForDebug>(
    `/api/d/models/${encodeURIComponent(modelCode)}`,
  );
  const effort = detail.params?.reasoningEffort;
  return !!effort && effort !== "—";
}

export async function paramDefaultsOf(modelCode: string): Promise<{
  temperature?: number;
  topP?: number;
  maxTokens?: number;
  reasoningEffort?: string;
}> {
  const detail = await callJson<ModelDetailForDebug>(
    `/api/d/models/${encodeURIComponent(modelCode)}`,
  );
  const num = (v?: string) => (v === undefined || v === "—" || v === "" ? undefined : Number(v));
  return {
    temperature: num(detail.params?.temperature),
    topP: num(detail.params?.topP),
    maxTokens: num(detail.params?.maxTokens),
    reasoningEffort:
      detail.params?.reasoningEffort && detail.params.reasoningEffort !== "—"
        ? detail.params.reasoningEffort
        : undefined,
  };
}

export async function appKeyOptionsOf(
  clientCode: string,
): Promise<{ value: string; label: string }[]> {
  const detail = await callJson<{ appKeyId?: string }>(
    `/api/d/clients/${encodeURIComponent(clientCode)}`,
  );
  return detail.appKeyId ? [{ value: detail.appKeyId, label: detail.appKeyId }] : [];
}

export async function routeOptionsOf(
  clientCode: string,
): Promise<{ value: string; label: string }[]> {
  const detail = await callJson<{ authRoutes?: AuthRoute[] }>(
    `/api/d/clients/${encodeURIComponent(clientCode)}`,
  );
  return (detail.authRoutes ?? []).map((r) => ({ value: r.code, label: `${r.name}（${r.alias}）` }));
}

export async function routeTagOptionsOf(
  routeCode: string,
): Promise<{ value: string; label: string }[]> {
  const detail = await callJson<{ tags?: string[] }>(
    `/api/d/routes/${encodeURIComponent(routeCode)}`,
  );
  return (detail.tags ?? []).map((t) => ({ value: t, label: t }));
}

// ── 写动作（ops/ 前缀 POST 约定） ──────────────────────────────

export async function testConnectionReal(providerCode: string): Promise<ConnectionTestResult> {
  return callJson<ConnectionTestResult>(
    `/api/d/ops/provider-connection-test?code=${encodeURIComponent(providerCode)}`,
    { method: "POST" },
  );
}

export async function simulateModelCallReal(
  _providerCode: string,
  modelCode: string,
  systemPrompt: string,
  params: ModelDebugParams,
  userText: string,
): Promise<ModelDebugResult> {
  return callJson<ModelDebugResult>(`/api/d/ops/debug-model-invoke`, {
    method: "POST",
    body: JSON.stringify({
      modelCode,
      systemPrompt,
      text: userText,
      chatParams: {
        temperature: params.temperature,
        topP: params.topP,
        maxTokens: params.maxTokens,
        reasoningEffort: params.reasoningEffort,
      },
    }),
  });
}

export async function simulateClientCallReal(
  context: {
    clientCode: string;
    appKeyId: string;
    routeCode: string;
    channel: string;
    routeTag: string;
    user: string;
  },
  userText: string,
  seq: number,
): Promise<ClientDebugRound> {
  const result = await callJson<Partial<ClientDebugRound>>(
    `/api/d/ops/debug-client-invoke`,
    {
      method: "POST",
      body: JSON.stringify({
        clientCode: context.clientCode,
        routeCode: context.routeCode,
        text: userText,
        routeTag: context.routeTag,
      }),
    },
  );
  return { id: `round-${seq}`, seq, ...result } as ClientDebugRound;
}
