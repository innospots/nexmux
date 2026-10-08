package com.innospots.nexmux.console.ops;

import java.math.BigDecimal;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * CallLog 展示记录（/api/d/call-logs UI 契约；列表行与详情共用）。
 * 关联（client/route/model/provider 名称）由装配层全量内存 join 补齐；
 * channel/routeTag 等演示元数据无真实列，诚实给 "—"。
 *
 * @param id                 请求 ID
 * @param status             状态（success / error / unfinished / cancelled / other）
 * @param statusLabel        状态文案
 * @param timeUtc            完整时间（UTC 标注）
 * @param timeShort          短时间
 * @param latencyMs          耗时（毫秒）
 * @param latencyText        耗时文案
 * @param userEmail          用户邮箱
 * @param clientCode         客户端名称（关联；无命中为 "—"）
 * @param clientName         客户端名称（同 clientCode，契约保留）
 * @param appKeyId           AppKey 展示 ID
 * @param channel            渠道（无真实列，"—"）
 * @param routeTag           路由标签（无真实列，"—"）
 * @param application        应用
 * @param routeCode          路由编码
 * @param routeName          路由名称
 * @param routeGroup         路由分组
 * @param protocol           协议
 * @param modelCode          模型编码
 * @param modelName          模型名称（关联）
 * @param providerCode       提供方编码
 * @param providerName       提供方名称（关联）
 * @param inputTokens        输入 tokens
 * @param outputTokens       输出 tokens
 * @param cacheReadTokens    缓存读 tokens
 * @param cacheWriteTokens   缓存写 tokens
 * @param usageText          用量摘要（in/out 缩略）
 * @param inputTokensText    输入 tokens 文案（千分位）
 * @param outputTokensText   输出 tokens 文案
 * @param cacheReadText      缓存读文案
 * @param cacheWriteText     缓存写文案
 * @param totalTokensText    总 tokens 文案
 * @param costUsd            成本（USD）
 * @param billingUsd         计费（USD）
 * @param marginUsd          毛利（USD）
 * @param costText           成本文案（4 位小数）
 * @param billingText        计费文案
 * @param marginText         毛利文案（非正为 "—"）
 * @param standardPriceText  标准价（无数据，"—"）
 * @param pricingSnapshot    定价快照文案
 * @param httpStatusText     HTTP 状态文案
 * @param ingressMethod      入口方法
 * @param ingressPath        入口路径
 * @param authResult         鉴权结果文案
 * @param clientIp           客户端 IP
 * @param alias              路由别名
 * @param strategyText       调度策略文案
 * @param upstreamEndpoint   上游端点
 * @param upstreamModel      上游模型
 * @param upstreamStatusText 上游状态文案
 * @param upstreamLatencyText 上游耗时文案
 * @param ttftText           TTFT 文案
 * @param retries            重试次数
 * @param retriesText        重试文案
 * @param traceId            追踪 ID
 * @param error              错误信息（无则 null）
 * @param trace              路由追踪决策链
 * @param pr                 提示词快照（按策略留存）
 */
@Schema(name = "CallLogRecord", description = "CallLog 展示记录（UI 契约）")
public record CallLogRecord(
        String id,
        String status,
        String statusLabel,
        String timeUtc,
        String timeShort,
        long latencyMs,
        String latencyText,
        String userEmail,
        String clientCode,
        String clientName,
        String appKeyId,
        String channel,
        String routeTag,
        String application,
        String routeCode,
        String routeName,
        String routeGroup,
        String protocol,
        String modelCode,
        String modelName,
        String providerCode,
        String providerName,
        Long inputTokens,
        Long outputTokens,
        long cacheReadTokens,
        long cacheWriteTokens,
        String usageText,
        String inputTokensText,
        String outputTokensText,
        String cacheReadText,
        String cacheWriteText,
        String totalTokensText,
        BigDecimal costUsd,
        BigDecimal billingUsd,
        BigDecimal marginUsd,
        String costText,
        String billingText,
        String marginText,
        String standardPriceText,
        String pricingSnapshot,
        String httpStatusText,
        String ingressMethod,
        String ingressPath,
        String authResult,
        String clientIp,
        String alias,
        String strategyText,
        String upstreamEndpoint,
        String upstreamModel,
        String upstreamStatusText,
        String upstreamLatencyText,
        String ttftText,
        int retries,
        String retriesText,
        String traceId,
        ErrorInfo error,
        List<TraceStep> trace,
        PrView pr) {

    /**
     * 错误信息。
     *
     * @param code    错误码
     * @param type    错误类型
     * @param message 错误消息
     */
    @Schema(name = "CallLogError", description = "错误信息")
    public record ErrorInfo(
            String code,
            String type,
            String message) {
    }

    /**
     * 路由追踪决策链单步。
     *
     * @param name     步骤名
     * @param input    输入
     * @param decision 判断
     * @param output   输出
     * @param duration 耗时
     * @param reason   原因
     */
    @Schema(name = "TraceStep", description = "路由追踪决策链单步")
    public record TraceStep(
            String name,
            String input,
            String decision,
            String output,
            String duration,
            String reason) {
    }

    /**
     * 提示词快照视图。
     *
     * @param policy     留存策略
     * @param policyNote 策略说明
     * @param prompt     提示词（按策略可能为 null）
     * @param response   响应（按策略可能为 null）
     */
    @Schema(name = "CallLogPrView", description = "提示词快照视图")
    public record PrView(
            String policy,
            String policyNote,
            String prompt,
            String response) {
    }
}
