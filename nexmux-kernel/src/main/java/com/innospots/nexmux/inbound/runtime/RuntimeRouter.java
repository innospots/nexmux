package com.innospots.nexmux.inbound.runtime;

import com.innospots.nexmux.core.route.domain.selection.CandidateSelector;
import java.util.Optional;
import java.util.Set;

/**
 * Runtime 选路骨架：已发布快照缓存 + 候选选择，与调试台共用
 * {@link CandidateSelector}（ADR-0001 审计 B1：调试通过 == 上线一致）。
 *
 * <p>只消费 {@code published_route_spec}（P0 可调用闭环的最小链路）；
 * 鉴权、计量、熔断与有界 Failover 由后续批次补齐。
 */
public final class RuntimeRouter {

    private final PublishedRouteCache routeCache;
    private final CandidateSelector candidateSelector = new CandidateSelector();

    public RuntimeRouter(PublishedRouteCache routeCache) {
        this.routeCache = routeCache;
    }

    /** 路由缓存（流水线需要 routeId 做授权与计量归属）。 */
    public PublishedRouteCache routeCache() {
        return routeCache;
    }

    /**
     * 按入口解析目标候选。路由未发布 / 无可用候选返回 {@link Optional#empty()}，
     * 由调用方映射为 {@code ROUTE_NOT_PUBLISHED}(503) / 无候选(422)。
     *
     * @param ingress              入口路径（如 {@code /v1/chat/completions}）
     * @param unhealthyModelCodes  Runtime 健康视图（无数据时传空集）
     * @param sessionKey           会话键（粘性 / HASH 策略使用，可空）
     */
    public Optional<CandidateSelector.Selection> resolveByIngress(
            String ingress, Set<String> unhealthyModelCodes, String sessionKey) {
        return routeCache.findByIngress(ingress)
                .flatMap(route -> candidateSelector.select(route.spec(), unhealthyModelCodes, sessionKey));
    }

    /** 按别名解析目标候选（客户端以 alias 调用时）。 */
    public Optional<CandidateSelector.Selection> resolveByAlias(
            String alias, Set<String> unhealthyModelCodes, String sessionKey) {
        return resolveByAlias(alias, Set.of(), unhealthyModelCodes, sessionKey);
    }

    /** 按别名解析目标候选，强制不可用模型与Binding健康视图分别过滤。 */
    public Optional<CandidateSelector.Selection> resolveByAlias(
            String alias, Set<String> unavailableModelCodes,
            Set<String> unhealthyModelCodes, String sessionKey) {
        return routeCache.findByAlias(alias)
                .flatMap(route -> candidateSelector.select(
                        route.spec(), unavailableModelCodes, unhealthyModelCodes, sessionKey));
    }
}
