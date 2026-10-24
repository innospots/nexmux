package com.innospots.nexmux.inbound.runtime;

import com.innospots.nexmux.core.access.dao.ClientDao;
import com.innospots.nexmux.core.model.dao.ModelDao;
import com.innospots.nexmux.core.model.domain.entity.ModelEntity;
import com.innospots.nexmux.core.provider.dao.ProviderCredentialDao;
import com.innospots.nexmux.core.provider.dao.ProviderDao;
import com.innospots.nexmux.core.provider.domain.entity.ProviderCredentialEntity;
import com.innospots.nexmux.core.provider.domain.entity.ProviderEntity;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class BindingHealthProberTest {

    private ModelDao modelDao;
    private ProviderDao providerDao;
    private ProviderCredentialDao credentialDao;
    private ClientDao clientDao;
    private RuntimeCatalogCache catalogCache;
    private BindingHealthRegistry registry;
    private BindingHealthDao healthDao;

    @BeforeEach
    void setUp() {
        modelDao = Mockito.mock(ModelDao.class);
        providerDao = Mockito.mock(ProviderDao.class);
        credentialDao = Mockito.mock(ProviderCredentialDao.class);
        clientDao = Mockito.mock(ClientDao.class);
        catalogCache = new RuntimeCatalogCache(modelDao, providerDao, credentialDao, clientDao);
        registry = new BindingHealthRegistry(3, 10000);
        healthDao = Mockito.mock(BindingHealthDao.class);
    }

    private HttpServer modelsEndpoint(int status) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/models", exchange -> {
            byte[] body = "{\"data\":[]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        return server;
    }

    private void stubCatalog(String baseUrl, String providerStatus) {
        ProviderEntity provider = new ProviderEntity();
        provider.setProviderId("pv-1");
        provider.setStatus(providerStatus);
        provider.setBaseUrl(baseUrl);
        ProviderCredentialEntity credential = new ProviderCredentialEntity();
        credential.setProviderId("pv-1");
        credential.setCiphertext("sk-test");
        ModelEntity model = new ModelEntity();
        model.setModelCode("openai/o4-mini");
        model.setStatus("ENABLED");
        model.setPrimaryProviderId("pv-1");
        model.setPrimaryProviderModelId("o4-mini");
        when(modelDao.selectList(null)).thenReturn(List.of(model));
        when(providerDao.selectList(null)).thenReturn(List.of(provider));
        when(credentialDao.selectList(null)).thenReturn(List.of(credential));
        when(clientDao.selectList(null)).thenReturn(List.of());
        catalogCache.refresh();
    }

    @Test
    void probeSuccessMarksHealthyAndPersists() throws Exception {
        HttpServer server = modelsEndpoint(200);
        try {
            stubCatalog("http://127.0.0.1:" + server.getAddress().getPort() + "/v1", "ENABLED");

            int probed = new BindingHealthProber(catalogCache, registry, healthDao, 3000,
                    new com.innospots.nexmux.core.shared.security.CredentialCipher(null)).probeAll();

            assertThat(probed).isEqualTo(1);
            assertThat(registry.statusOf(new BindingHealthRegistry.Target("pv-1", "o4-mini")))
                    .isEqualTo(HealthStatus.HEALTHY);
            Mockito.verify(healthDao).insert(Mockito.<BindingHealthEntity>argThat(row ->
                    "pv-1".equals(row.getProviderId())
                            && "o4-mini".equals(row.getProviderModelId())
                            && "HEALTHY".equals(row.getHealthStatus())
                            && "openai/o4-mini".equals(row.getModelCode())));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void probeFailureTripsUnavailableAfterThreshold() throws Exception {
        HttpServer server = modelsEndpoint(500);
        try {
            stubCatalog("http://127.0.0.1:" + server.getAddress().getPort() + "/v1", "ENABLED");
            BindingHealthProber prober = new BindingHealthProber(catalogCache, registry, healthDao, 3000,
                    new com.innospots.nexmux.core.shared.security.CredentialCipher(null));

            prober.probeAll();
            prober.probeAll();
            assertThat(registry.statusOf(new BindingHealthRegistry.Target("pv-1", "o4-mini")))
                    .isEqualTo(HealthStatus.HEALTHY);

            prober.probeAll();

            assertThat(registry.statusOf(new BindingHealthRegistry.Target("pv-1", "o4-mini")))
                    .isEqualTo(HealthStatus.UNAVAILABLE);
            // 失败也持久化（upsert 首轮 insert）
            Mockito.verify(healthDao, Mockito.atLeastOnce()).insert(Mockito.<BindingHealthEntity>argThat(row ->
                    row.getConsecutiveFailures() != null && row.getConsecutiveFailures() >= 1
                            && row.getLastError() != null && row.getLastError().contains("500")));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void skipsDisabledProvider() {
        stubCatalog("http://127.0.0.1:1/v1", "DISABLED");

        int probed = new BindingHealthProber(catalogCache, registry, healthDao, 3000,
                    new com.innospots.nexmux.core.shared.security.CredentialCipher(null)).probeAll();

        assertThat(probed).isZero();
        Mockito.verifyNoInteractions(healthDao);
    }
}
