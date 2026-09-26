package com.innospots.nexmux.console.endpoint.client;

import com.innospots.nexus.base.domain.response.PageResult;
import com.innospots.nexus.base.domain.response.R;
import com.innospots.nexmux.core.access.domain.request.ClientCreateRequest;
import com.innospots.nexmux.core.access.domain.request.ClientPageRequest;
import com.innospots.nexmux.core.access.domain.request.ClientRouteGrantsRequest;
import com.innospots.nexmux.core.access.domain.request.ClientUpdateRequest;
import com.innospots.nexmux.core.access.domain.vo.ClientCreateResultVo;
import com.innospots.nexmux.core.access.domain.vo.ClientRotateResultVo;
import com.innospots.nexmux.core.access.domain.vo.ClientVo;
import com.innospots.nexmux.core.access.service.ClientService;
import com.innospots.nexus.core.openapi.NexusAuthenticatedApi;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;

@Path("/api/nexmux/clients")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@NexusAuthenticatedApi
@RequiredArgsConstructor
public class ClientEndpoint {

    private final ClientService clientService;

    @GET
    public R<PageResult<ClientVo>> page(
            @QueryParam("pageNo") @DefaultValue("1") Long pageNo,
            @QueryParam("pageSize") @DefaultValue("20") Long pageSize) {
        return R.ok(clientService.page(new ClientPageRequest(pageNo, pageSize)));
    }

    @GET
    @Path("/{clientId}")
    public R<ClientVo> get(@PathParam("clientId") String clientId) {
        return R.ok(clientService.get(clientId));
    }

    @POST
    public R<ClientCreateResultVo> create(ClientCreateRequest request) {
        return R.ok(clientService.create(request));
    }

    @PUT
    @Path("/{clientId}")
    public R<ClientVo> update(@PathParam("clientId") String clientId, ClientUpdateRequest request) {
        return R.ok(clientService.update(clientId, request));
    }

    @POST
    @Path("/{clientId}/rotate-app-key")
    public R<ClientRotateResultVo> rotateAppKey(@PathParam("clientId") String clientId) {
        return R.ok(clientService.rotateAppKey(clientId));
    }

    @PUT
    @Path("/{clientId}/route-grants")
    public R<ClientVo> routeGrants(@PathParam("clientId") String clientId, ClientRouteGrantsRequest request) {
        return R.ok(clientService.updateRouteGrants(clientId, request));
    }
}
