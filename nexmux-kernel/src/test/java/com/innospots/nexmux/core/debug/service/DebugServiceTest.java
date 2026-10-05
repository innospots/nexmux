package com.innospots.nexmux.core.debug.service;

import com.innospots.nexmux.core.access.dao.ClientDao;
import com.innospots.nexmux.core.access.domain.entity.ClientEntity;
import com.innospots.nexmux.core.audit.service.ConfigAuditService;
import com.innospots.nexmux.core.debug.domain.request.ClientInvokeDebugRequest;
import com.innospots.nexmux.core.debug.domain.request.ModelInvokeDebugRequest;
import com.innospots.nexmux.core.model.dao.ModelDao;
import com.innospots.nexmux.core.model.domain.entity.ModelEntity;
import com.innospots.nexmux.core.provider.dao.ProviderCredentialDao;
import com.innospots.nexmux.core.provider.dao.ProviderDao;
import com.innospots.nexmux.core.provider.domain.entity.ProviderCredentialEntity;
import com.innospots.nexmux.core.provider.domain.entity.ProviderEntity;
import com.innospots.nexmux.core.route.dao.RouteDao;
import com.innospots.nexmux.core.route.domain.entity.RouteEntity;
import com.innospots.nexmux.core.shared.status.NexmuxStatusCode;
import com.innospots.nexus.base.exception.NexusException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class DebugServiceTest {

    private ModelDao modelDao;
    private ProviderDao providerDao;
    private ProviderCredentialDao providerCredentialDao;
    private ClientDao clientDao;
    private RouteDao routeDao;
    private ConfigAuditService configAuditService;
    private DebugService debugService;

    @BeforeEach
    void setUp() {
        modelDao = Mockito.mock(ModelDao.class);
        providerDao = Mockito.mock(ProviderDao.class);
        providerCredentialDao = Mockito.mock(ProviderCredentialDao.class);
        clientDao = Mockito.mock(ClientDao.class);
        routeDao = Mockito.mock(RouteDao.class);
        configAuditService = Mockito.mock(ConfigAuditService.class);
        debugService = new DebugService(
                modelDao, providerDao, providerCredentialDao, clientDao, routeDao, configAuditService);
    }

    private ModelEntity model() {
        ModelEntity entity = new ModelEntity();
        entity.setModelId("md-1");
        entity.setModelCode("openai/o4-mini");
        entity.setPrimaryProviderId("pv-1");
        entity.setPrimaryProviderModelId("o4-mini");
        entity.setStatus("ENABLED");
        return entity;
    }

    private ProviderEntity provider() {
        ProviderEntity entity = new ProviderEntity();
        entity.setProviderId("pv-1");
        entity.setProviderCode("openai");
        entity.setBaseUrl("https://api.openai.com/v1");
        entity.setTimeoutMs(60000);
        entity.setStatus("ENABLED");
        return entity;
    }

    private ProviderCredentialEntity credential() {
        ProviderCredentialEntity entity = new ProviderCredentialEntity();
        entity.setProviderId("pv-1");
        entity.setCiphertext("sk-test");
        entity.setKeyVersion(1);
        return entity;
    }

    private RouteEntity publishedRoute() {
        RouteEntity entity = new RouteEntity();
        entity.setRouteId("rt-1");
        entity.setAlias("general");
        entity.setStatus("PUBLISHED");
        entity.setPublishedRouteSpec(
                "{\"alias\":\"general\",\"strategyType\":\"FIXED\","
                        + "\"candidates\":[{\"modelCode\":\"openai/o4-mini\",\"weight\":100,\"enabled\":true}]}");
        return entity;
    }

    private ClientEntity client(String status, String routeIdsJson) {
        ClientEntity entity = new ClientEntity();
        entity.setClientId("cl-1");
        entity.setClientCode("mobile-app");
        entity.setStatus(status);
        entity.setAuthorizedRouteIds(routeIdsJson);
        return entity;
    }

    // ── modelInvoke 目标解析 ────────────────────────────────────

    @Test
    void modelInvokeRejectsUnknownModel() {
        when(modelDao.selectById("md-x")).thenReturn(null);

        assertThatThrownBy(() -> debugService.modelInvoke(
                new ModelInvokeDebugRequest("md-x", "{\"messages\":[]}")))
                .isInstanceOfSatisfying(NexusException.class, e ->
                        assertThat(e.code()).isEqualTo(
                                NexmuxStatusCode.RESOURCE_NOT_FOUND.fullCode()));
    }

    @Test
    void modelInvokeReturnsFailureBodyWhenNoCredential() {
        when(modelDao.selectById("md-1")).thenReturn(model());
        when(providerDao.selectById("pv-1")).thenReturn(provider());
        when(providerCredentialDao.selectOne(any())).thenReturn(null);

        var result = debugService.modelInvoke(new ModelInvokeDebugRequest("md-1", "{\"messages\":[]}"));

        // 失败以结果体返回（不抛异常）：HTTP 422 + 可读错误
        assertThat(result.httpStatus()).isEqualTo(422);
        assertThat(result.body()).contains("credential");
    }

    // ── clientInvoke 前置校验 ───────────────────────────────────

    @Test
    void clientInvokeRejectsDisabledClient() {
        when(clientDao.selectById("cl-1")).thenReturn(client("DISABLED", "[\"rt-1\"]"));

        var result = debugService.clientInvoke(
                new ClientInvokeDebugRequest("cl-1", "general", "{\"messages\":[]}"));

        assertThat(result.httpStatus()).isEqualTo(403);
        assertThat(result.body()).contains("disabled");
    }

    @Test
    void clientInvokeRejectsUngrantedRoute() {
        when(clientDao.selectById("cl-1")).thenReturn(client("ENABLED", "[\"rt-other\"]"));
        when(routeDao.selectOne(any())).thenReturn(publishedRoute());

        var result = debugService.clientInvoke(
                new ClientInvokeDebugRequest("cl-1", "general", "{\"messages\":[]}"));

        assertThat(result.httpStatus()).isEqualTo(403);
        assertThat(result.body()).contains("not granted");
    }

    @Test
    void clientInvokeRejectsUnpublishedRoute() {
        RouteEntity draft = publishedRoute();
        draft.setStatus("DRAFT");
        draft.setPublishedRouteSpec(null);
        when(clientDao.selectById("cl-1")).thenReturn(client("ENABLED", "[\"rt-1\"]"));
        when(routeDao.selectOne(any())).thenReturn(draft);

        assertThatThrownBy(() -> debugService.clientInvoke(
                new ClientInvokeDebugRequest("cl-1", "general", "{\"messages\":[]}")))
                .isInstanceOfSatisfying(NexusException.class, e ->
                        assertThat(e.code()).isEqualTo(
                                NexmuxStatusCode.ROUTE_NOT_PUBLISHED.fullCode()));
    }

    @Test
    void clientInvokeResolvesFirstEnabledCandidate() {
        when(clientDao.selectById("cl-1")).thenReturn(client("ENABLED", "[\"rt-1\"]"));
        when(routeDao.selectOne(any())).thenReturn(publishedRoute());
        when(modelDao.selectOne(any())).thenReturn(model());
        when(providerDao.selectById("pv-1")).thenReturn(provider());
        when(providerCredentialDao.selectOne(any())).thenReturn(null);

        // 无凭证走失败结果体，但解析路径已验证（model 按候选 modelCode 被查询）
        var result = debugService.clientInvoke(
                new ClientInvokeDebugRequest("cl-1", "general", "{\"messages\":[]}"));

        assertThat(result.httpStatus()).isEqualTo(422);
        Mockito.verify(modelDao).selectOne(any());
    }
}
