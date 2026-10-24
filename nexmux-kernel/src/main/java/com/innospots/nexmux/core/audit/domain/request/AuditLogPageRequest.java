package com.innospots.nexmux.core.audit.domain.request;

/**
 * 审计日志分页查询请求。
 *
 * @param pageNo       页码，从 1 开始
 * @param pageSize     每页条数
 * @param start        起始日期（yyyy-MM-dd，含）
 * @param end          截止日期（yyyy-MM-dd，含）
 * @param operator     操作人 ID
 * @param operation    操作类型（PUBLISH / CREATE / UPDATE / DISABLE …）
 * @param resourceType 资源类型（ROUTE / PROVIDER / MODEL / CLIENT …）
 * @param resourceId   资源 ID 模糊
 */
public record AuditLogPageRequest(
        Long pageNo,
        Long pageSize,
        String start,
        String end,
        String operator,
        String operation,
        String resourceType,
        String resourceId) {
}
