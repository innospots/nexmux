package com.innospots.nexmux.inbound.runtime;

import com.innospots.nexmux.core.access.dao.ClientDao;
import com.innospots.nexmux.core.access.domain.entity.ClientEntity;
import com.innospots.nexmux.core.model.dao.ModelDao;
import com.innospots.nexmux.core.model.domain.entity.ModelEntity;
import com.innospots.nexmux.core.provider.dao.ProviderCredentialDao;
import com.innospots.nexmux.core.provider.dao.ProviderDao;
import com.innospots.nexmux.core.provider.domain.entity.ProviderCredentialEntity;
import com.innospots.nexmux.core.provider.domain.entity.ProviderEntity;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class RuntimeCatalogCacheTest {

    private ModelDao modelDao;
    private ProviderDao providerDao;
    private ProviderCredentialDao credentialDao;
    private ClientDao clientDao;
    private RuntimeCatalogCache cache;

    @BeforeEach
    void setUp() {
        modelDao = Mockito.mock(ModelDao.class);
        providerDao = Mockito.mock(ProviderDao.class);
        credentialDao = Mockito.mock(ProviderCredentialDao.class);
        clientDao = Mockito.mock(ClientDao.class);
        cache = new RuntimeCatalogCache(modelDao, providerDao, credentialDao, clientDao);
    }

    private static ModelEntity model(String code) {
        ModelEntity entity = new ModelEntity();
        entity.setModelId("md-" + code);
        entity.setModelCode(code);
        return entity;
    }

    private static ProviderEntity provider(String id) {
        ProviderEntity entity = new ProviderEntity();
        entity.setProviderId(id);
        return entity;
    }

    private static ProviderCredentialEntity credential(String providerId) {
        ProviderCredentialEntity entity = new ProviderCredentialEntity();
        entity.setProviderId(providerId);
        entity.setCiphertext("sk-" + providerId);
        return entity;
    }

    private static ClientEntity client(String code) {
        ClientEntity entity = new ClientEntity();
        entity.setClientId("cl-" + code);
        entity.setClientCode(code);
        return entity;
    }

    @Test
    void emptyBeforeRefresh() {
        assertThat(cache.findModel("a")).isEmpty();
        assertThat(cache.clients()).isEmpty();
    }

    @Test
    void refreshLoadsAllCatalogs() {
        when(modelDao.selectList(null)).thenReturn(List.of(model("m-a")));
        when(providerDao.selectList(null)).thenReturn(List.of(provider("pv-1")));
        when(credentialDao.selectList(null)).thenReturn(List.of(credential("pv-1")));
        when(clientDao.selectList(null)).thenReturn(List.of(client("app-a")));

        cache.refresh();

        assertThat(cache.findModel("m-a")).isPresent();
        assertThat(cache.findProvider("pv-1")).isPresent();
        assertThat(cache.findCredential("pv-1")).get()
                .extracting(ProviderCredentialEntity::getCiphertext).isEqualTo("sk-pv-1");
        assertThat(cache.clients()).hasSize(1);
    }

    @Test
    void refreshReplacesPreviousSnapshot() {
        when(modelDao.selectList(null)).thenReturn(List.of(model("m-a")));
        when(providerDao.selectList(null)).thenReturn(List.of());
        when(credentialDao.selectList(null)).thenReturn(List.of());
        when(clientDao.selectList(null)).thenReturn(List.of());
        cache.refresh();
        assertThat(cache.findModel("m-a")).isPresent();

        when(modelDao.selectList(null)).thenReturn(List.of());
        cache.refresh();

        assertThat(cache.findModel("m-a")).isEmpty();
    }
}
