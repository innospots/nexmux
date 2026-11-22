import { listUsers } from "@/backend/api/users";
import { corsPreflight } from "@/backend/cors";

/** 演示用户列表。登录后可见；模型平台授权由后端接口判定。 */
export function GET(request: Request) {
  return listUsers(request);
}

export const OPTIONS = corsPreflight;
