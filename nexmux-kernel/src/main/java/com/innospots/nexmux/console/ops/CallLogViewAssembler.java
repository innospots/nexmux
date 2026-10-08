package com.innospots.nexmux.console.ops;

import com.fasterxml.jackson.databind.JsonNode;
import com.innospots.nexmux.console.shared.LogTexts;
import com.innospots.nexmux.console.shared.StrategyLabels;
import com.innospots.nexmux.core.access.domain.vo.ClientVo;
import com.innospots.nexmux.core.access.service.ClientService;
import com.innospots.nexmux.core.model.domain.vo.ModelVo;
import com.innospots.nexmux.core.model.service.ModelService;
import com.innospots.nexmux.core.ops.domain.request.RequestLogPageRequest;
import com.innospots.nexmux.core.ops.domain.vo.RequestLogDetailVo;
import com.innospots.nexmux.core.ops.domain.vo.RequestLogVo;
import com.innospots.nexmux.core.ops.service.RequestLogQueryService;
import com.innospots.nexmux.core.provider.domain.vo.ProviderVo;
import com.innospots.nexmux.core.provider.service.ProviderService;
import com.innospots.nexmux.core.route.domain.vo.RouteVo;
import com.innospots.nexmux.core.route.service.RouteService;
import com.innospots.nexus.base.domain.response.PageResult;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;

/**
 * CallLog UI 投影（/api/d/call-logs 契约的装配点）：RequestLog VO → 展示记录，
 * client/route/model/provider 全量内存 join，金额与 tokens 文案预计算。
 * 筛选统一拉全量（pageSize 5000 自保护，与原 BFF 一致），分页在前端表格。
 */
@RequiredArgsConstructor
public class CallLogViewAssembler {

    private static final Map<String, String> STATUS_LABELS = Map.of(
            "success", "成功",
            "error", "错误",
            "unfinished", "未完成",
            "cancelled", "已取消",
            "other", "其他");

    /** 筛选拉全量的自保护上限（与原 BFF 一致）。 */
    private static final long FULL_SCAN_PAGE_SIZE = 5000L;

    private final RequestLogQueryService requestLogQueryService;
    private final ClientService clientService;
    private final RouteService routeService;
    private final ModelService modelService;
    private final ProviderService providerService;

    /**
     * 筛选列表（pageNo 恒 1、pageSize 5000；total 为全量条数）。
     */
    public CallLogListResult assemble(RequestLogPageRequest request) {
        PageResult<RequestLogVo> page = requestLogQueryService.page(
                new RequestLogPageRequest(1L, FULL_SCAN_PAGE_SIZE, request.start(), request.end(),
                        request.status(), request.model(), request.provider(), request.protocol(),
                        request.routeGroup(), request.email(), request.appKey()));
        Relations relations = relations();
        List<CallLogRecord> items = page.records().stream()
                .map(vo -> toRecord(vo, null, relations))
                .toList();
        return new CallLogListResult(items, page.total(), LogTexts.thousands(items.size()));
    }

    /**
     * 按请求 ID 查询详情。
     */
    public CallLogRecord detail(String requestId) {
        RequestLogDetailVo detail = requestLogQueryService.get(requestId);
        return toRecord(baseViewOf(detail), detail, relations());
    }

    /** 详情 VO → 概要视图字段（record 无继承，显式提取公共字段）。 */
    private static RequestLogVo baseViewOf(RequestLogDetailVo d) {
        return new RequestLogVo(
                d.requestId(), d.clientId(), d.routeId(), d.modelCode(), d.status(), d.latencyMs(),
                d.errorCode(), d.routeTrace(), d.requestedAt(), d.appKeyId(), d.userEmail(),
                d.providerCode(), d.protocol(), d.routeGroup(), d.application(), d.alias(),
                d.httpStatus(), d.inputTokens(), d.outputTokens(), d.cacheReadTokens(),
                d.cacheWriteTokens(), d.costUsd(), d.billingUsd());
    }

    private Relations relations() {
        Map<String, String> clientNameById = new LinkedHashMap<>();
        for (ClientVo client : clientService.listAll()) {
            clientNameById.put(client.clientId(), client.displayName());
        }
        Map<String, RouteVo> routeById = new LinkedHashMap<>();
        for (RouteVo route : routeService.listAll()) {
            routeById.put(route.routeId(), route);
        }
        Map<String, String> modelNameByCode = new LinkedHashMap<>();
        for (ModelVo model : modelService.listAll()) {
            modelNameByCode.put(model.modelCode(), model.displayName());
        }
        Map<String, String> providerNameByCode = new LinkedHashMap<>();
        for (ProviderVo provider : providerService.listAll()) {
            providerNameByCode.put(provider.providerCode(), provider.displayName());
        }
        return new Relations(clientNameById, routeById, modelNameByCode, providerNameByCode);
    }

    private record Relations(
            Map<String, String> clientNameById,
            Map<String, RouteVo> routeById,
            Map<String, String> modelNameByCode,
            Map<String, String> providerNameByCode) {
    }

    private static CallLogRecord toRecord(RequestLogVo vo, RequestLogDetailVo detail, Relations relations) {
        String status = statusOf(vo.status());
        Long input = vo.inputTokens();
        Long output = vo.outputTokens();
        long total = (input == null ? 0 : input) + (output == null ? 0 : output);
        BigDecimal cost = vo.costUsd();
        BigDecimal billing = vo.billingUsd();
        BigDecimal margin = billing != null && cost != null ? billing.subtract(cost) : null;
        RouteVo route = relations.routeById.get(vo.routeId() == null ? "" : vo.routeId());
        String strategyLabel = route != null && route.routeSpec() != null
                ? StrategyLabels.of(route.routeSpec().strategyType()) : "—";
        String clientName = relations.clientNameById.getOrDefault(
                vo.clientId() == null ? "" : vo.clientId(), "—");
        long latencyMs = vo.latencyMs() == null ? 0 : vo.latencyMs();
        String time = LogTexts.fullTime(vo.requestedAt());
        JsonNode prompt = LogTexts.readJson(detail == null ? null : detail.promptSnapshot());

        return new CallLogRecord(
                vo.requestId(),
                status,
                STATUS_LABELS.getOrDefault(status, status),
                time + " UTC",
                LogTexts.shortTime(vo.requestedAt()) + " UTC",
                latencyMs,
                latencyMs + "ms",
                vo.userEmail() == null ? "—" : vo.userEmail(),
                clientName,
                clientName,
                vo.appKeyId() == null ? "—" : vo.appKeyId(),
                "—",
                "—",
                vo.application() == null ? "—" : vo.application(),
                route == null ? "—" : route.routeCode(),
                route == null ? "—" : route.displayName(),
                vo.routeGroup() == null ? "—" : vo.routeGroup(),
                vo.protocol() == null ? "—" : vo.protocol(),
                vo.modelCode() == null ? "—" : vo.modelCode(),
                relations.modelNameByCode.getOrDefault(
                        vo.modelCode() == null ? "" : vo.modelCode(),
                        vo.modelCode() == null ? "—" : vo.modelCode()),
                vo.providerCode() == null ? "—" : vo.providerCode(),
                relations.providerNameByCode.getOrDefault(
                        vo.providerCode() == null ? "" : vo.providerCode(),
                        vo.providerCode() == null ? "—" : vo.providerCode()),
                input,
                output,
                vo.cacheReadTokens() == null ? 0 : vo.cacheReadTokens(),
                vo.cacheWriteTokens() == null ? 0 : vo.cacheWriteTokens(),
                input != null
                        ? LogTexts.compactTokens(input) + "/"
                                + (output != null ? LogTexts.compactTokens(output) : "—")
                        : "—",
                input != null ? LogTexts.thousands(input) : "—",
                output != null ? LogTexts.thousands(output) : "—",
                LogTexts.thousands(vo.cacheReadTokens() == null ? 0 : vo.cacheReadTokens()),
                LogTexts.thousands(vo.cacheWriteTokens() == null ? 0 : vo.cacheWriteTokens()),
                input != null ? LogTexts.thousands(total) : "—",
                cost,
                billing,
                margin,
                cost != null ? moneyText(cost) : "—",
                billing != null ? moneyText(billing) : "—",
                margin != null && margin.signum() > 0 ? moneyText(margin) : "—",
                "—",
                pricingText(detail == null ? null : detail.pricingSnapshot()),
                vo.httpStatus() != null ? String.valueOf(vo.httpStatus()) : "—",
                detail == null || detail.ingressMethod() == null ? "POST" : detail.ingressMethod(),
                detail == null || detail.ingressPath() == null ? "—" : detail.ingressPath(),
                vo.httpStatus() != null && (vo.httpStatus() == 401 || vo.httpStatus() == 403)
                        ? "失败：AppKey 校验未通过" : "通过（AppKey 有效）",
                detail == null || detail.clientIp() == null ? "—" : detail.clientIp(),
                vo.alias() == null ? "—" : vo.alias(),
                strategyLabel,
                detail == null || detail.upstreamEndpoint() == null ? "—" : detail.upstreamEndpoint(),
                detail == null || detail.upstreamModel() == null ? "—" : detail.upstreamModel(),
                detail == null || detail.upstreamStatus() == null ? "—" : detail.upstreamStatus(),
                detail != null && detail.upstreamLatencyMs() != null
                        ? detail.upstreamLatencyMs() + "ms" : "—",
                detail != null && detail.ttftMs() != null ? detail.ttftMs() + "ms" : "—",
                detail == null || detail.retries() == null ? 0 : detail.retries(),
                String.valueOf(detail == null || detail.retries() == null ? 0 : detail.retries()),
                detail == null || detail.traceId() == null ? "—" : detail.traceId(),
                vo.errorCode() != null
                        ? new CallLogRecord.ErrorInfo(
                                vo.errorCode(),
                                detail == null || detail.errorType() == null ? "—" : detail.errorType(),
                                vo.errorCode())
                        : null,
                traceStepsOf(detail == null ? null : detail.routeTrace()),
                new CallLogRecord.PrView(
                        textOf(prompt, "policy", "masked"),
                        textOf(prompt, "policyNote", "按策略未留存"),
                        textOrNull(prompt, "prompt"),
                        textOrNull(prompt, "response")));
    }

    private static String statusOf(String backendStatus) {
        String key = backendStatus == null ? "" : backendStatus.toLowerCase();
        return STATUS_LABELS.containsKey(key) ? key : "other";
    }

    /** 金额 → 4 位小数字符串（对齐 toFixed(4)）。 */
    private static String moneyText(BigDecimal value) {
        return value.setScale(4, RoundingMode.HALF_UP).toPlainString();
    }

    /** pricingSnapshot JSON → 定价文案（版本（生效）· C= · M= · 时段倍率）。 */
    private static String pricingText(String snapshot) {
        JsonNode s = LogTexts.readJson(snapshot);
        if (s == null || !s.hasNonNull("priceVersion")) {
            return "—";
        }
        List<String> parts = new ArrayList<>();
        parts.add(s.get("priceVersion").asText() + "（" + textOf(s, "priceEffective", "—") + " 生效）");
        if (s.has("costRate")) {
            parts.add("C=" + s.get("costRate").asText());
        }
        if (s.has("billingRate")) {
            parts.add("M=" + s.get("billingRate").asText());
        }
        JsonNode multiplier = s.get("multiplier");
        parts.add(multiplier != null && multiplier.isNumber() && multiplier.doubleValue() != 1
                ? "时段倍率 ×" + multiplier.asText() : "无时段倍率");
        return String.join(" · ", parts);
    }

    private static List<CallLogRecord.TraceStep> traceStepsOf(String routeTrace) {
        JsonNode trace = LogTexts.readJson(routeTrace);
        if (trace == null || !trace.isArray()) {
            return List.of();
        }
        List<CallLogRecord.TraceStep> out = new ArrayList<>();
        trace.forEach(step -> out.add(new CallLogRecord.TraceStep(
                textOf(step, "name", ""), textOf(step, "input", ""), textOf(step, "decision", ""),
                textOf(step, "output", ""), textOf(step, "duration", ""), textOf(step, "reason", ""))));
        return out;
    }

    private static String textOf(JsonNode node, String field, String fallback) {
        String value = textOrNull(node, field);
        return value == null ? fallback : value;
    }

    private static String textOrNull(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
