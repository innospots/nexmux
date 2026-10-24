package com.innospots.nexmux.core.route.domain.selection;

import com.innospots.nexmux.core.route.domain.value.RouteCandidate;
import com.innospots.nexmux.core.route.domain.value.RouteSpec;
import com.innospots.nexmux.core.shared.selection.WeightedPick;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 路由候选选择器：调试台与未来 Runtime 共用同一选路实现（ADR-0001 审计 B1 修复），
 * 保证「调试通过 == 上线一致」。
 *
 * <p>语义顺序：启用过滤 → 健康过滤（{@code filterUnhealthyBindings} 且调用方提供健康视图时）
 * → 优先级分层（仅保留最优 tier，P0 &lt; P1 &lt; P2，未标注视为最低兜底）
 * → 粘性会话覆盖（{@code stickySession} 且提供 sessionKey 时按哈希固定候选）
 * → 负载策略（FIXED / WEIGHTED / ROUND_ROBIN / RANDOM / HASH，缺省 FIXED，
 * 未知策略按 FIXED 处理）。
 */
public final class CandidateSelector {

    /**
     * 一次候选选择的结果与上下文（供调试台展示与 Runtime routeTrace 观测）。
     *
     * @param candidate           命中候选
     * @param strategyType        实际执行的策略（已归一化，非空）
     * @param poolSize            参与策略选择的候选池大小（过滤与分层之后）
     * @param healthFilterApplied 健康过滤是否实际剔除了候选
     * @param stickyApplied       是否由粘性会话直接定选（绕过负载策略）
     */
    public record Selection(
            RouteCandidate candidate,
            String strategyType,
            int poolSize,
            boolean healthFilterApplied,
            boolean stickyApplied) {
    }

    /** ROUND_ROBIN 游标（实例级；Runtime 每进程一个实例即可）。 */
    private final AtomicLong roundRobinCursor = new AtomicLong();

    /** 无健康视图与会话键的便捷入口（等价于 {@code select(spec, Set.of(), null)}）。 */
    public Optional<Selection> select(RouteSpec spec) {
        return select(spec, Set.of(), null);
    }

    /**
     * 选择候选。
     *
     * @param spec                 已发布路由快照
     * @param unhealthyModelCodes  已知不健康 modelCode 视图（无健康数据时传空集，健康过滤即为无操作）
     * @param sessionKey           会话键（stickySession / HASH 策略使用，可空）
     * @return 命中结果；候选池为空时 {@link Optional#empty()}
     */
    public Optional<Selection> select(RouteSpec spec, Set<String> unhealthyModelCodes, String sessionKey) {
        return select(spec, Set.of(), unhealthyModelCodes, sessionKey);
    }

    /**
     * 选择候选，将强制不可用状态与可选健康过滤分开处理。
     *
     * @param spec                   已发布路由快照
     * @param unavailableModelCodes  强制不可用模型（如已停用），不受健康过滤开关影响
     * @param unhealthyModelCodes    已知不健康模型，仅在路由开启健康过滤时剔除
     * @param sessionKey             会话键
     * @return 命中结果；候选池为空时 {@link Optional#empty()}
     */
    public Optional<Selection> select(RouteSpec spec, Set<String> unavailableModelCodes,
                                      Set<String> unhealthyModelCodes, String sessionKey) {
        if (spec == null) {
            return Optional.empty();
        }
        List<RouteCandidate> pool = spec.candidates().stream()
                .filter(c -> c.enabled() == null || c.enabled())
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        if (unavailableModelCodes != null && !unavailableModelCodes.isEmpty()) {
            pool.removeIf(c -> unavailableModelCodes.contains(c.modelCode()));
        }
        boolean healthFilterApplied = false;
        if (spec.filterUnhealthyBindings() && unhealthyModelCodes != null && !unhealthyModelCodes.isEmpty()) {
            healthFilterApplied = pool.removeIf(c -> unhealthyModelCodes.contains(c.modelCode()));
        }
        pool = bestPriorityTier(pool);
        if (pool.isEmpty()) {
            return Optional.empty();
        }
        String strategy = spec.strategyType() == null || spec.strategyType().isBlank()
                ? "FIXED" : spec.strategyType();
        if (Boolean.TRUE.equals(spec.stickySession()) && sessionKey != null && !sessionKey.isBlank()) {
            return Optional.of(new Selection(
                    hashPick(pool, sessionKey), strategy, pool.size(), healthFilterApplied, true));
        }
        RouteCandidate picked = switch (strategy) {
            case "WEIGHTED" -> WeightedPick.pick(pool, RouteCandidate::weight).orElse(pool.get(0));
            case "ROUND_ROBIN" -> pool.get((int) Math.floorMod(roundRobinCursor.getAndIncrement(), pool.size()));
            case "RANDOM" -> pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
            case "HASH" -> sessionKey == null || sessionKey.isBlank() ? pool.get(0) : hashPick(pool, sessionKey);
            default -> pool.get(0);
        };
        return Optional.of(new Selection(picked, strategy, pool.size(), healthFilterApplied, false));
    }

    /** 优先级分层：存在标注优先级的候选时仅保留最优 tier（P0 &lt; P1 &lt; P2；未标注视为兜底，不进入分层池）。 */
    private static List<RouteCandidate> bestPriorityTier(List<RouteCandidate> pool) {
        Optional<String> best = pool.stream()
                .map(RouteCandidate::priority)
                .filter(p -> p != null && !p.isBlank())
                .min(Comparator.naturalOrder());
        if (best.isEmpty()) {
            return pool;
        }
        List<RouteCandidate> tier = pool.stream()
                .filter(c -> best.get().equals(c.priority()))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        return tier.isEmpty() ? pool : tier;
    }

    /** 哈希定选：按 modelCode 排序保证确定性，再对会话键取模。 */
    private static RouteCandidate hashPick(List<RouteCandidate> pool, String sessionKey) {
        List<RouteCandidate> sorted = pool.stream()
                .sorted(Comparator.comparing(RouteCandidate::modelCode))
                .toList();
        return sorted.get(Math.floorMod(sessionKey.hashCode(), sorted.size()));
    }
}
