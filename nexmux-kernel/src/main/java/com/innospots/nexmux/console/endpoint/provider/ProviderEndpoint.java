package com.innospots.nexmux.console.endpoint.provider;

import com.innospots.nexus.base.domain.response.R;
import com.innospots.nexmux.console.provider.ProviderOption;
import com.innospots.nexmux.console.provider.ProviderListResult;
import com.innospots.nexmux.console.provider.ProviderRecord;
import com.innospots.nexmux.console.provider.ProviderViewAssembler;
import com.innospots.nexmux.console.provider.PullModelRow;
import com.innospots.nexmux.core.provider.domain.request.ProviderCreateRequest;
import com.innospots.nexmux.core.provider.domain.request.ProviderPageRequest;
import com.innospots.nexmux.core.provider.domain.request.ProviderUpdateRequest;
import com.innospots.nexmux.core.provider.domain.vo.ConnectionTestVo;
import com.innospots.nexmux.core.provider.domain.vo.ProviderVo;
import com.innospots.nexmux.core.provider.service.ProviderService;
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
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;

@Path("/api/d/providers")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "NexmuxProvider", description = "模型服务提供方")
@NexusAuthenticatedApi
@RequiredArgsConstructor
public class ProviderEndpoint {

    private final ProviderService providerService;
    private final ProviderViewAssembler providerViewAssembler;

    /**
     * 分页列表（UI 契约：{items,total,filters} 直出，不包 R）。
     * UI 快筛 status 跨两维：enabled/disabled → 启停状态；pending/none → 密钥状态。
     */
    @GET
    @Operation(operationId = "nexmuxProviderPage", summary = "分页查询提供方")
    public ProviderListResult page(
            @Parameter(description = "页码，从 1 开始", example = "1")
            @QueryParam("page") @DefaultValue("1") Long page,
            @Parameter(description = "每页条数", example = "20")
            @QueryParam("pageSize") @DefaultValue("20") Long pageSize,
            @Parameter(description = "关键字（名称 / 编码 / baseUrl 模糊）")
            @QueryParam("keyword") String keyword,
            @Parameter(description = "快筛（all / enabled / disabled / pending / none）")
            @QueryParam("status") @DefaultValue("all") String status,
            @Parameter(description = "厂商编码，逗号分隔")
            @QueryParam("types") String types) {
        List<String> typeList = types == null || types.isBlank()
                ? List.of()
                : Arrays.stream(types.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        String backendStatus = switch (status) {
            case "enabled" -> "ENABLED";
            case "disabled" -> "DISABLED";
            default -> null;
        };
        String keyStatus = "pending".equals(status) || "none".equals(status) ? status : null;
        return providerViewAssembler.assemble(
                new ProviderPageRequest(page, pageSize, keyword, backendStatus, keyStatus, typeList));
    }

    /**
     * 按稳定业务键查询详情（UI 契约：展示记录直出，不包 R）。
     */
    @GET
    @Path("/{providerCode}")
    @Operation(operationId = "nexmuxProviderGetByCode", summary = "按编码查询提供方详情")
    public ProviderRecord detail(
            @Parameter(description = "提供方编码", required = true)
            @PathParam("providerCode") String providerCode) {
        return providerViewAssembler.detail(providerCode);
    }

    /**
     * 拉取上游模型清单（UI 契约：行数组直出；code → providerId 由装配层解析）。
     */
    @GET
    @Path("/{providerCode}/pull-models")
    @Operation(operationId = "nexmuxProviderPullModels", summary = "拉取上游模型清单")
    public List<PullModelRow> pullModels(
            @Parameter(description = "提供方编码", required = true)
            @PathParam("providerCode") String providerCode) {
        return providerViewAssembler.pullModels(providerCode);
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
    @Path("/{providerId}/disable")
    @Operation(operationId = "nexmuxProviderDisable", summary = "停用提供方")
    public R<ProviderVo> disable(
            @Parameter(description = "提供方 ID", required = true) @PathParam("providerId") String providerId) {
        return R.ok(providerService.disable(providerId));
    }

    @POST
    @Path("/{providerId}/connection-test")
    @Operation(operationId = "nexmuxProviderConnectionTest", summary = "提供方连通性测试")
    public R<ConnectionTestVo> connectionTest(
            @Parameter(description = "提供方 ID", required = true) @PathParam("providerId") String providerId) {
        return R.ok(providerService.connectionTest(providerId));
    }
}
