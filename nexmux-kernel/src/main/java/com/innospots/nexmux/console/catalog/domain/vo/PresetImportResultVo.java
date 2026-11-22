package com.innospots.nexmux.console.catalog.domain.vo;

import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * 供应商模板导入结果。
 *
 * @param imported 新建成功的供应商
 * @param skipped  未找到对应模板而被跳过的编码
 * @param failures 创建失败的条目
 */
@Schema(name = "PresetImportResultVo", description = "供应商模板导入结果")
public record PresetImportResultVo(
        @Schema(description = "新建成功的供应商", required = true)
        List<Item> imported,
        @Schema(description = "未找到模板的编码", required = true)
        List<String> skipped,
        @Schema(description = "创建失败的条目", required = true)
        List<Failure> failures) {

    /**
     * 导入成功的供应商。
     *
     * @param presetCode        模板编码
     * @param localProviderCode 本地供应商编码（重复导入时追加序号后缀）
     * @param displayName       本地供应商名称
     * @param modelCount        模板内模型数
     */
    @Schema(name = "PresetImportItem", description = "导入成功的供应商")
    public record Item(
            @Schema(description = "模板编码", required = true)
            String presetCode,
            @Schema(description = "本地供应商编码", required = true)
            String localProviderCode,
            @Schema(description = "本地供应商名称", required = true)
            String displayName,
            @Schema(description = "模板内模型数")
            int modelCount) {
    }

    /**
     * 导入失败的供应商。
     *
     * @param presetCode 模板编码
     * @param message    失败原因
     */
    @Schema(name = "PresetImportFailure", description = "导入失败的供应商")
    public record Failure(
            @Schema(description = "模板编码", required = true)
            String presetCode,
            @Schema(description = "失败原因", required = true)
            String message) {
    }
}
