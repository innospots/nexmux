package com.innospots.nexmux.core.stats.service;

import com.innospots.nexmux.core.stats.dao.StatsQueryDao;
import com.innospots.nexmux.core.stats.domain.vo.UsageRowVo;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 聚合行转换工具（Map → 数值；比率计算）。
 */
final class StatsMapping {

    private StatsMapping() {
    }

    static long longOf(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        return 0L;
    }

    static double doubleOf(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return 0.0;
    }

    static BigDecimal decimalOf(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        return BigDecimal.ZERO;
    }

    static String stringOf(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? null : String.valueOf(value);
    }

    static double rate(long part, long total) {
        return total == 0 ? 0.0 : (double) part / total;
    }

    static UsageRowVo toUsageRow(Map<String, Object> row) {
        long requests = longOf(row, "requests");
        BigDecimal cost = decimalOf(row, "costUsd");
        return new UsageRowVo(
                stringOf(row, "dim"),
                requests,
                longOf(row, "errorCount"),
                longOf(row, "inputTokens"),
                longOf(row, "outputTokens"),
                longOf(row, "cacheReadTokens"),
                decimalOf(row, "standardUsd"),
                decimalOf(row, "billedUsd"),
                cost,
                rate(longOf(row, "successCount"), requests),
                doubleOf(row, "avgLatencyMs"),
                doubleOf(row, "avgTtftMs"),
                doubleOf(row, "avgUpstreamMs"),
                rate(longOf(row, "failoverCount"), requests),
                requests == 0 ? 1.0 : 1.0 + (double) longOf(row, "retriesTotal") / requests);
    }

    static List<UsageRowVo> toUsageRows(List<Map<String, Object>> rows) {
        return rows.stream().map(StatsMapping::toUsageRow).toList();
    }
}
