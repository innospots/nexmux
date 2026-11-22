/**
 * Shell 会话。登录成功后 cookie 值是平台登录名。
 * 页面是否可见由这里还原的用户决定；数据接口仍用平台 access token。
 */
export const SESSION_COOKIE = "pactor_demo_session";

export interface ShellSessionUser {
  id: string;
  username: string;
  name: string;
  roles: string[];
  permissions: string[];
}

export function findUserBySession(
  cookieValue: string | undefined,
): ShellSessionUser | null {
  const username = cookieValue?.trim();
  if (!username) return null;
  return {
    id: username,
    username,
    name: username,
    roles: ["admin"],
    permissions: ["user:view"],
  };
}
