package com.innospots.nexmux.console.ops;

import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Cost 列表结果（/api/d/costs UI 契约）。
 *
 * @param items 列表行
 * @param total 全量条数（不随筛选变化）
 */
@Schema(name = "CostListResult", description = "Cost 列表结果（UI 契约）")
public record CostListResult(
        List<CostRecord> items,
        long total) {
}
