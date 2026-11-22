import { setLocale } from "@/backend/api/locale";

/** 写入语言偏好。调用方随后整页刷新，sitemap 与页面按该语言重建。 */
export function POST(request: Request) {
  return setLocale(request);
}
