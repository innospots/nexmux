import { cookies } from "next/headers";

import { buildRuntimeBootstrap } from "@pactor-app/shell/server";

import { fetchBackendMenus, mergeHiddenRouteMenus } from "@/backend/navigation";
import { findUserBySession, SESSION_COOKIE } from "@/backend/session";

const ACCESS_TOKEN_COOKIE = "nexus_platform_access_token";

/**
 * 页面索引仍来自装配结果。已登录平台时，导航菜单改为后端
 * /api/d/platform/navigation/menus（console/meta-sitemap.yaml）下发。
 *
 * <p>隐藏路由（详情 / 新建 / 编辑 / 仪表盘等无菜单项页面）始终按页面索引补全：
 * 不能依赖后端菜单是否取到，否则令牌过期回退默认站点菜单时，这些页面会 404。</p>
 *
 * <p>令牌失效（401/403）时清空会话 Cookie 并返回 `auth: { expired: true }`，
 * 前端据此跳回登录页，避免停留在半失效状态的菜单上。</p>
 */
export async function GET() {
  const store = await cookies();
  const user = findUserBySession(store.get(SESSION_COOKIE)?.value);
  const body = buildRuntimeBootstrap(user);
  const accessToken = store.get(ACCESS_TOKEN_COOKIE)?.value?.trim();
  let menus = (body.menus ?? []) as unknown as Parameters<typeof mergeHiddenRouteMenus>[0];
  if (accessToken) {
    const backend = await fetchBackendMenus(accessToken);
    if (backend.unauthorized) {
      const payload = {
        ...body,
        menus: mergeHiddenRouteMenus([]),
        auth: { ...(body.auth ?? {}), expired: true },
      };
      const response = Response.json(payload, {
        headers: { "cache-control": "no-store" },
      });
      const cleared = { httpOnly: true, sameSite: "lax" as const, path: "/", maxAge: 0 };
      response.headers.append("set-cookie", serialize(SESSION_COOKIE, "", cleared));
      response.headers.append("set-cookie", serialize(ACCESS_TOKEN_COOKIE, "", cleared));
      return response;
    }
    if (backend.menus && backend.menus.length > 0) {
      menus = backend.menus;
    }
  }
  body.menus = mergeHiddenRouteMenus(menus);
  return Response.json(body, {
    headers: { "cache-control": "no-store" },
  });
}

function serialize(
  name: string,
  value: string,
  options: { httpOnly?: boolean; sameSite?: "lax" | "strict" | "none"; path?: string; maxAge?: number },
): string {
  const parts = [`${name}=${encodeURIComponent(value)}`];
  if (options.path) {
    parts.push(`Path=${options.path}`);
  }
  if (options.maxAge !== undefined) {
    parts.push(`Max-Age=${options.maxAge}`);
  }
  if (options.httpOnly) {
    parts.push("HttpOnly");
  }
  if (options.sameSite) {
    parts.push(`SameSite=${options.sameSite}`);
  }
  return parts.join("; ");
}
