import type { PactorHttpHooks } from "pactor-app";

/**
 * 页面运行时 HTTP 出口的响应钩子：BFF 写通道（/api/data/* 的 POST/PUT/DELETE）
 * 把后端错误收敛为 200 + { code, message, data } 信封（框架默认适配器在非 2xx
 * 时只抛出含状态码的通用错误，读不到响应体）。此处识别信封并在 code 非 0 时
 * 抛出真实错误文案，DSL 事件链经 `${actions.<id>.error.message}` 读取。
 *
 * 识别条件要求 code/message/data 三键同时存在，避免误伤含 code 字段的业务
 * 载荷（如 ProviderRecord.code）。
 */
export const pactorHttpHooks: PactorHttpHooks = {
  response(data) {
    if (
      typeof data === "object" &&
      data !== null &&
      "code" in data &&
      "message" in data &&
      "data" in data
    ) {
      const envelope = data as { code: number | string; message?: string; data: unknown };
      if (envelope.code !== 0 && envelope.code !== "0") {
        throw new Error(envelope.message ?? `业务错误码 ${envelope.code}`);
      }
      return envelope.data;
    }
    return data;
  },
};
