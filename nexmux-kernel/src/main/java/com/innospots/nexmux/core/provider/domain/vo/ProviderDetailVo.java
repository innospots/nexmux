package com.innospots.nexmux.core.provider.domain.vo;

import java.time.LocalDateTime;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * 提供方详情（按稳定业务键查询；不含明文密钥）。
 *
 * @param providerId           提供方 ID
 * @param providerCode         唯一编码（稳定业务键）
 * @param displayName          显示名称
 * @param providerType         提供方类型
 * @param defaultProtocol      默认协议
 * @param vendorCode           厂商编码
 * @param sourceVendorCode     上游厂商编码
 * @param iconUri              图标 URI
 * @param baseUrl              服务基址
 * @param region               区域
 * @param timeoutMs            超时（毫秒）
 * @param proxy                代理
 * @param description          描述
 * @param capabilityExtension  能力扩展（JSON 文本）
 * @param status               状态
 * @param keyStatus            密钥状态（configured / pending / none）
 * @param keyVersion           密钥版本（无凭证为 null）
 * @param rotatedAt            密钥最近轮换时间
 * @param models               该提供方下的模型行
 * @param createdAt            创建时间
 * @param updatedAt            更新时间
 * @param updatedBy            最近操作人
 */
@Schema(name = "ProviderDetailVo", description = "提供方详情")
public record ProviderDetailVo(
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
        @Schema(description = "代理")
        String proxy,
        @Schema(description = "描述")
        String description,
        @Schema(description = "能力扩展（JSON 文本）")
        String capabilityExtension,
        @Schema(description = "状态", examples = {"ENABLED"})
        String status,
        @Schema(description = "密钥状态", examples = {"configured"})
        String keyStatus,
        @Schema(description = "密钥版本")
        Integer keyVersion,
        @Schema(description = "密钥最近轮换时间", format = "date-time")
        LocalDateTime rotatedAt,
        @Schema(description = "该提供方下的模型行")
        List<ProviderModelRowVo> models,
        @Schema(description = "创建时间", format = "date-time")
        LocalDateTime createdAt,
        @Schema(description = "更新时间", format = "date-time")
        LocalDateTime updatedAt,
        @Schema(description = "最近操作人")
        String updatedBy) {
}
