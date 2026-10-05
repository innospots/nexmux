package com.innospots.nexmux.core.access.domain.request;

import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * 更新接入客户端。
 *
 * @param displayName   显示名称
 * @param quotaSpec     配额规格（JSON 或 DSL）
 * @param rateLimitSpec 限流规格（JSON 或 DSL）
 * @param tags          标签
 */
@Schema(name = "ClientUpdateRequest", description = "更新接入客户端")
public record ClientUpdateRequest(
        @Schema(description = "显示名称")
        String displayName,
        @Schema(description = "配额规格")
        String quotaSpec,
        @Schema(description = "限流规格")
        String rateLimitSpec,
        @Schema(description = "标签")
        List<String> tags) {
}
