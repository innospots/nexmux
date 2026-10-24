package com.innospots.nexmux.core.provider.domain.vo;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * 模型服务提供方概要。
 *
 * @param providerId         提供方 ID
 * @param providerCode       唯一编码
 * @param displayName        显示名称
 * @param providerType       提供方类型
 * @param defaultProtocol    默认协议
 * @param vendorCode         厂商编码
 * @param sourceVendorCode   上游厂商编码
 * @param iconUri            图标 URI
 * @param baseUrl            服务基址
 * @param region             区域
 * @param timeoutMs          超时（毫秒）
 * @param status             状态
 * @param keyStatus          密钥状态（configured / pending / none）
 * @param modelCount         该提供方下的模型数
 */
@Schema(name = "ProviderVo", description = "模型服务提供方概要")
public record ProviderVo(
        @Schema(description = "提供方 ID", required = true)
        String providerId,
        @Schema(description = "唯一编码", required = true)
        String providerCode,
        @Schema(description = "显示名称", required = true)
        String displayName,
        @Schema(description = "提供方类型")
        String providerType,
        @Schema(description = "默认协议")
        String defaultProtocol,
        @Schema(description = "厂商编码")
        String vendorCode,
        @Schema(description = "上游厂商编码")
        String sourceVendorCode,
        @Schema(description = "图标 URI")
        String iconUri,
        @Schema(description = "服务基址")
        String baseUrl,
        @Schema(description = "区域")
        String region,
        @Schema(description = "超时（毫秒）")
        Integer timeoutMs,
        @Schema(description = "状态", examples = {"ENABLED"})
        String status,
        @Schema(description = "密钥状态", examples = {"configured"})
        String keyStatus,
        @Schema(description = "该提供方下的模型数")
        long modelCount) {
}
