package com.innospots.nexmux.console.endpoint.client;

import com.innospots.nexus.base.domain.response.R;
import com.innospots.nexmux.console.access.ClientListResult;
import com.innospots.nexmux.console.access.ClientRecord;
import com.innospots.nexmux.console.access.ClientViewAssembler;
import com.innospots.nexmux.core.access.domain.request.ClientCreateRequest;
import com.innospots.nexmux.core.access.domain.request.ClientPageRequest;
import com.innospots.nexmux.core.access.domain.request.ClientRouteGrantsRequest;
import com.innospots.nexmux.core.access.domain.request.ClientUpdateRequest;
import com.innospots.nexmux.core.access.domain.vo.ClientCreateResultVo;
import com.innospots.nexmux.core.access.domain.vo.ClientRotateResultVo;
import com.innospots.nexmux.core.access.domain.vo.ClientVo;
import com.innospots.nexmux.core.access.domain.vo.ClientPolicyRevisionVo;
import com.innospots.nexmux.core.access.service.ClientService;
import com.innospots.nexus.core.openapi.NexusAuthenticatedApi;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import java.util.Arrays;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
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

@Path("/api/d/clients")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "NexmuxClient", description = "接入客户端与授权")
@NexusAuthenticatedApi
@RequiredArgsConstructor
public class ClientEndpoint {

    private final ClientService clientService;
    private final ClientViewAssembler clientViewAssembler;

    /**
     * 分页列表（UI 契约：{items,total,filters} 直出，不包 R）。
     * expired 为展示态：enabled/expired 快筛映射 ENABLED 后由装配层本地过滤。
     */
    @GET
    @Operation(operationId = "nexmuxClientPage", summary = "分页查询客户端")
    public ClientListResult page(
            @Parameter(description = "页码，从 1 开始", example = "1")
            @QueryParam("page") @DefaultValue("1") Long page,
            @Parameter(description = "每页条数", example = "20")
            @QueryParam("pageSize") @DefaultValue("20") Long pageSize,
            @Parameter(description = "快筛（all / enabled / disabled / expired）")
            @QueryParam("status") @DefaultValue("all") String status,
            @Parameter(description = "名称模糊")
            @QueryParam("name") String name,
            @Parameter(description = "编码模糊")
            @QueryParam("code") String code,
            @Parameter(description = "标签，逗号分隔（取交集）")
            @QueryParam("tags") String tags) {
        List<String> tagList = tags == null || tags.isBlank()
                ? List.of()
                : Arrays.stream(tags.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        String backendStatus = switch (status) {
            case "enabled", "expired" -> "ENABLED";
            case "disabled" -> "DISABLED";
            default -> null;
        };
        return clientViewAssembler.assemble(
                new ClientPageRequest(page, pageSize, backendStatus, name, code, tagList), status);
    }

    /**
     * 按稳定业务键查询详情（UI 契约：展示记录直出，不包 R）。
     */
    @GET
    @Path("/{clientCode}")
    @Operation(operationId = "nexmuxClientGetByCode", summary = "按编码查询客户端详情")
    public ClientRecord detail(
            @Parameter(description = "客户端编码", required = true) @PathParam("clientCode") String clientCode) {
        return clientViewAssembler.detail(clientCode);
    }

    @POST
    @Operation(operationId = "nexmuxClientCreate", summary = "创建客户端")
    public R<ClientCreateResultVo> create(
            @RequestBody(description = "创建客户端", required = true) ClientCreateRequest request) {
        return R.ok(clientService.create(request));
    }

    @PUT
    @Path("/{clientId}")
    @Operation(operationId = "nexmuxClientUpdate", summary = "更新客户端")
    public R<ClientVo> update(
            @Parameter(description = "客户端 ID", required = true) @PathParam("clientId") String clientId,
            @RequestBody(description = "更新客户端", required = true) ClientUpdateRequest request) {
        return R.ok(clientService.update(clientId, request));
    }

    @POST
    @Path("/{clientId}/rotate-app-key")
    @Operation(operationId = "nexmuxClientRotateAppKey", summary = "轮换客户端 AppKey")
    public R<ClientRotateResultVo> rotateAppKey(
            @Parameter(description = "客户端 ID", required = true) @PathParam("clientId") String clientId) {
        return R.ok(clientService.rotateAppKey(clientId));
    }

    @PUT
    @Path("/{clientId}/route-grants")
    @Operation(operationId = "nexmuxClientRouteGrants", summary = "更新客户端路由授权")
    public R<ClientVo> routeGrants(
            @Parameter(description = "客户端 ID", required = true) @PathParam("clientId") String clientId,
            @RequestBody(description = "路由授权", required = true) ClientRouteGrantsRequest request) {
        return R.ok(clientService.updateRouteGrants(clientId, request));
    }

    @POST
    @Path("/{clientId}/enable")
    @Operation(operationId = "nexmuxClientEnable", summary = "启用客户端")
    public R<ClientVo> enable(
            @Parameter(description = "客户端 ID", required = true) @PathParam("clientId") String clientId) {
        return R.ok(clientService.setEnabled(clientId, true));
    }

    @POST
    @Path("/{clientId}/disable")
    @Operation(operationId = "nexmuxClientDisable", summary = "停用客户端")
    public R<ClientVo> disable(
            @Parameter(description = "客户端 ID", required = true) @PathParam("clientId") String clientId) {
        return R.ok(clientService.disable(clientId));
    }

    @POST
    @Path("/{clientId}/publish")
    @Operation(operationId = "nexmuxClientPublish", summary = "查询当前 Client 策略发布版本")
    public R<ClientPolicyRevisionVo> publish(@PathParam("clientId") String clientId) {
        return R.ok(clientService.publish(clientId));
    }

    @DELETE
    @Path("/{clientId}")
    @Operation(operationId = "nexmuxClientDelete", summary = "删除客户端")
    public R<Void> delete(
            @Parameter(description = "客户端 ID", required = true) @PathParam("clientId") String clientId) {
        clientService.delete(clientId);
        return R.ok();
    }
}
