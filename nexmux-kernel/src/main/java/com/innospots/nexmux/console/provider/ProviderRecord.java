package com.innospots.nexmux.console.provider;

import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Provider 展示记录（/api/d/providers UI 契约；列表行与详情共用，
 * 列表行中详情专有字段取缺省值）。字段对齐 provider-list / provider-detail /
 * provider-edit 页面的 {@code ${row.*}} / {@code ${data.detail.*}} 绑定。
 *
 * @param code                唯一编码（稳定业务键）
 * @param name                显示名称
 * @param displayName         显示名称（同名多实例区分展示）
 * @param icon                图标名（Icon 组件白名单）
 * @param providerType        提供方类型
 * @param protocol            默认协议
 * @param family              厂商族（vendorCode）
 * @param status              启停状态（enabled / disabled）
 * @param keyStatus           密钥状态（configured / pending / none）
 * @param baseUrl             服务基址
 * @param region              区域
 * @param timeout             超时（毫秒）
 * @param proxy               代理
 * @param maskedKey           脱敏密钥文案
 * @param lastTest            最近连通性测试（无数据为 null）
 * @param capabilities        能力标签（capabilityExtension JSON 派生）
 * @param routes              路由引用统计
 * @param routeSummary        路由摘要文案
 * @param models              关联模型行
 * @param modelCount          模型总数
 * @param enabledModelCount   启用模型数
 * @param description         描述
 * @param capabilityExtension 能力扩展（JSON 原文，编辑页回填）
 * @param createdAt           创建时间（yyyy-MM-dd HH:mm）
 * @param updatedAt           更新时间（yyyy-MM-dd HH:mm）
 * @param operator            最近操作人
 */
@Schema(name = "ProviderRecord", description = "Provider 展示记录（UI 契约）")
public record ProviderRecord(
        @Schema(description = "唯一编码", required = true)
        String code,
        @Schema(description = "显示名称", required = true)
        String name,
        @Schema(description = "显示名称（同名多实例区分展示）", required = true)
        String displayName,
        @Schema(description = "图标名")
        String icon,
        @Schema(description = "提供方类型")
        String providerType,
        @Schema(description = "默认协议")
        String protocol,
        @Schema(description = "厂商族")
        String family,
        @Schema(description = "启停状态", examples = {"enabled"})
        String status,
        @Schema(description = "密钥状态", examples = {"configured"})
        String keyStatus,
        @Schema(description = "服务基址")
        String baseUrl,
        @Schema(description = "区域")
        String region,
        @Schema(description = "超时（毫秒）")
        long timeout,
        @Schema(description = "代理")
        String proxy,
        @Schema(description = "脱敏密钥文案")
        String maskedKey,
        @Schema(description = "最近连通性测试")
        LastTest lastTest,
        @Schema(description = "能力标签")
        List<String> capabilities,
        @Schema(description = "路由引用统计")
        RouteStat routes,
        @Schema(description = "路由摘要文案")
        String routeSummary,
        @Schema(description = "关联模型行")
        List<ModelRow> models,
        @Schema(description = "模型总数")
        long modelCount,
        @Schema(description = "启用模型数")
        long enabledModelCount,
        @Schema(description = "描述")
        String description,
        @Schema(description = "能力扩展（JSON 原文）")
        String capabilityExtension,
        @Schema(description = "创建时间")
        String createdAt,
        @Schema(description = "更新时间")
        String updatedAt,
        @Schema(description = "最近操作人")
        String operator) {

    /**
     * 路由引用统计。
     *
     * @param total   引用该 Provider 的路由总数
     * @param enabled 其中已发布（启用）数
     */
    @Schema(name = "ProviderRouteStat", description = "路由引用统计")
    public record RouteStat(
            @Schema(description = "路由总数", required = true)
            long total,
            @Schema(description = "已发布数", required = true)
            long enabled) {
    }

    /**
     * 最近连通性测试。
     *
     * @param time   测试时间
     * @param ok     是否成功
     * @param detail 详情文案
     */
    @Schema(name = "ProviderLastTest", description = "最近连通性测试")
    public record LastTest(
            @Schema(description = "测试时间")
            String time,
            @Schema(description = "是否成功")
            boolean ok,
            @Schema(description = "详情文案")
            String detail) {
    }

    /**
     * 关联模型行。
     *
     * @param code            模型编码
     * @param codeUi          模型编码（UI 形式，/ 以 ~ 替代，供页面跳转）
     * @param name            显示名称
     * @param type            模型类型
     * @param providerModelId 上游模型 ID
     * @param pricing         定价摘要（P0 恒 "—"）
     * @param status          状态（ONLINE / OFFLINE）
     * @param routes          引用该模型的路由数
     */
    @Schema(name = "ProviderModelRow", description = "关联模型行")
    public record ModelRow(
            @Schema(description = "模型编码", required = true)
            String code,
            @Schema(description = "模型编码（UI 形式，~ 替代 /）", required = true)
            String codeUi,
            @Schema(description = "显示名称", required = true)
            String name,
            @Schema(description = "模型类型")
            String type,
            @Schema(description = "上游模型 ID")
            String providerModelId,
            @Schema(description = "定价摘要")
            String pricing,
            @Schema(description = "状态", examples = {"ONLINE"})
            String status,
            @Schema(description = "引用路由数")
            long routes) {
    }
}
