package com.innospots.nexmux.console.endpoint.client;

import com.innospots.nexus.base.domain.response.PageResult;
import com.innospots.nexus.base.domain.response.R;
import com.innospots.nexmux.core.access.domain.request.ClientTemplateCreateRequest;
import com.innospots.nexmux.core.access.domain.request.ClientTemplatePageRequest;
import com.innospots.nexmux.core.access.domain.request.ClientTemplateUpdateRequest;
import com.innospots.nexmux.core.access.domain.vo.ClientTemplateDetailVo;
import com.innospots.nexmux.core.access.domain.vo.ClientTemplateVo;
import com.innospots.nexmux.core.access.domain.vo.ClientVo;
import com.innospots.nexmux.core.access.service.ClientTemplateService;
import com.innospots.nexus.core.openapi.NexusAuthenticatedApi;
import jakarta.ws.rs.Consumes;
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

    @GET
    @Operation(operationId = "nexmuxClientTemplatePage", summary = "分页查询客户端模板")
    public R<PageResult<ClientTemplateVo>> page(
            @Parameter(description = "页码，从 1 开始", example = "1")
            @QueryParam("pageNo") @DefaultValue("1") Long pageNo,
            @Parameter(description = "每页条数", example = "20")
            @QueryParam("pageSize") @DefaultValue("20") Long pageSize,
            @Parameter(description = "状态（ENABLED / DISABLED）")
            @QueryParam("status") String status,
            @Parameter(description = "名称 / 编码模糊")
            @QueryParam("keyword") String keyword,
            @Parameter(description = "标签，逗号分隔（取交集）")
            @QueryParam("tags") String tags) {
        List<String> tagList = tags == null || tags.isBlank()
                ? List.of()
                : Arrays.stream(tags.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        return R.ok(clientTemplateService.page(
                new ClientTemplatePageRequest(pageNo, pageSize, status, keyword, tagList)));
    }

    @GET
    @Path("/by-code/{templateCode}")
    @Operation(operationId = "nexmuxClientTemplateGetByCode", summary = "按编码查询模板详情")
    public R<ClientTemplateDetailVo> getByCode(
            @Parameter(description = "模板编码", required = true) @PathParam("templateCode") String templateCode) {
        return R.ok(clientTemplateService.getByCode(templateCode));
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

    @GET
    @Path("/{templateId}/created-clients")
    @Operation(operationId = "nexmuxClientTemplateCreatedClients", summary = "查询基于模板创建的客户端")
    public R<List<ClientVo>> createdClients(
            @Parameter(description = "模板 ID", required = true) @PathParam("templateId") String templateId) {
        return R.ok(clientTemplateService.createdClients(templateId));
    }
}
