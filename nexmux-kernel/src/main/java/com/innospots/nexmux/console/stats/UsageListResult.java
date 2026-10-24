package com.innospots.nexmux.console.stats;

import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * 用量列表结果（/api/d/stats-*-usage UI 契约：{items,summary,total}）。
 *
 * @param items   用量行
 * @param summary 合计
 * @param total   行数
 * @param <T>     行类型
 */
@Schema(name = "UsageListResult", description = "用量列表结果（UI 契约）")
public record UsageListResult<T>(
        List<T> items,
        UsageSummary summary,
        long total) {

    /**
     * 用量合计。
     *
     * @param standard 标准价合计
     * @param billed   计费合计
     * @param cost     成本合计
     */
    @Schema(name = "UsageSummary", description = "用量合计")
    public record UsageSummary(
            String standard,
            String billed,
            String cost) {
    }
}
