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
import jakarta.ws.rs.GET;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;

@Path("/api/nexmux")
@Produces(MediaType.APPLICATION_JSON)
@NexusAuthenticatedApi
@RequiredArgsConstructor
public class OpsEndpoint {

    private final RequestLogQueryService requestLogQueryService;
    private final AuditQueryService auditQueryService;

    @GET
    @Path("/request-logs")
    public R<PageResult<RequestLogVo>> pageRequestLogs(
            @QueryParam("pageNo") @DefaultValue("1") Long pageNo,
            @QueryParam("pageSize") @DefaultValue("20") Long pageSize) {
        return R.ok(requestLogQueryService.page(new RequestLogPageRequest(pageNo, pageSize)));
    }

    @GET
    @Path("/request-logs/{requestId}")
    public R<RequestLogVo> getRequestLog(@PathParam("requestId") String requestId) {
        return R.ok(requestLogQueryService.get(requestId));
    }

    @GET
    @Path("/audit-logs")
    public R<PageResult<AuditLogVo>> pageAuditLogs(
            @QueryParam("pageNo") @DefaultValue("1") Long pageNo,
            @QueryParam("pageSize") @DefaultValue("20") Long pageSize) {
        return R.ok(auditQueryService.page(new AuditLogPageRequest(pageNo, pageSize)));
    }

    @GET
    @Path("/audit-logs/{auditId}")
    public R<AuditLogVo> getAuditLog(@PathParam("auditId") String auditId) {
        return R.ok(auditQueryService.get(auditId));
    }
}
