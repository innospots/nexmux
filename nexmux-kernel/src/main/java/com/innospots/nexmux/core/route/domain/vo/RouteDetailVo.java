package com.innospots.nexmux.core.route.domain.vo;

import com.innospots.nexmux.core.route.domain.value.RouteSpec;
import java.time.LocalDateTime;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * 路由详情（按稳定业务键查询；含草稿与发布快照对照）。
 *
 * @param routeId            路由 ID
 * @param routeCode          路由编码（稳定业务键）
 * @param alias              路由别名
 * @param displayName        显示名称
 * @param status             状态（DRAFT / PUBLISHED / DISABLED）
 * @param routeSpec          草稿规格
 * @param publishedRouteSpec 发布快照规格
 * @param publishedAt        最近发布时间
 * @param publishedBy        最近发布人
 * @param createdAt          创建时间
 * @param updatedAt          更新时间
 * @param updatedBy          最近操作人
 */
@Schema(name = "RouteDetailVo", description = "路由详情")
public record RouteDetailVo(
        @Schema(description = "路由 ID", required = true)
        String routeId,
        @Schema(description = "路由编码", required = true)
        String routeCode,
        @Schema(description = "路由别名", required = true)
        String alias,
        @Schema(description = "显示名称", required = true)
        String displayName,
        @Schema(description = "状态", examples = {"PUBLISHED"})
        String status,
        @Schema(description = "草稿规格")
        RouteSpec routeSpec,
        @Schema(description = "发布快照规格")
        RouteSpec publishedRouteSpec,
        @Schema(description = "最近发布时间", format = "date-time")
        LocalDateTime publishedAt,
        @Schema(description = "最近发布人")
        String publishedBy,
        @Schema(description = "创建时间", format = "date-time")
        LocalDateTime createdAt,
        @Schema(description = "更新时间", format = "date-time")
        LocalDateTime updatedAt,
        @Schema(description = "最近操作人")
        String updatedBy) {
}
