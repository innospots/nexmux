package com.innospots.nexmux.console.endpoint.catalog;

import com.innospots.nexmux.console.catalog.CatalogLocale;
import com.innospots.nexmux.console.catalog.ProviderModelPresetViewAssembler;
import com.innospots.nexmux.console.catalog.domain.request.PresetImportRequest;
import com.innospots.nexmux.console.catalog.domain.vo.ModelPresetVo;
import com.innospots.nexmux.console.catalog.domain.vo.PresetImportResultVo;
import com.innospots.nexmux.console.catalog.domain.vo.ProviderPresetCatalogVo;
import com.innospots.nexmux.console.catalog.domain.vo.ProviderPresetVo;
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
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * 供应商模板目录（classpath 预设，不预置数据库）。
 *
 * <p>多语言：模板名称 / 说明按请求语言解析为单字符串。语言取自查询参数 {@code lang}
 * 或请求头 {@code Accept-Language}（Shell 的模块代理按语言偏好 Cookie 注入），
 * 缺省 {@code zh-CN}。</p>
 */
@EndpointModule(value = "provider", domain = "nexmux")
@Path("/api/d/provider-presets")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "NexmuxProviderPreset", description = "供应商模板目录")
@NexusAuthenticatedApi
@RequiredArgsConstructor
public class ProviderModelPresetEndpoint {

    private final ProviderModelPresetViewAssembler viewAssembler;

    @GET
    @Operation(operationId = "nexmuxProviderPresetCatalog", summary = "供应商模板目录")
    @EndpointGrant(action = EndpointGrantAction.READ, name = "provider.preset-catalog", group = "view")
    public ProviderPresetCatalogVo catalog(
            @Parameter(description = "语言（BCP 47；缺省按 Accept-Language 或 zh-CN）")
            @QueryParam("lang") String lang,
            @Parameter(description = "标准 Accept-Language 回退链")
            @HeaderParam("Accept-Language") String acceptLanguage) {
        return viewAssembler.catalog(CatalogLocale.resolve(localeTag(lang, acceptLanguage)));
    }

    @GET
    @Path("/{providerCode}")
    @Operation(operationId = "nexmuxProviderPresetGet", summary = "单个供应商模板")
    @EndpointGrant(action = EndpointGrantAction.READ, name = "provider.preset-get", group = "view")
    public ProviderPresetVo provider(
            @Parameter(description = "模板编码", required = true) @PathParam("providerCode") String providerCode,
            @Parameter(description = "语言（BCP 47）") @QueryParam("lang") String lang,
            @HeaderParam("Accept-Language") String acceptLanguage) {
        return viewAssembler.provider(providerCode, CatalogLocale.resolve(localeTag(lang, acceptLanguage)));
    }

    @GET
    @Path("/{providerCode}/models")
    @Operation(operationId = "nexmuxProviderPresetModels", summary = "供应商模板下的模型")
    @EndpointGrant(action = EndpointGrantAction.READ, name = "provider.preset-models", group = "view")
    public List<ModelPresetVo> models(
            @Parameter(description = "模板编码", required = true) @PathParam("providerCode") String providerCode,
            @Parameter(description = "语言（BCP 47）") @QueryParam("lang") String lang,
            @HeaderParam("Accept-Language") String acceptLanguage) {
        return viewAssembler.models(providerCode, CatalogLocale.resolve(localeTag(lang, acceptLanguage)));
    }

    /**
     * 导入选中的模板：每个模板都会插入一条新的供应商行（编码冲突时追加序号后缀）。
     *
     * <p>只创建供应商，不写入模型：模型库由用户按需自行建模，详情页展示的
     * 「可用模型」直接来自模板目录。</p>
     *
     * @param request       选中的模板编码
     * @param includeModels 是否同时创建模板内模型（缺省 false）
     * @param lang          语言（BCP 47）
     * @param acceptLanguage Accept-Language 回退链
     * @return 导入结果
     */
    @POST
    @Path("/import")
    @Operation(operationId = "nexmuxProviderPresetImport", summary = "导入选中供应商模板")
    @EndpointGrant(action = EndpointGrantAction.CREATE, name = "provider.preset-import", group = "create")
    public PresetImportResultVo importPresets(
            @RequestBody(description = "导入请求", required = true) PresetImportRequest request,
            @Parameter(description = "是否同时创建模板内模型（缺省 false）")
            @QueryParam("includeModels") @DefaultValue("false") Boolean includeModels,
            @Parameter(description = "语言（BCP 47）") @QueryParam("lang") String lang,
            @HeaderParam("Accept-Language") String acceptLanguage) {
        boolean withModels = Boolean.TRUE.equals(includeModels);
        Locale locale = CatalogLocale.resolve(localeTag(lang, acceptLanguage));
        return viewAssembler.importPresets(request.providerCodes(), withModels, locale);
    }

    private static String localeTag(String lang, String acceptLanguage) {
        return lang == null || lang.isBlank() ? acceptLanguage : lang;
    }
}
