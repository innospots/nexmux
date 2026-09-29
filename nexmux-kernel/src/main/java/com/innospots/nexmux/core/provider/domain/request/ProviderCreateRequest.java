package com.innospots.nexmux.core.provider.domain.request;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * 创建模型服务提供方。
 *
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
 */
@Schema(name = "ProviderCreateRequest", description = "创建模型服务提供方")
public record ProviderCreateRequest(
        @Schema(description = "唯一编码", required = true, examples = {"openai-cn"})
        String providerCode,
        @Schema(description = "显示名称", required = true, examples = {"OpenAI 中国"})
        String displayName,
        @Schema(description = "提供方类型", examples = {"CLOUD"})
        String providerType,
        @Schema(description = "默认协议", examples = {"HTTP"})
        String defaultProtocol,
        @Schema(description = "厂商编码")
        String vendorCode,
        @Schema(description = "上游厂商编码")
        String sourceVendorCode,
        @Schema(description = "图标 URI")
        String iconUri,
        @Schema(description = "服务基址", examples = {"https://api.example.com"})
        String baseUrl,
        @Schema(description = "区域", examples = {"cn-north-1"})
        String region,
        @Schema(description = "超时（毫秒）", examples = {"30000"})
        Integer timeoutMs) {
}
