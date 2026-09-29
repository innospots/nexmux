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
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
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
@Tag(name = "NexmuxProvider", description = "模型服务提供方")
@NexusAuthenticatedApi
@RequiredArgsConstructor
public class ProviderEndpoint {

    private final ProviderService providerService;

    @GET
    @Operation(operationId = "nexmuxProviderPage", summary = "分页查询提供方")
    public R<PageResult<ProviderVo>> page(
            @Parameter(description = "页码，从 1 开始", example = "1")
            @QueryParam("pageNo") @DefaultValue("1") Long pageNo,
            @Parameter(description = "每页条数", example = "20")
            @QueryParam("pageSize") @DefaultValue("20") Long pageSize) {
        return R.ok(providerService.page(new ProviderPageRequest(pageNo, pageSize)));
    }

    @GET
    @Path("/{providerId}")
    @Operation(operationId = "nexmuxProviderGet", summary = "查询提供方详情")
    public R<ProviderVo> get(
            @Parameter(description = "提供方 ID", required = true) @PathParam("providerId") String providerId) {
        return R.ok(providerService.get(providerId));
    }

    @POST
    @Operation(operationId = "nexmuxProviderCreate", summary = "创建提供方")
    public R<ProviderVo> create(@RequestBody(description = "创建提供方", required = true) ProviderCreateRequest request) {
        return R.ok(providerService.create(request));
    }

    @PUT
    @Path("/{providerId}")
    @Operation(operationId = "nexmuxProviderUpdate", summary = "更新提供方")
    public R<ProviderVo> update(
            @Parameter(description = "提供方 ID", required = true) @PathParam("providerId") String providerId,
            @RequestBody(description = "更新提供方", required = true) ProviderUpdateRequest request) {
        return R.ok(providerService.update(providerId, request));
    }

    @POST
    @Path("/{providerId}/enable")
    @Operation(operationId = "nexmuxProviderEnable", summary = "启用提供方")
    public R<ProviderVo> enable(
            @Parameter(description = "提供方 ID", required = true) @PathParam("providerId") String providerId) {
        return R.ok(providerService.setEnabled(providerId, true));
    }

    @POST
    @Path("/{providerId}/connection-test")
    @Operation(operationId = "nexmuxProviderConnectionTest", summary = "提供方连通性测试")
    public R<Boolean> connectionTest(
            @Parameter(description = "提供方 ID", required = true) @PathParam("providerId") String providerId) {
        return R.ok(providerService.connectionTest(providerId));
    }
}
