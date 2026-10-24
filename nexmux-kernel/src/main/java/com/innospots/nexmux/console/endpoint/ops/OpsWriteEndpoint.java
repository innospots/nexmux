package com.innospots.nexmux.console.endpoint.ops;

import com.fasterxml.jackson.databind.JsonNode;
import com.innospots.nexmux.core.model.domain.request.ModelCreateRequest;
import com.innospots.nexmux.core.model.domain.request.ModelUpdateRequest;
import com.innospots.nexmux.core.model.domain.vo.ModelDetailVo;
import com.innospots.nexmux.core.model.domain.vo.ModelVo;
import com.innospots.nexmux.core.model.service.ModelService;
import com.innospots.nexmux.core.provider.domain.request.ProviderCreateRequest;
import com.innospots.nexmux.core.provider.domain.request.ProviderUpdateRequest;
import com.innospots.nexmux.core.provider.domain.vo.ConnectionTestVo;
import com.innospots.nexmux.core.provider.domain.vo.ProviderDetailVo;
import com.innospots.nexmux.core.provider.service.ProviderService;
import com.innospots.nexmux.core.route.domain.request.RouteCreateRequest;
import com.innospots.nexmux.core.route.domain.request.RoutePublishRequest;
import com.innospots.nexmux.core.route.domain.request.RouteUpdateRequest;
import com.innospots.nexmux.core.route.domain.value.RouteCandidate;
import com.innospots.nexmux.core.route.domain.value.RouteSpec;
import com.innospots.nexmux.core.route.domain.vo.RouteDetailVo;
import com.innospots.nexmux.core.route.service.RouteService;
import com.innospots.nexmux.core.shared.json.NexmuxJson;
import com.innospots.nexus.core.openapi.NexusAuthenticatedApi;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 写操作端点（/api/d/ops 单段 kebab 路径约定，POST；UI 写闭环约定：
 * 成功 {ok:true}，失败非 2xx 由全局异常映射）。code → id 解析在此完成。
 * 表单整包提交时只取契约字段（与原 BFF 写映射一致）。
 */
@Path("/api/d/ops")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "NexmuxOpsWrite", description = "配置写操作")
@NexusAuthenticatedApi
@RequiredArgsConstructor
public class OpsWriteEndpoint {

    private final ProviderService providerService;
    private final ModelService modelService;
    private final RouteService routeService;

    // ── Provider ───────────────────────────────────────────────

    @POST
    @Path("/provider-enable")
    @Operation(operationId = "nexmuxOpsProviderEnable", summary = "启用提供方")
    public OpsResult providerEnable(@QueryParam("code") String code) {
        providerService.setEnabled(providerIdOf(code), true);
        return OpsResult.OK;
    }

    @POST
    @Path("/provider-disable")
    @Operation(operationId = "nexmuxOpsProviderDisable", summary = "停用提供方")
    public OpsResult providerDisable(@QueryParam("code") String code) {
        providerService.disable(providerIdOf(code));
        return OpsResult.OK;
    }

    @POST
    @Path("/provider-delete")
    @Operation(operationId = "nexmuxOpsProviderDelete", summary = "删除提供方")
    public OpsResult providerDelete(@QueryParam("code") String code) {
        providerService.delete(providerIdOf(code));
        return OpsResult.OK;
    }

    /** 连通性测试（UI 契约：{ok,latencyMs,detail} 直出）。 */
    @POST
    @Path("/provider-connection-test")
    @Operation(operationId = "nexmuxOpsProviderConnectionTest", summary = "提供方连通性测试")
    public ConnectionTestVo providerConnectionTest(@QueryParam("code") String code) {
        return providerService.connectionTest(providerIdOf(code));
    }

    /** 更新（表单整包提交，只取契约字段：displayName/baseUrl/region/timeoutMs）。 */
    @POST
    @Path("/provider-save")
    @Operation(operationId = "nexmuxOpsProviderSave", summary = "更新提供方")
    public OpsResult providerSave(@QueryParam("code") String code, JsonNode body) {
        providerService.update(providerIdOf(code), new ProviderUpdateRequest(
                textOrNull(body, "displayName"),
                textOrNull(body, "baseUrl"),
                textOrNull(body, "region"),
                body != null && body.has("timeoutMs") && body.get("timeoutMs").isNumber()
                        ? body.get("timeoutMs").asInt() : null,
                textOrNull(body, "apiKey"),
                textOrNull(body, "description"),
                textOrNull(body, "proxy")));
        return OpsResult.OK;
    }

    /** 创建（vendorCode 缺省回填 providerCode，sourceVendorCode 缺省同 vendorCode）。 */
    @POST
    @Path("/provider-create")
    @Operation(operationId = "nexmuxOpsProviderCreate", summary = "创建提供方")
    public OpsResult providerCreate(JsonNode body) {
        String providerCode = textOrNull(body, "providerCode");
        String vendorCode = textOrNull(body, "vendorCode");
        String sourceVendorCode = textOrNull(body, "sourceVendorCode");
        providerService.create(new ProviderCreateRequest(
                providerCode,
                textOrNull(body, "displayName"),
                textOrNull(body, "providerType"),
                textOrNull(body, "defaultProtocol"),
                vendorCode == null || vendorCode.isBlank() ? providerCode : vendorCode,
                sourceVendorCode == null || sourceVendorCode.isBlank()
                        ? (vendorCode == null || vendorCode.isBlank() ? providerCode : vendorCode)
                        : sourceVendorCode,
                textOrNull(body, "iconUri"),
                textOrNull(body, "baseUrl"),
                textOrNull(body, "region"),
                body != null && body.has("timeoutMs") && body.get("timeoutMs").isNumber()
                        ? body.get("timeoutMs").asInt() : null,
                textOrNull(body, "apiKey"),
                textOrNull(body, "description"),
                textOrNull(body, "proxy")));
        return OpsResult.OK;
    }

    // ── Model ──────────────────────────────────────────────────

    @POST
    @Path("/model-enable")
    @Operation(operationId = "nexmuxOpsModelEnable", summary = "启用模型")
    public OpsResult modelEnable(@QueryParam("code") String code) {
        modelService.setEnabled(modelIdOf(code), true);
        return OpsResult.OK;
    }

    @POST
    @Path("/model-disable")
    @Operation(operationId = "nexmuxOpsModelDisable", summary = "停用模型")
    public OpsResult modelDisable(@QueryParam("code") String code) {
        modelService.disable(modelIdOf(code));
        return OpsResult.OK;
    }

    /** 调用测试（UI 契约：{ok} 直出）。 */
    @POST
    @Path("/model-invoke-test")
    @Operation(operationId = "nexmuxOpsModelInvokeTest", summary = "模型调用测试")
    public OpsResult modelInvokeTest(@QueryParam("code") String code) {
        return new OpsResult(modelService.invokeTest(modelIdOf(code)));
    }

    /** 更新（表单整包提交，只取契约字段；模态/标签兼容数组与逗号分隔文本）。 */
    @POST
    @Path("/model-save")
    @Operation(operationId = "nexmuxOpsModelSave", summary = "更新模型")
    public OpsResult modelSave(@QueryParam("code") String code, JsonNode body) {
        modelService.update(modelIdOf(code), new ModelUpdateRequest(
                textOrNull(body, "displayName"),
                textOrNull(body, "description"),
                positiveLongOrNull(body, "contextLength"),
                positiveLongOrNull(body, "maxOutputTokens"),
                stringListOrNull(body, "inputModalities"),
                stringListOrNull(body, "outputModalities"),
                stringListOrNull(body, "tags"),
                jsonTextOrNull(body, "metadata"),
                defaultParamsOf(body),
                capabilityTagsOf(body),
                integerOrNull(body, "primaryBindingWeight"),
                decimalOrNull(body, "primaryCostMultiplier"),
                decimalOrNull(body, "primaryReferenceMultiplier"),
                flagOrNull(body, "primaryBindingEnabled"),
                jsonTextOrNull(body, "extraBindings"),
                jsonTextOrNull(body, "pricingSpec")));
        return OpsResult.OK;
    }

    /** 创建（表单选择「启用」时创建后补一次 enable）。 */
    @POST
    @Path("/model-create")
    @Operation(operationId = "nexmuxOpsModelCreate", summary = "创建模型")
    public OpsResult modelCreate(JsonNode body) {
        ModelVo created = modelService.create(new ModelCreateRequest(
                textOrNull(body, "modelCode"),
                textOrNull(body, "displayName"),
                textOrNull(body, "modelType"),
                textOrNull(body, "primaryProviderCode"),
                textOrNull(body, "primaryProviderModelId"),
                textOrNull(body, "primaryUpstreamProtocol"),
                positiveLongOrNull(body, "contextLength"),
                positiveLongOrNull(body, "maxOutputTokens"),
                stringListOrNull(body, "inputModalities"),
                stringListOrNull(body, "outputModalities"),
                stringListOrNull(body, "tags"),
                textOrNull(body, "description"),
                jsonTextOrNull(body, "metadata"),
                defaultParamsOf(body),
                capabilityTagsOf(body),
                integerOrNull(body, "primaryBindingWeight"),
                decimalOrNull(body, "primaryCostMultiplier"),
                decimalOrNull(body, "primaryReferenceMultiplier"),
                flagOrNull(body, "primaryBindingEnabled"),
                jsonTextOrNull(body, "extraBindings"),
                jsonTextOrNull(body, "pricingSpec")));
        if ("enabled".equals(textOrNull(body, "status")) && created.modelId() != null) {
            modelService.setEnabled(created.modelId(), true);
        }
        return OpsResult.OK;
    }

    // ── Route ──────────────────────────────────────────────────

    @POST
    @Path("/route-enable")
    @Operation(operationId = "nexmuxOpsRouteEnable", summary = "启用路由（DISABLED → PUBLISHED）")
    public OpsResult routeEnable(@QueryParam("code") String code) {
        routeService.enable(routeIdOf(code));
        return OpsResult.OK;
    }

    @POST
    @Path("/route-disable")
    @Operation(operationId = "nexmuxOpsRouteDisable", summary = "停用路由（PUBLISHED → DISABLED）")
    public OpsResult routeDisable(@QueryParam("code") String code) {
        routeService.disable(routeIdOf(code));
        return OpsResult.OK;
    }

    @POST
    @Path("/route-publish")
    @Operation(operationId = "nexmuxOpsRoutePublish", summary = "发布路由（草稿 → PUBLISHED）")
    public OpsResult routePublish(@QueryParam("code") String code) {
        routeService.publish(routeIdOf(code), new RoutePublishRequest(null, null));
        return OpsResult.OK;
    }

    @POST
    @Path("/route-delete")
    @Operation(operationId = "nexmuxOpsRouteDelete", summary = "删除路由")
    public OpsResult routeDelete(@QueryParam("code") String code) {
        routeService.delete(routeIdOf(code));
        return OpsResult.OK;
    }

    /** 创建（表单 type 字段无落点——类型由候选数派生；候选模型在编辑页维护）。 */
    @POST
    @Path("/route-create")
    @Operation(operationId = "nexmuxOpsRouteCreate", summary = "创建路由")
    public OpsResult routeCreate(JsonNode body) {
        routeService.create(new RouteCreateRequest(
                textOrNull(body, "code"),
                textOrNull(body, "alias"),
                textOrNull(body, "name"),
                textOrNull(body, "description"),
                textOrNull(body, "path"),
                textOrNull(body, "protocol"),
                textOrNull(body, "strategy"),
                flagOrNull(body, "stickySession"),
                flagOrNull(body, "healthFilter"),
                stringListOrNull(body, "tags"),
                candidatesOf(body, List.of())));
        return OpsResult.OK;
    }

    /**
     * 更新（合并语义：基本信息/入口/策略取表单新值，候选模型保留现状——
     * 候选行的动态编辑未接线，整包覆盖会清空模型）。
     */
    @POST
    @Path("/route-save")
    @Operation(operationId = "nexmuxOpsRouteSave", summary = "更新路由（保留候选模型）")
    public OpsResult routeSave(@QueryParam("code") String code, JsonNode body) {
        RouteDetailVo detail = routeService.getByCode(code);
        RouteSpec spec = detail.routeSpec();
        RouteSpec merged = new RouteSpec(
                orElse(textOrNull(body, "alias"), spec.alias()),
                orElse(textOrNull(body, "path"), spec.ingress()),
                orElse(textOrNull(body, "strategy"), spec.strategyType()),
                candidatesOf(body, spec.candidates()),
                orElse(flagOrNull(body, "healthFilter"), spec.filterUnhealthyBindings()),
                spec.schemaVersion(),
                orElse(textOrNull(body, "description"), spec.description()),
                orElse(textOrNull(body, "protocol"), spec.protocol()),
                orElse(stringListOrNull(body, "tags"), spec.tags()),
                orElse(flagOrNull(body, "stickySession"), spec.stickySession()));
        routeService.updateDraft(detail.routeId(), new RouteUpdateRequest(
                orElse(textOrNull(body, "name"), detail.displayName()), merged));
        return OpsResult.OK;
    }

    // ── code → id 解析 ─────────────────────────────────────────

    private String routeIdOf(String code) {
        return routeService.getByCode(code).routeId();
    }


    private String providerIdOf(String code) {
        ProviderDetailVo detail = providerService.getByCode(code);
        return detail.providerId();
    }

    /** UI 编码 ~ 还原为 / 后按稳定业务键解析。 */
    private String modelIdOf(String uiCode) {
        ModelDetailVo detail = modelService.getByCode(uiCode.replace("~", "/"));
        return detail.modelId();
    }

    // ── 表单字段提取（数组 / 逗号·中文顿号分隔文本兼容） ──────

    private static String jsonTextOrNull(JsonNode body, String field) {
        if (body == null || !body.hasNonNull(field)) {
            return null;
        }
        JsonNode value = body.get(field);
        if (value.isObject() || value.isArray()) {
            return NexmuxJson.write(value);
        }
        String text = value.asText().trim();
        return text.isEmpty() || "—".equals(text) ? null : text;
    }

    /** 同时接受结构化 defaultParams 与页面平铺参数。 */
    private static String defaultParamsOf(JsonNode body) {
        String direct = jsonTextOrNull(body, "defaultParams");
        if (direct != null) {
            return direct;
        }
        Map<String, Object> params = new LinkedHashMap<>();
        putParam(body, params, "temperature", "temperature");
        putParam(body, params, "topP", "topP");
        putParam(body, params, "topK", "topK");
        putParam(body, params, "maxTokensParam", "maxTokens");
        putParam(body, params, "frequencyPenalty", "frequencyPenalty");
        putParam(body, params, "presencePenalty", "presencePenalty");
        putParam(body, params, "reasoningEffort", "reasoningEffort");
        putParam(body, params, "seed", "seed");
        putParam(body, params, "stopSequences", "stopSequences");
        putParam(body, params, "responseFormat", "responseFormat");
        return params.isEmpty() ? null : NexmuxJson.write(params);
    }

    private static void putParam(JsonNode body, Map<String, Object> params,
                                 String field, String target) {
        if (body == null || !body.hasNonNull(field)) {
            return;
        }
        JsonNode value = body.get(field);
        if (value.isTextual() && (value.asText().isBlank() || "—".equals(value.asText()))) {
            return;
        }
        params.put(target, value);
    }

    private static String capabilityTagsOf(JsonNode body) {
        List<String> capabilities = stringListOrNull(body, "capabilities");
        return capabilities == null ? null : NexmuxJson.write(capabilities);
    }

    static String textOrNull(JsonNode body, String field) {
        if (body == null) {
            return null;
        }
        JsonNode value = body.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    /** 表单 on/off 开关 → Boolean（未提交为 null）。 */
    static Boolean flagOrNull(JsonNode body, String field) {
        String value = textOrNull(body, field);
        if (value == null) {
            return null;
        }
        return "on".equals(value) || "true".equals(value);
    }

    private static String orElse(String value, String fallback) {
        return value == null ? fallback : value;
    }

    private static Boolean orElse(Boolean value, Boolean fallback) {
        return value == null ? fallback : value;
    }

    private static boolean orElse(Boolean value, boolean fallback) {
        return value == null ? fallback : value;
    }

    private static List<String> orElse(List<String> value, List<String> fallback) {
        return value == null ? fallback : value;
    }

    private static Integer integerOrNull(JsonNode body, String field) {
        return body != null && body.hasNonNull(field) && body.get(field).isNumber()
                ? body.get(field).intValue() : null;
    }

    static Long positiveLongOrNull(JsonNode body, String field) {
        if (body == null) {
            return null;
        }
        JsonNode value = body.get(field);
        if (value == null || !value.isNumber() || value.longValue() <= 0) {
            return null;
        }
        return value.longValue();
    }

    static List<String> stringListOrNull(JsonNode body, String field) {
        if (body == null) {
            return null;
        }
        JsonNode value = body.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        List<String> out = new ArrayList<>();
        if (value.isArray()) {
            value.forEach(item -> {
                if (item.isTextual() && !item.asText().isEmpty()) {
                    out.add(item.asText());
                }
            });
            return out;
        }
        if (value.isTextual() && !value.asText().isBlank()) {
            // 兼容编辑页回填的展示格式（" · " 分隔）与用户输入的逗号分隔
            for (String part : value.asText().split("[,·]")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    out.add(trimmed);
                }
            }
            return out;
        }
        return null;
    }

    /**
     * 写操作结果（UI 写闭环：{ok} 判定）。
     *
     * @param ok 是否成功
     */
    /** 候选模型数组（编辑页「添加模型」整包提交；缺省 = 保留现状）。 */
    private static List<RouteCandidate> candidatesOf(JsonNode body, List<RouteCandidate> fallback) {
        JsonNode arr = body == null ? null : body.get("candidates");
        if (arr == null || !arr.isArray()) {
            return fallback;
        }
        List<RouteCandidate> out = new ArrayList<>();
        for (JsonNode c : arr) {
            String modelCode = c.hasNonNull("modelCode") ? c.get("modelCode").asText() : null;
            if (modelCode == null || modelCode.isBlank()) {
                continue;
            }
            out.add(new RouteCandidate(
                    modelCode,
                    c.hasNonNull("weight") && c.get("weight").isNumber() ? c.get("weight").asInt() : 100,
                    c.hasNonNull("status") ? "enabled".equals(c.get("status").asText()) : null,
                    c.hasNonNull("priority") ? c.get("priority").asText() : null,
                    decimalOrNull(c, "costRate"),
                    decimalOrNull(c, "billingRate")));
        }
        return out;
    }

    private static java.math.BigDecimal decimalOrNull(JsonNode node, String field) {
        return node != null && node.hasNonNull(field) && node.get(field).isNumber()
                ? node.get(field).decimalValue() : null;
    }

    @Schema(name = "OpsResult", description = "写操作结果（UI 契约）")
    public record OpsResult(
            @Schema(description = "是否成功", required = true)
            boolean ok) {

        /** 成功占位（写闭环不依赖返回体内容）。 */
        public static final OpsResult OK = new OpsResult(true);
    }
}
