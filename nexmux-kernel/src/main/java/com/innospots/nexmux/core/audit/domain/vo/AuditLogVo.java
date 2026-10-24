package com.innospots.nexmux.core.audit.domain.vo;

import java.time.LocalDateTime;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * 配置变更审计日志概要（列表行）。
 */
@Schema(name = "AuditLogVo", description = "配置变更审计日志概要")
public record AuditLogVo(
        @Schema(description = "审计 ID", required = true)
        String auditId,
        @Schema(description = "资源类型")
        String resourceType,
        @Schema(description = "资源 ID")
        String resourceId,
        @Schema(description = "操作类型")
        String operation,
        @Schema(description = "变更摘要")
        String changeSummary,
        @Schema(description = "变更差异")
        String changeDiff,
        @Schema(description = "操作人 ID")
        String operatorId,
        @Schema(description = "操作 IP")
        String clientIp,
        @Schema(description = "关联请求 ID")
        String requestId,
        @Schema(description = "操作终端")
        String userAgent,
        @Schema(description = "创建时间", format = "date-time")
        LocalDateTime createdAt) {
}
