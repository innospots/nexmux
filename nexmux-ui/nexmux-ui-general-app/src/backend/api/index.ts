import { corsHeaders } from "./http";
import { callUpstream, proxyAuditLogs, proxySystemSettings, proxyUsers } from "./upstream";

export async function dispatchBusinessData(
  request: Request,
  parts: string[],
): Promise<Response> {
  const path = parts.join("/");

  if (parts[0] === "platform" || parts[0] === "nexus") {
    return callUpstream(request, `/api/d/${path}`);
  }
  if (path === "dashboard" || path.startsWith("dashboard/")) {
    return callUpstream(request, "/api/d/stats-dashboard");
  }
  if (path === "system-settings" || path.startsWith("system-settings/")) {
    return proxySystemSettings(request);
  }
  if (path === "users" || path.startsWith("users/")) {
    return proxyUsers(request);
  }
  if (path === "dictionaries" || path.startsWith("dictionaries/")) {
    const suffix = path.slice("dictionaries".length);
    return callUpstream(request, `/api/d/platform/dictionary-types${suffix}`);
  }
  if (path === "audit-logs" || path.startsWith("audit-logs/")) {
    return proxyAuditLogs(request);
  }

  return Response.json(
    { error: "接口不存在" },
    { status: 404, headers: corsHeaders(request) },
  );
}
