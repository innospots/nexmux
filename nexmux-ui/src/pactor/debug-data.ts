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

export async function callJson<T>(url: string, init?: RequestInit): Promise<T> {
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

/** 详情路径契约：modelCode 中的 / 以 ~ 替代（服务端 ModelEndpoint 约定）。 */
function modelDetailUrl(modelCode: string): string {
  return `/api/d/models/${encodeURIComponent(modelCode.replaceAll("/", "~"))}`;
}

export async function capabilitiesOf(modelCode: string): Promise<string[]> {
  const detail = await callJson<ModelDetailForDebug>(modelDetailUrl(modelCode));
  return detail.params?.capabilities ?? [];
}

export async function supportsReasoning(modelCode: string): Promise<boolean> {
  const detail = await callJson<ModelDetailForDebug>(modelDetailUrl(modelCode));
  const effort = detail.params?.reasoningEffort;
  return !!effort && effort !== "—";
}

export async function paramDefaultsOf(modelCode: string): Promise<{
  temperature?: number;
  topP?: number;
  maxTokens?: number;
  reasoningEffort?: string;
}> {
  const detail = await callJson<ModelDetailForDebug>(modelDetailUrl(modelCode));
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
  const envelope = await callJson<{ data?: ModelDebugResult } & Partial<ModelDebugResult>>(
    `/api/d/debug/model-invoke`,
    {
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
    },
  );
  return (envelope.data ?? envelope) as ModelDebugResult;
}

// ── 流式调试（SSE）─────────────────────────────────────────────
//
// 契约（服务端 DebugService 流式端点）：上游 chunk 逐行穿透（OpenAI 兼容
// delta），终态 `event: debug-meta`（tokens / 延迟 / 路由命中上下文 /
// requestJson / responseJson），失败 `event: debug-error`，均以
// `data: [DONE]` 收尾。

/** debug-meta 事件负载（空值字段服务端不输出） */
export interface DebugStreamMeta {
  requestId: string;
  status: number;
  latencyMs: number;
  ttftMs: number;
  tokensIn?: number;
  tokensOut?: number;
  routeGroup?: string;
  target?: string;
  quotaPreview?: string;
  rateLimitResult?: string;
  requestJson?: string;
  responseJson?: string;
}

export interface DebugStreamError {
  requestId?: string;
  status: number;
  message: string;
}

export interface DebugStreamHandlers {
  /** 正文增量（choices[0].delta.content 累积追加） */
  onDelta: (text: string) => void;
  /** 正常收尾（debug-meta） */
  onMeta: (meta: DebugStreamMeta) => void;
  /** 失败收尾（debug-error 或传输层失败）；与 onMeta 互斥且必居其一 */
  onError: (error: DebugStreamError) => void;
}

/** SSE 逐事件解析：事件以空行分隔，event: 指定类型，data: 为负载 */
async function readSseStream(
  body: ReadableStream<Uint8Array>,
  dispatch: (event: string, data: string) => boolean | void,
): Promise<void> {
  const reader = body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";
  const handleBlock = (block: string): boolean | void => {
    let event = "message";
    const dataLines: string[] = [];
    for (const line of block.split("\n")) {
      if (line.startsWith("event: ")) event = line.slice(7).trim();
      else if (line.startsWith("data: ")) dataLines.push(line.slice(6));
      else if (line.startsWith("data:")) dataLines.push(line.slice(5).trim());
    }
    if (dataLines.length > 0) return dispatch(event, dataLines.join("\n"));
  };
  for (;;) {
    const { done, value } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });
    let sep: number;
    while ((sep = buffer.indexOf("\n\n")) >= 0) {
      const block = buffer.slice(0, sep);
      buffer = buffer.slice(sep + 2);
      if (handleBlock(block) === true) return;
    }
  }
  if (buffer.trim()) handleBlock(buffer);
}

/** 调试流式调用共用入口：非 SSE 响应（转发层空数据回退 / 未登录）转为 onError */
export async function streamDebugCall(
  url: string,
  body: unknown,
  handlers: DebugStreamHandlers,
  signal?: AbortSignal,
): Promise<void> {
  const response = await fetch(url, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify(body),
    signal,
  });
  const type = response.headers.get("content-type") ?? "";
  if (!response.ok) {
    const payload = await response.json().catch(() => ({}));
    handlers.onError({
      status: response.status,
      message: (payload as { error?: string }).error ?? `HTTP ${response.status}`,
    });
    return;
  }
  if (!type.includes("text/event-stream") || !response.body) {
    handlers.onError({ status: 502, message: "调试流式接口不可用（上游未返回 SSE）" });
    return;
  }
  await readSseStream(response.body, (event, data) => {
    if (event === "debug-meta") {
      handlers.onMeta(JSON.parse(data) as DebugStreamMeta);
      return;
    }
    if (event === "debug-error") {
      handlers.onError(JSON.parse(data) as DebugStreamError);
      return true;
    }
    if (data === "[DONE]") return true;
    try {
      const chunk = JSON.parse(data) as {
        choices?: { delta?: { content?: string } }[];
      };
      const delta = chunk.choices?.[0]?.delta?.content;
      if (typeof delta === "string" && delta) handlers.onDelta(delta);
    } catch {
      // 非 JSON 心跳行等忽略
    }
  });
}

/** 模型调试流式：直连供应商模型 */
export function streamModelCall(
  modelCode: string,
  systemPrompt: string,
  chatParams: {
    temperature?: number;
    topP?: number;
    maxTokens?: number;
    reasoningEffort?: string;
  },
  text: string,
  handlers: DebugStreamHandlers,
  signal?: AbortSignal,
): Promise<void> {
  return streamDebugCall(
    "/api/d/debug/model-invoke/stream",
    { modelCode, systemPrompt, text, chatParams },
    handlers,
    signal,
  );
}

/** 客户端调试流式：客户端 + 路由全链路 */
export function streamClientCall(
  context: { clientCode: string; routeCode: string; routeTag: string },
  text: string,
  handlers: DebugStreamHandlers,
  signal?: AbortSignal,
): Promise<void> {
  return streamDebugCall(
    "/api/d/debug/client-invoke/stream",
    {
      clientCode: context.clientCode,
      routeCode: context.routeCode,
      text,
      routeTag: context.routeTag,
    },
    handlers,
    signal,
  );
}
