package com.innospots.nexmux.console.model;

import java.math.BigDecimal;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Model 展示记录（/api/d/models UI 契约；列表行与详情共用，列表行中详情专有
 * 字段取缺省值）。字段对齐 model-list / model-detail / model-edit 页面的
 * {@code ${row.*}} / {@code ${data.detail.*}} 绑定，派生文案由
 * {@link ModelViewAssembler} 预计算。
 *
 * @param code                模型编码（/ 以 ~ 替代，页面路径段不支持 /）
 * @param name                显示名称
 * @param providerCode        主提供方编码
 * @param providerName        主提供方名称
 * @param providerIcon        主提供方图标名
 * @param type                模型类型（LLM / Embedding / Image / Audio）
 * @param typeLabel           类型展示文案
 * @param releaseDate         发布日期
 * @param contextWindow       上下文窗口
 * @param maxTokens           最大输出 tokens（0 表示未设置，展示 —）
 * @param inputModalities     输入模态
 * @param outputModalities    输出模态
 * @param tags                标签
 * @param description         描述
 * @param metadata            元数据（JSON 文本）
 * @param status              启停状态（enabled / disabled）
 * @param tiers               定价分档
 * @param timeMultipliers     时段倍率
 * @param currency            货币
 * @param priceVersion        当前定价版本
 * @param priceEffective      当前定价生效时间
 * @param priceVersions       定价历史版本
 * @param suppliers           供应商模型（主绑定 + 扩展绑定）
 * @param routes              路由引用统计
 * @param routeRefs           路由引用行
 * @param params              默认调用参数
 * @param createdAt           创建时间（yyyy-MM-dd HH:mm）
 * @param updatedAt           更新时间（yyyy-MM-dd HH:mm）
 * @param operator            最近操作人
 * @param specSummary         规格摘要（上下文 · 最大输出）
 * @param pricingSummary      定价摘要（默认档）
 * @param tierLabel           分档标签（多档 "N档"，单档空串）
 * @param routeSummary        路由摘要文案
 * @param modalityPreview     模态预览（如 T+I → T）
 * @param inputModalitiesText 输入模态文案
 * @param outputModalitiesText 输出模态文案
 * @param tagsText            标签文案
 * @param providerSummary     提供方摘要
 * @param primaryUpstreamId   主供应商上游 Model ID
 * @param supplierWeightTotal 启用实例权重合计（文案）
 * @param shortDate           最近更新短日期（MM-dd）
 */
@Schema(name = "ModelRecord", description = "Model 展示记录（UI 契约）")
public record ModelRecord(
        @Schema(description = "模型 ID", required = true)
        String id,
        String code,
        String name,
        String providerCode,
        String providerName,
        String providerIcon,
        String type,
        String typeLabel,
        String releaseDate,
        long contextWindow,
        long maxTokens,
        List<String> inputModalities,
        List<String> outputModalities,
        List<String> tags,
        String description,
        String metadata,
        String status,
        List<ModelTier> tiers,
        List<TimeMultiplier> timeMultipliers,
        String currency,
        String priceVersion,
        String priceEffective,
        List<PriceVersion> priceVersions,
        List<SupplierModel> suppliers,
        RouteStat routes,
        List<RouteRef> routeRefs,
        ModelParams params,
        String createdAt,
        String updatedAt,
        String operator,
        String specSummary,
        String pricingSummary,
        String tierLabel,
        String routeSummary,
        String modalityPreview,
        String inputModalitiesText,
        String outputModalitiesText,
        String tagsText,
        String providerSummary,
        String primaryUpstreamId,
        String supplierWeightTotal,
        String shortDate) {

    /**
     * 定价分档。
     *
     * @param upto      档位上限（null 为开放档）
     * @param input     输入单价（每 M tokens）
     * @param output    输出单价（每 M tokens）
     * @param cacheRead 缓存读单价
     * @param cacheWrite 缓存写单价
     */
    @Schema(name = "ModelTier", description = "定价分档")
    public record ModelTier(
            BigDecimal upto,
            BigDecimal input,
            BigDecimal output,
            BigDecimal cacheRead,
            BigDecimal cacheWrite) {
    }

    /**
     * 时段倍率。
     *
     * @param days  适用日
     * @param start 开始时间
     * @param end   结束时间
     * @param rate  倍率
     */
    @Schema(name = "TimeMultiplier", description = "时段倍率")
    public record TimeMultiplier(
            String days,
            String start,
            String end,
            BigDecimal rate) {
    }

    /**
     * 供应商时段倍率。
     *
     * @param days    适用日
     * @param start   开始时间
     * @param end     结束时间
     * @param cost    成本倍率
     * @param billing 计费倍率
     */
    @Schema(name = "SupplierTimeRate", description = "供应商时段倍率")
    public record SupplierTimeRate(
            String days,
            String start,
            String end,
            BigDecimal cost,
            BigDecimal billing) {
    }

    /**
     * 供应商模型（主绑定或扩展绑定的展示投影）。
     *
     * @param providerCode    提供方编码
     * @param providerName    提供方名称
     * @param providerIcon    提供方图标名
     * @param providerModelId 上游模型 ID
     * @param weight          权重
     * @param costRate        成本倍率 C
     * @param billingRate     计费倍率 M
     * @param status          绑定启停（enabled / disabled）
     * @param timeRates       时段倍率（null = 未配置）
     * @param keyConfigured   提供方密钥已配置
     * @param lastProbe       最近探测（无数据为 null）
     */
    @Schema(name = "SupplierModel", description = "供应商模型")
    public record SupplierModel(
            String providerCode,
            String providerName,
            String providerIcon,
            String providerModelId,
            int weight,
            BigDecimal costRate,
            BigDecimal billingRate,
            String status,
            List<SupplierTimeRate> timeRates,
            boolean keyConfigured,
            LastProbe lastProbe) {
    }

    /**
     * 最近探测。
     *
     * @param time 探测时间
     * @param ok   是否健康
     */
    @Schema(name = "SupplierLastProbe", description = "最近探测")
    public record LastProbe(
            String time,
            boolean ok) {
    }

    /**
     * 路由引用行。
     *
     * @param name   路由名称
     * @param alias  路由别名
     * @param type   类型（单模型 / 多模型池）
     * @param weight 该模型在路由中的权重
     * @param status 状态（启用 / 停用）
     * @param health 健康状态
     */
    @Schema(name = "ModelRouteRef", description = "路由引用行")
    public record RouteRef(
            String name,
            String alias,
            String type,
            int weight,
            String status,
            String health) {
    }

    /**
     * 定价历史版本。
     *
     * @param version   版本号
     * @param current   是否当前版本
     * @param effective 生效时间
     * @param summary   摘要
     */
    @Schema(name = "PriceVersion", description = "定价历史版本")
    public record PriceVersion(
            String version,
            boolean current,
            String effective,
            String summary) {
    }

    /**
     * 默认调用参数（缺省值展示 "—"）。
     *
     * @param temperature      temperature
     * @param topP             top_p
     * @param topK             top_k
     * @param maxTokens        max_tokens
     * @param frequencyPenalty frequency_penalty
     * @param presencePenalty  presence_penalty
     * @param reasoningEffort  reasoning_effort
     * @param seed             seed
     * @param stopSequences    stop
     * @param responseFormat   response_format
     * @param capabilities     能力标签
     */
    @Schema(name = "ModelParams", description = "默认调用参数")
    public record ModelParams(
            String temperature,
            String topP,
            String topK,
            String maxTokens,
            String frequencyPenalty,
            String presencePenalty,
            String reasoningEffort,
            String seed,
            String stopSequences,
            String responseFormat,
            List<String> capabilities) {
    }

    /**
     * 路由引用统计。
     *
     * @param total  引用该模型的路由总数
     * @param active 其中已发布（活跃）数
     */
    @Schema(name = "ModelRouteStat", description = "路由引用统计")
    public record RouteStat(
            long total,
            long active) {
    }
}
