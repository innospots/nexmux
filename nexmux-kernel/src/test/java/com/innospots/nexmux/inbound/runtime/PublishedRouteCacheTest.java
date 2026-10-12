package com.innospots.nexmux.inbound.runtime;

import com.innospots.nexmux.core.route.dao.RouteDao;
import com.innospots.nexmux.core.route.domain.entity.RouteEntity;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class PublishedRouteCacheTest {

    private RouteDao routeDao;
    private PublishedRouteCache cache;

    @BeforeEach
    void setUp() {
        routeDao = Mockito.mock(RouteDao.class);
        cache = new PublishedRouteCache(routeDao);
    }

    private static RouteEntity route(String alias, String status, String publishedSpec) {
        RouteEntity entity = new RouteEntity();
        entity.setRouteId("rt-" + alias);
        entity.setAlias(alias);
        entity.setStatus(status);
        entity.setPublishedRouteSpec(publishedSpec);
        return entity;
    }

    private static String specJson(String alias, String ingress) {
        return "{\"alias\":\"" + alias + "\",\"ingress\":\"" + ingress
                + "\",\"strategyType\":\"FIXED\","
                + "\"candidates\":[{\"modelCode\":\"openai/o4-mini\",\"weight\":100}]}";
    }

    @Test
    void emptyBeforeRefresh() {
        assertThat(cache.size()).isZero();
        assertThat(cache.findByAlias("general")).isEmpty();
    }

    @Test
    void refreshLoadsOnlyPublishedSnapshots() {
        when(routeDao.selectList(any())).thenReturn(List.of(
                route("general", "PUBLISHED", specJson("general", "/v1/chat/completions"))));

        cache.refresh();

        // Dao 层已按 status=PUBLISHED AND published_route_spec IS NOT NULL 过滤，
        // 缓存只消费快照列，不触碰草稿 route_spec
        assertThat(cache.size()).isEqualTo(1);
        assertThat(cache.findByAlias("general")).isPresent();
        assertThat(cache.findByIngress("/v1/chat/completions")).isPresent();
        assertThat(cache.findByAlias("general").get().spec().strategyType()).isEqualTo("FIXED");
        assertThat(cache.findByAlias("general").get().routeId()).isEqualTo("rt-general");
        assertThat(cache.lastSkipped()).isZero();
    }

    @Test
    void refreshSkipsUnparseableSnapshotWithoutFailing() {
        when(routeDao.selectList(any())).thenReturn(List.of(
                route("general", "PUBLISHED", specJson("general", "/v1/chat/completions")),
                route("broken", "PUBLISHED", "{not-json")));

        cache.refresh();

        assertThat(cache.size()).isEqualTo(2);
        assertThat(cache.lastSkipped()).isEqualTo(1);
        assertThat(cache.findByAlias("general")).isPresent();
        assertThat(cache.findByAlias("broken")).isEmpty();
    }

    @Test
    void refreshReplacesPreviousSnapshot() {
        when(routeDao.selectList(any())).thenReturn(List.of(
                route("general", "PUBLISHED", specJson("general", "/v1/chat/completions"))));
        cache.refresh();
        assertThat(cache.findByAlias("general")).isPresent();

        // 路由下线后刷新：旧快照不再可见
        when(routeDao.selectList(any())).thenReturn(List.of());
        cache.refresh();

        assertThat(cache.size()).isZero();
        assertThat(cache.findByAlias("general")).isEmpty();
    }

    @Test
    void routerResolvesCandidateThroughSharedSelector() {
        when(routeDao.selectList(any())).thenReturn(List.of(
                route("general", "PUBLISHED", specJson("general", "/v1/chat/completions"))));
        cache.refresh();
        RuntimeRouter router = new RuntimeRouter(cache);

        var selection = router.resolveByIngress("/v1/chat/completions", Set.of(), null);

        assertThat(selection).isPresent();
        assertThat(selection.get().candidate().modelCode()).isEqualTo("openai/o4-mini");
        assertThat(router.resolveByIngress("/v1/unknown", Set.of(), null)).isEmpty();
        assertThat(router.resolveByAlias("general", Set.of(), null)).isPresent();
    }
}
