import { clearPlatformSession, platformLogin, rejectDemoLogin } from "@/backend/api/auth";
import { dataPartsFromPath, proxyDataRequest } from "@/backend/api/data-proxy";
import { setLocale } from "@/backend/api/locale";
import type { ModuleApiHandler, ModuleApiHandlers, ModuleApiMethod } from "@/backend/api/types";
import { listUsers } from "@/backend/api/users";

const DATA_PREFIX = "/api/d";
const DATA_MAX_DEPTH = 10;

/**
 * 业务 API 分发表。独立运行的 Next 路由与模块 server 入口共用。
 *
 * Shell 分发器只支持精确路径和固定段数的 `:param`，因此 /api/d
 * 按深度各注册一条通配，再还原真实路径。
 */
export function createNexmuxApiHandlers(): ModuleApiHandlers {
  const methods: ModuleApiMethod[] = ["GET", "POST", "PUT", "PATCH", "DELETE"];
  const handlers: ModuleApiHandlers = {};

  const dispatchData: ModuleApiHandler = (request, context) =>
    proxyDataRequest(request, dataPartsFromPath(new URL(context.url).pathname));

  for (let depth = 0; depth <= DATA_MAX_DEPTH; depth += 1) {
    const key = depth === 0
      ? DATA_PREFIX
      : `${DATA_PREFIX}/${Array.from({ length: depth }, (_, index) => `:p${index}`).join("/")}`;
    const methodMap: ModuleApiHandlers[string] = {};
    for (const method of methods) {
      methodMap[method] = dispatchData;
    }
    handlers[key] = methodMap;
  }

  handlers["/api/auth/login"] = { POST: (request) => rejectDemoLogin(request) };
  handlers["/api/auth/logout"] = { POST: (request) => clearPlatformSession(request) };
  handlers["/api/auth/platform-login"] = { POST: (request) => platformLogin(request) };
  handlers["/api/i18n/locale"] = { POST: (request) => setLocale(request) };
  handlers["/api/users"] = { GET: (request) => listUsers(request) };

  return handlers;
}
