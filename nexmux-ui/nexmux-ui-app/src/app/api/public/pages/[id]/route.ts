import type { PageDsl } from "@pactor-app/core";
import { cookies } from "next/headers";

import { readPlatformAccessToken, resolveSessionUser } from "@/backend/auth/accounts";
import { corsHeaders, corsPreflight } from "@/backend/cors";
import { applyActionAccess, collectActionRequests, stripAccessDeclarations } from "@/backend/pages/action-access";
import { getRequestI18n } from "@/backend/pages/i18n";
import { verifyPageAccess } from "@/backend/pages/page-access";
import { findPageDslById, getLazyPage } from "@/backend/pages/sitemap";

/** 懒加载页面：平台校验页面授权，并按操作授权裁剪 DSL 动作。 */
export async function GET(
  request: Request,
  context: { params: Promise<{ id: string }> },
) {
  const { id } = await context.params;
  const store = await cookies();
  const user = resolveSessionUser(store);
  const i18n = await getRequestI18n(request);
  const source = findPageDslById(id);
  const requests = source ? collectActionRequests(source, source.actions ?? {}) : [];
  const access = await verifyPageAccess({
    pageId: id,
    user,
    accessToken: readPlatformAccessToken(store),
    cookie: request.headers.get("cookie"),
    requests,
  });
  if (!access.allowed) {
    return Response.json(
      { error: access.reason, reason: access.reason, redirectTo: access.redirectTo },
      { status: 403, headers: corsHeaders(request) },
    );
  }
  const entry = getLazyPage(id, user, i18n);
  if (!entry) {
    return Response.json(
      { error: "页面不存在或无权限" },
      { status: 404, headers: corsHeaders(request) },
    );
  }
  const permissions = access.requestPermissions ?? {};
  const groups = access.grantGroups ?? [];
  const incomplete = requests.some(({ method, url }) => typeof permissions[`${method}:${url}`] !== "boolean")
    || !Array.isArray(access.grantGroups);
  const bypassActionAccess = access.publicPage === true || access.ignoreGrantGroup === true;
  const pageDsl = bypassActionAccess
    ? stripAccessDeclarations(entry.pageDsl) as PageDsl
    : applyActionAccess(entry.pageDsl, { allowed: permissions, grantGroups: groups }, incomplete, undefined);
  return Response.json(
    {
      id,
      resourceType: "page",
      version: entry.version,
      dslVersion: "1.0",
      schema: pageDsl,
      updatedAt: new Date().toISOString(),
    },
    { headers: { "cache-control": "no-store", ...corsHeaders(request) } },
  );
}

export const OPTIONS = corsPreflight;
