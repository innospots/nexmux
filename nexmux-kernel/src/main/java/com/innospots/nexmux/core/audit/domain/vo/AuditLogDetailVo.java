package com.innospots.nexmux.core.audit.domain.vo;

import java.time.LocalDateTime;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * 配置变更审计日志详情（含操作上下文）。
 */
@Schema(name = "AuditLogDetailVo", description = "配置变更审计日志详情")
public record AuditLogDetailVo(
        String auditId,
        String resourceType,
        String resourceId,
        String operation,
        String changeSummary,
        @Schema(description = "变更差异（JSON 原文；信封格式 {before, after} 或单快照）")
        String changeDiff,
        String operatorId,
        String clientIp,
        String requestId,
        String userAgent,
        LocalDateTime createdAt) {
}
