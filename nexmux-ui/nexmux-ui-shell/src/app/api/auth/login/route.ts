import { NextResponse } from "next/server";

import {
  ACCESS_TOKEN_COOKIE,
  loginPlatform,
  PLATFORM_LOGIN_COOKIE,
} from "@/backend/platform-login";
import { SESSION_COOKIE } from "@/backend/session";
import { corsHeaders, corsPreflight } from "@/backend/cors";

/** 平台登录：明文密码在服务端加密后交给 /api/d/platform/public/auth/login。 */
export async function POST(request: Request) {
  const body = (await request.json().catch(() => ({}))) as {
    username?: string;
    password?: string;
  };
  const username = body.username?.trim() ?? "";
  const password = body.password ?? "";
  if (!username || !password) {
    return Response.json(
      { error: "用户名或密码错误" },
      { status: 400, headers: corsHeaders(request) },
    );
  }
  const result = await loginPlatform(username, password);
  if ("error" in result) {
    return Response.json(
      { error: result.error },
      { status: result.status, headers: corsHeaders(request) },
    );
  }
  const response = NextResponse.json(
    { token: result.accessToken, user: { username, name: username } },
    { headers: corsHeaders(request) },
  );
  const secure = process.env.NODE_ENV === "production";
  response.cookies.set(ACCESS_TOKEN_COOKIE, result.accessToken, {
    httpOnly: true,
    sameSite: "lax",
    path: "/",
    secure,
  });
  response.cookies.set(PLATFORM_LOGIN_COOKIE, username, {
    httpOnly: true,
    sameSite: "lax",
    path: "/",
    secure,
  });
  response.cookies.set(SESSION_COOKIE, username, {
    httpOnly: true,
    sameSite: "lax",
    path: "/",
    secure,
  });
  return response;
}

export const OPTIONS = corsPreflight;
