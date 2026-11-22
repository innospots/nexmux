import { cookiesFrom } from "@/backend/api/cookies";
import { readPlatformAccessToken, resolveSessionUser } from "@/backend/auth/accounts";
import { corsHeaders } from "@/backend/cors";
import { serverBaseUrl } from "@/frontend/config";

/** 平台用户列表。不再返回演示账号。 */
export async function listUsers(request: Request): Promise<Response> {
  const store = cookiesFrom(request);
  if (!resolveSessionUser(store)) {
    return Response.json({ error: "未登录" }, { status: 401, headers: corsHeaders(request) });
  }
  const token = readPlatformAccessToken(store);
  let upstream: Response;
  try {
    upstream = await fetch(`${serverBaseUrl}/api/d/platform/users`, {
      headers: token ? { authorization: `Bearer ${token}` } : {},
      cache: "no-store",
    });
  } catch {
    return Response.json(
      { success: false, message: "后端接口不可用" },
      { status: 502, headers: corsHeaders(request) },
    );
  }
  const body = await upstream.arrayBuffer();
  return new Response(body, {
    status: upstream.status,
    headers: {
      "content-type": upstream.headers.get("content-type") ?? "application/json",
      "cache-control": "no-store",
      ...corsHeaders(request),
    },
  });
}
