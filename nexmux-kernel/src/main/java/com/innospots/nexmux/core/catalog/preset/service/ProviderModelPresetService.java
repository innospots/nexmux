package com.innospots.nexmux.core.catalog.preset.service;

import com.innospots.nexmux.core.catalog.preset.config.ModelPresetItem;
import com.innospots.nexmux.core.catalog.preset.config.ProviderModelPresetsDocument;
import com.innospots.nexmux.core.catalog.preset.config.ProviderPresetItem;
import com.innospots.nexmux.core.catalog.preset.config.ProviderPresetsDocument;
import com.innospots.nexmux.core.catalog.preset.loader.ProviderModelPresetLoader;
import com.innospots.nexmux.core.shared.json.NexmuxJson;
import com.innospots.nexmux.core.shared.status.NexmuxStatusCode;
import com.innospots.nexus.base.exception.NexusException;
import com.innospots.nexus.base.status.NexusStatusCode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;

/**
 * 读取 classpath 预设目录（供应商 / 模型模板）。
 *
 * <p>本服务只负责目录读取与模板结构计算，不触达数据库；导入落库由
 * {@code ProviderModelPresetImportService} 承担。</p>
 */
@RequiredArgsConstructor
public class ProviderModelPresetService {

    private final ProviderModelPresetLoader loader;

    private volatile ProviderPresetsDocument providerCache;

    /**
     * 目录元信息。
     *
     * @return 供应商预设文档
     */
    public ProviderPresetsDocument catalogMeta() {
        return providerPresets();
    }

    /**
     * 全部供应商模板。
     *
     * @return 模板列表（保持预设顺序）
     */
    public List<ProviderPresetItem> listProviders() {
        return providerPresets().getProviders();
    }

    /**
     * 按编码取供应商模板。
     *
     * @param providerCode 模板编码
     * @return 模板条目
     * @throws NexusException 模板不存在时
     */
    public ProviderPresetItem getProvider(String providerCode) {
        String code = providerCode == null ? "" : providerCode.trim();
        return providerPresets().getProviders().stream()
                .filter(item -> code.equals(item.getProviderCode()))
                .findFirst()
                .orElseThrow(() -> NexusException.build(
                        NexmuxStatusCode.RESOURCE_NOT_FOUND,
                        "provider preset not found: " + providerCode));
    }

    /**
     * 按编码查找供应商模板（不抛异常）。
     *
     * @param providerCode 模板编码
     * @return 模板条目；不存在时为空
     */
    public Optional<ProviderPresetItem> findProvider(String providerCode) {
        String code = providerCode == null ? "" : providerCode.trim();
        if (code.isEmpty()) {
            return Optional.empty();
        }
        return providerPresets().getProviders().stream()
                .filter(item -> code.equals(item.getProviderCode()))
                .findFirst();
    }

    /**
     * 供应商模板下的模型模板。
     *
     * @param providerCode 模板编码
     * @return 模型模板
     */
    public List<ModelPresetItem> listModels(String providerCode) {
        return loadModels(providerCode).getModels();
    }

    /**
     * 模板内模型覆盖的上游协议（去重，缺省补上供应商默认协议）。
     *
     * @param providerCode 模板编码
     * @return 协议列表
     */
    public List<String> protocolsOf(String providerCode) {
        ProviderPresetItem provider = getProvider(providerCode);
        Set<String> protocols = new LinkedHashSet<>();
        for (ModelPresetItem model : listModels(providerCode)) {
            String protocol = model.getPrimaryUpstreamProtocol();
            if (protocol != null && !protocol.isBlank()) {
                protocols.add(protocol.trim());
            }
        }
        if (protocols.isEmpty() && provider.getDefaultProtocol() != null) {
            protocols.add(provider.getDefaultProtocol());
        }
        return List.copyOf(protocols);
    }

    /**
     * 模板内模型编码集合（按模板编码归并模型编码前缀）。
     *
     * @param providerCode 模板编码
     * @return 模型编码集合
     */
    public Set<String> modelCodesOf(String providerCode) {
        return listModels(providerCode).stream()
                .map(ModelPresetItem::getModelCode)
                .filter(code -> code != null && !code.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * 模板编码 → 该模板导入后的候选本地编码（{@code code} / {@code code-2} / …）。
     *
     * @param providerCode 模板编码
     * @param usedCodes    已占用的本地供应商编码
     * @return 首个可用编码
     */
    public static String nextAvailableCode(String providerCode, Set<String> usedCodes) {
        String base = providerCode == null ? "" : providerCode.trim();
        if (!usedCodes.contains(base)) {
            return base;
        }
        int suffix = 2;
        while (usedCodes.contains(base + "-" + suffix)) {
            suffix++;
        }
        return base + "-" + suffix;
    }

    /**
     * 模板下模型草稿描述：优先请求语言环境文案。
     *
     * @param item   模型模板
     * @param locale 语言环境
     * @return 描述文本，可空
     */
    public static String descriptionText(ModelPresetItem item, Locale locale) {
        if (item.getDescription() == null || item.getDescription().isEmpty()) {
            return null;
        }
        return item.getDescription().value(locale);
    }

    /**
     * 能力标签 JSON 文本。
     *
     * @param item 模型模板
     * @return JSON 文本，可空
     */
    public static String capabilityTagsText(ModelPresetItem item) {
        if (item.getCapabilityTags() == null || item.getCapabilityTags().isNull()) {
            return null;
        }
        return NexmuxJson.write(item.getCapabilityTags());
    }

    /**
     * 定价 JSON 文本。
     *
     * @param item 模型模板
     * @return JSON 文本，可空
     */
    public static String pricingText(ModelPresetItem item) {
        if (item.getPricingSpec() == null || item.getPricingSpec().isNull()) {
            return null;
        }
        return NexmuxJson.write(item.getPricingSpec());
    }

    /**
     * 元数据 JSON 文本。
     *
     * @param item 模型模板
     * @return JSON 文本，可空
     */
    public static String metadataText(ModelPresetItem item) {
        if (item.getMetadataSpec() == null || item.getMetadataSpec().isNull()) {
            return null;
        }
        return NexmuxJson.write(item.getMetadataSpec());
    }

    /**
     * 按模板编码加载模型预设（编码不存在时转 404）。
     *
     * @param providerCode 模板编码
     * @return 模型预设文档
     */
    public ProviderModelPresetsDocument loadModels(String providerCode) {
        try {
            return loader.loadModelPresets(providerCode);
        } catch (NexusException exception) {
            if (NexusStatusCode.CONFIG_ERROR.fullCode().equals(exception.code())) {
                throw NexusException.build(
                        NexmuxStatusCode.RESOURCE_NOT_FOUND,
                        "model presets not found for provider: " + providerCode);
            }
            throw exception;
        }
    }

    private ProviderPresetsDocument providerPresets() {
        ProviderPresetsDocument cached = providerCache;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (providerCache == null) {
                providerCache = loader.loadProviderPresets();
            }
            return providerCache;
        }
    }

    /**
     * 批量模型导入结果。
     *
     * @param created  成功数
     * @param failures 失败明细
     */
    public record ModelPresetImportResult(int created, List<ModelPresetFailure> failures) {
    }

    /**
     * 单条模型导入失败。
     *
     * @param id    上游模型 ID
     * @param error 失败原因
     */
    public record ModelPresetFailure(String id, String error) {
    }

    /**
     * 汇总失败明细的便捷构造。
     *
     * @param failures 明细
     * @return 不可变列表
     */
    public static List<ModelPresetFailure> immutableFailures(List<ModelPresetFailure> failures) {
        return failures == null ? List.of() : new ArrayList<>(failures);
    }
}
