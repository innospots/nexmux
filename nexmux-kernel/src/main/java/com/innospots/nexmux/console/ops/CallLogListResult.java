package com.innospots.nexmux.console.ops;

import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * CallLog 列表结果（/api/d/call-logs UI 契约）。
 *
 * @param items        列表行
 * @param total        全量条数（不随筛选变化）
 * @param filteredText 当前筛选命中数（千分位）
 */
@Schema(name = "CallLogListResult", description = "CallLog 列表结果（UI 契约）")
public record CallLogListResult(
        List<CallLogRecord> items,
        long total,
        String filteredText) {
}
