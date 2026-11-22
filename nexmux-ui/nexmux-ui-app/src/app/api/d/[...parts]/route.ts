import { proxyDataRequest } from "@/backend/api/data-proxy";
import { corsPreflight } from "@/backend/cors";

type Context = { params: Promise<{ parts: string[] }> };

/** 独立运行数据转发。模块模式下由 module/server.ts 承接同一实现。 */
async function handle(request: Request, context: Context) {
  const { parts } = await context.params;
  return proxyDataRequest(request, parts);
}

export const GET = handle;
export const POST = handle;
export const PUT = handle;
export const PATCH = handle;
export const DELETE = handle;
export const OPTIONS = corsPreflight;
