import { cookies, headers } from "next/headers";

import { serverBaseUrl } from "@/frontend/config";

export interface RequestI18n {
  locale: "zh-CN" | "en-US";
  defaultLocale: "zh-CN";
  messages: Record<string, string>;
}

function resolveLocale(cookie: string | undefined, acceptLanguage: string | null): RequestI18n["locale"] {
  const raw = (cookie ?? acceptLanguage ?? "").toLowerCase();
  return raw.startsWith("en") ? "en-US" : "zh-CN";
}

/** 拉取控制台语言包；后端不可用时回退到 YAML 中文文案。 */
export async function getRequestI18n(request: Request): Promise<RequestI18n> {
  const store = await cookies();
  const headerStore = await headers();
  const locale = resolveLocale(
    store.get("locale")?.value,
    request.headers.get("accept-language") ?? headerStore.get("accept-language"),
  );
  const messages: Record<string, string> = {};
  try {
    const response = await fetch(
      `${serverBaseUrl}/api/public/i18n/bundles?locale=${encodeURIComponent(locale)}&namespaces=nexmux,common,role`,
      { cache: "no-store", signal: AbortSignal.timeout(3000) },
    );
    const body = await response.json();
    const bundles = body?.data?.bundles ?? body?.bundles;
    if (bundles && typeof bundles === "object") {
      for (const bundle of Object.values(bundles) as Array<{ messages?: Record<string, string> }>) {
        Object.assign(messages, bundle?.messages ?? {});
      }
    }
  } catch {
    // 语言包缺失时页面标题使用 YAML 回退文案。
  }
  return { locale, defaultLocale: "zh-CN", messages };
}

export function translate(text: string, messages: Readonly<Record<string, string>>): string {
  return text.replace(/\$i18n\{([^:}]+):([^}]*)\}/g, (_match, key: string, fallback: string) => {
    const value = messages[key];
    return value !== undefined && value !== "" ? value : fallback;
  });
}
