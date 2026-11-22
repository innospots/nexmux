import type { CookieReader } from "@/backend/auth/accounts";

/** 从请求 Cookie 头还原读取器，供 Next 路由与模块 server 共用。 */
export function cookiesFrom(request: Request): CookieReader {
  const header = request.headers.get("cookie") ?? "";
  const values = new Map<string, string>();
  for (const part of header.split(";")) {
    const trimmed = part.trim();
    if (!trimmed) {
      continue;
    }
    const eq = trimmed.indexOf("=");
    if (eq <= 0) {
      continue;
    }
    const name = trimmed.slice(0, eq).trim();
    const raw = trimmed.slice(eq + 1).trim();
    try {
      values.set(name, decodeURIComponent(raw));
    } catch {
      values.set(name, raw);
    }
  }
  return {
    get(name: string) {
      const value = values.get(name);
      return value === undefined ? undefined : { value };
    },
  };
}

export function serializeCookie(
  name: string,
  value: string,
  options: {
    httpOnly?: boolean;
    path?: string;
    secure?: boolean;
    maxAge?: number;
  },
): string {
  const parts = [
    `${name}=${encodeURIComponent(value)}`,
    `Path=${options.path ?? "/"}`,
    "SameSite=Lax",
  ];
  if (options.httpOnly) {
    parts.push("HttpOnly");
  }
  if (options.secure) {
    parts.push("Secure");
  }
  if (options.maxAge !== undefined) {
    parts.push(`Max-Age=${options.maxAge}`);
  }
  return parts.join("; ");
}

export function jsonWithCookies(
  body: unknown,
  init: ResponseInit,
  cookies: string[],
): Response {
  const headers = new Headers(init.headers);
  for (const cookie of cookies) {
    headers.append("set-cookie", cookie);
  }
  return Response.json(body, { ...init, headers });
}
