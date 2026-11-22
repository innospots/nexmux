import { platformLogin } from "@/backend/api/auth";
import { corsPreflight } from "@/backend/cors";

/** 代理平台登录并写入 httpOnly 会话 cookie。 */
export function POST(request: Request) {
  return platformLogin(request);
}

export const OPTIONS = corsPreflight;
