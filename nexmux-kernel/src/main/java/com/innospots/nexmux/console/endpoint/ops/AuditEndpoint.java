package com.innospots.nexmux.console.endpoint.ops;

import com.innospots.nexmux.console.audit.AuditRecord;
import com.innospots.nexmux.console.audit.AuditViewMapper;
import com.innospots.nexmux.core.audit.domain.request.AuditLogPageRequest;
import com.innospots.nexmux.core.audit.service.AuditQueryService;
import com.innospots.nexus.core.endpoint.grant.EndpointGrant;
import com.innospots.nexus.core.endpoint.grant.EndpointGrantAction;
import com.innospots.nexus.core.endpoint.grant.EndpointModule;
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
import lombok.RequiredArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * 配置审计只读接口，授权归属 {@code nexmux.audit:view}。
 */
@EndpointModule(value = "audit", domain = "nexmux")
@Path("/api/d")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "NexmuxOps", description = "配置审计")
@NexusAuthenticatedApi
@RequiredArgsConstructor
public class AuditEndpoint {

    private final AuditQueryService auditQueryService;
    private final AuditViewMapper auditViewMapper;

    /**
     * 审计日志筛选列表。
     */
    @GET
    @Path("/audit-logs")
    @EndpointGrant(action = EndpointGrantAction.READ, name = "audit.page", group = "view")
    @Operation(operationId = "nexmuxAuditLogPage", summary = "筛选查询审计日志")
    public AuditListResult auditLogs(
            @QueryParam("start") String start,
            @QueryParam("end") String end,
            @QueryParam("operator") @DefaultValue("all") String operator,
            @QueryParam("operation") @DefaultValue("all") String operation,
            @QueryParam("resourceType") @DefaultValue("all") String resourceType,
            @QueryParam("resourceId") String resourceId) {
        var page = auditQueryService.page(new AuditLogPageRequest(
                1L, 5000L, start, end,
                "all".equals(operator) ? null : operator,
                upper(operation), upper(resourceType), resourceId));
        List<AuditRecord> items = page.records().stream().map(auditViewMapper::toRecord).toList();
        return new AuditListResult(items,
                com.innospots.nexmux.console.shared.LogTexts.thousands(items.size()));
    }

    /**
     * 审计日志详情。
     */
    @GET
    @Path("/audit-logs/{auditId}")
    @EndpointGrant(action = EndpointGrantAction.READ, name = "audit.get", group = "view")
    @Operation(operationId = "nexmuxAuditLogGet", summary = "查询审计日志详情")
    public AuditRecord auditLog(
            @Parameter(description = "审计日志 ID", required = true) @PathParam("auditId") String auditId) {
        return auditViewMapper.toRecord(auditQueryService.get(auditId));
    }

    /**
     * 审计操作人去重列表。
     */
    @GET
    @Path("/audit-operators")
    @EndpointGrant(action = EndpointGrantAction.READ, name = "audit.operators", group = "view")
    @Operation(operationId = "nexmuxAuditLogOperators", summary = "审计操作人去重列表")
    public AuditOperatorsResult auditOperators() {
        return new AuditOperatorsResult(auditQueryService.operators());
    }

    private static String upper(String value) {
        return value == null || value.isBlank() || "all".equals(value) ? null : value.toUpperCase();
    }

    /**
     * 审计列表结果。
     *
     * @param items        列表行
     * @param filteredText 筛选命中数
     */
    @Schema(name = "AuditListResult", description = "审计列表结果（UI 契约）")
    public record AuditListResult(
            List<AuditRecord> items,
            String filteredText) {
    }

    /**
     * 审计操作人结果。
     *
     * @param items 操作人列表
     */
    @Schema(name = "AuditOperatorsResult", description = "审计操作人结果（UI 契约）")
    public record AuditOperatorsResult(
            List<String> items) {
    }
}
