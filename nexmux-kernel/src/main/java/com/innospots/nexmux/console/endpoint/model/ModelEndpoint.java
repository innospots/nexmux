package com.innospots.nexmux.console.endpoint.model;

import com.innospots.nexus.base.domain.response.R;
import com.innospots.nexmux.console.model.ModelListResult;
import com.innospots.nexmux.console.model.ModelRecord;
import com.innospots.nexmux.console.model.ModelViewAssembler;
import com.innospots.nexmux.core.model.domain.request.ModelCreateRequest;
import com.innospots.nexmux.core.model.domain.request.ModelPageRequest;
import com.innospots.nexmux.core.model.domain.request.ModelUpdateRequest;
import com.innospots.nexmux.core.model.domain.vo.ModelVo;
import com.innospots.nexmux.core.model.service.ModelService;
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

@Path("/api/d/models")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "NexmuxModel", description = "模型与调用配置")
@NexusAuthenticatedApi
@RequiredArgsConstructor
public class ModelEndpoint {

    private final ModelService modelService;
    private final ModelViewAssembler modelViewAssembler;

    /**
     * 分页列表（UI 契约：{items,total,filters} 直出，不包 R）。
     */
    @GET
    @Operation(operationId = "nexmuxModelPage", summary = "分页查询模型")
    public ModelListResult page(
            @Parameter(description = "页码，从 1 开始", example = "1")
            @QueryParam("page") @DefaultValue("1") Long page,
            @Parameter(description = "每页条数", example = "20")
            @QueryParam("pageSize") @DefaultValue("20") Long pageSize,
            @Parameter(description = "模型类型（all / LLM / Embedding / Image / Audio）")
            @QueryParam("type") @DefaultValue("all") String type,
            @Parameter(description = "提供方编码，逗号分隔")
            @QueryParam("providers") String providers) {
        List<String> providerList = providers == null || providers.isBlank()
                ? List.of()
                : Arrays.stream(providers.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        return modelViewAssembler.assemble(new ModelPageRequest(
                page, pageSize, "all".equals(type) ? null : type, providerList));
    }

    /**
     * 按稳定业务键查询详情（UI 契约：展示记录直出，不包 R；~ 还原为 /）。
     */
    @GET
    @Path("/{modelCode}")
    @Operation(operationId = "nexmuxModelGetByCode", summary = "按编码查询模型详情")
    public ModelRecord detail(
            @Parameter(description = "模型编码（/ 以 ~ 替代）", required = true)
            @PathParam("modelCode") String modelCode) {
        return modelViewAssembler.detail(modelCode);
    }

    @POST
    @Operation(operationId = "nexmuxModelCreate", summary = "创建模型")
    public R<ModelVo> create(@RequestBody(description = "创建模型", required = true) ModelCreateRequest request) {
        return R.ok(modelService.create(request));
    }

    @POST
    @Path("/{modelId}/enable")
    @Operation(operationId = "nexmuxModelEnable", summary = "启用模型")
    public R<ModelVo> enable(
            @Parameter(description = "模型 ID", required = true) @PathParam("modelId") String modelId) {
        return R.ok(modelService.setEnabled(modelId, true));
    }

    @POST
    @Path("/{modelId}/disable")
    @Operation(operationId = "nexmuxModelDisable", summary = "停用模型")
    public R<ModelVo> disable(
            @Parameter(description = "模型 ID", required = true) @PathParam("modelId") String modelId) {
        return R.ok(modelService.disable(modelId));
    }

    @PUT
    @Path("/{modelId}")
    @Operation(operationId = "nexmuxModelUpdate", summary = "更新模型")
    public R<ModelVo> update(
            @Parameter(description = "模型 ID", required = true) @PathParam("modelId") String modelId,
            @RequestBody(description = "更新模型", required = true) ModelUpdateRequest request) {
        return R.ok(modelService.update(modelId, request));
    }

    @POST
    @Path("/{modelId}/invoke-test")
    @Operation(operationId = "nexmuxModelInvokeTest", summary = "模型调用测试")
    public R<Boolean> invokeTest(
            @Parameter(description = "模型 ID", required = true) @PathParam("modelId") String modelId) {
        return R.ok(modelService.invokeTest(modelId));
    }
}
