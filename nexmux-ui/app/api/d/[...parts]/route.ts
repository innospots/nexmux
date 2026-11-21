import { cookies } from "next/headers";

import { readPlatformAccessToken, resolveSessionUser } from "@/backend/auth/accounts";
import { corsHeaders, corsPreflight } from "@/backend/cors";
import { serverBaseUrl } from "@/frontend/config";

function isReadMethod(method: string): boolean {
  return method === "GET" || method === "HEAD";
}

/** 读请求在上游缺失时返回空对象；写请求返回失败，避免把未执行的变更显示为成功。 */
function emptyData(request: Request): Response {
  if (!isReadMethod(request.method)) {
    return Response.json(
      { success: false, code: "UPSTREAM_UNAVAILABLE", message: "后端接口不可用" },
      { status: 502, headers: { "cache-control": "no-store", ...corsHeaders(request) } },
    );
  }
  return Response.json({}, { headers: { "cache-control": "no-store", ...corsHeaders(request) } });
}

async function forward(request: Request, parts: string[], accessToken?: string): Promise<Response> {
  const url = new URL(request.url);
  const target = `${serverBaseUrl}/api/d/${parts.join("/")}${url.search}`;
  const headers = new Headers();
  const cookie = request.headers.get("cookie");
  if (cookie) headers.set("cookie", cookie);
  if (accessToken) headers.set("authorization", `Bearer ${accessToken}`);
  const contentType = request.headers.get("content-type");
  if (contentType) headers.set("content-type", contentType);
  const init: RequestInit = { method: request.method, headers };
  if (request.method !== "GET" && request.method !== "HEAD") {
    init.body = await request.text();
  }
  let upstream: globalThis.Response;
  try {
    upstream = await fetch(target, init);
  } catch {
    return emptyData(request);
  }
  const upstreamType = upstream.headers.get("content-type") ?? "";
  if (upstreamType.includes("text/event-stream")) {
    return new Response(upstream.body, {
      status: upstream.status,
      headers: {
        "content-type": upstreamType,
        "cache-control": "no-store",
        "x-accel-buffering": "no",
        ...corsHeaders(request),
      },
    });
  }
  const body = await upstream.arrayBuffer();
  const resHeaders: Record<string, string> = {
    "cache-control": "no-store",
    ...corsHeaders(request),
  };
  if (upstream.status === 404) {
    if (upstreamType.includes("json") && body.byteLength > 0) {
      resHeaders["content-type"] = upstreamType;
      return new Response(body, { status: upstream.status, headers: resHeaders });
    }
    return emptyData(request);
  }
  if (!upstreamType.includes("json")) {
    if (upstream.status >= 400) {
      const message = new TextDecoder().decode(body).trim() || upstream.statusText;
      return Response.json(
        { code: upstream.status, message },
        { status: upstream.status, headers: { ...resHeaders, "content-type": "application/json" } },
      );
    }
    return emptyData(request);
  }
  resHeaders["content-type"] = upstreamType;
  return new Response(body, { status: upstream.status, headers: resHeaders });
}

function isAnonymousPlatformPublicPath(parts: string[]): boolean {
  return parts.length >= 2 && parts[0] === "platform" && parts[1] === "public";
}

async function handle(request: Request, context: { params: Promise<{ parts: string[] }> }) {
  const { parts } = await context.params;
  const store = await cookies();
  const accessToken = readPlatformAccessToken(store);
  if (!isAnonymousPlatformPublicPath(parts) && !resolveSessionUser(store)) {
    return Response.json({ error: "未登录" }, { status: 401, headers: corsHeaders(request) });
  }
  return forward(request, parts, accessToken);
}

export const GET = handle;
export const POST = handle;
export const PUT = handle;
export const DELETE = handle;
export const OPTIONS = corsPreflight;
