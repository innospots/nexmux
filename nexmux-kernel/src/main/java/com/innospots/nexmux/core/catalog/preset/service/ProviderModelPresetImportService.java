package com.innospots.nexmux.core.catalog.preset.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.innospots.nexmux.core.catalog.preset.PresetI18n;
import com.innospots.nexmux.core.catalog.preset.config.ModelPresetItem;
import com.innospots.nexmux.core.catalog.preset.config.ProviderPresetItem;
import com.innospots.nexmux.core.model.dao.ModelDao;
import com.innospots.nexmux.core.model.domain.entity.ModelEntity;
import com.innospots.nexmux.core.model.domain.request.ModelCreateRequest;
import com.innospots.nexmux.core.model.service.ModelService;
import com.innospots.nexmux.core.provider.dao.ProviderDao;
import com.innospots.nexmux.core.provider.domain.entity.ProviderEntity;
import com.innospots.nexmux.core.provider.domain.request.ProviderCreateRequest;
import com.innospots.nexmux.core.provider.domain.vo.ProviderVo;
import com.innospots.nexmux.core.provider.service.ProviderService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;

/**
 * 供应商模板导入：把 classpath 预设写入配置库。
 *
 * <p>导入语义：<b>每次导入都插入新的供应商行</b>，模板编码已被占用时追加
 * {@code -2}、{@code -3} 等后缀，避免与既有行冲突；名称与描述按本次请求语言
 * 落库为单字符串（库表为单值列，不做多语言存储）。</p>
 */
@RequiredArgsConstructor
public class ProviderModelPresetImportService {

    private final ProviderModelPresetService presetService;
    private final ProviderService providerService;
    private final ModelService modelService;
    private final ProviderDao providerDao;
    private final ModelDao modelDao;

    /**
     * 按模板导入供应商（不导入模型）。
     *
     * @param providerCodes 模板编码集合
     * @param locale        落库文案语言
     * @return 导入结果
     */
    public ImportResult importProviders(List<String> providerCodes, Locale locale) {
        List<ImportedProvider> imported = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        List<ImportFailure> failures = new ArrayList<>();
        Set<String> usedCodes = usedProviderCodes();
        for (String rawCode : distinct(providerCodes)) {
            Optional<ProviderPresetItem> preset = presetService.findProvider(rawCode);
            if (preset.isEmpty()) {
                skipped.add(rawCode);
                continue;
            }
            try {
                ImportedProvider result = createProvider(preset.get(), usedCodes, locale);
                usedCodes.add(result.providerCode());
                imported.add(result);
            } catch (RuntimeException exception) {
                failures.add(new ImportFailure(rawCode, messageOf(exception)));
            }
        }
        return new ImportResult(imported, skipped, failures);
    }

    /**
     * 按模板导入供应商并一并创建模板内模型。
     *
     * <p>供应商编码冲突时按 {@code -2} 后缀顺延；模型编码沿用模板编码，
     * 已存在则跳过并记入 {@code skippedModels}。</p>
     *
     * @param providerCodes 模板编码集合
     * @param locale        落库文案语言
     * @return 导入结果
     */
    public ImportResult importProvidersWithModels(List<String> providerCodes, Locale locale) {
        List<ImportedProvider> imported = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        List<ImportFailure> failures = new ArrayList<>();
        Set<String> usedCodes = usedProviderCodes();
        for (String rawCode : distinct(providerCodes)) {
            Optional<ProviderPresetItem> preset = presetService.findProvider(rawCode);
            if (preset.isEmpty()) {
                skipped.add(rawCode);
                continue;
            }
            try {
                ImportedProvider provider = createProvider(preset.get(), usedCodes, locale);
                usedCodes.add(provider.providerCode());
                int models = createModels(rawCode, provider.providerCode(), locale);
                imported.add(new ImportedProvider(
                        provider.presetCode(), provider.providerCode(), provider.displayName(), models));
            } catch (RuntimeException exception) {
                failures.add(new ImportFailure(rawCode, messageOf(exception)));
            }
        }
        return new ImportResult(imported, skipped, failures);
    }

    /**
     * 在已有供应商下批量创建模板模型。
     *
     * @param providerCode     已存在的本地供应商编码
     * @param providerModelIds 选中的上游模型 ID（空 = 全部）
     * @param locale           落库文案语言
     * @return 创建结果
     */
    public ProviderModelPresetService.ModelPresetImportResult importModels(
            String providerCode, List<String> providerModelIds, Locale locale) {
        ProviderVo provider = providerService.listAll().stream()
                .filter(item -> item.providerCode().equals(providerCode))
                .findFirst()
                .orElseThrow(() -> com.innospots.nexus.base.exception.NexusException.build(
                        com.innospots.nexmux.core.shared.status.NexmuxStatusCode.RESOURCE_NOT_FOUND,
                        "provider not found: " + providerCode));
        String presetCode = resolvePresetCode(providerCode);
        Map<String, ModelPresetItem> byId = new HashMap<>();
        for (ModelPresetItem item : presetService.listModels(presetCode)) {
            byId.put(item.getProviderModelId(), item);
        }
        Set<String> selected = providerModelIds == null || providerModelIds.isEmpty()
                ? byId.keySet()
                : new LinkedHashSet<>(providerModelIds);
        Set<String> existingCodes = modelDao.selectList(new LambdaQueryWrapper<ModelEntity>()
                        .eq(ModelEntity::getPrimaryProviderId, provider.providerId()))
                .stream()
                .map(ModelEntity::getModelCode)
                .collect(Collectors.toSet());
        List<ProviderModelPresetService.ModelPresetFailure> failures = new ArrayList<>();
        int created = 0;
        for (String id : selected) {
            if (id == null || id.isBlank()) {
                continue;
            }
            ModelPresetItem preset = byId.get(id);
            if (preset == null) {
                failures.add(new ProviderModelPresetService.ModelPresetFailure(id, "preset not found"));
                continue;
            }
            String modelCode = resolveModelCode(preset, providerCode);
            if (existingCodes.contains(modelCode)) {
                continue;
            }
            try {
                modelService.create(toCreateRequest(preset, providerCode, modelCode, locale));
                created++;
            } catch (RuntimeException exception) {
                failures.add(new ProviderModelPresetService.ModelPresetFailure(id, messageOf(exception)));
            }
        }
        return new ProviderModelPresetService.ModelPresetImportResult(created, failures);
    }

    /**
     * 模板编码 → 本地已导入的供应商编码（后缀形式视为同源）。
     *
     * @param localProviderCode 本地供应商编码
     * @return 推测的模板编码
     */
    public String resolvePresetCode(String localProviderCode) {
        String code = localProviderCode == null ? "" : localProviderCode.trim();
        if (presetService.findProvider(code).isPresent()) {
            return code;
        }
        int index = code.lastIndexOf('-');
        while (index > 0) {
            String candidate = code.substring(0, index);
            if (presetService.findProvider(candidate).isPresent()) {
                return candidate;
            }
            index = code.lastIndexOf('-', index - 1);
        }
        return code;
    }

    private ImportedProvider createProvider(
            ProviderPresetItem preset, Set<String> usedCodes, Locale locale) {
        String localCode = ProviderModelPresetService.nextAvailableCode(preset.getProviderCode(), usedCodes);
        String displayName = PresetI18n.localizedOr(
                preset.getDisplayName(), locale, preset.getProviderCode());
        if (!localCode.equals(preset.getProviderCode())) {
            displayName = displayName + " (" + localCode + ")";
        }
        ProviderVo created = providerService.create(new ProviderCreateRequest(
                localCode,
                displayName,
                preset.getProviderType(),
                preset.getDefaultProtocol(),
                preset.getVendorCode(),
                preset.getSourceVendorCode(),
                null,
                preset.getBaseUrl(),
                null,
                preset.getTimeoutMs() == null ? 60000 : preset.getTimeoutMs(),
                null,
                PresetI18n.localized(preset.getDescription(), locale),
                null));
        int modelCount = preset.getModelCount() == null ? 0 : preset.getModelCount();
        return new ImportedProvider(preset.getProviderCode(), created.providerCode(), displayName, modelCount);
    }

    private int createModels(String presetCode, String localProviderCode, Locale locale) {
        int created = 0;
        for (ModelPresetItem item : presetService.listModels(presetCode)) {
            String modelCode = resolveModelCode(item, localProviderCode);
            try {
                modelService.create(toCreateRequest(item, localProviderCode, modelCode, locale));
                created++;
            } catch (RuntimeException ignored) {
                // 单条模型失败不影响供应商导入（模型可在详情页再次拉取导入）
            }
        }
        return created;
    }

    private static String resolveModelCode(ModelPresetItem preset, String localProviderCode) {
        String code = preset.getModelCode();
        if (code == null || code.isBlank()) {
            return localProviderCode + "/" + preset.getProviderModelId();
        }
        int slash = code.indexOf('/');
        return slash < 0 ? code : localProviderCode + code.substring(slash);
    }

    private ModelCreateRequest toCreateRequest(
            ModelPresetItem preset, String providerCode, String modelCode, Locale locale) {
        return new ModelCreateRequest(
                modelCode,
                PresetI18n.localizedOr(preset.getDisplayName(), locale, preset.getProviderModelId()),
                preset.getModelType(),
                providerCode,
                preset.getProviderModelId(),
                preset.getPrimaryUpstreamProtocol(),
                preset.getContextLength(),
                preset.getMaxOutputTokens(),
                preset.getInputModalities(),
                preset.getOutputModalities(),
                preset.getTags(),
                ProviderModelPresetService.descriptionText(preset, locale),
                ProviderModelPresetService.metadataText(preset),
                null,
                ProviderModelPresetService.capabilityTagsText(preset),
                100,
                null,
                null,
                true,
                "[]",
                ProviderModelPresetService.pricingText(preset));
    }

    private Set<String> usedProviderCodes() {
        return providerDao.selectList(null).stream()
                .map(ProviderEntity::getProviderCode)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static List<String> distinct(List<String> values) {
        Set<String> distinct = new LinkedHashSet<>();
        for (String value : values == null ? List.<String>of() : values) {
            if (value != null && !value.isBlank()) {
                distinct.add(value.trim());
            }
        }
        return List.copyOf(distinct);
    }

    private static String messageOf(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    /**
     * 导入结果。
     *
     * @param imported 新建成功的供应商
     * @param skipped  未找到模板的编码
     * @param failures 创建失败的条目
     */
    public record ImportResult(
            List<ImportedProvider> imported,
            List<String> skipped,
            List<ImportFailure> failures) {
    }

    /**
     * 新建成功的供应商。
     *
     * @param presetCode        模板编码
     * @param providerCode      本地供应商编码
     * @param displayName       本地供应商名称
     * @param modelCount        已创建的模型数
     */
    public record ImportedProvider(
            String presetCode,
            String providerCode,
            String displayName,
            int modelCount) {
    }

    /**
     * 导入失败明细。
     *
     * @param presetCode 模板编码
     * @param message    失败原因
     */
    public record ImportFailure(String presetCode, String message) {
    }
}
