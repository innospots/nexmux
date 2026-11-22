package com.innospots.nexmux.console.endpoint.ops;

import com.fasterxml.jackson.databind.JsonNode;
import com.innospots.nexus.core.endpoint.grant.EndpointGrant;
import com.innospots.nexus.core.endpoint.grant.EndpointGrantAction;
import com.innospots.nexus.core.endpoint.grant.EndpointModule;
import com.innospots.nexus.core.openapi.NexusAuthenticatedApi;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * 路由写操作，路径保持 {@code /api/d/ops/route-*}，授权归属 route 模块。
 */
@EndpointModule(value = "route", domain = "nexmux")
@Path("/api/d/ops")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "NexmuxOpsWrite", description = "路由写操作")
@NexusAuthenticatedApi
@RequiredArgsConstructor
public class OpsRouteWriteEndpoint {

    private final OpsWriteEndpoint writes;

    @POST
    @Path("/route-enable")
    @EndpointGrant(action = EndpointGrantAction.STATUS, name = "route.enable", group = "status")
    @Operation(operationId = "nexmuxOpsRouteEnable", summary = "启用路由")
    public OpsWriteEndpoint.OpsResult routeEnable(@QueryParam("code") String code) {
        return writes.routeEnable(code);
    }

    @POST
    @Path("/route-disable")
    @EndpointGrant(action = EndpointGrantAction.STATUS, name = "route.disable", group = "status")
    @Operation(operationId = "nexmuxOpsRouteDisable", summary = "停用路由")
    public OpsWriteEndpoint.OpsResult routeDisable(@QueryParam("code") String code) {
        return writes.routeDisable(code);
    }

    @POST
    @Path("/route-publish")
    @EndpointGrant(action = EndpointGrantAction.EXECUTE, name = "route.publish", group = "execute")
    @Operation(operationId = "nexmuxOpsRoutePublish", summary = "发布路由")
    public OpsWriteEndpoint.OpsResult routePublish(@QueryParam("code") String code) {
        return writes.routePublish(code);
    }

    @POST
    @Path("/route-delete")
    @EndpointGrant(action = EndpointGrantAction.DELETE, name = "route.delete", group = "delete")
    @Operation(operationId = "nexmuxOpsRouteDelete", summary = "删除路由")
    public OpsWriteEndpoint.OpsResult routeDelete(@QueryParam("code") String code) {
        return writes.routeDelete(code);
    }

    @POST
    @Path("/route-create")
    @EndpointGrant(action = EndpointGrantAction.CREATE, name = "route.create", group = "create")
    @Operation(operationId = "nexmuxOpsRouteCreate", summary = "创建路由")
    public OpsWriteEndpoint.OpsResult routeCreate(JsonNode body) {
        return writes.routeCreate(body);
    }

    @POST
    @Path("/route-save")
    @EndpointGrant(action = EndpointGrantAction.UPDATE, name = "route.update", group = "update")
    @Operation(operationId = "nexmuxOpsRouteSave", summary = "更新路由")
    public OpsWriteEndpoint.OpsResult routeSave(@QueryParam("code") String code, JsonNode body) {
        return writes.routeSave(code, body);
    }
}
