package com.innospots.nexmux.console.endpoint.provider;

import com.innospots.nexmux.console.provider.ProviderOption;
import com.innospots.nexmux.console.provider.ProviderViewAssembler;
import com.innospots.nexus.core.openapi.NexusAuthenticatedApi;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Provider 下拉选项端点（单段 kebab 路径约定；模型新建/编辑页 Select 数据源）。
 */
@Path("/api/d/provider-options")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "NexmuxProvider", description = "模型服务提供方")
@NexusAuthenticatedApi
@RequiredArgsConstructor
public class ProviderOptionsEndpoint {

    private final ProviderViewAssembler providerViewAssembler;

    /**
     * 下拉选项（UI 契约：{items:[{label,value}]} 直出，不包 R）。
     */
    @GET
    @Operation(operationId = "nexmuxProviderOptions", summary = "Provider 下拉选项")
    public OptionsResult options() {
        return new OptionsResult(providerViewAssembler.options());
    }

    /**
     * 选项结果（{items} 形状约定）。
     *
     * @param items 选项列表
     */
    @Schema(name = "ProviderOptionsResult", description = "Provider 下拉选项结果")
    public record OptionsResult(
            @Schema(description = "选项列表", required = true)
            List<ProviderOption> items) {
    }
}
