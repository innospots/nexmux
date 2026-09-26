package com.innospots.nexmux.console.endpoint.provider;

import com.innospots.nexus.base.domain.response.PageResult;
import com.innospots.nexus.base.domain.response.R;
import com.innospots.nexmux.core.provider.domain.request.ProviderCreateRequest;
import com.innospots.nexmux.core.provider.domain.request.ProviderPageRequest;
import com.innospots.nexmux.core.provider.domain.request.ProviderUpdateRequest;
import com.innospots.nexmux.core.provider.domain.vo.ProviderVo;
import com.innospots.nexmux.core.provider.service.ProviderService;
import com.innospots.nexus.core.openapi.NexusAuthenticatedApi;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;

@Path("/api/nexmux/providers")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@NexusAuthenticatedApi
@RequiredArgsConstructor
public class ProviderEndpoint {

    private final ProviderService providerService;

    @GET
    public R<PageResult<ProviderVo>> page(
            @QueryParam("pageNo") @DefaultValue("1") Long pageNo,
            @QueryParam("pageSize") @DefaultValue("20") Long pageSize) {
        return R.ok(providerService.page(new ProviderPageRequest(pageNo, pageSize)));
    }

    @GET
    @Path("/{providerId}")
    public R<ProviderVo> get(@PathParam("providerId") String providerId) {
        return R.ok(providerService.get(providerId));
    }

    @POST
    public R<ProviderVo> create(ProviderCreateRequest request) {
        return R.ok(providerService.create(request));
    }

    @PUT
    @Path("/{providerId}")
    public R<ProviderVo> update(@PathParam("providerId") String providerId, ProviderUpdateRequest request) {
        return R.ok(providerService.update(providerId, request));
    }

    @POST
    @Path("/{providerId}/enable")
    public R<ProviderVo> enable(@PathParam("providerId") String providerId) {
        return R.ok(providerService.setEnabled(providerId, true));
    }

    @POST
    @Path("/{providerId}/connection-test")
    public R<Boolean> connectionTest(@PathParam("providerId") String providerId) {
        return R.ok(providerService.connectionTest(providerId));
    }
}
