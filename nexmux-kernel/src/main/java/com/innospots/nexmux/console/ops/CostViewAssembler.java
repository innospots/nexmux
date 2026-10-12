package com.innospots.nexmux.console.ops;

import com.fasterxml.jackson.databind.JsonNode;
import com.innospots.nexmux.console.shared.DisplayFormats;
import com.innospots.nexmux.console.shared.DisplayFormatsProvider;
import com.innospots.nexmux.console.shared.LogTexts;
import com.innospots.nexmux.core.access.domain.vo.ClientVo;
import com.innospots.nexmux.core.access.service.ClientService;
import com.innospots.nexmux.core.model.domain.vo.ModelVo;
import com.innospots.nexmux.core.model.service.ModelService;
import com.innospots.nexmux.core.ops.domain.request.CostPageRequest;
import com.innospots.nexmux.core.ops.domain.vo.CostRecordVo;
import com.innospots.nexmux.core.ops.service.CostQueryService;
import com.innospots.nexmux.core.provider.domain.vo.ProviderVo;
import com.innospots.nexmux.core.provider.service.ProviderService;
import com.innospots.nexus.base.domain.response.PageResult;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;

/**
 * Cost UI 投影（/api/d/costs 契约的装配点）：CostRecord VO → 展示记录，
 * client/model/provider 全量内存 join，定价明细从 pricing_snapshot JSON 展开，
 * 成本计算过程文案预计算。筛选统一拉全量（pageSize 5000 自保护）。
 */
@RequiredArgsConstructor
public class CostViewAssembler {

    /** 筛选拉全量的自保护上限（与原 BFF 一致）。 */
    private static final long FULL_SCAN_PAGE_SIZE = 5000L;

    private final CostQueryService costQueryService;
    private final ClientService clientService;
    private final ModelService modelService;
    private final ProviderService providerService;
    private final DisplayFormatsProvider displayFormats;

    /**
     * 筛选列表（pageNo 恒 1、pageSize 5000；total 为全量条数）。
     */
    public CostListResult assemble(CostPageRequest request) {
        PageResult<CostRecordVo> page = costQueryService.page(
                new CostPageRequest(1L, FULL_SCAN_PAGE_SIZE, request.application(), request.appKey(),
                        request.alias(), request.model(), request.provider(), request.start(), request.end()));
        Relations relations = relations();
        return new CostListResult(
                page.records().stream().map(vo -> toRecord(vo, relations)).toList(),
                page.total());
    }

    /**
     * 按请求 ID 查询详情。
     */
    public CostRecord detail(String requestId) {
        return toRecord(costQueryService.get(requestId), relations());
    }

    private Relations relations() {
        Map<String, String> clientNameById = new LinkedHashMap<>();
        for (ClientVo client : clientService.listAll()) {
            clientNameById.put(client.clientId(), client.displayName());
        }
        Map<String, String> modelNameByCode = new LinkedHashMap<>();
        for (ModelVo model : modelService.listAll()) {
            modelNameByCode.put(model.modelCode(), model.displayName());
        }
        Map<String, String> providerNameByCode = new LinkedHashMap<>();
        for (ProviderVo provider : providerService.listAll()) {
            providerNameByCode.put(provider.providerCode(), provider.displayName());
        }
        return new Relations(clientNameById, modelNameByCode, providerNameByCode);
    }

    private record Relations(
            Map<String, String> clientNameById,
            Map<String, String> modelNameByCode,
            Map<String, String> providerNameByCode) {
    }

    private CostRecord toRecord(CostRecordVo vo, Relations relations) {
        DisplayFormats formats = displayFormats.current();
        JsonNode snap = LogTexts.readJson(vo.pricingSnapshot());
        long input = vo.inputTokens() == null ? 0 : vo.inputTokens();
        long output = vo.outputTokens() == null ? 0 : vo.outputTokens();
        long cacheRead = vo.cacheReadTokens() == null ? 0 : vo.cacheReadTokens();
        BigDecimal multiplier = decimalOf(snap, "multiplier", BigDecimal.ONE);
        BigDecimal tierInput = decimalOf(snap, "tierInputPrice", BigDecimal.ZERO);
        BigDecimal tierOutput = decimalOf(snap, "tierOutputPrice", BigDecimal.ZERO);
        BigDecimal tierCache = snap != null && snap.hasNonNull("tierCacheReadPrice")
                ? snap.get("tierCacheReadPrice").decimalValue() : null;

        BigDecimal inputCost = costOf(input, tierInput, multiplier);
        BigDecimal outputCost = costOf(output, tierOutput, multiplier);
        BigDecimal cacheCost = cacheRead > 0 && tierCache != null
                ? costOf(cacheRead, tierCache, multiplier) : null;
        BigDecimal totalCost = vo.costUsd() != null
                ? vo.costUsd()
                : inputCost.add(outputCost).add(cacheCost == null ? BigDecimal.ZERO : cacheCost);

        String clientName = relations.clientNameById.getOrDefault(
                vo.clientId() == null ? "" : vo.clientId(), "—");
        String modelName = relations.modelNameByCode.getOrDefault(
                vo.modelCode() == null ? "" : vo.modelCode(),
                vo.modelCode() == null ? "—" : vo.modelCode());
        String providerName = relations.providerNameByCode.getOrDefault(
                vo.providerCode() == null ? "" : vo.providerCode(),
                vo.providerCode() == null ? "—" : vo.providerCode());
        String shortTime = formats.minuteTime(vo.requestedAt());
        long minutesAgo = vo.requestedAt() == null
                ? 0 : Math.max(0, Duration.between(vo.requestedAt(), LocalDateTime.now()).toMinutes());
        Long tierUpto = snap != null && snap.hasNonNull("tierUpto") ? snap.get("tierUpto").asLong() : null;
        String multiplierText = multiplier.stripTrailingZeros().toPlainString();

        return new CostRecord(
                vo.requestId(),
                minutesAgo,
                formats.fullTime(vo.requestedAt()),
                shortTime,
                vo.application() == null ? "—" : vo.application(),
                clientName,
                vo.appKeyId() == null ? "—" : vo.appKeyId(),
                vo.alias() == null ? "—" : vo.alias(),
                vo.modelCode() == null ? "—" : vo.modelCode(),
                modelName,
                vo.providerCode() == null ? "—" : vo.providerCode(),
                providerName,
                textOf(snap, "currency", "USD"),
                input,
                output,
                cacheRead,
                textOf(snap, "priceVersion", "—"),
                textOf(snap, "priceEffective", "—"),
                tierUpto,
                tierInput,
                tierOutput,
                tierCache,
                multiplier,
                snap != null && snap.hasNonNull("multiplierRule")
                        ? snap.get("multiplierRule").asText()
                        : (multiplier.compareTo(BigDecimal.ONE) == 0
                                ? "未命中时段倍率（按 1.0 计）" : "时段倍率 ×" + multiplierText),
                tierUpto == null ? "∞" : "≤" + LogTexts.compactTokens(tierUpto),
                multiplierText + "x",
                moneyText(formats, inputCost),
                moneyText(formats, outputCost),
                cacheCost != null ? moneyText(formats, cacheCost) : "—",
                moneyText(formats, totalCost),
                shortTime + " · " + clientName + "（" + (vo.appKeyId() == null ? "—" : vo.appKeyId()) + "）· "
                        + modelName + " · " + providerName,
                tierUpto == null ? "UPTO ∞（无上限档）" : "UPTO ≤ " + LogTexts.thousands(tierUpto),
                "输入 " + formats.currencySymbol() + numberText(tierInput) + "/M · 输出 "
                        + formats.currencySymbol() + numberText(tierOutput)
                        + "/M · 缓存读 " + (tierCache != null ? formats.currencySymbol() + numberText(tierCache) + "/M" : "—"),
                LogTexts.thousands(input),
                LogTexts.thousands(output),
                cacheRead > 0 ? LogTexts.thousands(cacheRead) : "—",
                LogTexts.thousands(input) + " × " + formats.currencySymbol() + numberText(tierInput) + " × " + multiplierText
                        + " ÷ 1M = " + formats.currencySymbol() + moneyText(formats, inputCost),
                LogTexts.thousands(output) + " × " + formats.currencySymbol() + numberText(tierOutput) + " × " + multiplierText
                        + " ÷ 1M = " + formats.currencySymbol() + moneyText(formats, outputCost),
                cacheCost != null && tierCache != null
                        ? LogTexts.thousands(cacheRead) + " × " + formats.currencySymbol() + numberText(tierCache) + " × "
                                + multiplierText + " ÷ 1M = " + formats.currencySymbol() + moneyText(formats, cacheCost)
                        : "无缓存用量");
    }

    /** 成本 = tokens × 单价 × 倍率 ÷ 1M。 */
    private static BigDecimal costOf(long tokens, BigDecimal price, BigDecimal multiplier) {
        return BigDecimal.valueOf(tokens).multiply(price).multiply(multiplier)
                .divide(BigDecimal.valueOf(1_000_000), 10, RoundingMode.HALF_UP);
    }

    /** 金额 → 配置小数字符串（无符号；公式内自带币种符号）。 */
    private static String moneyText(DisplayFormats formats, BigDecimal value) {
        return value.setScale(formats.decimals(), RoundingMode.HALF_UP).toPlainString();
    }

    private static String numberText(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private static BigDecimal decimalOf(JsonNode node, String field, BigDecimal fallback) {
        if (node == null) {
            return fallback;
        }
        JsonNode value = node.get(field);
        return value == null || !value.isNumber() ? fallback : value.decimalValue();
    }

    private static String textOf(JsonNode node, String field, String fallback) {
        if (node == null) {
            return fallback;
        }
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? fallback : value.asText();
    }
}
