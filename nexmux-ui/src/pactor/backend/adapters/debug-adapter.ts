/**
 * 调试台适配器（BFF 写通道）：组 OpenAI payload → /debug/*-invoke → 解析为
 * 前端调试页形状（ModelDebugResult / ClientDebugRound）。
 * 失败语义：上游/凭证错误以结果体返回（status + 可读 reply），不抛异常（Q10-A）。
 */

import { backendClient } from "../client";

// ── 后端 VO 镜像 ──────────────────────────────────────────────

interface DebugInvokeResultVo {
  requestId: string;
  auditClass: string;
  httpStatus: number;
  body: string;
  latencyMs: number;
}

interface ModelDetailLite {
  modelId: string;
  modelCode: string;
  displayName?: string;
  primaryProviderId?: string;
}

interface ClientDetailLite {
  clientId: string;
  clientCode: string;
}

interface RouteDetailLite {
  routeId: string;
  alias: string;
  displayName?: string;
  tags?: string[];
}

// ── 结果形状（与调试页 mock 对齐） ─────────────────────────────

export interface ModelDebugRealResult {
  reply: string;
  tokensIn: number;
  tokensOut: number;
  ttftMs: number;
  latencyMs: number;
  status: number;
  requestId: string;
}

export interface ClientDebugRealRound extends ModelDebugRealResult {
  requestJson: string;
  requestCompact: string;
  responseJson: string;
  responseCompact: string;
  target: string;
  routeGroup: string;
  quotaPreview: string;
  rateLimitResult: string;
}

// ── payload 组装 ───────────────────────────────────────────────

interface ChatParams {
  temperature?: number;
  topP?: number;
  maxTokens?: number;
  reasoningEffort?: string;
  streaming?: boolean;
  tools?: boolean;
}

function buildPayload(modelCode: string, systemPrompt: string, text: string, params: ChatParams) {
  const messages: { role: string; content: string }[] = [];
  if (systemPrompt) messages.push({ role: "system", content: systemPrompt });
  messages.push({ role: "user", content: text });
  const payload: Record<string, unknown> = { model: modelCode, messages, stream: false };
  if (params.temperature != null) payload.temperature = params.temperature;
  if (params.topP != null) payload.top_p = params.topP;
  if (params.maxTokens != null) payload.max_tokens = params.maxTokens;
  return payload;
}

// ── 响应解析 ───────────────────────────────────────────────────

interface ChatCompletionBody {
  choices?: { message?: { content?: string } }[];
  usage?: { prompt_tokens?: number; completion_tokens?: number };
  error?: string | { message?: string };
}

function parseResult(vo: DebugInvokeResultVo, latencyMs: number): ModelDebugRealResult {
  let body: ChatCompletionBody = {};
  try {
    body = JSON.parse(vo.body) as ChatCompletionBody;
  } catch {
    /* 非 JSON 响应原文 */
  }
  const errorText =
    typeof body.error === "string"
      ? body.error
      : body.error?.message ?? (vo.httpStatus >= 400 ? vo.body.slice(0, 200) : null);
  return {
    reply: errorText
      ? `【调用失败 ${vo.httpStatus}】${errorText}`
      : (body.choices?.[0]?.message?.content ?? vo.body.slice(0, 500)),
    tokensIn: body.usage?.prompt_tokens ?? 0,
    tokensOut: body.usage?.completion_tokens ?? 0,
    // 非流式调用无真实 TTFT：以整体延迟近似（页面上报文侧可见完整耗时）
    ttftMs: latencyMs,
    latencyMs,
    status: vo.httpStatus,
    requestId: vo.requestId,
  };
}

// ── 对外服务 ───────────────────────────────────────────────────

export async function invokeModelDebug(params: {
  modelCode: string;
  systemPrompt?: string;
  text: string;
  chatParams?: ChatParams;
}): Promise<ModelDebugRealResult> {
  const model = await backendClient.get<ModelDetailLite>(
    `/api/nexmux/models/by-code/${params.modelCode.split("/").map(encodeURIComponent).join("/")}`,
  );
  const payload = buildPayload(
    params.modelCode,
    params.systemPrompt ?? "",
    params.text,
    params.chatParams ?? {},
  );
  const vo = await backendClient.post<DebugInvokeResultVo>("/api/nexmux/debug/model-invoke", {
    modelId: model.modelId,
    payload: JSON.stringify(payload),
  });
  return parseResult(vo, vo.latencyMs);
}

export async function invokeClientDebug(params: {
  clientCode: string;
  routeCode: string;
  text: string;
  routeTag?: string;
}): Promise<ClientDebugRealRound> {
  const [client, route] = await Promise.all([
    backendClient.get<ClientDetailLite>(
      `/api/nexmux/clients/by-code/${encodeURIComponent(params.clientCode)}`,
    ),
    backendClient.get<RouteDetailLite & { routeSpec?: { tags?: string[] } }>(
      `/api/nexmux/routes/by-code/${encodeURIComponent(params.routeCode)}`,
    ),
  ]);
  const payload = buildPayload(params.routeCode, "", params.text, {});
  const payloadText = JSON.stringify(payload, null, 2);
  const vo = await backendClient.post<DebugInvokeResultVo>("/api/nexmux/debug/client-invoke", {
    clientId: client.clientId,
    routeAlias: route.alias,
    payload: JSON.stringify(payload),
  });
  const result = parseResult(vo, vo.latencyMs);
  const routeTags = (route as { routeSpec?: { tags?: string[] } }).routeSpec?.tags ?? [];
  return {
    ...result,
    requestJson: payloadText,
    requestCompact: JSON.stringify(payload),
    responseJson: (() => {
      try {
        return JSON.stringify(JSON.parse(vo.body), null, 2);
      } catch {
        return vo.body;
      }
    })(),
    responseCompact: vo.body,
    target: route.displayName ?? params.routeCode,
    routeGroup: params.routeTag || routeTags[0] || "—",
    quotaPreview: "—（Runtime 未建，联调不扣减）",
    rateLimitResult: "—（Runtime 未建）",
  };
}
