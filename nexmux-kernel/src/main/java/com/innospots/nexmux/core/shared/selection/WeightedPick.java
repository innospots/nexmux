package com.innospots.nexmux.core.shared.selection;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.ToIntFunction;

/**
 * 加权随机选择（路由候选与 Binding 选择共用，nexmux-runtime-design §5.2）。
 * 权重 ≤ 0 视为 0；总权重为 0 时回退首个元素。
 */
public final class WeightedPick {

    private WeightedPick() {
    }

    /**
     * 按权重随机选中一个元素。
     *
     * @param items    候选列表（非空判断由调用方负责，空列表返回 empty）
     * @param weightFn 权重函数
     */
    public static <T> Optional<T> pick(List<T> items, ToIntFunction<T> weightFn) {
        if (items == null || items.isEmpty()) {
            return Optional.empty();
        }
        long total = items.stream().mapToLong(item -> Math.max(weightFn.applyAsInt(item), 0)).sum();
        if (total <= 0) {
            return Optional.of(items.get(0));
        }
        long roll = ThreadLocalRandom.current().nextLong(total);
        long cursor = 0;
        for (T item : items) {
            cursor += Math.max(weightFn.applyAsInt(item), 0);
            if (roll < cursor) {
                return Optional.of(item);
            }
        }
        return Optional.of(items.get(items.size() - 1));
    }
}
