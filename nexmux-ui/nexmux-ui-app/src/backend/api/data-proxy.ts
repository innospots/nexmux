import { readPlatformAccessToken, resolveSessionUser } from "@/backend/auth/accounts";
import { corsHeaders } from "@/backend/cors";
import { cookiesFrom } from "@/backend/api/cookies";
import { serverBaseUrl } from "@/frontend/config";

/** 读请求在上游缺失时返回失败，不再伪造空数据。 */
function emptyData(request: Request): Response {
  return Response.json(
    { success: false, code: "UPSTREAM_UNAVAILABLE", message: "后端接口不可用" },
    { status: 502, headers: { "cache-control": "no-store", ...corsHeaders(request) } },
  );
}

async function forward(request: Request, parts: string[], accessToken?: string): Promise<Response> {
  const url = new URL(request.url);
  const target = `${serverBaseUrl}/api/d/${parts.join("/")}${url.search}`;
  const headers = new Headers();
  const cookie = request.headers.get("cookie");
  if (cookie) {
    headers.set("cookie", cookie);
  }
  if (accessToken) {
    headers.set("authorization", `Bearer ${accessToken}`);
  }
  // 语言偏好：Cookie 优先（console 语言开关写 locale=messages 的同一 Cookie），
  // 缺失时沿用浏览器 Accept-Language，供后端多语言字段（如供应商模板）解析。
  const localeCookie = readLocaleCookie(cookie);
  if (localeCookie) {
    headers.set("accept-language", localeCookie);
  } else {
    const acceptLanguage = request.headers.get("accept-language");
    if (acceptLanguage) {
      headers.set("accept-language", acceptLanguage);
    }
  }
  const contentType = request.headers.get("content-type");
  if (contentType) {
    headers.set("content-type", contentType);
  }
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
    return Response.json(
      { success: false, code: "NOT_FOUND", message: "后端接口不存在" },
      { status: 404, headers: resHeaders },
    );
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

/** 从请求 Cookie 头解析语言偏好（locale=zh-CN / en-US），非法值返回 null。 */
function readLocaleCookie(cookie: string | null): string | null {
  if (!cookie) {
    return null;
  }
  for (const part of cookie.split(";")) {
    const [name, ...rest] = part.split("=");
    if (name?.trim() === "locale") {
      const value = decodeURIComponent(rest.join("=").trim());
      if (value === "zh-CN" || value === "en-US") {
        return value;
      }
      return null;
    }
  }
  return null;
}

function isAnonymousPlatformPublicPath(parts: string[]): boolean {
  return parts.length >= 2 && parts[0] === "platform" && parts[1] === "public";
}

/** 转发 /api/d/*。会话从请求 Cookie 读取，Next 路由与模块 server 共用。 */
export async function proxyDataRequest(request: Request, parts: string[]): Promise<Response> {
  const store = cookiesFrom(request);
  const accessToken = readPlatformAccessToken(store);
  if (!isAnonymousPlatformPublicPath(parts) && !resolveSessionUser(store)) {
    return Response.json({ error: "未登录" }, { status: 401, headers: corsHeaders(request) });
  }
  return forward(request, parts, accessToken);
}

/** 从逻辑路径 /api/d/... 取出转发段。 */
export function dataPartsFromPath(pathname: string): string[] {
  const prefix = "/api/d";
  const rest = pathname === prefix || pathname.startsWith(`${prefix}/`)
    ? pathname.slice(prefix.length)
    : pathname;
  return rest.replace(/^\//, "").split("/").filter(Boolean);
}
