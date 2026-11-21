import { NextResponse } from "next/server";

import {
  ACCESS_TOKEN_COOKIE,
  LEGACY_DEMO_SESSION_COOKIE,
  PLATFORM_LOGIN_COOKIE,
} from "@/backend/auth/accounts";
import { corsHeaders, corsPreflight } from "@/backend/cors";

/** 清平台会话 cookie。 */
export async function POST(request: Request) {
  const response = NextResponse.json({ ok: true }, { headers: corsHeaders(request) });
  const expired = { httpOnly: true, sameSite: "lax" as const, path: "/", maxAge: 0 };
  response.cookies.set(ACCESS_TOKEN_COOKIE, "", expired);
  response.cookies.set(PLATFORM_LOGIN_COOKIE, "", expired);
  response.cookies.set(LEGACY_DEMO_SESSION_COOKIE, "", expired);
  return response;
}

export const OPTIONS = corsPreflight;
