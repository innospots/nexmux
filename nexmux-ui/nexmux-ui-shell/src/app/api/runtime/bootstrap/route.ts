import { cookies } from "next/headers";

import { buildRuntimeBootstrap } from "@pactor-app/shell/server";

import { loadBackendMenus, mergeHiddenRouteMenus } from "@/backend/navigation";
import { findUserBySession, SESSION_COOKIE } from "@/backend/session";

const ACCESS_TOKEN_COOKIE = "nexus_platform_access_token";

/**
 * 页面索引仍来自装配结果。已登录平台时，导航菜单改为后端
 * /api/d/platform/navigation/menus（console/meta-sitemap.yaml）下发。
 */
export async function GET() {
  const store = await cookies();
  const user = findUserBySession(store.get(SESSION_COOKIE)?.value);
  const body = buildRuntimeBootstrap(user);
  const accessToken = store.get(ACCESS_TOKEN_COOKIE)?.value?.trim();
  if (accessToken) {
    const menus = await loadBackendMenus(accessToken);
    if (menus && menus.length > 0) {
      // 后端菜单只含可见导航；补齐 hidden 路由（详情 / 新建 / 编辑）
      body.menus = mergeHiddenRouteMenus(menus);
    }
  }
  return Response.json(body, {
    headers: { "cache-control": "no-store" },
  });
}
