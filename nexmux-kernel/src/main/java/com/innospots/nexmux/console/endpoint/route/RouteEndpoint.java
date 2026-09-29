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
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
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
@Tag(name = "NexmuxRoute", description = "路由与发布")
@NexusAuthenticatedApi
@RequiredArgsConstructor
public class RouteEndpoint {

    private final RouteService routeService;

    @GET
    @Operation(operationId = "nexmuxRoutePage", summary = "分页查询路由")
    public R<PageResult<RouteVo>> page(
            @Parameter(description = "页码，从 1 开始", example = "1")
            @QueryParam("pageNo") @DefaultValue("1") Long pageNo,
            @Parameter(description = "每页条数", example = "20")
            @QueryParam("pageSize") @DefaultValue("20") Long pageSize) {
        return R.ok(routeService.page(new RoutePageRequest(pageNo, pageSize)));
    }

    @GET
    @Path("/{routeId}")
    @Operation(operationId = "nexmuxRouteGet", summary = "查询路由详情")
    public R<RouteVo> get(
            @Parameter(description = "路由 ID", required = true) @PathParam("routeId") String routeId) {
        return R.ok(routeService.get(routeId));
    }

    @POST
    @Operation(operationId = "nexmuxRouteCreate", summary = "创建路由")
    public R<RouteVo> create(@RequestBody(description = "创建路由", required = true) RouteCreateRequest request) {
        return R.ok(routeService.create(request));
    }

    @PUT
    @Path("/{routeId}")
    @Operation(operationId = "nexmuxRouteUpdate", summary = "更新路由草稿")
    public R<RouteVo> update(
            @Parameter(description = "路由 ID", required = true) @PathParam("routeId") String routeId,
            @RequestBody(description = "更新路由草稿", required = true) RouteUpdateRequest request) {
        return R.ok(routeService.updateDraft(routeId, request));
    }

    @POST
    @Path("/{routeId}/publish")
    @Operation(operationId = "nexmuxRoutePublish", summary = "发布路由")
    public R<RouteVo> publish(
            @Parameter(description = "路由 ID", required = true) @PathParam("routeId") String routeId,
            @RequestBody(description = "发布路由", required = true) RoutePublishRequest request) {
        return R.ok(routeService.publish(routeId, request));
    }
}
