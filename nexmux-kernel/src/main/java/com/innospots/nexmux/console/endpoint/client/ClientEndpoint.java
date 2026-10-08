package com.innospots.nexmux.console.endpoint.client;

import com.innospots.nexus.base.domain.response.PageResult;
import com.innospots.nexus.base.domain.response.R;
import com.innospots.nexmux.core.access.domain.request.ClientCreateRequest;
import com.innospots.nexmux.core.access.domain.request.ClientPageRequest;
import com.innospots.nexmux.core.access.domain.request.ClientRouteGrantsRequest;
import com.innospots.nexmux.core.access.domain.request.ClientUpdateRequest;
import com.innospots.nexmux.core.access.domain.vo.ClientCreateResultVo;
import com.innospots.nexmux.core.access.domain.vo.ClientDetailVo;
import com.innospots.nexmux.core.access.domain.vo.ClientRotateResultVo;
import com.innospots.nexmux.core.access.domain.vo.ClientVo;
import com.innospots.nexmux.core.access.service.ClientService;
import com.innospots.nexus.core.openapi.NexusAuthenticatedApi;
import jakarta.ws.rs.Consumes;
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

    @GET
    @Operation(operationId = "nexmuxClientPage", summary = "分页查询客户端")
    public R<PageResult<ClientVo>> page(
            @Parameter(description = "页码，从 1 开始", example = "1")
            @QueryParam("pageNo") @DefaultValue("1") Long pageNo,
            @Parameter(description = "每页条数", example = "20")
            @QueryParam("pageSize") @DefaultValue("20") Long pageSize,
            @Parameter(description = "状态（ENABLED / DISABLED）")
            @QueryParam("status") String status,
            @Parameter(description = "名称模糊")
            @QueryParam("name") String name,
            @Parameter(description = "编码模糊")
            @QueryParam("code") String code,
            @Parameter(description = "标签，逗号分隔（取交集）")
            @QueryParam("tags") String tags) {
        List<String> tagList = tags == null || tags.isBlank()
                ? List.of()
                : Arrays.stream(tags.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        return R.ok(clientService.page(
                new ClientPageRequest(pageNo, pageSize, status, name, code, tagList)));
    }

    @GET
    @Path("/by-code/{clientCode}")
    @Operation(operationId = "nexmuxClientGetByCode", summary = "按编码查询客户端详情")
    public R<ClientDetailVo> getByCode(
            @Parameter(description = "客户端编码", required = true) @PathParam("clientCode") String clientCode) {
        return R.ok(clientService.getByCode(clientCode));
    }

    @GET
    @Path("/{clientId}")
    @Operation(operationId = "nexmuxClientGet", summary = "查询客户端详情")
    public R<ClientVo> get(
            @Parameter(description = "客户端 ID", required = true) @PathParam("clientId") String clientId) {
        return R.ok(clientService.get(clientId));
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
}
