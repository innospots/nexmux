package com.innospots.nexmux.console.endpoint.model;

import com.innospots.nexus.base.domain.response.PageResult;
import com.innospots.nexus.base.domain.response.R;
import com.innospots.nexmux.core.model.domain.request.ModelCreateRequest;
import com.innospots.nexmux.core.model.domain.request.ModelPageRequest;
import com.innospots.nexmux.core.model.domain.vo.ModelVo;
import com.innospots.nexmux.core.model.service.ModelService;
import com.innospots.nexus.core.openapi.NexusAuthenticatedApi;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;

@Path("/api/nexmux/models")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@NexusAuthenticatedApi
@RequiredArgsConstructor
public class ModelEndpoint {

    private final ModelService modelService;

    @GET
    public R<PageResult<ModelVo>> page(
            @QueryParam("pageNo") @DefaultValue("1") Long pageNo,
            @QueryParam("pageSize") @DefaultValue("20") Long pageSize) {
        return R.ok(modelService.page(new ModelPageRequest(pageNo, pageSize)));
    }

    @GET
    @Path("/{modelId}")
    public R<ModelVo> get(@PathParam("modelId") String modelId) {
        return R.ok(modelService.get(modelId));
    }

    @POST
    public R<ModelVo> create(ModelCreateRequest request) {
        return R.ok(modelService.create(request));
    }

    @POST
    @Path("/{modelId}/enable")
    public R<ModelVo> enable(@PathParam("modelId") String modelId) {
        return R.ok(modelService.setEnabled(modelId, true));
    }

    @POST
    @Path("/{modelId}/invoke-test")
    public R<Boolean> invokeTest(@PathParam("modelId") String modelId) {
        return R.ok(modelService.invokeTest(modelId));
    }
}
