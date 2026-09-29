package com.innospots.nexmux.core.route.domain.vo;

import com.innospots.nexmux.core.route.domain.value.RouteSpec;
import java.time.LocalDateTime;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * 路由概要。
 *
 * @param routeId     路由 ID
 * @param alias       路由别名
 * @param displayName 显示名称
 * @param status      状态
 * @param routeSpec   路由规格
 * @param publishedAt 最近发布时间
 */
@Schema(name = "RouteVo", description = "路由概要")
public record RouteVo(
        @Schema(description = "路由 ID", required = true)
        String routeId,
        @Schema(description = "路由别名", required = true)
        String alias,
        @Schema(description = "显示名称", required = true)
        String displayName,
        @Schema(description = "状态", examples = {"PUBLISHED"})
        String status,
        @Schema(description = "路由规格")
        RouteSpec routeSpec,
        @Schema(description = "最近发布时间", format = "date-time")
        LocalDateTime publishedAt) {
}
