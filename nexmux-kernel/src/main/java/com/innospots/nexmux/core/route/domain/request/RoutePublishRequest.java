package com.innospots.nexmux.core.route.domain.request;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * 发布路由。
 *
 * @param changeSummary 变更摘要
 * @param operatorId    操作人 ID
 */
@Schema(name = "RoutePublishRequest", description = "发布路由")
public record RoutePublishRequest(
        @Schema(description = "变更摘要")
        String changeSummary,
        @Schema(description = "操作人 ID")
        String operatorId) {
}
