package com.innospots.nexmux.console.endpoint.model;

import com.innospots.nexus.base.domain.response.PageResult;
import com.innospots.nexus.base.domain.response.R;
import com.innospots.nexmux.core.model.domain.request.ModelCreateRequest;
import com.innospots.nexmux.core.model.domain.request.ModelPageRequest;
import com.innospots.nexmux.core.model.domain.request.ModelUpdateRequest;
import com.innospots.nexmux.core.model.domain.vo.ModelDetailVo;
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

    @GET
    @Operation(operationId = "nexmuxModelPage", summary = "分页查询模型")
    public R<PageResult<ModelVo>> page(
            @Parameter(description = "页码，从 1 开始", example = "1")
            @QueryParam("pageNo") @DefaultValue("1") Long pageNo,
            @Parameter(description = "每页条数", example = "20")
            @QueryParam("pageSize") @DefaultValue("20") Long pageSize,
            @Parameter(description = "模型类型（LLM / Embedding / Image / Audio）")
            @QueryParam("type") String type,
            @Parameter(description = "厂商编码，逗号分隔")
            @QueryParam("providers") String providers) {
        List<String> providerList = providers == null || providers.isBlank()
                ? List.of()
                : Arrays.stream(providers.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        return R.ok(modelService.page(new ModelPageRequest(pageNo, pageSize, type, providerList)));
    }

    @GET
    @Path("/by-code/{modelCode: .+}")
    @Operation(operationId = "nexmuxModelGetByCode", summary = "按编码查询模型详情")
    public R<ModelDetailVo> getByCode(
            @Parameter(description = "模型编码（可含 /，如 openai/o4-mini）", required = true)
            @PathParam("modelCode") String modelCode) {
        return R.ok(modelService.getByCode(modelCode));
    }

    @GET
    @Path("/{modelId}")
    @Operation(operationId = "nexmuxModelGet", summary = "查询模型详情")
    public R<ModelVo> get(
            @Parameter(description = "模型 ID", required = true) @PathParam("modelId") String modelId) {
        return R.ok(modelService.get(modelId));
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
