const BACKEND = process.env.NEXMUX_API_BASE_URL ?? "http://localhost:8082";

type Envelope<T> = {
  success?: boolean;
  code?: string | number;
  message?: string;
  data?: T;
};

export const ACCESS_TOKEN_COOKIE = "nexus_platform_access_token";
export const PLATFORM_LOGIN_COOKIE = "nexus_platform_login";

function succeeded(envelope: Envelope<unknown> | null): boolean {
  if (!envelope || envelope.success === false) return false;
  const code = envelope.code;
  return code === undefined || code === 0 || code === "0" || code === "OK";
}

async function encryptPassword(password: string): Promise<string> {
  const response = await fetch(`${BACKEND}/api/d/platform/public/auth/rsa-public-key`, {
    cache: "no-store",
  });
  const body = (await response.json()) as Envelope<{ publicKeyBase64?: string }>;
  const publicKeyBase64 = body.data?.publicKeyBase64;
  if (!response.ok || !publicKeyBase64) {
    throw new Error(body.message ?? "无法获取登录公钥");
  }
  const key = await crypto.subtle.importKey(
    "spki",
    Buffer.from(publicKeyBase64, "base64"),
    { name: "RSA-OAEP", hash: "SHA-256" },
    false,
    ["encrypt"],
  );
  const encrypted = await crypto.subtle.encrypt(
    { name: "RSA-OAEP" },
    key,
    new TextEncoder().encode(password),
  );
  return Buffer.from(encrypted).toString("base64");
}

/** 用平台账号登录。成功时返回 access token。 */
export async function loginPlatform(
  login: string,
  password: string,
): Promise<{ accessToken: string; message?: string } | { error: string; status: number }> {
  let encryptedPassword: string;
  try {
    encryptedPassword = await encryptPassword(password);
  } catch (error) {
    const message = error instanceof Error ? error.message : "无法连接认证服务";
    return { error: message, status: 502 };
  }
  let upstream: Response;
  try {
    upstream = await fetch(`${BACKEND}/api/d/platform/public/auth/login`, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({
        login,
        encryptedPassword,
        captchaClientKey: "",
        captchaCode: "",
      }),
    });
  } catch {
    return { error: "无法连接认证服务", status: 502 };
  }
  const envelope = (await upstream.json().catch(() => null)) as Envelope<{ accessToken?: string }> | null;
  if (!upstream.ok || !succeeded(envelope)) {
    return {
      error: envelope?.message ?? "用户名或密码错误",
      status: upstream.status >= 400 ? upstream.status : 401,
    };
  }
  const accessToken = envelope?.data?.accessToken?.trim();
  if (!accessToken) {
    return { error: "登录响应缺少 accessToken", status: 502 };
  }
  return { accessToken };
}
