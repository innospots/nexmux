package com.innospots.nexmux.core.route.service;

import com.innospots.nexmux.core.audit.service.ConfigAuditService;
import com.innospots.nexmux.core.route.dao.RouteDao;
import com.innospots.nexmux.core.route.domain.entity.RouteEntity;
import com.innospots.nexmux.core.route.domain.request.RoutePublishRequest;
import com.innospots.nexmux.core.shared.analysis.DependencyImpactAnalyzer;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoutePublishServiceTest {

    private RouteDao routeDao;
    private ConfigAuditService configAuditService;
    private DependencyImpactAnalyzer dependencyImpactAnalyzer;
    private RouteService routeService;

    @BeforeEach
    void setUp() {
        routeDao = Mockito.mock(RouteDao.class);
        configAuditService = Mockito.mock(ConfigAuditService.class);
        dependencyImpactAnalyzer = Mockito.mock(DependencyImpactAnalyzer.class);
        routeService = new RouteService(routeDao, configAuditService, dependencyImpactAnalyzer);
    }

    @Test
    void publishCopiesSpecAndSetsPublishedStatus() {
        RouteEntity entity = new RouteEntity();
        entity.setRouteId("rt-1");
        entity.setRouteSpec("{\"alias\":\"a\"}");
        entity.setStatus("DRAFT");
        when(routeDao.selectById("rt-1")).thenReturn(entity);
        when(dependencyImpactAnalyzer.analyzeDisable(any(), any()))
                .thenReturn(new DependencyImpactAnalyzer.ImpactReport(List.of(), List.of(), false));

        var result = routeService.publish("rt-1", new RoutePublishRequest("publish", "op-1"));

        assertThat(result.status()).isEqualTo("PUBLISHED");
        assertThat(entity.getPublishedRouteSpec()).isEqualTo(entity.getRouteSpec());
        verify(configAuditService).record("ROUTE", "rt-1", "PUBLISH", "publish",
                "{\"before\":null,\"after\":{\"alias\":\"a\"}}", "op-1");
    }
}
