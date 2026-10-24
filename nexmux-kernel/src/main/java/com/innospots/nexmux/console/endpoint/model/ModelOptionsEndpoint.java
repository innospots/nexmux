package com.innospots.nexmux.console.endpoint.model;

import com.innospots.nexmux.console.model.ModelOption;
import com.innospots.nexmux.console.model.ModelViewAssembler;
import com.innospots.nexus.core.openapi.NexusAuthenticatedApi;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * Model 下拉选项端点（单段 kebab 路径约定；路由编辑「添加模型」Select 数据源）。
 */
@Path("/api/d/model-options")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "NexmuxModel", description = "模型管理")
@NexusAuthenticatedApi
@RequiredArgsConstructor
public class ModelOptionsEndpoint {

    private final ModelViewAssembler modelViewAssembler;

    /**
     * 启用模型下拉选项（UI 契约：{items:[{label,value}]} 直出，不包 R）。
     */
    @GET
    @Operation(operationId = "nexmuxModelOptions", summary = "启用模型下拉选项")
    public OptionsResult options() {
        return new OptionsResult(modelViewAssembler.options());
    }

    /**
     * 选项结果（{items} 形状约定）。
     *
     * @param items 选项列表
     */
    @Schema(name = "ModelOptionsResult", description = "Model 下拉选项结果")
    public record OptionsResult(
            @Schema(description = "选项列表", required = true)
            List<ModelOption> items) {
    }
}
