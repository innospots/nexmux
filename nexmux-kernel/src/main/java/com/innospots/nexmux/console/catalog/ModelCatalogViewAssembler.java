package com.innospots.nexmux.console.catalog;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.innospots.nexmux.console.catalog.domain.request.ModelCatalogImportRequest;
import com.innospots.nexmux.console.catalog.domain.vo.ModelCatalogRowVo;
import com.innospots.nexmux.console.catalog.domain.vo.ModelCatalogVo;
import com.innospots.nexmux.console.catalog.domain.vo.ModelImportResultVo;
import com.innospots.nexmux.console.shared.FilterChip;
import com.innospots.nexmux.core.catalog.preset.PresetI18n;
import com.innospots.nexmux.core.catalog.preset.config.ModelPresetItem;
import com.innospots.nexmux.core.catalog.preset.service.ProviderModelPresetImportService;
import com.innospots.nexmux.core.catalog.preset.service.ProviderModelPresetService;
import com.innospots.nexmux.core.model.dao.ModelDao;
import com.innospots.nexmux.core.model.domain.entity.ModelEntity;
import com.innospots.nexmux.core.provider.dao.ProviderDao;
import com.innospots.nexmux.core.provider.domain.entity.ProviderEntity;import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;

/**
 * 模型目录投影：聚合**本地已创建供应商**在模板目录中的可用模型，
 * 提供「安装模型类型」与「供应商」两个维度的筛选，并标记是否已建模。
 *
 * <p>目录只覆盖本地已存在（导入或新建）的供应商：未创建的供应商没有落库记录，
 * 其模型不属于「可安装到当前系统」的范围。已建模的行在 UI 中不可再次勾选。</p>
 */
@RequiredArgsConstructor
public class ModelCatalogViewAssembler {

    /** 安装模型类型展示名（与前端选项一致）。 */
    private static final Map<String, String> TYPE_LABELS = Map.of(
            "LLM", "大语言模型",
            "Image", "文生图",
            "Audio", "音频",
            "Embedding", "Embedding");

    private final ProviderModelPresetService presetService;
    private final ProviderModelPresetImportService importService;
    private final ProviderDao providerDao;
    private final ModelDao modelDao;

    /**
     * 模型目录（按类型 / 供应商 / 关键字过滤）。
     *
     * @param keyword         关键字（上游模型 ID、名称或供应商编码模糊，忽略大小写）
     * @param types           安装模型类型集合（空 = 全部）
     * @param providerCodes   供应商编码集合（空 = 全部本地供应商）
     * @param onlyImportable  true 时只返回尚未建模的行
     * @param locale          文案语言环境
     * @return 目录视图
     */
    public ModelCatalogVo catalog(
            String keyword, List<String> types, List<String> providerCodes,
            boolean onlyImportable, Locale locale) {
        List<ModelCatalogRowVo> all = collectRows(locale);
        Set<String> typeFilter = normalize(types);
        Set<String> providerFilter = normalize(providerCodes);
        String lowerKeyword = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);

        List<ModelCatalogRowVo> filtered = all.stream()
                .filter(row -> typeFilter.isEmpty() || typeFilter.contains(row.modelType()))
                .filter(row -> providerFilter.isEmpty() || providerFilter.contains(row.providerCode()))
                .filter(row -> !onlyImportable || !row.existsLocally())
                .filter(row -> lowerKeyword.isEmpty() || matchesKeyword(row, lowerKeyword))
                .toList();

        long importedCount = all.stream().filter(ModelCatalogRowVo::existsLocally).count();
        return new ModelCatalogVo(
                filtered.size(),
                all.size(),
                all.size() - (int) importedCount,
                (int) importedCount,
                filtered,
                typeFiltersOf(all),
                providerFiltersOf(all));
    }

    /**
     * 批量导入选中模型（已存在的上游模型自动跳过）。
     *
     * @param items  选中项（供应商 + 上游模型 ID）
     * @param locale 落库文案语言
     * @return 导入结果
     */
    public ModelImportResultVo importModels(List<ModelCatalogImportRequest.Item> items, Locale locale) {
        Map<String, List<String>> byProvider = new LinkedHashMap<>();
        for (ModelCatalogImportRequest.Item item : items == null ? List.<ModelCatalogImportRequest.Item>of() : items) {
            if (item == null || isBlank(item.providerCode()) || isBlank(item.modelId())) {
                continue;
            }
            byProvider.computeIfAbsent(item.providerCode().trim(), key -> new ArrayList<>())
                    .add(item.modelId().trim());
        }
        int created = 0;
        List<String> skipped = new ArrayList<>();
        List<ModelImportResultVo.Failure> failures = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : byProvider.entrySet()) {
            // 已存在（已建模）的上游模型先计入跳过，再交给导入服务处理其余项
            Set<String> modeled = modeledProviderModelIdsByCode(entry.getKey());
            List<String> pending = new ArrayList<>();
            for (String modelId : entry.getValue()) {
                if (modeled.contains(modelId)) {
                    skipped.add(modelId);
                } else {
                    pending.add(modelId);
                }
            }
            if (pending.isEmpty()) {
                continue;
            }
            ProviderModelPresetService.ModelPresetImportResult result =
                    importService.importModels(entry.getKey(), pending, locale);
            created += result.created();
            for (ProviderModelPresetService.ModelPresetFailure failure : result.failures()) {
                failures.add(new ModelImportResultVo.Failure(failure.id(), failure.error()));
            }
        }
        return new ModelImportResultVo(created, List.copyOf(new LinkedHashSet<>(skipped)), failures);
    }

    /** 供应商编码 → 已建模的上游模型 ID。 */
    private Set<String> modeledProviderModelIdsByCode(String providerCode) {
        ProviderEntity provider = providerDao.selectOne(new LambdaQueryWrapper<ProviderEntity>()
                .eq(ProviderEntity::getProviderCode, providerCode));
        if (provider == null) {
            return Set.of();
        }
        return modeledProviderModelIds(provider.getProviderId());
    }

    /** 本地供应商 → 模板目录中的可用模型。 */
    private List<ModelCatalogRowVo> collectRows(Locale locale) {
        List<ProviderEntity> providers = providerDao.selectList(
                new LambdaQueryWrapper<ProviderEntity>().orderByAsc(ProviderEntity::getCreatedAt));
        List<ModelCatalogRowVo> rows = new ArrayList<>();
        for (ProviderEntity provider : providers) {
            String presetCode = importService.resolvePresetCode(provider.getProviderCode());
            if (presetService.findProvider(presetCode).isEmpty()) {
                continue;
            }
            Set<String> modeled = modeledProviderModelIds(provider.getProviderId());
            for (ModelPresetItem item : presetService.listModels(presetCode)) {
                String providerModelId = item.getProviderModelId();
                if (isBlank(providerModelId)) {
                    continue;
                }
                rows.add(new ModelCatalogRowVo(
                        provider.getProviderCode(),
                        provider.getDisplayName(),
                        presetCode,
                        providerModelId,
                        PresetI18n.localizedOr(item.getDisplayName(), locale, providerModelId),
                        truncate(PresetI18n.localized(item.getDescription(), locale), 120),
                        isBlank(item.getModelType()) ? "LLM" : item.getModelType(),
                        item.getContextLength(),
                        item.getMaxOutputTokens(),
                        pricingLabelOf(item.getPricingSpec()),
                        modeled.contains(providerModelId)));
            }
        }
        return rows;
    }

    private Set<String> modeledProviderModelIds(String providerId) {
        Set<String> ids = new LinkedHashSet<>();
        for (ModelEntity model : modelDao.selectList(new LambdaQueryWrapper<ModelEntity>()
                .eq(ModelEntity::getPrimaryProviderId, providerId))) {
            if (!isBlank(model.getPrimaryProviderModelId())) {
                ids.add(model.getPrimaryProviderModelId());
            }
        }
        return ids;
    }

    private static List<FilterChip> typeFiltersOf(List<ModelCatalogRowVo> rows) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (ModelCatalogRowVo row : rows) {
            counts.merge(row.modelType(), 1L, Long::sum);
        }
        List<FilterChip> chips = new ArrayList<>();
        chips.add(new FilterChip("all", "全部 " + rows.size(), null));
        counts.forEach((type, count) -> chips.add(new FilterChip(
                type,
                TYPE_LABELS.getOrDefault(type, type) + " " + count,
                null)));
        return chips;
    }

    private static List<FilterChip> providerFiltersOf(List<ModelCatalogRowVo> rows) {
        Map<String, Long> counts = new LinkedHashMap<>();
        Map<String, String> names = new LinkedHashMap<>();
        for (ModelCatalogRowVo row : rows) {
            counts.merge(row.providerCode(), 1L, Long::sum);
            names.putIfAbsent(row.providerCode(), row.providerName());
        }
        List<FilterChip> chips = new ArrayList<>();
        chips.add(new FilterChip("all", "全部供应商", null));
        counts.forEach((code, count) -> chips.add(new FilterChip(
                code,
                names.getOrDefault(code, code) + " " + count,
                null)));
        return chips;
    }

    private static boolean matchesKeyword(ModelCatalogRowVo row, String lowerKeyword) {
        return row.id().toLowerCase(Locale.ROOT).contains(lowerKeyword)
                || row.name().toLowerCase(Locale.ROOT).contains(lowerKeyword)
                || row.providerCode().toLowerCase(Locale.ROOT).contains(lowerKeyword);
    }

    /** 定价摘要：input / output（每百万 tokens）。 */
    private static String pricingLabelOf(JsonNode pricing) {
        if (pricing == null || pricing.isNull() || !pricing.isObject()) {
            return null;
        }
        String input = decimalText(pricing.get("input"));
        String output = decimalText(pricing.get("output"));
        if (input == null && output == null) {
            return null;
        }
        String currency = pricing.path("currency").asText("USD");
        String symbol = "USD".equalsIgnoreCase(currency) ? "$" : currency + " ";
        String unit = pricing.path("unit").asText("");
        String suffix = "per_million_tokens".equals(unit) ? "/M" : "";
        return symbol + nullToDash(input) + " / " + symbol + nullToDash(output) + suffix;
    }

    private static String decimalText(JsonNode node) {
        if (node == null || node.isNull() || !node.isNumber()) {
            return null;
        }
        return BigDecimal.valueOf(node.asDouble()).stripTrailingZeros().toPlainString();
    }

    private static String nullToDash(String value) {
        return value == null ? "—" : value;
    }

    /**
     * 截断长文本（模型说明篇幅很大，列表只作首行提示，避免撑宽表格）。
     *
     * @param value     原文
     * @param maxLength 最大字符数
     * @return 截断后的文本
     */
    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength).trim() + "…";
    }

    private static Set<String> normalize(List<String> values) {
        Set<String> result = new LinkedHashSet<>();
        for (String value : values == null ? List.<String>of() : values) {
            if (!isBlank(value)) {
                result.add(value.trim());
            }
        }
        result.remove("all");
        return result;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
