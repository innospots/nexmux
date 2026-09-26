package com.innospots.nexmux.console.endpoint.debug;

import com.innospots.nexus.base.domain.response.R;
import com.innospots.nexmux.core.debug.domain.request.ClientInvokeDebugRequest;
import com.innospots.nexmux.core.debug.domain.request.ModelInvokeDebugRequest;
import com.innospots.nexmux.core.debug.domain.vo.DebugInvokeResultVo;
import com.innospots.nexmux.core.debug.service.DebugService;
import com.innospots.nexus.core.openapi.NexusAuthenticatedApi;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;

@Path("/api/nexmux/debug")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@NexusAuthenticatedApi
@RequiredArgsConstructor
public class DebugEndpoint {

    private final DebugService debugService;

    @POST
    @Path("/model-invoke")
    public R<DebugInvokeResultVo> modelInvoke(ModelInvokeDebugRequest request) {
        return R.ok(debugService.modelInvoke(request));
    }

    @POST
    @Path("/client-invoke")
    public R<DebugInvokeResultVo> clientInvoke(ClientInvokeDebugRequest request) {
        return R.ok(debugService.clientInvoke(request));
    }
}
