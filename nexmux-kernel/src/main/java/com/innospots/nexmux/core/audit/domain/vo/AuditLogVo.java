package com.innospots.nexmux.core.audit.domain.vo;

import java.time.LocalDateTime;

public record AuditLogVo(
        String auditId,
        String resourceType,
        String resourceId,
        String operation,
        String changeSummary,
        String changeDiff,
        String operatorId,
        LocalDateTime createdAt) {
}
