package com.innospots.nexmux.console.access;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * 授权路由快照（Client / ClientTemplate 共用；由 authorizedRouteIds 关联路由补全）。
 *
 * @param code      路由编码
 * @param name      路由名称
 * @param alias     路由别名
 * @param typeLabel 类型文案（单模型 / 多模型池）
 * @param path      入口路径
 */
@Schema(name = "AuthRoute", description = "授权路由快照")
public record AuthRoute(
        String code,
        String name,
        String alias,
        String typeLabel,
        String path) {
}
