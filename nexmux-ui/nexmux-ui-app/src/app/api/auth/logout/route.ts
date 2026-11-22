import { clearPlatformSession } from "@/backend/api/auth";
import { corsPreflight } from "@/backend/cors";

/** 清平台会话 cookie。 */
export function POST(request: Request) {
  return clearPlatformSession(request);
}

export const OPTIONS = corsPreflight;
