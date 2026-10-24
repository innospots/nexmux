package com.innospots.nexmux.console.endpoint.route;

import com.innospots.nexus.base.domain.response.R;
import com.innospots.nexmux.console.route.RouteListResult;
import com.innospots.nexmux.console.route.RouteRecord;
import com.innospots.nexmux.console.route.RouteViewAssembler;
import com.innospots.nexmux.core.route.domain.request.RouteCreateRequest;
import com.innospots.nexmux.core.route.domain.request.RoutePageRequest;
import com.innospots.nexmux.core.route.domain.request.RoutePublishRequest;
import com.innospots.nexmux.core.route.domain.request.RouteUpdateRequest;
import com.innospots.nexmux.core.route.domain.vo.RouteVo;
import com.innospots.nexmux.core.route.domain.vo.RouteVersionVo;
import com.innospots.nexmux.core.route.service.RouteService;
import com.innospots.nexus.core.openapi.NexusAuthenticatedApi;
import jakarta.ws.rs.Consumes;
import java.util.Arrays;
import java.util.List;
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

@Path("/api/d/routes")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "NexmuxRoute", description = "路由与发布")
@NexusAuthenticatedApi
@RequiredArgsConstructor
public class RouteEndpoint {

    private final RouteService routeService;
    private final RouteViewAssembler routeViewAssembler;

    /**
     * 分页列表（UI 契约：{items,total,filters} 直出，不包 R）。
     * UI 快筛 status 映射：enabled→PUBLISHED、disabled→DISABLED、draft→DRAFT。
     */
    @GET
    @Operation(operationId = "nexmuxRoutePage", summary = "分页查询路由")
    public RouteListResult page(
            @Parameter(description = "页码，从 1 开始", example = "1")
            @QueryParam("page") @DefaultValue("1") Long page,
            @Parameter(description = "每页条数", example = "20")
            @QueryParam("pageSize") @DefaultValue("20") Long pageSize,
            @Parameter(description = "快筛（all / enabled / disabled / draft）")
            @QueryParam("status") @DefaultValue("all") String status,
            @Parameter(description = "类型（all / single / pool / conditional）")
            @QueryParam("type") @DefaultValue("all") String type,
            @Parameter(description = "标签，逗号分隔（取交集）")
            @QueryParam("tags") String tags,
            @Parameter(description = "模型编码（UI 形式，~ 即 /；命中任一候选即匹配）")
            @QueryParam("model") String model) {
        List<String> tagList = tags == null || tags.isBlank()
                ? List.of()
                : Arrays.stream(tags.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        String backendStatus = switch (status) {
            case "enabled" -> "PUBLISHED";
            case "disabled" -> "DISABLED";
            case "draft" -> "DRAFT";
            default -> null;
        };
        String backendModel = model == null || model.isBlank() ? null : model.replace("~", "/");
        return routeViewAssembler.assemble(new RoutePageRequest(
                page, pageSize, backendStatus, "all".equals(type) ? null : type, tagList, backendModel));
    }

    /**
     * 按稳定业务键查询详情（UI 契约：展示记录直出，不包 R）。
     */
    @GET
    @Path("/{routeCode}")
    @Operation(operationId = "nexmuxRouteGetByCode", summary = "按编码查询路由详情")
    public RouteRecord detail(
            @Parameter(description = "路由编码", required = true) @PathParam("routeCode") String routeCode) {
        return routeViewAssembler.detail(routeCode);
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

    @GET
    @Path("/{routeCode}/versions")
    @Operation(operationId = "nexmuxRouteVersions", summary = "查询 Route 运行配置版本")
    public R<java.util.List<RouteVersionVo>> versions(@PathParam("routeCode") String routeCode) {
        return R.ok(routeService.versions(routeCode));
    }

    @POST
    @Path("/{routeCode}/rollback")
    @Operation(operationId = "nexmuxRouteRollback", summary = "回滚 Route 到历史运行版本")
    public R<RouteVo> rollback(@PathParam("routeCode") String routeCode,
                               @QueryParam("revision") Long revision,
                               @QueryParam("operatorId") String operatorId) {
        return R.ok(routeService.rollback(routeCode, revision, operatorId));
    }

    @POST
    @Path("/{routeId}/enable")
    @Operation(operationId = "nexmuxRouteEnable", summary = "启用路由（DISABLED → PUBLISHED）")
    public R<RouteVo> enable(
            @Parameter(description = "路由 ID", required = true) @PathParam("routeId") String routeId) {
        return R.ok(routeService.enable(routeId));
    }

    @POST
    @Path("/{routeId}/disable")
    @Operation(operationId = "nexmuxRouteDisable", summary = "停用路由（PUBLISHED → DISABLED）")
    public R<RouteVo> disable(
            @Parameter(description = "路由 ID", required = true) @PathParam("routeId") String routeId) {
        return R.ok(routeService.disable(routeId));
    }
}
