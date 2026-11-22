/**
 * 模块服务端入口：Shell 通过 /api/modules/nexmux/* 分发到此处。
 * 逻辑路径仍是 /api/d、/api/auth、/api/i18n、/api/users。
 * 独立运行的 Next 路由调用同一套实现。
 */
import { createNexmuxApiHandlers } from "@/backend/api/handlers";

export const handlers = createNexmuxApiHandlers();
