package com.innospots.nexmux.console.endpoint.ops;

import com.innospots.nexus.core.endpoint.grant.EndpointModule;
import com.innospots.nexus.core.endpoint.grant.EndpointGrantAction;
import com.innospots.nexus.core.endpoint.grant.EndpointGrant;
import com.innospots.nexmux.console.ops.CallLogListResult;
import com.innospots.nexmux.console.ops.CallLogRecord;
import com.innospots.nexmux.console.ops.CallLogViewAssembler;
import com.innospots.nexmux.console.ops.CostListResult;
import com.innospots.nexmux.console.ops.CostRecord;
import com.innospots.nexmux.console.ops.CostViewAssembler;
import com.innospots.nexmux.core.ops.domain.request.CostPageRequest;
import com.innospots.nexmux.core.ops.domain.request.RequestLogPageRequest;
import com.innospots.nexus.core.openapi.NexusAuthenticatedApi;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 运行管理数据端点（/api/d 单段 kebab 路径约定；UI 契约形状直出，不包 R）。
 */
@EndpointModule(value = "ops", domain = "nexmux")
@Path("/api/d")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "NexmuxOps", description = "请求与审计日志")
@NexusAuthenticatedApi
@RequiredArgsConstructor
public class OpsEndpoint {

    private final CallLogViewAssembler callLogViewAssembler;
    private final CostViewAssembler costViewAssembler;

    /**
     * 调用日志筛选列表（UI 契约：{items,total,filteredText} 直出）。
     */
    @GET
    @Path("/call-logs")
    @Operation(operationId = "nexmuxCallLogPage", summary = "筛选查询调用日志")
    @EndpointGrant(action = EndpointGrantAction.READ, name = "ops.call-logs", group = "view")
    public CallLogListResult callLogs(
            @QueryParam("start") String start,
            @QueryParam("end") String end,
            @Parameter(description = "状态（all / SUCCESS / ERROR / ...）")
            @QueryParam("status") @DefaultValue("all") String status,
            @QueryParam("model") @DefaultValue("all") String model,
            @QueryParam("provider") @DefaultValue("all") String provider,
            @QueryParam("protocol") @DefaultValue("all") String protocol,
            @QueryParam("routeGroup") @DefaultValue("all") String routeGroup,
            @QueryParam("email") String email,
            @QueryParam("appKey") String appKey) {
        return callLogViewAssembler.assemble(new RequestLogPageRequest(
                null, null, start, end,
                upper(status), nonAll(model), nonAll(provider), nonAll(protocol), nonAll(routeGroup),
                email, appKey));
    }

    /**
     * 调用日志详情（UI 契约：展示记录直出）。
     */
    @GET
    @Path("/call-logs/{requestId}")
    @Operation(operationId = "nexmuxCallLogGet", summary = "查询调用日志详情")
    @EndpointGrant(action = EndpointGrantAction.READ, name = "ops.call-log", group = "view")
    public CallLogRecord callLog(
            @Parameter(description = "请求 ID", required = true) @PathParam("requestId") String requestId) {
        return callLogViewAssembler.detail(requestId);
    }

    /**
     * 模型筛选项（UI 契约：{items:[{label,value}]} 直出；日志中出现过的模型，
     * 供调用日志 / 成本明细页的模型筛选下拉）。
     */
    @GET
    @Path("/request-log-model-options")
    @Operation(operationId = "nexmuxRequestLogModelOptions", summary = "日志模型筛选项")
    @EndpointGrant(action = EndpointGrantAction.READ, name = "ops.model-options", group = "view")
    public ModelOptionsResult requestLogModelOptions() {
        return new ModelOptionsResult(callLogViewAssembler.modelOptions());
    }

    /**
     * 模型筛选项结果（{items} 形状约定）。
     *
     * @param items 选项列表
     */
    @Schema(name = "RequestLogModelOptionsResult", description = "日志模型筛选项结果")
    public record ModelOptionsResult(
            @Schema(description = "选项列表", required = true)
            List<com.innospots.nexmux.console.model.ModelOption> items) {
    }

    /**
     * 成本明细筛选列表（UI 契约：{items,total} 直出）。
     */
    @GET
    @Path("/costs")
    @Operation(operationId = "nexmuxCostPage", summary = "筛选查询成本明细")
    @EndpointGrant(action = EndpointGrantAction.READ, name = "ops.costs", group = "cost")
    public CostListResult costs(
            @QueryParam("application") String application,
            @QueryParam("appKey") String appKey,
            @QueryParam("alias") String alias,
            @QueryParam("model") @DefaultValue("all") String model,
            @QueryParam("provider") @DefaultValue("all") String provider,
            @QueryParam("start") String start,
            @QueryParam("end") String end) {
        return costViewAssembler.assemble(new CostPageRequest(
                null, null, application, appKey, alias, nonAll(model), nonAll(provider), start, end));
    }

    /**
     * 成本明细详情（UI 契约：展示记录直出）。
     */
    @GET
    @Path("/costs/{requestId}")
    @Operation(operationId = "nexmuxCostGet", summary = "查询成本明细详情")
    @EndpointGrant(action = EndpointGrantAction.READ, name = "ops.cost", group = "cost")
    public CostRecord cost(
            @Parameter(description = "请求 ID", required = true) @PathParam("requestId") String requestId) {
        return costViewAssembler.detail(requestId);
    }

    private static String upper(String value) {
        return value == null || value.isBlank() || "all".equals(value) ? null : value.toUpperCase();
    }

    private static String nonAll(String value) {
        return value == null || value.isBlank() || "all".equals(value) ? null : value;
    }
}
