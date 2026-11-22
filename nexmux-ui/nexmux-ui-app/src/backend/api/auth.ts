import {
  ACCESS_TOKEN_COOKIE,
  LEGACY_DEMO_SESSION_COOKIE,
  PLATFORM_LOGIN_COOKIE,
} from "@/backend/auth/accounts";
import { corsHeaders } from "@/backend/cors";
import { jsonWithCookies, serializeCookie } from "@/backend/api/cookies";
import { serverBaseUrl } from "@/frontend/config";

type NexusEnvelope<T> = {
  success?: boolean;
  code?: string | number;
  message?: string;
  data?: T;
};

type AuthTokenVo = {
  accessToken?: string;
  refreshToken?: string;
};

function isSuccess(envelope: NexusEnvelope<unknown>): boolean {
  if (envelope.success === false) {
    return false;
  }
  const code = envelope.code;
  if (code === undefined) {
    return true;
  }
  return code === 0 || code === "0" || code === "OK";
}

function sessionCookie(name: string, value: string, maxAge?: number): string {
  return serializeCookie(name, value, {
    httpOnly: true,
    path: "/",
    secure: process.env.NODE_ENV === "production",
    maxAge,
  });
}

/** 演示登录已关闭，请使用平台登录。 */
export function rejectDemoLogin(request: Request): Response {
  return Response.json(
    { error: "请使用平台账号登录" },
    { status: 410, headers: corsHeaders(request) },
  );
}

/** 清平台会话 cookie。 */
export function clearPlatformSession(request: Request): Response {
  return jsonWithCookies(
    { ok: true },
    { headers: corsHeaders(request) },
    [
      sessionCookie(ACCESS_TOKEN_COOKIE, "", 0),
      sessionCookie(PLATFORM_LOGIN_COOKIE, "", 0),
      sessionCookie(LEGACY_DEMO_SESSION_COOKIE, "", 0),
    ],
  );
}

/** 代理平台登录并写入 httpOnly 会话 cookie。 */
export async function platformLogin(request: Request): Promise<Response> {
  const body = (await request.json().catch(() => ({}))) as {
    login?: string;
    encryptedPassword?: string;
  };
  const login = body.login?.trim() ?? "";
  if (!login || !body.encryptedPassword) {
    return Response.json(
      { code: "BAD_REQUEST", message: "请填写登录名与密码" },
      { status: 400, headers: corsHeaders(request) },
    );
  }
  let upstream: globalThis.Response;
  try {
    upstream = await fetch(`${serverBaseUrl}/api/d/platform/public/auth/login`, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({
        login,
        encryptedPassword: body.encryptedPassword,
        captchaClientKey: "",
        captchaCode: "",
      }),
    });
  } catch {
    return Response.json(
      { code: "UPSTREAM", message: "无法连接认证服务" },
      { status: 502, headers: corsHeaders(request) },
    );
  }
  const raw = await upstream.json().catch(() => null);
  const envelope = raw as NexusEnvelope<AuthTokenVo> | null;
  if (!upstream.ok || !envelope || !isSuccess(envelope)) {
    const message = envelope?.message ?? `登录失败（HTTP ${upstream.status}）`;
    return Response.json(
      { code: envelope?.code ?? upstream.status, message },
      { status: upstream.status >= 400 ? upstream.status : 401, headers: corsHeaders(request) },
    );
  }
  const accessToken = envelope.data?.accessToken?.trim();
  if (!accessToken) {
    return Response.json(
      { code: "UPSTREAM", message: "登录响应缺少 accessToken" },
      { status: 502, headers: corsHeaders(request) },
    );
  }
  return jsonWithCookies(
    { success: true, code: "OK", message: "OK", data: envelope.data },
    { headers: corsHeaders(request) },
    [
      sessionCookie(ACCESS_TOKEN_COOKIE, accessToken),
      sessionCookie(PLATFORM_LOGIN_COOKIE, login),
    ],
  );
}
