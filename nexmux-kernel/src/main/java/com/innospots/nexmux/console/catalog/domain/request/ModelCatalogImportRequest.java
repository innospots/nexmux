package com.innospots.nexmux.console.catalog.domain.request;

import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * 模型目录批量导入请求。
 *
 * @param models 选中的模型（供应商 + 上游模型 ID）
 */
@Schema(name = "ModelCatalogImportRequest", description = "模型目录批量导入请求")
public record ModelCatalogImportRequest(
        @Schema(description = "选中的模型", required = true)
        List<Item> models) {

    /**
     * 单条导入项。
     *
     * @param providerCode 本地供应商编码
     * @param modelId      上游模型 ID
     */
    @Schema(name = "ModelCatalogImportItem", description = "模型目录导入项")
    public record Item(
            @Schema(description = "本地供应商编码", required = true)
            String providerCode,
            @Schema(description = "上游模型 ID", required = true)
            String modelId) {
    }
}
