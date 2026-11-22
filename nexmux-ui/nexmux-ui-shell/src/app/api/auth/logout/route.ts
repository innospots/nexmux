import { NextResponse } from "next/server";

import { SESSION_COOKIE } from "@/backend/session";
import {
  ACCESS_TOKEN_COOKIE,
  PLATFORM_LOGIN_COOKIE,
} from "@/backend/platform-login";
import { corsHeaders, corsPreflight } from "@/backend/cors";

/** 清平台会话与 Shell 会话。 */
export async function POST(request: Request) {
  const response = NextResponse.json(
    { ok: true },
    { headers: corsHeaders(request) },
  );
  const expired = { httpOnly: true, sameSite: "lax" as const, path: "/", maxAge: 0 };
  response.cookies.set(SESSION_COOKIE, "", expired);
  response.cookies.set(ACCESS_TOKEN_COOKIE, "", expired);
  response.cookies.set(PLATFORM_LOGIN_COOKIE, "", expired);
  return response;
}

/** 预检（跨源 POST JSON） */
export const OPTIONS = corsPreflight;
