import { corsHeaders, requestCookies } from "./http";

const BACKEND = process.env.NEXMUX_API_BASE_URL ?? "http://localhost:8082";
const ACCESS_TOKEN_COOKIE = "nexus_platform_access_token";

type Envelope = {
  success?: boolean;
  code?: string | number;
  message?: string;
  data?: unknown;
};

function isEnvelope(value: unknown): value is Envelope {
  return value !== null && typeof value === "object" && "data" in value && ("success" in value || "code" in value);
}

/** 平台接口的 R<T> 拆掉，页面 DSL 直接消费 data。 */
export function unwrapPayload(body: unknown): unknown {
  if (!isEnvelope(body)) return body;
  if (body.success === false) return body;
  return body.data === undefined ? body : body.data;
}

export async function callUpstream(request: Request, apiPath: string): Promise<Response> {
  const url = new URL(request.url);
  const headers = new Headers();
  const token = requestCookies(request).get(ACCESS_TOKEN_COOKIE);
  if (token) headers.set("authorization", `Bearer ${token}`);
  const cookie = request.headers.get("cookie");
  if (cookie) headers.set("cookie", cookie);
  const contentType = request.headers.get("content-type");
  if (contentType) headers.set("content-type", contentType);
  const init: RequestInit = { method: request.method === "HEAD" ? "GET" : request.method, headers };
  if (init.method !== "GET" && init.method !== "HEAD") {
    init.body = await request.text();
  }
  let upstream: Response;
  try {
    upstream = await fetch(`${BACKEND}${apiPath}${url.search}`, init);
  } catch {
    return Response.json(
      { error: "无法连接后端" },
      { status: 502, headers: corsHeaders(request) },
    );
  }
  const text = await upstream.text();
  let payload: unknown = text;
  if (text.length > 0) {
    try {
      payload = unwrapPayload(JSON.parse(text));
    } catch {
      payload = { error: text };
    }
  } else {
    payload = null;
  }
  return Response.json(payload, {
    status: upstream.status,
    headers: { "cache-control": "no-store", ...corsHeaders(request) },
  });
}

/** 系统设置页的 key/value 补丁对应平台上的分项 PUT。 */
const SETTING_PUT: Record<string, { path: string; field: string }> = {
  siteName: { path: "/api/d/platform/settings/console-site-name", field: "siteName" },
  description: { path: "/api/d/platform/settings/console-site-description", field: "description" },
  language: { path: "/api/d/platform/settings/console-display-language", field: "language" },
  timezone: { path: "/api/d/platform/settings/console-timezone", field: "timezone" },
};

export async function proxySystemSettings(request: Request): Promise<Response> {
  const method = request.method === "HEAD" ? "GET" : request.method;
  if (method === "GET") {
    const response = await callUpstream(request, "/api/d/platform/settings/console-general");
    if (!response.ok) return response;
    const data = (await response.json()) as Record<string, unknown>;
    return Response.json(data, {
      headers: { "cache-control": "no-store", ...corsHeaders(request) },
    });
  }
  if (method === "PATCH") {
    const input = (await request.json().catch(() => null)) as { key?: unknown; value?: unknown } | null;
    const mapped = typeof input?.key === "string" ? SETTING_PUT[input.key] : undefined;
    if (!mapped || typeof input?.value !== "string") {
      return Response.json({ error: "设置项无效" }, { status: 400, headers: corsHeaders(request) });
    }
    return callUpstream(
      new Request(request.url, {
        method: "PUT",
        headers: request.headers,
        body: JSON.stringify({ [mapped.field]: input.value }),
      }),
      mapped.path,
    );
  }
  return Response.json({ error: "不支持的请求方法" }, { status: 405, headers: corsHeaders(request) });
}

const ROLE_CODES: Record<string, string> = {
  admin: "mux_admin",
  member: "mux_operator",
  guest: "mux_observer",
};

async function encryptPassword(password: string): Promise<string> {
  const response = await callUpstream(
    new Request("http://module.local/rsa"),
    "/api/d/platform/public/auth/rsa-public-key",
  );
  const body = (await response.json()) as { publicKeyBase64?: string; message?: string };
  if (!response.ok || !body.publicKeyBase64) {
    throw new Error(body.message ?? "无法获取登录公钥");
  }
  const key = await crypto.subtle.importKey(
    "spki",
    Buffer.from(body.publicKeyBase64, "base64"),
    { name: "RSA-OAEP", hash: "SHA-256" },
    false,
    ["encrypt"],
  );
  const encrypted = await crypto.subtle.encrypt(
    { name: "RSA-OAEP" },
    key,
    new TextEncoder().encode(password),
  );
  return Buffer.from(encrypted).toString("base64");
}

export async function proxyUsers(request: Request): Promise<Response> {
  const method = request.method === "HEAD" ? "GET" : request.method;
  if (method === "GET") {
    const response = await callUpstream(request, "/api/d/platform/users");
    if (!response.ok) return response;
    const page = (await response.json()) as { records?: Array<Record<string, unknown>> };
    const rows = (page.records ?? []).map((user) => ({
      id: user.adminUserId,
      name: user.displayName ?? user.loginName,
      username: user.loginName,
      email: user.email ?? "",
      role: "",
      status: user.status === "ACTIVE" ? "ENABLED" : user.status,
      createdAt: typeof user.createdAt === "string" ? user.createdAt.slice(0, 10) : "",
    }));
    return Response.json(rows, { headers: { "cache-control": "no-store", ...corsHeaders(request) } });
  }
  if (method === "POST") {
    const input = (await request.json().catch(() => ({}))) as {
      name?: string;
      username?: string;
      email?: string;
      role?: string;
      password?: string;
    };
    if (!input.username?.trim() || !input.password) {
      return Response.json(
        { ok: false, error: "创建平台用户需要登录名和密码" },
        { status: 400, headers: corsHeaders(request) },
      );
    }
    const encryptedPassword = await encryptPassword(input.password);
    return callUpstream(
      new Request(request.url, {
        method: "POST",
        headers: request.headers,
        body: JSON.stringify({
          loginName: input.username.trim(),
          displayName: input.name?.trim() || input.username.trim(),
          email: input.email?.trim(),
          roleCodes: ROLE_CODES[input.role ?? ""] ?? input.role,
          encryptedPassword,
          securityRealm: "PLATFORM",
        }),
      }),
      "/api/d/platform/users",
    );
  }
  if (method === "PATCH") {
    const input = (await request.json().catch(() => ({}))) as {
      id?: string;
      action?: string;
      status?: string;
      name?: string;
      email?: string;
    };
    if (!input.id) {
      return Response.json({ ok: false, error: "缺少用户" }, { status: 400, headers: corsHeaders(request) });
    }
    if (input.action === "status") {
      const status = input.status === "DISABLED" ? "DISABLED" : "ACTIVE";
      return callUpstream(
        new Request(request.url, {
          method: "PUT",
          headers: request.headers,
          body: JSON.stringify({ status }),
        }),
        `/api/d/platform/users/${input.id}/status`,
      );
    }
    return callUpstream(
      new Request(request.url, {
        method: "PUT",
        headers: request.headers,
        body: JSON.stringify({ displayName: input.name, email: input.email }),
      }),
      `/api/d/platform/users/${input.id}`,
    );
  }
  if (method === "DELETE") {
    return Response.json(
      { ok: false, error: "平台未提供删除用户接口" },
      { status: 405, headers: corsHeaders(request) },
    );
  }
  return Response.json(
    { error: "不支持的请求方法" },
    { status: 405, headers: corsHeaders(request) },
  );
}

export async function proxyAuditLogs(request: Request): Promise<Response> {
  if (request.method !== "GET" && request.method !== "HEAD") {
    return Response.json(
      { error: "不支持的请求方法" },
      { status: 405, headers: corsHeaders(request) },
    );
  }
  const incoming = new URL(request.url);
  const page = incoming.searchParams.get("page") ?? incoming.searchParams.get("pageNo") ?? "1";
  const target = new URL(`${BACKEND}/api/d/platform/audit-logs`);
  target.searchParams.set("pageNo", page);
  target.searchParams.set("pageSize", "10");
  const keyword = incoming.searchParams.get("keyword");
  if (keyword) target.searchParams.set("input", keyword);
  const response = await callUpstream(
    new Request(target.toString(), { method: "GET", headers: request.headers }),
    "/api/d/platform/audit-logs",
  );
  if (!response.ok) return response;
  const body = (await response.json()) as { records?: Array<Record<string, unknown>>; total?: number };
  const records = (body.records ?? []).map((row) => ({
    id: row.logId,
    time: row.occurredAt,
    user: row.operatorName,
    action: row.summary ?? row.operationType,
    result: row.result === "SUCCESS" ? "成功" : row.result === "FAILURE" ? "失败" : row.result,
    ip: row.operatorId ?? "",
  }));
  return Response.json(
    { records, total: body.total ?? records.length },
    { headers: { "cache-control": "no-store", ...corsHeaders(request) } },
  );
}
