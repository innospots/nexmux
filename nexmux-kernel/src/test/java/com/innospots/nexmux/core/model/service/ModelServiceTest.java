package com.innospots.nexmux.core.model.service;

import com.innospots.nexmux.core.model.dao.ModelDao;
import com.innospots.nexmux.core.model.domain.entity.ModelEntity;
import com.innospots.nexmux.core.audit.service.ConfigAuditService;
import com.innospots.nexmux.core.model.domain.request.ModelCreateRequest;
import com.innospots.nexmux.core.model.domain.request.ModelPageRequest;
import com.innospots.nexmux.core.model.domain.request.ModelUpdateRequest;
import com.innospots.nexmux.core.model.domain.vo.ModelDetailVo;
import com.innospots.nexmux.core.provider.dao.ProviderDao;
import com.innospots.nexmux.core.provider.domain.entity.ProviderEntity;
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

class ModelServiceTest {

    private ModelDao modelDao;
    private ProviderDao providerDao;
    private ModelService modelService;

    @BeforeEach
    void setUp() {
        // LambdaQueryWrapper 列名解析依赖 TableInfo（纯单测无 SqlSessionFactory，手动初始化）
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""),
                com.innospots.nexmux.core.model.domain.entity.ModelEntity.class);
        modelDao = Mockito.mock(ModelDao.class);
        providerDao = Mockito.mock(ProviderDao.class);
        modelService = new ModelService(modelDao, providerDao, Mockito.mock(ConfigAuditService.class));
    }

    private ProviderEntity provider(String id, String code) {
        ProviderEntity entity = new ProviderEntity();
        entity.setProviderId(id);
        entity.setProviderCode(code);
        entity.setDisplayName("阿里云百炼");
        entity.setVendorCode("dashscope");
        entity.setStatus("ENABLED");
        return entity;
    }

    private ModelEntity model(String id, String code) {
        ModelEntity entity = new ModelEntity();
        entity.setModelId(id);
        entity.setModelCode(code);
        entity.setDisplayName("Qwen3.5 Flash");
        entity.setModelType("LLM");
        entity.setStatus("ENABLED");
        entity.setContextLength(262144L);
        entity.setMaxOutputTokens(32768L);
        entity.setInputModalities("text,image");
        entity.setOutputModalities("text");
        entity.setTags("[\"general\"]");
        entity.setPrimaryProviderId("pv-1");
        entity.setPrimaryProviderModelId("qwen3.5-flash");
        entity.setPrimaryBindingEnabled(true);
        entity.setPrimaryBindingWeight(100);
        entity.setExtraBindings("[]");
        return entity;
    }

    // ── create（B1 遗留：primaryProvider 关联） ────────────────

    @Test
    void createResolvesPrimaryProviderByCode() {
        when(modelDao.selectCount(any())).thenReturn(0L);
        when(providerDao.selectOne(any())).thenReturn(provider("pv-1", "dashscope"));

        var vo = modelService.create(new ModelCreateRequest(
                "qwen3.5-flash", "Qwen3.5 Flash", "LLM",
                "dashscope", "qwen3.5-flash", null,
                262144L, 32768L, List.of("text", "image"), List.of("text"),
                List.of("general"), "轻量通用模型"));

        assertThat(vo.modelCode()).isEqualTo("qwen3.5-flash");
        Mockito.verify(modelDao).insert(Mockito.<ModelEntity>argThat(e ->
                "pv-1".equals(e.getPrimaryProviderId())
                        && "qwen3.5-flash".equals(e.getPrimaryProviderModelId())
                        && "text,image".equals(e.getInputModalities())));
    }

    @Test
    void createRejectsUnknownProviderCode() {
        when(modelDao.selectCount(any())).thenReturn(0L);
        when(providerDao.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> modelService.create(new ModelCreateRequest(
                "m1", "M1", "LLM", "ghost", null, null,
                null, null, null, null, null, null)))
                .isInstanceOfSatisfying(NexusException.class, e ->
                        assertThat(e.code()).isEqualTo(
                                NexmuxStatusCode.RESOURCE_NOT_FOUND.fullCode()));
    }

    // ── getByCode ─────────────────────────────────────────────

    @Test
    void getByCodeReturnsDetail() {
        when(modelDao.selectOne(any())).thenReturn(model("md-1", "qwen3.5-flash"));

        ModelDetailVo detail = modelService.getByCode("qwen3.5-flash");

        assertThat(detail.modelCode()).isEqualTo("qwen3.5-flash");
        assertThat(detail.modelType()).isEqualTo("LLM");
        assertThat(detail.contextLength()).isEqualTo(262144L);
        assertThat(detail.inputModalities()).isEqualTo("text,image");
        assertThat(detail.primaryProviderId()).isEqualTo("pv-1");
        assertThat(detail.extraBindings()).isEqualTo("[]");
    }

    @Test
    void getByCodeThrowsNotFoundWhenMissing() {
        when(modelDao.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> modelService.getByCode("missing"))
                .isInstanceOfSatisfying(NexusException.class, e ->
                        assertThat(e.code()).isEqualTo(
                                NexmuxStatusCode.RESOURCE_NOT_FOUND.fullCode()));
    }

    // ── update / disable ──────────────────────────────────────

    @Test
    void updateAppliesEditableFields() {
        ModelEntity entity = model("md-1", "qwen3.5-flash");
        when(modelDao.selectById("md-1")).thenReturn(entity);

        modelService.update("md-1", new ModelUpdateRequest(
                "Qwen3.5 Flash（新）", "更新描述", 128000L, 8192L,
                List.of("text"), List.of("text"), List.of("fast")));

        assertThat(entity.getDisplayName()).isEqualTo("Qwen3.5 Flash（新）");
        assertThat(entity.getContextLength()).isEqualTo(128000L);
        assertThat(entity.getInputModalities()).isEqualTo("text");
        assertThat(entity.getTags()).isEqualTo("[\"fast\"]");
        Mockito.verify(modelDao).updateById(entity);
    }

    @Test
    void disableSetsStatusDisabled() {
        ModelEntity entity = model("md-1", "qwen3.5-flash");
        when(modelDao.selectById("md-1")).thenReturn(entity);

        var vo = modelService.disable("md-1");

        assertThat(entity.getStatus()).isEqualTo("DISABLED");
        assertThat(vo.status()).isEqualTo("DISABLED");
    }

    // ── page 筛选 ─────────────────────────────────────────────

    @Test
    void pageResolvesProviderCodesToIds() {
        when(providerDao.selectList(any())).thenReturn(List.of(provider("pv-1", "dashscope")));
        when(modelDao.selectCount(any())).thenReturn(1L);
        when(modelDao.selectList(any())).thenReturn(List.of(model("md-1", "qwen3.5-flash")));

        var result = modelService.page(new ModelPageRequest(
                1L, 20L, "LLM", List.of("dashscope"), null));

        assertThat(result.records()).hasSize(1);
        Mockito.verify(providerDao).selectList(any());
    }

    @Test
    void pageFiltersByKeywordOnCodeAndName() {
        when(modelDao.selectCount(any())).thenReturn(0L);
        when(modelDao.selectList(any())).thenReturn(List.of());

        modelService.page(new ModelPageRequest(1L, 20L, null, null, "flash"));

        // 关键字过滤作用于 modelCode 与 displayName（OR LIKE），与 Provider 列表语义一致
        var captor = org.mockito.ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class);
        Mockito.verify(modelDao).selectList(captor.capture());
        String sql = captor.getValue().getSqlSegment();
        assertThat(sql).contains("LIKE");
        assertThat(captor.getValue().getParamNameValuePairs().values())
                .allMatch(v -> "%flash%".equals(v));
    }
}
