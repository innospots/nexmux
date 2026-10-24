package com.innospots.nexmux.inbound.pipeline;

import com.innospots.nexmux.core.access.dao.ClientDao;
import com.innospots.nexmux.core.access.domain.entity.ClientEntity;
import com.innospots.nexmux.core.model.dao.ModelDao;
import com.innospots.nexmux.core.provider.dao.ProviderCredentialDao;
import com.innospots.nexmux.core.provider.dao.ProviderDao;
import com.innospots.nexmux.core.shared.security.AppKeySupport;
import com.innospots.nexmux.core.shared.status.NexmuxStatusCode;
import com.innospots.nexmux.inbound.runtime.RuntimeCatalogCache;
import com.innospots.nexus.base.exception.NexusException;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

class AppKeyVerifierTest {

    private ModelDao modelDao;
    private ProviderDao providerDao;
    private ProviderCredentialDao credentialDao;
    private ClientDao clientDao;
    private RuntimeCatalogCache catalogCache;
    private AppKeyVerifier verifier;

    @BeforeEach
    void setUp() {
        modelDao = Mockito.mock(ModelDao.class);
        providerDao = Mockito.mock(ProviderDao.class);
        credentialDao = Mockito.mock(ProviderCredentialDao.class);
        clientDao = Mockito.mock(ClientDao.class);
        when(modelDao.selectList(null)).thenReturn(List.of());
        when(providerDao.selectList(null)).thenReturn(List.of());
        when(credentialDao.selectList(null)).thenReturn(List.of());
        catalogCache = new RuntimeCatalogCache(modelDao, providerDao, credentialDao, clientDao);
        verifier = new AppKeyVerifier(catalogCache);
    }

    private ClientEntity clientWithKey(String plainKey, String status, LocalDateTime expiresAt) {
        AppKeySupport.Material material = AppKeySupport.generate();
        ClientEntity client = new ClientEntity();
        client.setClientId("cl-1");
        client.setClientCode("app-a");
        client.setStatus(status);
        client.setExpiresAt(expiresAt);
        client.setAppKeyHash(AppKeySupport.hash(plainKey, material.salt()));
        client.setAppKeySalt(material.salt());
        return client;
    }

    private void refreshWith(List<ClientEntity> clients) {
        when(clientDao.selectList(null)).thenReturn(clients);
        catalogCache.refresh();
    }

    @Test
    void authenticatesValidKey() throws Exception {
        ClientEntity client = clientWithKey("gw-sk-good", "ENABLED", null);
        refreshWith(List.of(client));

        assertThat(verifier.authenticate("gw-sk-good").getClientId()).isEqualTo("cl-1");
    }

    @Test
    void rejectsMissingKey() {
        assertThatThrownBy(() -> verifier.authenticate(null))
                .isInstanceOfSatisfying(NexusException.class, e ->
                        assertThat(e.code()).isEqualTo(NexmuxStatusCode.APP_KEY_INVALID.fullCode()));
        assertThatThrownBy(() -> verifier.authenticate("  "))
                .isInstanceOf(NexusException.class);
    }

    @Test
    void rejectsUnknownKey() throws Exception {
        refreshWith(List.of(clientWithKey("gw-sk-good", "ENABLED", null)));

        assertThatThrownBy(() -> verifier.authenticate("gw-sk-wrong"))
                .isInstanceOfSatisfying(NexusException.class, e ->
                        assertThat(e.code()).isEqualTo(NexmuxStatusCode.APP_KEY_INVALID.fullCode()));
    }

    @Test
    void rejectsDisabledClient() throws Exception {
        refreshWith(List.of(clientWithKey("gw-sk-good", "DISABLED", null)));

        assertThatThrownBy(() -> verifier.authenticate("gw-sk-good"))
                .isInstanceOfSatisfying(NexusException.class, e ->
                        assertThat(e.code()).isEqualTo(NexmuxStatusCode.APP_KEY_INVALID.fullCode()));
    }

    @Test
    void rejectsExpiredClient() throws Exception {
        refreshWith(List.of(clientWithKey("gw-sk-good", "ENABLED", LocalDateTime.now().minusDays(1))));

        assertThatThrownBy(() -> verifier.authenticate("gw-sk-good"))
                .isInstanceOfSatisfying(NexusException.class, e ->
                        assertThat(e.code()).isEqualTo(NexmuxStatusCode.APP_KEY_INVALID.fullCode()));
    }
}
