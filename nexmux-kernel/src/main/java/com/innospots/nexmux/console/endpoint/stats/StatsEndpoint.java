package com.innospots.nexmux.console.endpoint.stats;

import com.innospots.nexmux.console.stats.AvailabilityView;
import com.innospots.nexmux.console.stats.DashboardView;
import com.innospots.nexmux.console.stats.StatsViewAssembler;
import com.innospots.nexmux.console.stats.UsageListResult;
import com.innospots.nexus.core.openapi.NexusAuthenticatedApi;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 统计聚合端点（/api/d 单段 kebab 路径约定；UI 契约形状直出，不包 R）。
 */
@Path("/api/d")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "NexmuxStats", description = "统计聚合")
@NexusAuthenticatedApi
@RequiredArgsConstructor
public class StatsEndpoint {

    private final StatsViewAssembler statsViewAssembler;

    /**
     * 仪表盘聚合（UI 契约直出）。
     */
    @GET
    @Path("/stats-dashboard")
    @Operation(operationId = "nexmuxStatsDashboard", summary = "仪表盘聚合")
    public DashboardView dashboard(
            @Parameter(description = "起始日期（yyyy-MM-dd）") @QueryParam("start") String start,
            @Parameter(description = "截止日期（yyyy-MM-dd）") @QueryParam("end") String end) {
        return statsViewAssembler.dashboard(start, end);
    }

    /**
     * 模型用量聚合（UI 契约：{items,summary,total} 直出）。
     */
    @GET
    @Path("/stats-model-usage")
    @Operation(operationId = "nexmuxStatsModelUsage", summary = "模型用量聚合")
    public UsageListResult<StatsViewAssembler.ModelUsageRow> modelUsage(
            @QueryParam("start") String start, @QueryParam("end") String end) {
        return statsViewAssembler.modelUsage(start, end);
    }

    /**
     * 供应商用量聚合（UI 契约：{items,summary,total} 直出）。
     */
    @GET
    @Path("/stats-provider-usage")
    @Operation(operationId = "nexmuxStatsProviderUsage", summary = "供应商用量聚合")
    public UsageListResult<StatsViewAssembler.ProviderUsageRow> providerUsage(
            @QueryParam("start") String start, @QueryParam("end") String end) {
        return statsViewAssembler.providerUsage(start, end);
    }

    /**
     * 客户端用量聚合（UI 契约：{items,summary,total} 直出）。
     */
    @GET
    @Path("/stats-client-usage")
    @Operation(operationId = "nexmuxStatsClientUsage", summary = "客户端用量聚合")
    public UsageListResult<StatsViewAssembler.ClientUsageRow> clientUsage(
            @QueryParam("start") String start, @QueryParam("end") String end) {
        return statsViewAssembler.clientUsage(start, end);
    }

    /**
     * 供应商可用性聚合（UI 契约：{providers,compare,errors} 直出）。
     */
    @GET
    @Path("/stats-provider-availability")
    @Operation(operationId = "nexmuxStatsProviderAvailability", summary = "供应商可用性聚合")
    public AvailabilityView availability(
            @QueryParam("start") String start, @QueryParam("end") String end) {
        return statsViewAssembler.availability(start, end);
    }
}
