package com.innospots.nexmux.core.stats.dao;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 统计聚合查询（mux_request_log 单表；@Select 注解，无 XML）。
 * 维度列经 ${dim} 白名单替换（调用方仅传允许列名）；
 * 别名加双引号强制大小写（H2 结果集列名默认归一为大写，与 Map key 对齐）。
 */
@Mapper
public interface StatsQueryDao {

    String AGG_COLUMNS = """
            COUNT(*) AS "requests",
            COALESCE(SUM(input_tokens), 0) AS "inputTokens",
            COALESCE(SUM(output_tokens), 0) AS "outputTokens",
            COALESCE(SUM(input_tokens + output_tokens), 0) AS "tokens",
            COALESCE(SUM(cache_read_tokens), 0) AS "cacheReadTokens",
            COALESCE(SUM(COALESCE(standard_usd, cost_usd)), 0) AS "standardUsd",
            COALESCE(SUM(cost_usd), 0) AS "costUsd",
            COALESCE(SUM(billing_usd), 0) AS "billedUsd",
            COALESCE(SUM(CASE WHEN status = 'SUCCESS' THEN 1 ELSE 0 END), 0) AS "successCount",
            COALESCE(SUM(CASE WHEN status = 'ERROR' THEN 1 ELSE 0 END), 0) AS "errorCount",
            COALESCE(AVG(latency_ms), 0) AS "avgLatencyMs",
            COALESCE(AVG(ttft_ms), 0) AS "avgTtftMs",
            COALESCE(AVG(upstream_latency_ms), 0) AS "avgUpstreamMs",
            COALESCE(SUM(CASE WHEN COALESCE(retries, 0) > 0 THEN 1 ELSE 0 END), 0) AS "failoverCount",
            COALESCE(SUM(COALESCE(retries, 0)), 0) AS "retriesTotal"
            """;

    @Select("SELECT " + AGG_COLUMNS + ", COUNT(DISTINCT client_id) AS \"activeClients\""
            + " FROM mux_request_log WHERE requested_at >= #{start} AND requested_at <= #{end}")
    Map<String, Object> summarize(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Select("SELECT CAST(requested_at AS DATE) AS \"day\", COUNT(*) AS \"requests\","
            + " COALESCE(SUM(input_tokens + output_tokens), 0) AS \"tokens\","
            + " COALESCE(SUM(billing_usd), 0) AS \"costUsd\","
            + " COALESCE(AVG(latency_ms), 0) AS \"avgLatencyMs\""
            + " FROM mux_request_log WHERE requested_at >= #{start} AND requested_at <= #{end}"
            + " GROUP BY CAST(requested_at AS DATE) ORDER BY \"day\"")
    List<Map<String, Object>> trendByDay(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /** 分布统计（dim ∈ model_code / client_id）。 */
    @Select("SELECT ${dim} AS \"dim\", COUNT(*) AS \"requests\" FROM mux_request_log"
            + " WHERE requested_at >= #{start} AND requested_at <= #{end} AND ${dim} IS NOT NULL"
            + " GROUP BY ${dim} ORDER BY \"requests\" DESC")
    List<Map<String, Object>> requestsByDimension(
            @Param("start") LocalDateTime start, @Param("end") LocalDateTime end, @Param("dim") String dim);

    /** 维度用量聚合（dim ∈ model_code / provider_code / client_id 或组合表达式）。 */
    @Select("SELECT ${dim} AS \"dim\", " + AGG_COLUMNS
            + " FROM mux_request_log WHERE requested_at >= #{start} AND requested_at <= #{end}"
            + " AND ${dim} IS NOT NULL GROUP BY ${dim} ORDER BY \"requests\" DESC")
    List<Map<String, Object>> usageByDimension(
            @Param("start") LocalDateTime start, @Param("end") LocalDateTime end, @Param("dim") String dim);

    @Select("SELECT request_id AS \"requestId\","
            + " FORMATDATETIME(requested_at, 'yyyy-MM-dd''T''HH:mm:ss') AS \"requestedAt\","
            + " model_code AS \"modelCode\", provider_code AS \"providerCode\", error_code AS \"errorCode\""
            + " FROM mux_request_log WHERE status = 'ERROR'"
            + " ORDER BY requested_at DESC LIMIT #{limit}")
    List<Map<String, Object>> recentErrors(@Param("limit") int limit);

    /** 实时窗口（最近 N 秒）请求数与 tokens。 */
    @Select("SELECT COUNT(*) AS \"requests\","
            + " COALESCE(SUM(input_tokens + output_tokens), 0) AS \"tokens\""
            + " FROM mux_request_log WHERE requested_at >= DATEADD('SECOND', -#{windowSeconds}, CURRENT_TIMESTAMP)")
    Map<String, Object> realtime(@Param("windowSeconds") int windowSeconds);
}
