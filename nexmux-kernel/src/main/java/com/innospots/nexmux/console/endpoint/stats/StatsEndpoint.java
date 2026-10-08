package com.innospots.nexmux.console.endpoint.stats;

import com.innospots.nexus.base.domain.response.R;
import com.innospots.nexmux.core.stats.domain.vo.AvailabilityVo;
import com.innospots.nexmux.core.stats.domain.vo.DashboardVo;
import com.innospots.nexmux.core.stats.domain.vo.UsageRowVo;
import com.innospots.nexmux.core.stats.service.AvailabilityService;
import com.innospots.nexmux.core.stats.service.DashboardService;
import com.innospots.nexmux.core.stats.service.UsageStatsService;
import com.innospots.nexus.core.openapi.NexusAuthenticatedApi;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Path("/api/d/stats")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "NexmuxStats", description = "统计聚合")
@NexusAuthenticatedApi
@RequiredArgsConstructor
public class StatsEndpoint {

    private final DashboardService dashboardService;
    private final UsageStatsService usageStatsService;
    private final AvailabilityService availabilityService;

    @GET
    @Path("/dashboard")
    @Operation(operationId = "nexmuxStatsDashboard", summary = "仪表盘聚合")
    public R<DashboardVo> dashboard(
            @Parameter(description = "起始日期（yyyy-MM-dd）") @QueryParam("start") String start,
            @Parameter(description = "截止日期（yyyy-MM-dd）") @QueryParam("end") String end) {
        return R.ok(dashboardService.dashboard(rangeStart(start), rangeEnd(end)));
    }

    @GET
    @Path("/model-usage")
    @Operation(operationId = "nexmuxStatsModelUsage", summary = "模型用量聚合")
    public R<List<UsageRowVo>> modelUsage(
            @QueryParam("start") String start, @QueryParam("end") String end) {
        return R.ok(usageStatsService.usageByModel(rangeStart(start), rangeEnd(end)));
    }

    @GET
    @Path("/provider-usage")
    @Operation(operationId = "nexmuxStatsProviderUsage", summary = "供应商用量聚合")
    public R<List<UsageRowVo>> providerUsage(
            @QueryParam("start") String start, @QueryParam("end") String end) {
        return R.ok(usageStatsService.usageByProvider(rangeStart(start), rangeEnd(end)));
    }

    @GET
    @Path("/client-usage")
    @Operation(operationId = "nexmuxStatsClientUsage", summary = "客户端用量聚合")
    public R<List<UsageRowVo>> clientUsage(
            @QueryParam("start") String start, @QueryParam("end") String end) {
        return R.ok(usageStatsService.usageByClient(rangeStart(start), rangeEnd(end)));
    }

    @GET
    @Path("/provider-availability")
    @Operation(operationId = "nexmuxStatsProviderAvailability", summary = "供应商可用性聚合")
    public R<AvailabilityVo> availability(
            @QueryParam("start") String start, @QueryParam("end") String end) {
        return R.ok(availabilityService.availability(rangeStart(start), rangeEnd(end)));
    }

    private static LocalDateTime rangeStart(String start) {
        if (start == null || start.isBlank()) {
            return LocalDate.now().minusDays(6).atStartOfDay();
        }
        return LocalDate.parse(start.trim()).atStartOfDay();
    }

    private static LocalDateTime rangeEnd(String end) {
        if (end == null || end.isBlank()) {
            return LocalDate.now().atTime(LocalTime.MAX);
        }
        return LocalDate.parse(end.trim()).atTime(LocalTime.MAX);
    }
}
