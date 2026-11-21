import { corsHeaders, corsPreflight } from "@/backend/cors";

/** 演示登录已关闭，请使用平台登录。 */
export async function POST(request: Request) {
  return Response.json(
    { error: "请使用平台账号登录" },
    { status: 410, headers: corsHeaders(request) },
  );
}

export const OPTIONS = corsPreflight;
