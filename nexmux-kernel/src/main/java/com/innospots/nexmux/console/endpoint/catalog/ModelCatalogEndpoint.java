package com.innospots.nexmux.console.endpoint.catalog;

import com.innospots.nexmux.console.catalog.CatalogLocale;
import com.innospots.nexmux.console.catalog.ModelCatalogViewAssembler;
import com.innospots.nexmux.console.catalog.domain.request.ModelCatalogImportRequest;
import com.innospots.nexmux.console.catalog.domain.vo.ModelCatalogVo;
import com.innospots.nexmux.console.catalog.domain.vo.ModelImportResultVo;
import com.innospots.nexus.core.endpoint.grant.EndpointGrant;
import com.innospots.nexus.core.endpoint.grant.EndpointGrantAction;
import com.innospots.nexus.core.endpoint.grant.EndpointModule;
import com.innospots.nexus.core.openapi.NexusAuthenticatedApi;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * 模型目录：本地已创建供应商可用的上游模型，支持安装模型类型与供应商筛选。
 */
@EndpointModule(value = "model", domain = "nexmux")
@Path("/api/d/model-catalog")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "NexmuxModelCatalog", description = "模型目录")
@NexusAuthenticatedApi
@RequiredArgsConstructor
public class ModelCatalogEndpoint {

    private final ModelCatalogViewAssembler viewAssembler;

    /**
     * 目录查询。
     *
     * @param keyword   关键字（上游模型 ID / 名称 / 供应商编码）
     * @param types     安装模型类型，逗号分隔
     * @param providers 供应商编码，逗号分隔
     * @param all       true 时包含已建模行（缺省只返回可导入行）
     * @param lang      语言（BCP 47）
     * @param acceptLanguage Accept-Language 回退链
     * @return 目录视图
     */
    @GET
    @Operation(operationId = "nexmuxModelCatalog", summary = "模型目录（含类型与供应商筛选）")
    @EndpointGrant(action = EndpointGrantAction.READ, name = "model.catalog", group = "view")
    public ModelCatalogVo catalog(
            @Parameter(description = "关键字（上游模型 ID / 名称 / 供应商编码）")
            @QueryParam("keyword") String keyword,
            @Parameter(description = "安装模型类型，逗号分隔（LLM / Image / Audio / Embedding）")
            @QueryParam("types") String types,
            @Parameter(description = "供应商编码，逗号分隔")
            @QueryParam("providers") String providers,
            @Parameter(description = "是否包含已导入的模型（缺省 false）")
            @QueryParam("all") @DefaultValue("false") Boolean all,
            @Parameter(description = "语言（BCP 47）") @QueryParam("lang") String lang,
            @HeaderParam("Accept-Language") String acceptLanguage) {
        Locale locale = CatalogLocale.resolve(localeTag(lang, acceptLanguage));
        return viewAssembler.catalog(
                keyword, split(types), split(providers), !Boolean.TRUE.equals(all), locale);
    }

    /**
     * 批量导入选中模型。
     *
     * @param request        选中项
     * @param lang           语言（BCP 47）
     * @param acceptLanguage Accept-Language 回退链
     * @return 导入结果
     */
    @POST
    @Path("/import")
    @Operation(operationId = "nexmuxModelCatalogImport", summary = "从模型目录导入")
    @EndpointGrant(action = EndpointGrantAction.CREATE, name = "model.catalog-import", group = "create")
    public ModelImportResultVo importModels(
            @RequestBody(description = "导入请求", required = true) ModelCatalogImportRequest request,
            @Parameter(description = "语言（BCP 47）") @QueryParam("lang") String lang,
            @HeaderParam("Accept-Language") String acceptLanguage) {
        Locale locale = CatalogLocale.resolve(localeTag(lang, acceptLanguage));
        return viewAssembler.importModels(request.models(), locale);
    }

    private static String localeTag(String lang, String acceptLanguage) {
        return lang == null || lang.isBlank() ? acceptLanguage : lang;
    }

    private static List<String> split(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .toList();
    }
}
