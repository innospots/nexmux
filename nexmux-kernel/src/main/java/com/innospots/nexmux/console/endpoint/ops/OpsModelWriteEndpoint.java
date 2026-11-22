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
 * 模型写操作，路径保持 {@code /api/d/ops/model-*}，授权归属 model 模块。
 */
@EndpointModule(value = "model", domain = "nexmux")
@Path("/api/d/ops")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "NexmuxOpsWrite", description = "模型写操作")
@NexusAuthenticatedApi
@RequiredArgsConstructor
public class OpsModelWriteEndpoint {

    private final OpsWriteEndpoint writes;

    @POST
    @Path("/model-enable")
    @EndpointGrant(action = EndpointGrantAction.STATUS, name = "model.enable", group = "status")
    @Operation(operationId = "nexmuxOpsModelEnable", summary = "启用模型")
    public OpsWriteEndpoint.OpsResult modelEnable(@QueryParam("code") String code) {
        return writes.modelEnable(code);
    }

    @POST
    @Path("/model-disable")
    @EndpointGrant(action = EndpointGrantAction.STATUS, name = "model.disable", group = "status")
    @Operation(operationId = "nexmuxOpsModelDisable", summary = "停用模型")
    public OpsWriteEndpoint.OpsResult modelDisable(@QueryParam("code") String code) {
        return writes.modelDisable(code);
    }

    @POST
    @Path("/model-invoke-test")
    @EndpointGrant(action = EndpointGrantAction.EXECUTE, name = "model.invoke-test", group = "execute")
    @Operation(operationId = "nexmuxOpsModelInvokeTest", summary = "模型调用测试")
    public OpsWriteEndpoint.OpsResult modelInvokeTest(@QueryParam("code") String code) {
        return writes.modelInvokeTest(code);
    }

    @POST
    @Path("/model-save")
    @EndpointGrant(action = EndpointGrantAction.UPDATE, name = "model.update", group = "update")
    @Operation(operationId = "nexmuxOpsModelSave", summary = "更新模型")
    public OpsWriteEndpoint.OpsResult modelSave(@QueryParam("code") String code, JsonNode body) {
        return writes.modelSave(code, body);
    }

    @POST
    @Path("/model-create")
    @EndpointGrant(action = EndpointGrantAction.CREATE, name = "model.create", group = "create")
    @Operation(operationId = "nexmuxOpsModelCreate", summary = "创建模型")
    public OpsWriteEndpoint.OpsResult modelCreate(JsonNode body) {
        return writes.modelCreate(body);
    }
}
