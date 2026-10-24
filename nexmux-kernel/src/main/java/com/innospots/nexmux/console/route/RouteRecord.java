package com.innospots.nexmux.console.route;

import java.math.BigDecimal;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Route 展示记录（/api/d/routes UI 契约；列表行与详情共用，列表行中详情专有
 * 字段取缺省值）。字段对齐 route-list / route-detail / route-edit 页面的
 * {@code ${row.*}} / {@code ${data.detail.*}} 绑定。
 *
 * @param code              路由编码（稳定业务键）
 * @param name              显示名称
 * @param type              类型（single / pool / conditional，按候选数派生）
 * @param typeLabel         类型展示文案
 * @param alias             路由别名
 * @param tags              标签
 * @param description       描述
 * @param status            状态（enabled / disabled / draft）
 * @param statusLabel       状态展示文案
 * @param path              入口路径（ingress）
 * @param protocol          协议格式
 * @param operation         Ingress Operation（协议映射）
 * @param strategy          负载策略
 * @param strategyLabel     策略展示文案
 * @param stickySession     粘性 Session
 * @param healthFilter      是否过滤不健康绑定
 * @param models            候选模型行
 * @param createdAt         创建时间（yyyy-MM-dd HH:mm）
 * @param updatedAt         更新时间（yyyy-MM-dd HH:mm）
 * @param operator          最近操作人
 * @param entrySummary      入口摘要（path · protocol）
 * @param policySummary     策略摘要（N 模型 · 策略）
 * @param strategySummary   策略全称（label (code)）
 * @param tagsText          标签文案
 * @param enabledModelCount 启用候选数
 * @param weightTotal       启用候选权重合计
 * @param shortDate         最近更新短日期（MM-dd）
 * @param hasUnpublishedChanges 是否存在未发布的草稿变更（草稿规格 ≠ 发布快照）
 */
@Schema(name = "RouteRecord", description = "Route 展示记录（UI 契约）")
public record RouteRecord(
        @Schema(description = "路由 ID", required = true)
        String id,
        String code,
        String name,
        String type,
        String typeLabel,
        String alias,
        List<String> tags,
        String description,
        String status,
        String statusLabel,
        String path,
        String protocol,
        String operation,
        String strategy,
        String strategyLabel,
        boolean stickySession,
        boolean healthFilter,
        List<ModelEntry> models,
        String createdAt,
        String updatedAt,
        String operator,
        String entrySummary,
        String policySummary,
        String strategySummary,
        String tagsText,
        long enabledModelCount,
        long weightTotal,
        String shortDate,
        @Schema(description = "当前运行配置修订号")
        Long configRevision,
        @Schema(description = "是否存在未发布的草稿变更（草稿规格 ≠ 发布快照）")
        boolean hasUnpublishedChanges) {

    /**
     * 候选模型行。
     *
     * @param modelCode   模型编码
     * @param modelName   模型名称（未建模回退编码）
     * @param weight      权重
     * @param priority    优先级（缺省 P0）
     * @param costRate    供应成本倍率 C（null = 继承）
     * @param billingRate 用户计费倍率 M（null = 继承）
     * @param status      启用态（enabled / disabled）
     */
    @Schema(name = "RouteModelEntry", description = "候选模型行")
    public record ModelEntry(
            String modelCode,
            String modelName,
            int weight,
            String priority,
            BigDecimal costRate,
            BigDecimal billingRate,
            String status) {
    }
}
