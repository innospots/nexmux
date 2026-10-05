package com.innospots.nexmux.console.endpoint.ops;

import com.innospots.nexus.base.domain.response.PageResult;
import com.innospots.nexus.base.domain.response.R;
import com.innospots.nexmux.core.audit.domain.request.AuditLogPageRequest;
import com.innospots.nexmux.core.audit.domain.vo.AuditLogDetailVo;
import com.innospots.nexmux.core.audit.domain.vo.AuditLogVo;
import com.innospots.nexmux.core.audit.service.AuditQueryService;
import com.innospots.nexmux.core.ops.domain.request.CostPageRequest;
import com.innospots.nexmux.core.ops.domain.request.RequestLogPageRequest;
import com.innospots.nexmux.core.ops.domain.vo.CostRecordVo;
import com.innospots.nexmux.core.ops.domain.vo.RequestLogDetailVo;
import com.innospots.nexmux.core.ops.domain.vo.RequestLogVo;
import com.innospots.nexmux.core.ops.service.CostQueryService;
import com.innospots.nexmux.core.ops.service.RequestLogQueryService;
import com.innospots.nexus.core.openapi.NexusAuthenticatedApi;
import jakarta.ws.rs.DefaultValue;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;

@Path("/api/nexmux")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "NexmuxOps", description = "请求与审计日志")
@NexusAuthenticatedApi
@RequiredArgsConstructor
public class OpsEndpoint {

    private final RequestLogQueryService requestLogQueryService;
    private final AuditQueryService auditQueryService;
    private final CostQueryService costQueryService;

    @GET
    @Path("/request-logs")
    @Operation(operationId = "nexmuxRequestLogPage", summary = "分页查询请求日志")
    public R<PageResult<RequestLogVo>> pageRequestLogs(
            @Parameter(description = "页码，从 1 开始", example = "1")
            @QueryParam("pageNo") @DefaultValue("1") Long pageNo,
            @Parameter(description = "每页条数", example = "20")
            @QueryParam("pageSize") @DefaultValue("20") Long pageSize,
            @QueryParam("start") String start,
            @QueryParam("end") String end,
            @QueryParam("status") String status,
            @QueryParam("model") String model,
            @QueryParam("provider") String provider,
            @QueryParam("protocol") String protocol,
            @QueryParam("routeGroup") String routeGroup,
            @QueryParam("email") String email,
            @QueryParam("appKey") String appKey) {
        return R.ok(requestLogQueryService.page(new RequestLogPageRequest(
                pageNo, pageSize, start, end, status, model, provider, protocol, routeGroup, email, appKey)));
    }

    @GET
    @Path("/request-logs/{requestId}")
    @Operation(operationId = "nexmuxRequestLogGet", summary = "查询请求日志详情")
    public R<RequestLogDetailVo> getRequestLog(
            @Parameter(description = "请求日志 ID", required = true) @PathParam("requestId") String requestId) {
        return R.ok(requestLogQueryService.get(requestId));
    }

    @GET
    @Path("/audit-logs")
    @Operation(operationId = "nexmuxAuditLogPage", summary = "分页查询审计日志")
    public R<PageResult<AuditLogVo>> pageAuditLogs(
            @Parameter(description = "页码，从 1 开始", example = "1")
            @QueryParam("pageNo") @DefaultValue("1") Long pageNo,
            @Parameter(description = "每页条数", example = "20")
            @QueryParam("pageSize") @DefaultValue("20") Long pageSize,
            @QueryParam("start") String start,
            @QueryParam("end") String end,
            @QueryParam("operator") String operator,
            @QueryParam("operation") String operation,
            @QueryParam("resourceType") String resourceType,
            @QueryParam("resourceId") String resourceId) {
        return R.ok(auditQueryService.page(new AuditLogPageRequest(
                pageNo, pageSize, start, end, operator, operation, resourceType, resourceId)));
    }

    @GET
    @Path("/costs")
    @Operation(operationId = "nexmuxCostPage", summary = "分页查询成本明细")
    public R<PageResult<CostRecordVo>> pageCosts(
            @Parameter(description = "页码，从 1 开始", example = "1")
            @QueryParam("pageNo") @DefaultValue("1") Long pageNo,
            @Parameter(description = "每页条数", example = "20")
            @QueryParam("pageSize") @DefaultValue("20") Long pageSize,
            @QueryParam("application") String application,
            @QueryParam("appKey") String appKey,
            @QueryParam("alias") String alias,
            @QueryParam("model") String model,
            @QueryParam("provider") String provider,
            @QueryParam("start") String start,
            @QueryParam("end") String end) {
        return R.ok(costQueryService.page(new CostPageRequest(
                pageNo, pageSize, application, appKey, alias, model, provider, start, end)));
    }

    @GET
    @Path("/costs/{requestId}")
    @Operation(operationId = "nexmuxCostGet", summary = "查询成本明细详情")
    public R<CostRecordVo> getCost(
            @Parameter(description = "请求 ID", required = true) @PathParam("requestId") String requestId) {
        return R.ok(costQueryService.get(requestId));
    }

    @GET
    @Path("/audit-logs/operators")
    @Operation(operationId = "nexmuxAuditLogOperators", summary = "审计操作人去重列表")
    public R<List<String>> auditOperators() {
        return R.ok(auditQueryService.operators());
    }

    @GET
    @Path("/audit-logs/{auditId}")
    @Operation(operationId = "nexmuxAuditLogGet", summary = "查询审计日志详情")
    public R<AuditLogDetailVo> getAuditLog(
            @Parameter(description = "审计日志 ID", required = true) @PathParam("auditId") String auditId) {
        return R.ok(auditQueryService.get(auditId));
    }
}
