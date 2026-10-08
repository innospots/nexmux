package com.innospots.nexmux.console.audit;

import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Audit 展示记录（/api/d/audit-logs UI 契约；diffLines 由装配层对
 * before/after 快照做行级 diff（LCS）计算）。
 *
 * @param id              审计 ID
 * @param date            日期（yyyy-MM-dd）
 * @param timeUtc         完整时间（带时区标注）
 * @param timeShort       短时间（MM-dd HH:mm）
 * @param operator        操作人
 * @param operation       操作文案
 * @param operationType   操作码（小写）
 * @param resourceType    资源类型文案
 * @param resourceTypeKey 资源类型码（小写）
 * @param resourceId      资源标识
 * @param ip              操作 IP
 * @param requestId       请求 ID
 * @param userAgent       终端
 * @param beforeJson      变更前快照（pretty JSON，无则 null）
 * @param afterJson       变更后快照（pretty JSON，无则 null）
 * @param diffLines       行级 diff
 * @param context         上下文行
 */
@Schema(name = "AuditRecord", description = "Audit 展示记录（UI 契约）")
public record AuditRecord(
        String id,
        String date,
        String timeUtc,
        String timeShort,
        String operator,
        String operation,
        String operationType,
        String resourceType,
        String resourceTypeKey,
        String resourceId,
        String ip,
        String requestId,
        String userAgent,
        String beforeJson,
        String afterJson,
        List<DiffLine> diffLines,
        List<ContextRow> context) {

    /**
     * diff 行。
     *
     * @param type 行类型（same / add / remove）
     * @param text 行文本
     */
    @Schema(name = "AuditDiffLine", description = "diff 行")
    public record DiffLine(
            String type,
            String text) {
    }

    /**
     * 上下文行。
     *
     * @param label 标签
     * @param value 值
     */
    @Schema(name = "AuditContextRow", description = "上下文行")
    public record ContextRow(
            String label,
            String value) {
    }
}
