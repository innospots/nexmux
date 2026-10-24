package com.innospots.nexmux.console.endpoint.client;

import com.innospots.nexus.base.domain.response.R;
import com.innospots.nexmux.console.access.ClientRecord;
import com.innospots.nexmux.console.access.ClientTemplateListResult;
import com.innospots.nexmux.console.access.ClientTemplateRecord;
import com.innospots.nexmux.console.access.ClientTemplateViewAssembler;
import com.innospots.nexmux.core.access.domain.request.ClientTemplateCreateRequest;
import com.innospots.nexmux.core.access.domain.request.ClientTemplatePageRequest;
import com.innospots.nexmux.core.access.domain.request.ClientTemplateUpdateRequest;
import com.innospots.nexmux.core.access.domain.vo.ClientTemplateVo;
import com.innospots.nexmux.core.access.service.ClientTemplateService;
import com.innospots.nexus.core.openapi.NexusAuthenticatedApi;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.Arrays;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Path("/api/d/client-templates")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "NexmuxClientTemplate", description = "客户端模板")
@NexusAuthenticatedApi
@RequiredArgsConstructor
public class ClientTemplateEndpoint {

    private final ClientTemplateService clientTemplateService;
    private final ClientTemplateViewAssembler clientTemplateViewAssembler;

    /**
     * 分页列表（UI 契约：{items,total,filters} 直出，不包 R）。
     */
    @GET
    @Operation(operationId = "nexmuxClientTemplatePage", summary = "分页查询客户端模板")
    public ClientTemplateListResult page(
            @Parameter(description = "页码，从 1 开始", example = "1")
            @QueryParam("page") @DefaultValue("1") Long page,
            @Parameter(description = "每页条数", example = "20")
            @QueryParam("pageSize") @DefaultValue("20") Long pageSize,
            @Parameter(description = "快筛（all / enabled / disabled）")
            @QueryParam("status") @DefaultValue("all") String status,
            @Parameter(description = "名称 / 编码模糊")
            @QueryParam("keyword") String keyword,
            @Parameter(description = "标签，逗号分隔（取交集）")
            @QueryParam("tags") String tags) {
        List<String> tagList = tags == null || tags.isBlank()
                ? List.of()
                : Arrays.stream(tags.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        String backendStatus = switch (status) {
            case "enabled" -> "ENABLED";
            case "disabled" -> "DISABLED";
            default -> null;
        };
        return clientTemplateViewAssembler.assemble(
                new ClientTemplatePageRequest(page, pageSize, backendStatus, keyword, tagList));
    }

    /**
     * 按稳定业务键查询详情（UI 契约：展示记录直出，不包 R）。
     */
    @GET
    @Path("/{templateCode}")
    @Operation(operationId = "nexmuxClientTemplateGetByCode", summary = "按编码查询模板详情")
    public ClientTemplateRecord detail(
            @Parameter(description = "模板编码", required = true) @PathParam("templateCode") String templateCode) {
        return clientTemplateViewAssembler.detail(templateCode);
    }

    /**
     * 基于模板创建的客户端（UI 契约：展示记录数组直出，不包 R）。
     */
    @GET
    @Path("/{templateCode}/created-clients")
    @Operation(operationId = "nexmuxClientTemplateCreatedClients", summary = "基于模板创建的客户端")
    public List<ClientRecord> createdClients(
            @Parameter(description = "模板编码", required = true) @PathParam("templateCode") String templateCode) {
        return clientTemplateViewAssembler.createdClients(templateCode);
    }

    @POST
    @Operation(operationId = "nexmuxClientTemplateCreate", summary = "创建客户端模板")
    public R<ClientTemplateVo> create(
            @RequestBody(description = "创建模板", required = true) ClientTemplateCreateRequest request) {
        return R.ok(clientTemplateService.create(request));
    }

    @PUT
    @Path("/{templateId}")
    @Operation(operationId = "nexmuxClientTemplateUpdate", summary = "更新客户端模板")
    public R<ClientTemplateVo> update(
            @Parameter(description = "模板 ID", required = true) @PathParam("templateId") String templateId,
            @RequestBody(description = "更新模板", required = true) ClientTemplateUpdateRequest request) {
        return R.ok(clientTemplateService.update(templateId, request));
    }

    @POST
    @Path("/{templateId}/enable")
    @Operation(operationId = "nexmuxClientTemplateEnable", summary = "启用客户端模板")
    public R<ClientTemplateVo> enable(
            @Parameter(description = "模板 ID", required = true) @PathParam("templateId") String templateId) {
        return R.ok(clientTemplateService.setEnabled(templateId, true));
    }

    @POST
    @Path("/{templateId}/disable")
    @Operation(operationId = "nexmuxClientTemplateDisable", summary = "停用客户端模板")
    public R<ClientTemplateVo> disable(
            @Parameter(description = "模板 ID", required = true) @PathParam("templateId") String templateId) {
        return R.ok(clientTemplateService.disable(templateId));
    }

    @DELETE
    @Path("/{templateId}")
    @Operation(operationId = "nexmuxClientTemplateDelete", summary = "删除客户端模板")
    public R<Void> delete(
            @Parameter(description = "模板 ID", required = true) @PathParam("templateId") String templateId) {
        clientTemplateService.delete(templateId);
        return R.ok();
    }
}
