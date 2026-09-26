package com.innospots.nexmux.console.endpoint.route;

import com.innospots.nexus.base.domain.response.PageResult;
import com.innospots.nexus.base.domain.response.R;
import com.innospots.nexmux.core.route.domain.request.RouteCreateRequest;
import com.innospots.nexmux.core.route.domain.request.RoutePageRequest;
import com.innospots.nexmux.core.route.domain.request.RoutePublishRequest;
import com.innospots.nexmux.core.route.domain.request.RouteUpdateRequest;
import com.innospots.nexmux.core.route.domain.vo.RouteVo;
import com.innospots.nexmux.core.route.service.RouteService;
import com.innospots.nexus.core.openapi.NexusAuthenticatedApi;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;

@Path("/api/nexmux/routes")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@NexusAuthenticatedApi
@RequiredArgsConstructor
public class RouteEndpoint {

    private final RouteService routeService;

    @GET
    public R<PageResult<RouteVo>> page(
            @QueryParam("pageNo") @DefaultValue("1") Long pageNo,
            @QueryParam("pageSize") @DefaultValue("20") Long pageSize) {
        return R.ok(routeService.page(new RoutePageRequest(pageNo, pageSize)));
    }

    @GET
    @Path("/{routeId}")
    public R<RouteVo> get(@PathParam("routeId") String routeId) {
        return R.ok(routeService.get(routeId));
    }

    @POST
    public R<RouteVo> create(RouteCreateRequest request) {
        return R.ok(routeService.create(request));
    }

    @PUT
    @Path("/{routeId}")
    public R<RouteVo> update(@PathParam("routeId") String routeId, RouteUpdateRequest request) {
        return R.ok(routeService.updateDraft(routeId, request));
    }

    @POST
    @Path("/{routeId}/publish")
    public R<RouteVo> publish(@PathParam("routeId") String routeId, RoutePublishRequest request) {
        return R.ok(routeService.publish(routeId, request));
    }
}
