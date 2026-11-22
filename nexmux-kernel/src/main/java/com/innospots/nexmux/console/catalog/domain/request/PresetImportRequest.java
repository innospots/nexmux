package com.innospots.nexmux.console.catalog.domain.request;

import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * 供应商模板导入请求。
 *
 * <p>每次导入都会新建供应商行；模板编码已被占用时自动追加 {@code -2}、{@code -3} 等后缀。</p>
 *
 * @param providerCodes 选中的模板编码
 */
@Schema(name = "PresetImportRequest", description = "供应商模板导入请求")
public record PresetImportRequest(
        @Schema(description = "选中的模板编码", required = true, examples = {"[\"deepseek\",\"volcengine\"]"})
        List<String> providerCodes) {
}
