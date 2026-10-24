package com.innospots.nexmux.console.shared;

import java.util.Map;

/**
 * 负载策略 → 展示文案（Route 与 CallLog 投影共用，与原 BFF mock 逐字对齐）。
 */
public final class StrategyLabels {

    private static final Map<String, String> LABELS = Map.of(
            "WEIGHTED", "加权轮询",
            "HASH", "哈希分配",
            "PRIORITY", "优先级",
            "ROUND_ROBIN", "轮询",
            "RANDOM", "随机",
            "FIXED", "固定");

    private StrategyLabels() {
    }

    /** 策略编码 → 展示文案（未知编码原样返回）。 */
    public static String of(String strategyType) {
        if (strategyType == null) {
            return "—";
        }
        return LABELS.getOrDefault(strategyType, strategyType);
    }
}
