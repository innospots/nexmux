"use client";

import { useEffect } from "react";

/**
 * 会话过期守卫：拉取一次 runtime bootstrap，若服务端标记 `auth.expired`
 * （平台令牌失效），跳回登录页并带上回跳地址。
 *
 * <p>服务端已在同一响应里清空会话 Cookie，因此这里只需导航；
 * 用 `location.replace` 避免用户回退到失效页面。</p>
 */
export function SessionExpiryGuard() {
  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const response = await fetch("/api/runtime/bootstrap", {
          cache: "no-store",
          credentials: "include",
        });
        if (!response.ok || cancelled) {
          return;
        }
        const body = (await response.json()) as { auth?: { expired?: boolean } };
        if (body.auth?.expired === true) {
          const next = `${window.location.pathname}${window.location.search}`;
          window.location.replace(`/login?redirect=${encodeURIComponent(next)}`);
        }
      } catch {
        // 网络异常时保持当前页面，交给页面自身的错误态处理
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);
  return null;
}
