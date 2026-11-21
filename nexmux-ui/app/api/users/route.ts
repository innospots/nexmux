import { cookies } from "next/headers";

import { resolveSessionUser } from "@/backend/auth/accounts";
import { corsHeaders, corsPreflight } from "@/backend/cors";

const MOCK_USERS = [
  { id: "u-1", username: "admin", name: "演示管理员", role: "admin", email: "admin@example.com", createdAt: "2026-01-05" },
];

/** 演示用户列表。登录后可见；模型平台授权由后端接口判定。 */
export async function GET(request: Request) {
  const store = await cookies();
  if (!resolveSessionUser(store)) {
    return Response.json({ error: "未登录" }, { status: 401, headers: corsHeaders(request) });
  }
  return Response.json(MOCK_USERS, {
    headers: { "cache-control": "no-store", ...corsHeaders(request) },
  });
}

export const OPTIONS = corsPreflight;
