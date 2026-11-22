import { rejectDemoLogin } from "@/backend/api/auth";
import { corsPreflight } from "@/backend/cors";

/** 演示登录已关闭，请使用平台登录。 */
export function POST(request: Request) {
  return rejectDemoLogin(request);
}

export const OPTIONS = corsPreflight;
