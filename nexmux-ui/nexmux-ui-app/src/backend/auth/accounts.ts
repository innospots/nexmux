/**
 * 平台登录会话。access token 由 BFF 写入 httpOnly cookie，转发给后端 Bearer 鉴权。
 */
export interface DemoUser {
  id: string;
  username: string;
  name: string;
  roles: string[];
  permissions: string[];
}

export const ACCESS_TOKEN_COOKIE = "nexus_platform_access_token";
export const PLATFORM_LOGIN_COOKIE = "nexus_platform_login";
export const LEGACY_DEMO_SESSION_COOKIE = "pactor_demo_session";

export type CookieReader = {
  get: (name: string) => { value: string } | undefined;
};

export function resolveSessionUser(store: CookieReader): DemoUser | null {
  const accessToken = store.get(ACCESS_TOKEN_COOKIE)?.value?.trim();
  if (!accessToken) {
    return null;
  }
  const login = store.get(PLATFORM_LOGIN_COOKIE)?.value?.trim() || "platform";
  return {
    id: `platform-${login}`,
    username: login,
    name: login,
    roles: [],
    permissions: [],
  };
}

export function readPlatformAccessToken(store: CookieReader): string | undefined {
  const value = store.get(ACCESS_TOKEN_COOKIE)?.value?.trim();
  return value && value.length > 0 ? value : undefined;
}
