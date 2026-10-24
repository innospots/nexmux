package com.innospots.nexmux.core.access.domain.vo;

import java.time.LocalDateTime;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Client 策略发布版本。
 *
 * @param configRevision 配置修订号
 * @param publishedAt    最近策略发布时间
 */
@Schema(name = "ClientPolicyRevisionVo", description = "Client 策略发布版本")
public record ClientPolicyRevisionVo(
        @Schema(description = "配置修订号", required = true)
        long configRevision,
        @Schema(description = "最近策略发布时间", format = "date-time")
        LocalDateTime publishedAt) {
}
