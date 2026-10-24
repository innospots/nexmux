package com.innospots.nexmux.console.endpoint.debug;

import com.innospots.nexus.base.domain.response.R;
import com.innospots.nexmux.core.debug.domain.request.ClientInvokeDebugRequest;
import com.innospots.nexmux.core.debug.domain.request.ModelInvokeDebugRequest;
import com.innospots.nexmux.core.debug.domain.vo.DebugInvokeResultVo;
import com.innospots.nexmux.core.debug.service.DebugService;
import com.innospots.nexus.core.openapi.NexusAuthenticatedApi;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.core.StreamingOutput;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;

@Path("/api/d/debug")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "NexmuxDebug", description = "调试调用")
@NexusAuthenticatedApi
@RequiredArgsConstructor
public class DebugEndpoint {

    private final DebugService debugService;

    @POST
    @Path("/model-invoke")
    @Operation(operationId = "nexmuxDebugModelInvoke", summary = "调试模型调用")
    public R<DebugInvokeResultVo> modelInvoke(
            @RequestBody(description = "模型调试请求", required = true) ModelInvokeDebugRequest request) {
        return R.ok(debugService.modelInvoke(request));
    }

    @POST
    @Path("/client-invoke")
    @Operation(operationId = "nexmuxDebugClientInvoke", summary = "调试客户端调用")
    public R<DebugInvokeResultVo> clientInvoke(
            @RequestBody(description = "客户端调试请求", required = true) ClientInvokeDebugRequest request) {
        return R.ok(debugService.clientInvoke(request));
    }

    @POST
    @Path("/model-invoke/stream")
    @Produces("text/event-stream")
    @Operation(operationId = "nexmuxDebugModelInvokeStream", summary = "调试模型调用（SSE 流式）")
    public StreamingOutput modelInvokeStream(
            @RequestBody(description = "模型调试请求", required = true) ModelInvokeDebugRequest request) {
        return debugService.modelInvokeStream(request);
    }

    @POST
    @Path("/client-invoke/stream")
    @Produces("text/event-stream")
    @Operation(operationId = "nexmuxDebugClientInvokeStream", summary = "调试客户端调用（SSE 流式）")
    public StreamingOutput clientInvokeStream(
            @RequestBody(description = "客户端调试请求", required = true) ClientInvokeDebugRequest request) {
        return debugService.clientInvokeStream(request);
    }
}
