package com.innospots.nexmux.core.provider.service;

import com.innospots.nexmux.core.model.dao.ModelDao;
import com.innospots.nexmux.core.model.domain.entity.ModelEntity;
import com.innospots.nexmux.core.provider.dao.ProviderCredentialDao;
import com.innospots.nexmux.core.provider.dao.ProviderDao;
import com.innospots.nexmux.core.provider.domain.entity.ProviderCredentialEntity;
import com.innospots.nexmux.core.provider.domain.entity.ProviderEntity;
import com.innospots.nexmux.core.provider.domain.request.ProviderCreateRequest;
import com.innospots.nexmux.core.provider.domain.request.ProviderPageRequest;
import com.innospots.nexmux.core.provider.domain.request.ProviderUpdateRequest;
import com.innospots.nexmux.core.provider.domain.vo.ConnectionTestVo;
import com.innospots.nexmux.core.provider.domain.vo.ProviderDetailVo;
import com.innospots.nexmux.core.provider.domain.vo.UpstreamModelVo;
import com.innospots.nexmux.core.shared.status.NexmuxStatusCode;
import com.innospots.nexmux.core.audit.service.ConfigAuditService;
import com.innospots.nexus.base.exception.NexusException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class ProviderServiceTest {

    private ProviderDao providerDao;
    private ProviderCredentialDao providerCredentialDao;
    private ModelDao modelDao;
    private ProviderService providerService;

    @BeforeEach
    void setUp() {
        providerDao = Mockito.mock(ProviderDao.class);
        providerCredentialDao = Mockito.mock(ProviderCredentialDao.class);
        modelDao = Mockito.mock(ModelDao.class);
        providerService = new ProviderService(providerDao, providerCredentialDao, modelDao, Mockito.mock(ConfigAuditService.class),
                new com.innospots.nexmux.core.shared.security.CredentialCipher(null));
    }

    @Test
    void createEncryptsCredentialAtRestWhenCipherEnabled() {
        // 凭证加密：配置密钥后落库为 aesgcm:v1 密文且不含明文；可解密回原文
        com.innospots.nexmux.core.shared.security.CredentialCipher cipher =
                new com.innospots.nexmux.core.shared.security.CredentialCipher("test-secret");
        ProviderService encrypting = new ProviderService(providerDao, providerCredentialDao, modelDao,
                Mockito.mock(ConfigAuditService.class), cipher);
        when(providerDao.selectCount(any())).thenReturn(0L);

        encrypting.create(new ProviderCreateRequest("openai", "OpenAI", "openai", "openai-chat",
                "openai", null, null, "https://api.openai.com/v1", null, null, "sk-live-key", null, null));

        org.mockito.ArgumentCaptor<ProviderCredentialEntity> captor =
                org.mockito.ArgumentCaptor.forClass(ProviderCredentialEntity.class);
        Mockito.verify(providerCredentialDao).insert(captor.capture());
        String stored = captor.getValue().getCiphertext();
        assertThat(stored).startsWith("aesgcm:v1:").doesNotContain("sk-live-key");
        assertThat(cipher.decrypt(stored)).isEqualTo("sk-live-key");
    }

    @Test
    void createAndUpdatePersistProxy() {
        when(providerDao.selectCount(any())).thenReturn(0L);
        when(providerDao.selectById("pv-1")).thenReturn(provider("pv-1", "openai"));

        providerService.create(new ProviderCreateRequest("openai", "OpenAI", "COMMERCIAL",
                "openai-completions", "openai", null, null, "https://api.openai.com/v1",
                null, 60000, null, null, "127.0.0.1:18097"));
        providerService.update("pv-1", new ProviderUpdateRequest(
                "OpenAI", "https://api.openai.com/v1", null, 60000, null, null, "127.0.0.1:18098"));

        org.mockito.ArgumentCaptor<ProviderEntity> captor = org.mockito.ArgumentCaptor.forClass(ProviderEntity.class);
        Mockito.verify(providerDao).insert(captor.capture());
        assertThat(captor.getValue().getProxy()).isEqualTo("127.0.0.1:18097");
        assertThat(providerDao.selectById("pv-1").getProxy()).isEqualTo("127.0.0.1:18098");
    }

    private ProviderEntity provider(String id, String code) {
        ProviderEntity entity = new ProviderEntity();
        entity.setProviderId(id);
        entity.setProviderCode(code);
        entity.setDisplayName("阿里云百炼");
        entity.setProviderType("OPENAI");
        entity.setDefaultProtocol("OpenAI / Chat Completions");
        entity.setVendorCode("dashscope");
        entity.setSourceVendorCode("dashscope");
        entity.setBaseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1");
        entity.setStatus("ENABLED");
        return entity;
    }

    private ProviderCredentialEntity credential(String providerId, String ciphertext) {
        ProviderCredentialEntity entity = new ProviderCredentialEntity();
        entity.setCredentialId("pc-1");
        entity.setProviderId(providerId);
        entity.setCredentialMode("API_KEY");
        entity.setCiphertext(ciphertext);
        entity.setKeyVersion(1);
        return entity;
    }

    // ── getByCode ─────────────────────────────────────────────

    @Test
    void getByCodeReturnsDetailWithConfiguredKeyStatus() {
        ProviderEntity entity = provider("pv-1", "dashscope");
        when(providerDao.selectOne(any())).thenReturn(entity);
        when(providerCredentialDao.selectOne(any())).thenReturn(credential("pv-1", "sk-***"));
        ModelEntity model = new ModelEntity();
        model.setModelCode("qwen3.5-flash");
        model.setDisplayName("Qwen3.5 Flash");
        model.setModelType("LLM");
        model.setPrimaryProviderModelId("qwen3.5-flash");
        model.setStatus("ENABLED");
        when(modelDao.selectList(any())).thenReturn(List.of(model));

        ProviderDetailVo detail = providerService.getByCode("dashscope");

        assertThat(detail.providerCode()).isEqualTo("dashscope");
        assertThat(detail.keyStatus()).isEqualTo("configured");
        assertThat(detail.keyVersion()).isEqualTo(1);
        assertThat(detail.models()).hasSize(1);
        assertThat(detail.models().get(0).modelCode()).isEqualTo("qwen3.5-flash");
        assertThat(detail.models().get(0).status()).isEqualTo("ENABLED");
    }

    @Test
    void getByCodeDerivesPendingKeyStatusWhenCiphertextBlank() {
        when(providerDao.selectOne(any())).thenReturn(provider("pv-1", "deepseek"));
        when(providerCredentialDao.selectOne(any())).thenReturn(credential("pv-1", " "));
        when(modelDao.selectList(any())).thenReturn(List.of());

        ProviderDetailVo detail = providerService.getByCode("deepseek");

        assertThat(detail.keyStatus()).isEqualTo("pending");
    }

    @Test
    void getByCodeDerivesNoneKeyStatusWhenNoCredential() {
        when(providerDao.selectOne(any())).thenReturn(provider("pv-1", "ollama"));
        when(providerCredentialDao.selectOne(any())).thenReturn(null);
        when(modelDao.selectList(any())).thenReturn(List.of());

        ProviderDetailVo detail = providerService.getByCode("ollama");

        assertThat(detail.keyStatus()).isEqualTo("none");
        assertThat(detail.keyVersion()).isNull();
    }

    @Test
    void getByCodeThrowsNotFoundWhenMissing() {
        when(providerDao.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> providerService.getByCode("missing"))
                .isInstanceOfSatisfying(NexusException.class, e ->
                        assertThat(e.code()).isEqualTo(
                                NexmuxStatusCode.RESOURCE_NOT_FOUND.fullCode()));
    }

    // ── disable ───────────────────────────────────────────────

    @Test
    void disableSetsStatusDisabled() {
        ProviderEntity entity = provider("pv-1", "dashscope");
        when(providerDao.selectById("pv-1")).thenReturn(entity);

        var vo = providerService.disable("pv-1");

        assertThat(entity.getStatus()).isEqualTo("DISABLED");
        assertThat(vo.status()).isEqualTo("DISABLED");
        Mockito.verify(providerDao).updateById(entity);
    }

    // ── delete ──────────────────────────────────────────────

    @Test
    void deleteRemovesCredentialAndProvider() {
        ProviderEntity entity = provider("pv-1", "dashscope");
        when(providerDao.selectById("pv-1")).thenReturn(entity);
        when(modelDao.selectCount(any())).thenReturn(0L);
        ProviderCredentialEntity credential = credential("pv-1", "cipher");
        when(providerCredentialDao.selectOne(any())).thenReturn(credential);

        providerService.delete("pv-1");

        Mockito.verify(providerCredentialDao).deleteById(credential);
        Mockito.verify(providerDao).deleteById(entity);
    }

    @Test
    void deleteRejectsWhenModelsReference() {
        when(providerDao.selectById("pv-1")).thenReturn(provider("pv-1", "dashscope"));
        // D-05：引用检查 = 主绑定（3）+ 扩展绑定 extra_bindings（2），合并计数
        when(modelDao.selectCount(any())).thenReturn(3L, 2L);

        assertThatThrownBy(() -> providerService.delete("pv-1"))
                .isInstanceOf(NexusException.class)
                .hasMessageContaining("5");
        Mockito.verify(providerDao, Mockito.never()).deleteById(any(ProviderEntity.class));
    }

    // ── pullModels ────────────────────────────────────────────

    @Test
    void pullModelsRejectsWhenNoCredential() {
        when(providerDao.selectById("pv-1")).thenReturn(provider("pv-1", "dashscope"));
        when(providerCredentialDao.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> providerService.pullModels("pv-1"))
                .isInstanceOfSatisfying(NexusException.class, e ->
                        assertThat(e.code()).isEqualTo(
                                NexmuxStatusCode.OPERATION_PRECONDITION_FAILED.fullCode()));
    }

    @Test
    void upstreamRowsMarkLocalExistence() {
        ModelEntity local = new ModelEntity();
        local.setPrimaryProviderModelId("qwen3.5-flash");
        when(modelDao.selectList(any())).thenReturn(List.of(local));

        List<UpstreamModelVo> rows = providerService.toUpstreamRows(
                List.of("qwen3.5-flash", "qwen3-vl-plus"), "pv-1");

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).providerModelId()).isEqualTo("qwen3.5-flash");
        assertThat(rows.get(0).existsLocally()).isTrue();
        assertThat(rows.get(1).existsLocally()).isFalse();
    }

    // ── connectionTest ────────────────────────────────────────

    @Test
    void connectionTestFailsFastWhenNoCredential() {
        when(providerDao.selectById("pv-1")).thenReturn(provider("pv-1", "ollama"));
        when(providerCredentialDao.selectOne(any())).thenReturn(null);

        ConnectionTestVo result = providerService.connectionTest("pv-1");

        assertThat(result.ok()).isFalse();
        assertThat(result.detail()).contains("API Key");
    }

    @Test
    void connectionTestThrowsNotFoundWhenProviderMissing() {
        when(providerDao.selectById("pv-x")).thenReturn(null);

        assertThatThrownBy(() -> providerService.connectionTest("pv-x"))
                .isInstanceOfSatisfying(NexusException.class, e ->
                        assertThat(e.code()).isEqualTo(
                                NexmuxStatusCode.RESOURCE_NOT_FOUND.fullCode()));
    }

    // ── page 筛选 ─────────────────────────────────────────────

    @Test
    void pageAppliesKeyStatusPendingViaCredentialLookup() {
        ProviderCredentialEntity pending = credential("pv-1", "");
        when(providerCredentialDao.selectList(any())).thenReturn(List.of(pending));
        when(providerDao.selectCount(any())).thenReturn(1L);
        when(providerDao.selectList(any())).thenReturn(List.of(provider("pv-1", "deepseek")));

        var result = providerService.page(new ProviderPageRequest(
                1L, 20L, null, null, "pending", null));

        assertThat(result.records()).hasSize(1);
        Mockito.verify(providerCredentialDao).selectList(any());
    }

    // ── update + API Key ─────────────────────────────────────

    @Test
    void updateWithApiKeyCreatesCredential() {
        ProviderEntity entity = provider("pv-1", "deepseek");
        when(providerDao.selectById("pv-1")).thenReturn(entity);
        when(providerCredentialDao.selectOne(any())).thenReturn(null);

        providerService.update("pv-1", new ProviderUpdateRequest(
                "DeepSeek", "https://api.deepseek.com", null, null, "sk-test", null, null));

        Mockito.verify(providerCredentialDao).insert(Mockito.<ProviderCredentialEntity>argThat(c ->
                "pv-1".equals(c.getProviderId())
                        && "sk-test".equals(c.getCiphertext())
                        && Integer.valueOf(1).equals(c.getKeyVersion())
                        && c.getRotatedAt() != null));
    }

    @Test
    void updateWithApiKeyRotatesExistingCredential() {
        ProviderEntity entity = provider("pv-1", "deepseek");
        when(providerDao.selectById("pv-1")).thenReturn(entity);
        var existing = new com.innospots.nexmux.core.provider.domain.entity.ProviderCredentialEntity();
        existing.setCredentialId("cred-1");
        existing.setProviderId("pv-1");
        existing.setKeyVersion(3);
        when(providerCredentialDao.selectOne(any())).thenReturn(existing);

        providerService.update("pv-1", new ProviderUpdateRequest(
                "DeepSeek", "https://api.deepseek.com", null, null, "sk-new", null, null));

        assertThat(existing.getKeyVersion()).isEqualTo(4);
        assertThat(existing.getCiphertext()).isEqualTo("sk-new");
        Mockito.verify(providerCredentialDao).updateById(existing);
    }

    @Test
    void updateWithoutApiKeyLeavesCredentialUntouched() {
        ProviderEntity entity = provider("pv-1", "deepseek");
        when(providerDao.selectById("pv-1")).thenReturn(entity);

        providerService.update("pv-1", new ProviderUpdateRequest(
                "DeepSeek", "https://api.deepseek.com", null, null, null, null, null));

        Mockito.verify(providerCredentialDao, Mockito.never()).insert(any(ProviderCredentialEntity.class));
        Mockito.verify(providerCredentialDao, Mockito.never()).updateById(any(ProviderCredentialEntity.class));
    }
}
