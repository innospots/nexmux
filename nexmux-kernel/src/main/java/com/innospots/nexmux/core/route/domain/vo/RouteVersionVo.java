package com.innospots.nexmux.core.route.domain.vo;

import java.time.LocalDateTime;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Route 运行配置版本。
 *
 * @param revision     配置修订号
 * @param publishedAt  发布时间
 * @param operatorId   操作人
 * @param changeSummary 变更摘要
 * @param current      是否当前运行版本
 */
@Schema(name = "RouteVersionVo", description = "Route 运行配置版本")
public record RouteVersionVo(
        @Schema(description = "配置修订号", required = true)
        long revision,
        @Schema(description = "发布时间", format = "date-time")
        LocalDateTime publishedAt,
        @Schema(description = "操作人")
        String operatorId,
        @Schema(description = "变更摘要")
        String changeSummary,
        @Schema(description = "是否当前运行版本")
        boolean current) {
}
