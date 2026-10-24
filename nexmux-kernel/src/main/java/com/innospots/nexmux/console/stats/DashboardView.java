package com.innospots.nexmux.console.stats;

import java.math.BigDecimal;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Dashboard 视图（/api/d/stats-dashboard UI 契约）。
 *
 * @param realtime   实时指标
 * @param metricKeys 图表指标键
 * @param stats      汇总卡片
 * @param trend      趋势点
 * @param models     模型分布（Top5 + 其他）
 * @param clients    客户端分布（Top5 + 其他）
 */
@Schema(name = "DashboardView", description = "Dashboard 视图（UI 契约）")
public record DashboardView(
        Realtime realtime,
        MetricKeys metricKeys,
        Stats stats,
        List<TrendPoint> trend,
        List<Slice> models,
        List<Slice> clients) {

    /**
     * 实时指标。
     *
     * @param rpm 每分钟请求数
     * @param tpm 每分钟 tokens（缩略文案）
     */
    @Schema(name = "DashboardRealtime", description = "实时指标")
    public record Realtime(
            long rpm,
            String tpm) {
    }

    /**
     * 图表指标键（数组形状与契约一致）。
     *
     * @param requests 请求
     * @param tokens   tokens
     * @param cost     成本
     * @param latency  耗时
     */
    @Schema(name = "DashboardMetricKeys", description = "图表指标键")
    public record MetricKeys(
            List<String> requests,
            List<String> tokens,
            List<String> cost,
            List<String> latency) {
    }

    /**
     * 汇总卡片。
     *
     * @param requests      请求数（千分位）
     * @param activeClients 活跃客户端数
     * @param tokens        tokens（M 缩略）
     * @param successRate   成功率（1 位小数）
     * @param failures      失败数（千分位）
     * @param errorRate     错误率（2 位小数）
     * @param avgLatency    平均耗时（ms）
     * @param cost          成本（$4 位小数）
     * @param avgCost       平均成本（$4 位小数）
     */
    @Schema(name = "DashboardStats", description = "汇总卡片")
    public record Stats(
            String requests,
            long activeClients,
            String tokens,
            String successRate,
            String failures,
            String errorRate,
            long avgLatency,
            String cost,
            String avgCost) {
    }

    /**
     * 趋势点。
     *
     * @param t        日期（MM-dd）
     * @param requests 请求数
     * @param tokens   tokens（M，2 位小数）
     * @param cost     成本
     * @param latency  平均耗时
     */
    @Schema(name = "DashboardTrendPoint", description = "趋势点")
    public record TrendPoint(
            String t,
            long requests,
            double tokens,
            BigDecimal cost,
            long latency) {
    }

    /**
     * 分布切片。
     *
     * @param name    名称
     * @param percent 百分比
     */
    @Schema(name = "DashboardSlice", description = "分布切片")
    public record Slice(
            String name,
            long percent) {
    }
}
