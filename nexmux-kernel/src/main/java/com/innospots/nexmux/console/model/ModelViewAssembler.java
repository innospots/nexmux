package com.innospots.nexmux.console.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.innospots.nexmux.console.model.ModelRecord.ModelParams;
import com.innospots.nexmux.console.model.ModelRecord.ModelTier;
import com.innospots.nexmux.console.model.ModelRecord.PriceVersion;
import com.innospots.nexmux.console.model.ModelRecord.RouteRef;
import com.innospots.nexmux.console.model.ModelRecord.SupplierModel;
import com.innospots.nexmux.console.model.ModelRecord.SupplierTimeRate;
import com.innospots.nexmux.console.model.ModelRecord.TimeMultiplier;
import com.innospots.nexmux.console.shared.EnabledTotal;
import com.innospots.nexmux.console.shared.FilterChip;
import com.innospots.nexmux.console.shared.VendorIcons;
import com.innospots.nexmux.core.model.domain.request.ModelPageRequest;
import com.innospots.nexmux.core.model.domain.vo.ModelDetailVo;
import com.innospots.nexmux.core.model.domain.vo.ModelVo;
import com.innospots.nexmux.core.model.service.ModelService;
import com.innospots.nexmux.core.provider.domain.vo.ProviderVo;
import com.innospots.nexmux.core.provider.service.ProviderService;
import com.innospots.nexmux.core.route.domain.value.RouteCandidate;
import com.innospots.nexmux.core.route.domain.vo.RouteVo;
import com.innospots.nexmux.core.route.service.RouteService;
import com.innospots.nexmux.core.shared.json.NexmuxJson;
import com.innospots.nexus.base.domain.response.PageResult;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;

/**
 * Model UI 投影（/api/d/models 契约的装配点）：核心域 VO → 展示记录，
 * 定价整形 / 供应商组装 / 路由引用统计 / counts / filters 全部在此完成。
 * 构成口径基于全量（P0 数据量小），不随当前筛选变化。
 */
@RequiredArgsConstructor
public class ModelViewAssembler {

    /** 类型展示文案（与列表页 filters.type 口径一致）。 */
    private static final Map<String, String> TYPE_LABELS = Map.of(
            "LLM", "大语言模型 (LLM)",
            "Embedding", "Embedding",
            "Image", "文生图 (Image)",
            "Audio", "音频 (Audio)");

    /** filters.type 的固定选项顺序（value → 短文案）。 */
    private static final List<String[]> TYPE_OPTIONS = List.of(
            new String[]{"LLM", "大语言模型"},
            new String[]{"Image", "文生图"},
            new String[]{"Audio", "音频"},
            new String[]{"Embedding", "Embedding"});

    /** 模态 → 预览字母。 */
    private static final Map<String, String> MODALITY_LETTERS = Map.of(
            "text", "T", "image", "I", "audio", "A", "video", "V", "file", "F");

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter SHORT_DATE_FORMAT = DateTimeFormatter.ofPattern("MM-dd");

    private final ModelService modelService;
    private final ProviderService providerService;
    private final RouteService routeService;

    /**
     * 分页列表：items 为当前页，counts/filters 为全量构成口径。
     */
    public ModelListResult assemble(ModelPageRequest request) {
        PageResult<ModelVo> page = modelService.page(request);
        Relations relations = relations();

        List<ModelRecord> items = page.records().stream()
                .map(vo -> toRecord(vo, null, relations))
                .toList();
        List<ModelVo> allVos = modelService.listAll();
        ModelListResult.Counts counts = countsOf(allVos, relations.providers);
        return new ModelListResult(
                items, page.total(), page.pageNo(), page.pageSize(), page.pages(),
                counts, filtersOf(counts, relations.providers));
    }

    /**
     * 按稳定业务键查询详情（UI 编码 ~ 还原为 /）。
     */
    public ModelRecord detail(String uiCode) {
        ModelDetailVo detail = modelService.getByCode(uiCode.replace("~", "/"));
        return toRecord(baseViewOf(detail), detail, relations());
    }

    /** 详情 VO → 概要视图字段（record 无继承，显式提取公共字段）。 */
    private static ModelVo baseViewOf(ModelDetailVo d) {
        return new ModelVo(
                d.modelId(), d.modelCode(), d.displayName(), d.status(), d.modelType(),
                d.contextLength(), d.maxOutputTokens(), d.primaryProviderId(),
                d.pricingSpec(), d.extraBindings());
    }

    private Relations relations() {
        List<ProviderVo> providers = providerService.listAll();
        Map<String, ProviderVo> providersById = new LinkedHashMap<>();
        for (ProviderVo provider : providers) {
            providersById.put(provider.providerId(), provider);
        }
        return new Relations(providers, providersById, routeService.listAll());
    }

    private record Relations(
            List<ProviderVo> providers,
            Map<String, ProviderVo> providersById,
            List<RouteVo> routes) {
    }

    // ── VO → ModelRecord ──────────────────────────────────────

    private static ModelRecord toRecord(ModelVo vo, ModelDetailVo d, Relations relations) {
        JsonNode pricing = readJsonTolerant(vo.pricingSpec());
        List<ModelTier> tiers = tiersOf(pricing);
        List<SupplierModel> suppliers = suppliersOf(vo, d, relations.providersById);
        List<SupplierModel> enabledSuppliers = suppliers.stream()
                .filter(s -> "enabled".equals(s.status())).toList();
        SupplierModel primary = enabledSuppliers.isEmpty()
                ? (suppliers.isEmpty() ? null : suppliers.get(0))
                : enabledSuppliers.get(0);

        List<RouteRef> routeRefs = routeRefsOf(vo.modelCode(), relations.routes);
        long active = routeRefs.stream().filter(r -> "启用".equals(r.status())).count();
        ModelRecord.RouteStat routeStat = new ModelRecord.RouteStat(routeRefs.size(), active);

        List<String> inputModalities = csvList(d == null ? null : d.inputModalities());
        List<String> outputModalities = csvList(d == null ? null : d.outputModalities());
        List<String> tags = jsonStringList(d == null ? null : d.tags());
        long contextWindow = vo.contextLength() == null ? 0 : vo.contextLength();
        long maxTokens = vo.maxOutputTokens() == null ? 0 : vo.maxOutputTokens();
        String updatedAt = d == null ? "" : timeText(d.updatedAt());

        return new ModelRecord(
                vo.modelCode().replace("/", "~"),
                vo.displayName(),
                primary == null ? "" : primary.providerCode(),
                primary == null ? "" : primary.providerName(),
                primary == null ? "zap" : primary.providerIcon(),
                vo.modelType() == null || vo.modelType().isBlank() ? "LLM" : vo.modelType(),
                TYPE_LABELS.getOrDefault(nullToEmpty(vo.modelType()), nullToEmpty(vo.modelType())),
                d == null || d.releaseDate() == null ? "" : d.releaseDate().toString(),
                contextWindow,
                maxTokens,
                inputModalities,
                outputModalities,
                tags,
                d == null ? "" : nullToEmpty(d.description()),
                d == null ? "" : nullToEmpty(d.metadataSpec()),
                "ENABLED".equals(vo.status()) ? "enabled" : "disabled",
                tiers,
                timeMultipliersOf(pricing),
                textOf(pricing, "currency", "USD"),
                textOf(pricing, "priceVersion", ""),
                textOf(pricing, "priceEffective", ""),
                priceVersionsOf(pricing),
                suppliers,
                routeStat,
                routeRefs,
                paramsOf(d),
                d == null ? "" : timeText(d.createdAt()),
                updatedAt,
                d == null || d.updatedBy() == null ? "—" : d.updatedBy(),
                formatTokens(contextWindow) + " · " + (maxTokens > 0 ? formatTokens(maxTokens) : "—"),
                pricingSummaryOf(tiers),
                tiers.size() > 1 ? tiers.size() + "档" : "",
                routeStat.total() == 0
                        ? "未关联路由"
                        : routeStat.total() + " 条路由 · " + routeStat.active() + " 条活跃",
                modalityPreview(inputModalities) + " → " + modalityPreview(outputModalities),
                String.join(" · ", inputModalities),
                String.join(" · ", outputModalities),
                String.join(" · ", tags),
                primary == null
                        ? "未绑定供应商模型"
                        : primary.providerName() + "（主） · 共 " + suppliers.size() + " 个供应商模型",
                primary == null ? "—" : primary.providerModelId(),
                String.valueOf(enabledSuppliers.stream().mapToLong(SupplierModel::weight).sum()),
                d == null || d.updatedAt() == null ? "" : SHORT_DATE_FORMAT.format(d.updatedAt()));
    }

    // ── 定价 ───────────────────────────────────────────────────

    /**
     * pricingSpec → 标准分档数组（兼容 catalog 平铺单价：unit/input/output 视为单档）。
     */
    private static List<ModelTier> tiersOf(JsonNode pricing) {
        if (pricing == null || !pricing.isObject()) {
            return List.of();
        }
        JsonNode tiers = pricing.get("tiers");
        if (tiers != null && tiers.isArray() && !tiers.isEmpty()) {
            List<ModelTier> out = new ArrayList<>();
            tiers.forEach(tier -> out.add(new ModelTier(
                    decimalOrNull(tier.get("upto")),
                    decimalOrZero(tier.get("input")),
                    decimalOrZero(tier.get("output")),
                    decimalOrNull(tier.get("cacheRead")),
                    decimalOrNull(tier.get("cacheWrite")))));
            return out;
        }
        if (pricing.hasNonNull("unit") || pricing.has("input")) {
            return List.of(new ModelTier(
                    null,
                    decimalOrZero(pricing.get("input")),
                    decimalOrZero(pricing.get("output")),
                    decimalOrNull(pricing.get("cacheRead")),
                    decimalOrNull(pricing.get("cacheWrite"))));
        }
        return List.of();
    }

    private static List<TimeMultiplier> timeMultipliersOf(JsonNode pricing) {
        JsonNode rules = pricing == null ? null : pricing.get("timeMultipliers");
        if (rules == null || !rules.isArray()) {
            return List.of();
        }
        List<TimeMultiplier> out = new ArrayList<>();
        rules.forEach(rule -> out.add(new TimeMultiplier(
                textOf(rule, "days", ""), textOf(rule, "start", ""), textOf(rule, "end", ""),
                decimalOrZero(rule.get("rate")))));
        return out;
    }

    private static List<PriceVersion> priceVersionsOf(JsonNode pricing) {
        JsonNode versions = pricing == null ? null : pricing.get("priceVersions");
        if (versions == null || !versions.isArray()) {
            return List.of();
        }
        List<PriceVersion> out = new ArrayList<>();
        versions.forEach(v -> out.add(new PriceVersion(
                textOf(v, "version", ""),
                v.has("current") && v.get("current").asBoolean(false),
                textOf(v, "effective", ""),
                textOf(v, "summary", ""))));
        return out;
    }

    private static String pricingSummaryOf(List<ModelTier> tiers) {
        if (tiers.isEmpty()) {
            return "未定价";
        }
        ModelTier first = tiers.get(0);
        return "$" + numberText(first.input())
                + " / $" + (first.output().signum() > 0 ? numberText(first.output()) : "—")
                + " /M";
    }

    /** JSON 数字 → 展示文本（对齐 JS Number 插值：去尾随零，不科学计数）。 */
    private static String numberText(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    // ── 供应商 ─────────────────────────────────────────────────

    /**
     * 供应商组装：主绑定 + extraBindings JSON 数组。列表行无详情字段时主绑定
     * 取缺省（weight 100、启用、上游 ID "—"）。
     */
    private static List<SupplierModel> suppliersOf(
            ModelVo vo, ModelDetailVo d, Map<String, ProviderVo> providersById) {
        List<SupplierModel> suppliers = new ArrayList<>();
        if (d != null) {
            if (d.primaryProviderId() != null && !d.primaryProviderId().isBlank()) {
                suppliers.add(supplierOf(
                        providersById.get(d.primaryProviderId()),
                        d.primaryProviderModelId(),
                        d.primaryBindingWeight() == null ? 0 : d.primaryBindingWeight(),
                        d.primaryCostMultiplier(),
                        d.primaryReferenceMultiplier(),
                        null,
                        d.primaryBindingEnabled() == null || d.primaryBindingEnabled()));
            }
        } else if (vo.primaryProviderId() != null && !vo.primaryProviderId().isBlank()) {
            suppliers.add(supplierOf(
                    providersById.get(vo.primaryProviderId()), null, 100, null, null, null, true));
        }
        JsonNode bindings = readJsonTolerant(vo.extraBindings());
        if (bindings != null && bindings.isArray()) {
            for (JsonNode binding : bindings) {
                JsonNode providerId = binding.get("providerId");
                suppliers.add(supplierOf(
                        providerId == null ? null : providersById.get(providerId.asText()),
                        textOrNull(binding.get("providerModelId")),
                        binding.has("weight") ? binding.get("weight").asInt(0) : 0,
                        decimalOrNull(binding.get("costMultiplier")),
                        decimalOrNull(binding.get("referenceMultiplier")),
                        timeRatesOf(binding.get("timeRateRules")),
                        !binding.has("enabled") || binding.get("enabled").asBoolean(true)));
            }
        }
        return suppliers;
    }

    private static SupplierModel supplierOf(
            ProviderVo provider, String providerModelId, int weight,
            BigDecimal costMultiplier, BigDecimal referenceMultiplier,
            List<SupplierTimeRate> timeRates, boolean enabled) {
        return new SupplierModel(
                provider == null ? "—" : provider.providerCode(),
                provider == null ? "未知供应商" : provider.displayName(),
                VendorIcons.iconOf(provider == null ? null : provider.vendorCode()),
                providerModelId == null ? "—" : providerModelId,
                weight,
                costMultiplier == null ? BigDecimal.ONE : costMultiplier,
                referenceMultiplier == null ? BigDecimal.ONE : referenceMultiplier,
                enabled ? "enabled" : "disabled",
                timeRates,
                provider != null && "configured".equals(provider.keyStatus()),
                null);
    }

    private static List<SupplierTimeRate> timeRatesOf(JsonNode rules) {
        if (rules == null || !rules.isArray()) {
            return null;
        }
        List<SupplierTimeRate> out = new ArrayList<>();
        rules.forEach(rule -> out.add(new SupplierTimeRate(
                textOf(rule, "days", ""), textOf(rule, "start", ""), textOf(rule, "end", ""),
                decimalOrZero(rule.get("cost")), decimalOrZero(rule.get("billing")))));
        return out;
    }

    // ── 路由引用 ───────────────────────────────────────────────

    private static List<RouteRef> routeRefsOf(String modelCode, List<RouteVo> routes) {
        List<RouteRef> refs = new ArrayList<>();
        for (RouteVo route : routes) {
            List<RouteCandidate> candidates = route.routeSpec().candidates();
            candidates.stream()
                    .filter(c -> c.modelCode().equals(modelCode))
                    .findFirst()
                    .ifPresent(c -> refs.add(new RouteRef(
                            route.displayName(),
                            route.alias(),
                            candidates.size() > 1 ? "多模型池" : "单模型",
                            c.weight(),
                            "PUBLISHED".equals(route.status()) ? "启用" : "停用",
                            "HEALTHY")));
        }
        return refs;
    }

    // ── 默认参数 ───────────────────────────────────────────────

    private static ModelParams paramsOf(ModelDetailVo d) {
        JsonNode defaults = d == null ? null : readJsonTolerant(d.defaultParams());
        List<String> capabilities = d == null ? List.of() : jsonStringList(d.capabilityTags());
        return new ModelParams(
                textOf(defaults, "temperature", "—"),
                textOf(defaults, "topP", "—"),
                textOf(defaults, "topK", "—"),
                textOf(defaults, "maxTokens", "—"),
                textOf(defaults, "frequencyPenalty", "—"),
                textOf(defaults, "presencePenalty", "—"),
                textOf(defaults, "reasoningEffort", "—"),
                textOf(defaults, "seed", "—"),
                textOf(defaults, "stopSequences", "—"),
                textOf(defaults, "responseFormat", "—"),
                capabilities);
    }

    // ── counts / filters ──────────────────────────────────────

    private static ModelListResult.Counts countsOf(List<ModelVo> allVos, List<ProviderVo> providers) {
        Map<String, Long> types = new LinkedHashMap<>();
        types.put("LLM", 0L);
        types.put("Embedding", 0L);
        types.put("Image", 0L);
        types.put("Audio", 0L);
        for (ModelVo vo : allVos) {
            String type = vo.modelType() == null || vo.modelType().isBlank() ? "LLM" : vo.modelType();
            types.computeIfPresent(type, (k, v) -> v + 1);
        }
        Map<String, Long> providerCounts = new LinkedHashMap<>();
        for (ProviderVo provider : providers) {
            // 按主提供方归属计数（与构成口径约定一致，扩展绑定不计）
            providerCounts.put(provider.providerCode(), allVos.stream()
                    .filter(vo -> provider.providerId().equals(vo.primaryProviderId()))
                    .count());
        }
        return new ModelListResult.Counts(
                new EnabledTotal(
                        allVos.stream().filter(vo -> "ENABLED".equals(vo.status())).count(),
                        allVos.size()),
                types, providerCounts);
    }

    private static ModelListResult.Filters filtersOf(
            ModelListResult.Counts counts, List<ProviderVo> providers) {
        List<FilterChip> typeChips = new ArrayList<>();
        typeChips.add(new FilterChip("all", "全部 " + counts.all().total(), null));
        for (String[] option : TYPE_OPTIONS) {
            typeChips.add(new FilterChip(
                    option[0], option[1] + " " + counts.types().getOrDefault(option[0], 0L), null));
        }
        List<FilterChip> providerChips = new ArrayList<>();
        providerChips.add(new FilterChip("all", "全部 " + counts.all().total(), null));
        for (ProviderVo provider : providers) {
            providerChips.add(new FilterChip(
                    provider.providerCode(),
                    provider.displayName() + " "
                            + counts.providers().getOrDefault(provider.providerCode(), 0L),
                    null));
        }
        return new ModelListResult.Filters(typeChips, providerChips);
    }

    // ── 工具 ───────────────────────────────────────────────────

    /** token 数 → 缩略文案（128000 → 128K，4096 → 4.1K，1500000 → 1.5M）。 */
    static String formatTokens(long n) {
        if (n >= 1_000_000) {
            return scaled(n, 1_000_000, "M");
        }
        if (n >= 1_000) {
            return scaled(n, 1_000, "K");
        }
        return String.valueOf(n);
    }

    private static String scaled(long n, long unit, String suffix) {
        if (n % unit == 0) {
            return n / unit + suffix;
        }
        return BigDecimal.valueOf(n)
                .divide(BigDecimal.valueOf(unit), 1, RoundingMode.HALF_UP)
                .toPlainString() + suffix;
    }

    private static String modalityPreview(List<String> modalities) {
        List<String> letters = modalities.stream().map(ModelViewAssembler::modalityLetter).toList();
        return String.join("+", letters);
    }

    private static String modalityLetter(String modality) {
        String letter = MODALITY_LETTERS.get(modality);
        return letter != null ? letter : modality.substring(0, 1).toUpperCase();
    }

    private static List<String> csvList(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(text.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    private static List<String> jsonStringList(String json) {
        JsonNode node = readJsonTolerant(json);
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        node.forEach(item -> {
            if (item.isTextual()) {
                out.add(item.asText());
            }
        });
        return out;
    }

    private static String timeText(LocalDateTime time) {
        return time == null ? "" : TIME_FORMAT.format(time);
    }

    private static String textOf(JsonNode node, String field, String fallback) {
        String value = textOrNull(node == null ? null : node.get(field));
        return value == null ? fallback : value;
    }

    private static String textOrNull(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
    }

    private static BigDecimal decimalOrNull(JsonNode node) {
        return node == null || !node.isNumber() ? null : node.decimalValue();
    }

    private static BigDecimal decimalOrZero(JsonNode node) {
        return node == null || !node.isNumber() ? BigDecimal.ZERO : node.decimalValue();
    }

    /** CLOB JSON 原文容错解析：空 / 非法 JSON 按 null 处理（与原 BFF parseJson 口径一致）。 */
    private static JsonNode readJsonTolerant(String json) {
        try {
            return NexmuxJson.read(json, JsonNode.class);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
