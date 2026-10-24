package com.innospots.nexmux.inbound.runtime;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.innospots.nexmux.core.route.dao.RouteDao;
import com.innospots.nexmux.core.route.domain.entity.RouteEntity;
import com.innospots.nexmux.core.route.domain.value.RouteSpec;
import com.innospots.nexmux.core.shared.json.NexmuxJson;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 已发布路由快照缓存：Runtime 消费配置的唯一入口（ADR-0001 §2）。
 *
 * <p>只读 {@code status = PUBLISHED} 且 {@code published_route_spec} 非空的快照列，
 * 不触碰草稿 {@code route_spec}。由 Runtime 进程周期性 {@link #refresh()}；
 * 按架构 §2 不在 {@code nexmux-spring}（Console assembly）默认装配。
 */
public final class PublishedRouteCache {

    private final RouteDao routeDao;
    private final AtomicReference<Snapshot> snapshot = new AtomicReference<>(Snapshot.EMPTY);

    public PublishedRouteCache(RouteDao routeDao) {
        this.routeDao = routeDao;
    }

    /**
     * 已发布路由（快照 + 路由 ID）。
     *
     * @param routeId 路由 ID（授权判断与计量归属需要）
     * @param spec    已发布快照
     */
    public record PublishedRoute(String routeId, RouteSpec spec) {
    }

    /**
     * 重新加载已发布快照。单条快照解析失败跳过并计数（见 {@link #lastSkipped()}），
     * 不阻断整体刷新——配置错误不应拖垮整个数据面。
     */
    public void refresh() {
        List<RouteEntity> routes = routeDao.selectList(new LambdaQueryWrapper<RouteEntity>()
                .eq(RouteEntity::getStatus, "PUBLISHED")
                .isNotNull(RouteEntity::getPublishedRouteSpec));
        Map<String, PublishedRoute> byAlias = new HashMap<>();
        Map<String, PublishedRoute> byIngress = new HashMap<>();
        int skipped = 0;
        for (RouteEntity route : routes) {
            try {
                RouteSpec spec = NexmuxJson.read(route.getPublishedRouteSpec(), RouteSpec.class);
                PublishedRoute published = new PublishedRoute(route.getRouteId(), spec);
                if (spec.alias() != null && !spec.alias().isBlank()) {
                    byAlias.put(spec.alias(), published);
                }
                if (spec.ingress() != null && !spec.ingress().isBlank()) {
                    byIngress.put(spec.ingress(), published);
                }
            } catch (IllegalArgumentException e) {
                skipped++;
            }
        }
        snapshot.set(new Snapshot(
                Map.copyOf(byAlias), Map.copyOf(byIngress), routes.size(), skipped));
    }

    /** 按别名查快照（客户端调用以 alias 寻址）。 */
    public Optional<PublishedRoute> findByAlias(String alias) {
        return Optional.ofNullable(snapshot.get().byAlias().get(alias));
    }

    /** 按入口路径查快照（Ingress 协议接入以路径寻址）。 */
    public Optional<PublishedRoute> findByIngress(String ingress) {
        return Optional.ofNullable(snapshot.get().byIngress().get(ingress));
    }

    /** 当前快照全部已发布路由（按 alias 去重后不定序，{@code /v1/models} 用）。 */
    public List<PublishedRoute> routes() {
        return List.copyOf(snapshot.get().byAlias().values());
    }

    /** 最近一次刷新读取到的已发布路由数。 */
    public int size() {
        return snapshot.get().total();
    }

    /** 最近一次刷新中解析失败被跳过的快照数。 */
    public int lastSkipped() {
        return snapshot.get().skipped();
    }

    private record Snapshot(Map<String, PublishedRoute> byAlias, Map<String, PublishedRoute> byIngress,
                            int total, int skipped) {
        private static final Snapshot EMPTY = new Snapshot(Map.of(), Map.of(), 0, 0);
    }
}
