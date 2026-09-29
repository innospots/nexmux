package com.innospots.nexmux.console.endpoint.ops;

import com.innospots.nexus.base.domain.response.PageResult;
import com.innospots.nexus.base.domain.response.R;
import com.innospots.nexmux.core.audit.domain.request.AuditLogPageRequest;
import com.innospots.nexmux.core.audit.domain.vo.AuditLogVo;
import com.innospots.nexmux.core.audit.service.AuditQueryService;
import com.innospots.nexmux.core.ops.domain.request.RequestLogPageRequest;
import com.innospots.nexmux.core.ops.domain.vo.RequestLogVo;
import com.innospots.nexmux.core.ops.service.RequestLogQueryService;
import com.innospots.nexus.core.openapi.NexusAuthenticatedApi;
import jakarta.ws.rs.DefaultValue;
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

    @GET
    @Path("/request-logs")
    @Operation(operationId = "nexmuxRequestLogPage", summary = "分页查询请求日志")
    public R<PageResult<RequestLogVo>> pageRequestLogs(
            @Parameter(description = "页码，从 1 开始", example = "1")
            @QueryParam("pageNo") @DefaultValue("1") Long pageNo,
            @Parameter(description = "每页条数", example = "20")
            @QueryParam("pageSize") @DefaultValue("20") Long pageSize) {
        return R.ok(requestLogQueryService.page(new RequestLogPageRequest(pageNo, pageSize)));
    }

    @GET
    @Path("/request-logs/{requestId}")
    @Operation(operationId = "nexmuxRequestLogGet", summary = "查询请求日志详情")
    public R<RequestLogVo> getRequestLog(
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
            @QueryParam("pageSize") @DefaultValue("20") Long pageSize) {
        return R.ok(auditQueryService.page(new AuditLogPageRequest(pageNo, pageSize)));
    }

    @GET
    @Path("/audit-logs/{auditId}")
    @Operation(operationId = "nexmuxAuditLogGet", summary = "查询审计日志详情")
    public R<AuditLogVo> getAuditLog(
            @Parameter(description = "审计日志 ID", required = true) @PathParam("auditId") String auditId) {
        return R.ok(auditQueryService.get(auditId));
    }
}
