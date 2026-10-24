package com.innospots.nexmux.core.route.service;

import com.innospots.nexmux.core.audit.service.ConfigAuditService;
import com.innospots.nexmux.core.route.dao.RouteDao;
import com.innospots.nexmux.core.route.domain.entity.RouteEntity;
import com.innospots.nexmux.core.route.domain.request.RoutePublishRequest;
import com.innospots.nexmux.core.shared.analysis.DependencyImpactAnalyzer;
import com.innospots.nexmux.core.audit.dao.ConfigAuditLogDao;
import com.innospots.nexmux.core.shared.status.NexmuxStatusCode;
import com.innospots.nexus.base.exception.NexusException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
        routeService = new RouteService(routeDao, configAuditService, dependencyImpactAnalyzer,
                Mockito.mock(ConfigAuditLogDao.class));
    }

    @Test
    void publishCopiesSpecAndSetsPublishedStatus() {
        RouteEntity entity = new RouteEntity();
        entity.setRouteId("rt-1");
        entity.setRouteSpec("{\"alias\":\"a\",\"candidates\":[{\"modelCode\":\"m0\",\"weight\":100}]}");
        entity.setStatus("DRAFT");
        when(routeDao.selectById("rt-1")).thenReturn(entity);
        when(dependencyImpactAnalyzer.analyzeDisable(any(), any()))
                .thenReturn(new DependencyImpactAnalyzer.ImpactReport(List.of(), List.of(), false));

        var result = routeService.publish("rt-1", new RoutePublishRequest("publish", "op-1"));

        assertThat(result.status()).isEqualTo("PUBLISHED");
        assertThat(entity.getPublishedRouteSpec()).isEqualTo(entity.getRouteSpec());
        assertThat(entity.getConfigRevision()).isEqualTo(1L);
        verify(configAuditService).record("ROUTE", "rt-1", "PUBLISH", "publish",
                "{\"before\":null,\"after\":" + entity.getRouteSpec() + "}", "op-1");
    }

    @Test
    void publishRejectsZeroCandidates() {
        // D-01：0 候选模型的路由不得发布
        RouteEntity entity = new RouteEntity();
        entity.setRouteId("rt-1");
        entity.setRouteSpec("{\"alias\":\"a\",\"candidates\":[]}");
        entity.setStatus("DRAFT");
        when(routeDao.selectById("rt-1")).thenReturn(entity);
        when(dependencyImpactAnalyzer.analyzeDisable(any(), any()))
                .thenReturn(new DependencyImpactAnalyzer.ImpactReport(List.of(), List.of(), false));

        assertThatThrownBy(() -> routeService.publish("rt-1", new RoutePublishRequest(null, null)))
                .isInstanceOfSatisfying(NexusException.class, e ->
                        assertThat(e.code()).isEqualTo(
                                NexmuxStatusCode.OPERATION_PRECONDITION_FAILED.fullCode()));
        assertThat(entity.getStatus()).isEqualTo("DRAFT");
    }

    @Test
    void publishRejectsAllDisabledCandidates() {
        // D-01：候选全部停用时不得发布（enabled=false 与 Runtime 选路口径一致）
        RouteEntity entity = new RouteEntity();
        entity.setRouteId("rt-1");
        entity.setRouteSpec("{\"alias\":\"a\",\"candidates\":[{\"modelCode\":\"m0\",\"weight\":100,\"enabled\":false}]}");
        entity.setStatus("DRAFT");
        when(routeDao.selectById("rt-1")).thenReturn(entity);
        when(dependencyImpactAnalyzer.analyzeDisable(any(), any()))
                .thenReturn(new DependencyImpactAnalyzer.ImpactReport(List.of(), List.of(), false));

        assertThatThrownBy(() -> routeService.publish("rt-1", new RoutePublishRequest(null, null)))
                .isInstanceOfSatisfying(NexusException.class, e ->
                        assertThat(e.code()).isEqualTo(
                                NexmuxStatusCode.OPERATION_PRECONDITION_FAILED.fullCode()));
    }
}
