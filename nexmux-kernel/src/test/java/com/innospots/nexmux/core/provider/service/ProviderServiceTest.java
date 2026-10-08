package com.innospots.nexmux.core.provider.service;

import com.innospots.nexmux.core.model.dao.ModelDao;
import com.innospots.nexmux.core.model.domain.entity.ModelEntity;
import com.innospots.nexmux.core.provider.dao.ProviderCredentialDao;
import com.innospots.nexmux.core.provider.dao.ProviderDao;
import com.innospots.nexmux.core.provider.domain.entity.ProviderCredentialEntity;
import com.innospots.nexmux.core.provider.domain.entity.ProviderEntity;
import com.innospots.nexmux.core.provider.domain.request.ProviderPageRequest;
import com.innospots.nexmux.core.provider.domain.vo.ConnectionTestVo;
import com.innospots.nexmux.core.provider.domain.vo.ProviderDetailVo;
import com.innospots.nexmux.core.provider.domain.vo.UpstreamModelVo;
import com.innospots.nexmux.core.shared.status.NexmuxStatusCode;
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
        providerService = new ProviderService(providerDao, providerCredentialDao, modelDao);
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
        when(modelDao.selectCount(any())).thenReturn(3L);

        assertThatThrownBy(() -> providerService.delete("pv-1"))
                .isInstanceOf(NexusException.class)
                .hasMessageContaining("3");
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
}
