package com.innospots.nexmux.console.endpoint.ops;

import com.fasterxml.jackson.databind.JsonNode;
import com.innospots.nexmux.core.provider.domain.vo.ConnectionTestVo;
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
 * 供应商写操作，路径保持 {@code /api/d/ops/provider-*}，授权归属 provider 模块。
 */
@EndpointModule(value = "provider", domain = "nexmux")
@Path("/api/d/ops")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "NexmuxOpsWrite", description = "供应商写操作")
@NexusAuthenticatedApi
@RequiredArgsConstructor
public class OpsProviderWriteEndpoint {

    private final OpsWriteEndpoint writes;

    @POST
    @Path("/provider-enable")
    @EndpointGrant(action = EndpointGrantAction.STATUS, name = "provider.enable", group = "status")
    @Operation(operationId = "nexmuxOpsProviderEnable", summary = "启用提供方")
    public OpsWriteEndpoint.OpsResult providerEnable(@QueryParam("code") String code) {
        return writes.providerEnable(code);
    }

    @POST
    @Path("/provider-disable")
    @EndpointGrant(action = EndpointGrantAction.STATUS, name = "provider.disable", group = "status")
    @Operation(operationId = "nexmuxOpsProviderDisable", summary = "停用提供方")
    public OpsWriteEndpoint.OpsResult providerDisable(@QueryParam("code") String code) {
        return writes.providerDisable(code);
    }

    @POST
    @Path("/provider-delete")
    @EndpointGrant(action = EndpointGrantAction.DELETE, name = "provider.delete", group = "delete")
    @Operation(operationId = "nexmuxOpsProviderDelete", summary = "删除提供方")
    public OpsWriteEndpoint.OpsResult providerDelete(@QueryParam("code") String code) {
        return writes.providerDelete(code);
    }

    @POST
    @Path("/provider-connection-test")
    @EndpointGrant(action = EndpointGrantAction.EXECUTE, name = "provider.connection-test", group = "execute")
    @Operation(operationId = "nexmuxOpsProviderConnectionTest", summary = "提供方连通性测试")
    public ConnectionTestVo providerConnectionTest(@QueryParam("code") String code) {
        return writes.providerConnectionTest(code);
    }

    @POST
    @Path("/provider-save")
    @EndpointGrant(action = EndpointGrantAction.UPDATE, name = "provider.update", group = "update")
    @Operation(operationId = "nexmuxOpsProviderSave", summary = "更新提供方")
    public OpsWriteEndpoint.OpsResult providerSave(@QueryParam("code") String code, JsonNode body) {
        return writes.providerSave(code, body);
    }

    @POST
    @Path("/provider-create")
    @EndpointGrant(action = EndpointGrantAction.CREATE, name = "provider.create", group = "create")
    @Operation(operationId = "nexmuxOpsProviderCreate", summary = "创建提供方")
    public OpsWriteEndpoint.OpsResult providerCreate(JsonNode body) {
        return writes.providerCreate(body);
    }
}
