package com.innospots.nexmux.console.catalog.domain.vo;

import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * 模型目录导入结果。
 *
 * @param created  新建模型数
 * @param skipped  已存在而跳过的上游模型 ID
 * @param failures 创建失败明细
 */
@Schema(name = "ModelImportResultVo", description = "模型目录导入结果")
public record ModelImportResultVo(
        @Schema(description = "新建模型数")
        int created,
        @Schema(description = "已存在而跳过")
        List<String> skipped,
        @Schema(description = "创建失败明细")
        List<Failure> failures) {

    /**
     * 创建失败明细。
     *
     * @param id      上游模型 ID
     * @param message 失败原因
     */
    @Schema(name = "ModelImportFailure", description = "模型导入失败明细")
    public record Failure(
            @Schema(description = "上游模型 ID", required = true)
            String id,
            @Schema(description = "失败原因", required = true)
            String message) {
    }
}
