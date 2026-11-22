type ModuleApiMethod = "GET" | "POST" | "PUT" | "PATCH" | "DELETE" | "HEAD" | "OPTIONS";

type ModuleApiHandler = (
  request: Request,
  context: { moduleId: string; url: URL; params: Record<string, string> },
) => Response | Promise<Response>;

/** 供 Shell 类型检查。运行时实现是 dist-module/server/index.mjs。 */
export const handlers: Record<string, Partial<Record<ModuleApiMethod, ModuleApiHandler>>>;
