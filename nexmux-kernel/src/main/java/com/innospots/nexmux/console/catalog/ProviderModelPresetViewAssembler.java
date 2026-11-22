package com.innospots.nexmux.console.catalog;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.innospots.nexmux.console.catalog.domain.vo.ModelPresetVo;
import com.innospots.nexmux.console.catalog.domain.vo.PresetImportResultVo;
import com.innospots.nexmux.console.catalog.domain.vo.ProviderPresetCatalogVo;
import com.innospots.nexmux.console.catalog.domain.vo.ProviderPresetVo;
import com.innospots.nexmux.core.catalog.preset.PresetI18n;
import com.innospots.nexmux.core.catalog.preset.config.ModelPresetItem;
import com.innospots.nexmux.core.catalog.preset.config.ProviderPresetItem;
import com.innospots.nexmux.core.catalog.preset.config.ProviderPresetsDocument;
import com.innospots.nexmux.core.catalog.preset.service.ProviderModelPresetImportService;
import com.innospots.nexmux.core.catalog.preset.service.ProviderModelPresetService;
import com.innospots.nexmux.core.model.dao.ModelDao;
import com.innospots.nexmux.core.model.domain.entity.ModelEntity;
import com.innospots.nexmux.core.provider.dao.ProviderDao;
import com.innospots.nexmux.core.provider.domain.entity.ProviderEntity;
import com.innospots.nexmux.core.shared.json.NexmuxJson;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;

/**
 * 供应商模板目录的 UI 投影：classpath 预设 → 接口 VO，并附带本地导入状态。
 *
 * <p>模板名称 / 说明在装配阶段按请求语言解析为单字符串（库表与接口均为单值契约），
 * 避免依赖序列化线程的语言环境。</p>
 */
@RequiredArgsConstructor
public class ProviderModelPresetViewAssembler {

    private final ProviderModelPresetService presetService;
    private final ProviderModelPresetImportService importService;
    private final ProviderDao providerDao;
    private final ModelDao modelDao;

    /**
     * 模板目录。
     *
     * @param locale 文案语言环境
     * @return 目录视图
     */
    public ProviderPresetCatalogVo catalog(Locale locale) {
        ProviderPresetsDocument meta = presetService.catalogMeta();
        Map<String, ProviderEntity> importedByCode = importedProviders();
        List<ProviderPresetVo> providers = meta.getProviders().stream()
                .map(item -> toVo(item, importedByCode, locale))
                .toList();
        long importedCount = providers.stream().filter(ProviderPresetVo::imported).count();
        return new ProviderPresetCatalogVo(
                meta.getApiVersion(),
                meta.getUpdatedAt(),
                providers.size(),
                meta.getModelCount(),
                (int) importedCount,
                providers);
    }

    /**
     * 单个模板。
     *
     * @param providerCode 模板编码
     * @param locale       文案语言环境
     * @return 模板视图
     */
    public ProviderPresetVo provider(String providerCode, Locale locale) {
        ProviderPresetItem item = presetService.getProvider(providerCode);
        return toVo(item, importedProviders(), locale);
    }

    /**
     * 模板下的模型条目（含本地是否已存在）。
     *
     * @param providerCode 模板编码
     * @param locale       文案语言环境
     * @return 模型条目
     */
    public List<ModelPresetVo> models(String providerCode, Locale locale) {
        return toModelRows(presetService.listModels(providerCode), providerCode, locale);
    }

    /**
     * 本地供应商的「可用模型」：按本地编码反解模板，列出模板内的上游模型。
     *
     * <p>与「已建模模型」区分：这里描述该供应商可用的上游模型，不依赖是否已建模；
     * {@code existsLocally} 仅表示本地是否已创建对应 Model。</p>
     *
     * @param localProviderCode 本地供应商编码（可带 {@code -2} 等导入后缀）
     * @param locale            文案语言环境
     * @return 可用模型条目
     */
    public List<ModelPresetVo> availableModels(String localProviderCode, Locale locale) {
        String presetCode = importService.resolvePresetCode(localProviderCode);
        return toModelRows(presetService.listModels(presetCode), presetCode, locale);
    }

    private List<ModelPresetVo> toModelRows(
            List<ModelPresetItem> items, String presetCode, Locale locale) {
        Set<String> existing = existingProviderModelIds(presetCode);
        List<ModelPresetVo> rows = new ArrayList<>(items.size());
        for (ModelPresetItem item : items) {
            String providerModelId = item.getProviderModelId();
            rows.add(new ModelPresetVo(
                    providerModelId,
                    PresetI18n.localizedOr(item.getDisplayName(), locale, providerModelId),
                    PresetI18n.localized(item.getDescription(), locale),
                    item.getModelType(),
                    item.getContextLength(),
                    item.getMaxOutputTokens(),
                    item.getInputModalities(),
                    item.getOutputModalities(),
                    capabilityTagsOf(item),
                    existing.contains(providerModelId)));
        }
        return rows;
    }

    /**
     * 导入选中模板（每次导入插入新的供应商行）。
     *
     * @param providerCodes 模板编码
     * @param includeModels 是否同时创建模板内模型
     * @param locale        落库文案语言
     * @return 导入结果视图
     */
    public PresetImportResultVo importPresets(
            List<String> providerCodes, boolean includeModels, Locale locale) {
        ProviderModelPresetImportService.ImportResult result = includeModels
                ? importService.importProvidersWithModels(providerCodes, locale)
                : importService.importProviders(providerCodes, locale);
        return new PresetImportResultVo(
                result.imported().stream()
                        .map(item -> new PresetImportResultVo.Item(
                                item.presetCode(), item.providerCode(), item.displayName(), item.modelCount()))
                        .toList(),
                result.skipped(),
                result.failures().stream()
                        .map(item -> new PresetImportResultVo.Failure(item.presetCode(), item.message()))
                        .toList());
    }

    private ProviderPresetVo toVo(
            ProviderPresetItem item, Map<String, ProviderEntity> importedByCode, Locale locale) {
        List<String> protocols = presetService.protocolsOf(item.getProviderCode());
        ProviderEntity local = importedByCode.get(item.getProviderCode());
        return new ProviderPresetVo(
                item.getProviderCode(),
                PresetI18n.localizedOr(item.getDisplayName(), locale, item.getProviderCode()),
                PresetI18n.localized(item.getDescription(), locale),
                item.getProviderType(),
                item.getDefaultProtocol(),
                protocols,
                item.getVendorCode(),
                item.getSourceVendorCode(),
                item.getBaseUrl(),
                PresetEndpointUrls.chatUrl(item.getBaseUrl(), item.getDefaultProtocol()),
                item.getTimeoutMs(),
                item.getModelCount() == null ? 0 : item.getModelCount(),
                local != null,
                local == null ? null : local.getProviderCode());
    }

    /** 模板编码 → 已导入的本地供应商（后缀编码归并到首个模板编码）。 */
    private Map<String, ProviderEntity> importedProviders() {
        Map<String, ProviderEntity> byPresetCode = new LinkedHashMap<>();
        for (ProviderEntity entity : providerDao.selectList(null)) {
            String presetCode = importService.resolvePresetCode(entity.getProviderCode());
            byPresetCode.putIfAbsent(presetCode, entity);
        }
        return byPresetCode;
    }

    /** 模板下模型在本地是否已存在（按上游模型 ID 匹配该模板导入的任意供应商）。 */
    private Set<String> existingProviderModelIds(String presetCode) {
        List<String> providerIds = providerDao.selectList(null).stream()
                .filter(entity -> presetCode.equals(importService.resolvePresetCode(entity.getProviderCode())))
                .map(ProviderEntity::getProviderId)
                .toList();
        if (providerIds.isEmpty()) {
            return Set.of();
        }
        return modelDao.selectList(new LambdaQueryWrapper<ModelEntity>()
                        .in(ModelEntity::getPrimaryProviderId, providerIds))
                .stream()
                .map(ModelEntity::getPrimaryProviderModelId)
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toSet());
    }

    private static List<String> capabilityTagsOf(ModelPresetItem item) {
        if (item.getCapabilityTags() == null || item.getCapabilityTags().isNull()) {
            return List.of();
        }
        if (!item.getCapabilityTags().isArray()) {
            return List.of(NexmuxJson.write(item.getCapabilityTags()));
        }
        List<String> tags = new ArrayList<>();
        item.getCapabilityTags().forEach(node -> {
            if (node.isTextual()) {
                tags.add(node.asText());
            }
        });
        return tags;
    }
}
