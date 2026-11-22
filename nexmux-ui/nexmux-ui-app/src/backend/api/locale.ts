import { serializeCookie } from "@/backend/api/cookies";

const LOCALE_COOKIE = "locale";

function normalize(value: string): "zh-CN" | "en-US" | null {
  const raw = value.trim().toLowerCase().replace("_", "-");
  if (raw === "zh" || raw === "zh-cn") {
    return "zh-CN";
  }
  if (raw === "en" || raw === "en-us") {
    return "en-US";
  }
  return null;
}

/** 写入语言偏好。调用方随后整页刷新，sitemap 与页面按该语言重建。 */
export async function setLocale(request: Request): Promise<Response> {
  const payload = (await request.json().catch(() => null)) as { locale?: unknown } | null;
  const requested = typeof payload?.locale === "string" ? payload.locale : "";
  const locale = normalize(requested);
  if (!locale) {
    return Response.json({ error: "Unsupported locale" }, { status: 400 });
  }
  const headers = new Headers({ "cache-control": "no-store" });
  headers.append(
    "set-cookie",
    serializeCookie(LOCALE_COOKIE, locale, {
      path: "/",
      maxAge: 60 * 60 * 24 * 365,
    }),
  );
  return new Response(null, { status: 204, headers });
}
