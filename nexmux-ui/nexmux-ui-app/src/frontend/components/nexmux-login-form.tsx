"use client";

import { type FormEvent, useState } from "react";

import { apiUrl } from "../lib/api-base";


async function encryptPassword(plaintext: string): Promise<string> {
  const keyResponse = await fetch(apiUrl("/api/d/platform/public/auth/rsa-public-key"));
  const keyBody = await keyResponse.json();
  const publicKeyBase64 = keyBody?.data?.publicKeyBase64 as string | undefined;
  if (!keyResponse.ok || !publicKeyBase64) {
    throw new Error(keyBody?.message ?? "无法获取登录公钥");
  }
  const binary = atob(publicKeyBase64);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
  const key = await crypto.subtle.importKey(
    "spki",
    bytes.buffer,
    { name: "RSA-OAEP", hash: "SHA-256" },
    false,
    ["encrypt"],
  );
  const data = new TextEncoder().encode(plaintext);
  const encrypted = new Uint8Array(await crypto.subtle.encrypt({ name: "RSA-OAEP" }, key, data));
  let encoded = "";
  for (let i = 0; i < encrypted.length; i++) encoded += String.fromCharCode(encrypted[i]);
  return btoa(encoded);
}

/** 平台登录：RSA 加密密码后写入 httpOnly 会话。 */
export function NexmuxLoginForm() {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [pending, setPending] = useState(false);

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setPending(true);
    setError("");
    try {
      const encryptedPassword = await encryptPassword(password);
      const response = await fetch(apiUrl("/api/auth/platform-login"), {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({ login: username, encryptedPassword }),
      });
      const body = await response.json().catch(() => ({}));
      if (!response.ok) {
        throw new Error(body?.message ?? "用户名或密码错误");
      }
      window.location.assign("/");
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "登录失败");
      setPending(false);
    }
  }

  return (
    <form className="login-card" onSubmit={onSubmit}>
      <div className="login-eyebrow">RESTRICTED ACCESS</div>
      <div className="login-heading">管理后台登录</div>
      <p className="login-subtitle">使用已分配角色的平台账号登录。</p>
      <label className="mt-4 block text-sm">
        用户名
        <input className="mt-1 w-full rounded border px-3 py-2" value={username} onChange={(event) => setUsername(event.target.value)} />
      </label>
      <label className="mt-3 block text-sm">
        密码
        <input className="mt-1 w-full rounded border px-3 py-2" type="password" value={password} onChange={(event) => setPassword(event.target.value)} />
      </label>
      {error ? <p className="mt-3 text-sm text-red-600">{error}</p> : null}
      <button className="mt-4 w-full rounded bg-black px-3 py-2 text-white disabled:opacity-60" disabled={pending} type="submit">
        {pending ? "登录中…" : "登录"}
      </button>
    </form>
  );
}
