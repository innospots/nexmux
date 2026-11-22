package com.innospots.nexmux.console.catalog.domain.vo;

import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * 供应商模板（classpath {@code provider-models/provider-presets.yaml}）的接口视图。
 *
 * <p>模板名称与说明在装配阶段按请求语言（{@code lang} 参数或 {@code Accept-Language}）
 * 解析为单字符串，与 console 其余展示契约一致；导入落库使用同一语言取值。</p>
 *
 * @param providerCode     模板编码（重复导入时本地编码追加序号后缀）
 * @param displayName      供应商名称
 * @param description      模板说明
 * @param providerType     供应商类型（CLOUD / COMMERCIAL / CUSTOM）
 * @param defaultProtocol  默认上游协议
 * @param protocols        该模板下模型覆盖的全部上游协议（去重，保持预设顺序）
 * @param vendorCode       厂商编码
 * @param sourceVendorCode 上游厂商编码
 * @param baseUrl          默认服务基址
 * @param chatUrl          对话端点（基址含占位符或协议无法推导时为空）
 * @param timeoutMs        默认超时（毫秒）
 * @param modelCount       模板内模型数
 * @param imported         本地是否已存在该模板导入的供应商
 * @param localProviderCode 已存在时的本地供应商编码
 */
@Schema(name = "ProviderPresetVo", description = "供应商模板")
public record ProviderPresetVo(
        @Schema(description = "模板编码", required = true)
        String providerCode,
        @Schema(description = "供应商名称", required = true)
        String displayName,
        @Schema(description = "模板说明")
        String description,
        @Schema(description = "供应商类型", examples = {"COMMERCIAL"})
        String providerType,
        @Schema(description = "默认上游协议", examples = {"openai-completions"})
        String defaultProtocol,
        @Schema(description = "模板内模型覆盖的上游协议")
        List<String> protocols,
        @Schema(description = "厂商编码")
        String vendorCode,
        @Schema(description = "上游厂商编码")
        String sourceVendorCode,
        @Schema(description = "默认服务基址")
        String baseUrl,
        @Schema(description = "对话端点")
        String chatUrl,
        @Schema(description = "默认超时（毫秒）")
        Integer timeoutMs,
        @Schema(description = "模板内模型数")
        int modelCount,
        @Schema(description = "本地是否已导入")
        boolean imported,
        @Schema(description = "已导入时的本地供应商编码")
        String localProviderCode) {
}
