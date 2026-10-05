package com.innospots.nexmux.core.access.domain.request;

import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * 创建接入客户端。
 *
 * @param clientCode         客户端编码
 * @param displayName        显示名称
 * @param tags               标签
 * @param sourceTemplateCode 来源模板编码（按业务键关联；空 = 空白创建）
 * @param routeIds           授权路由 ID（空白创建时；模板创建时被模板覆盖）
 * @param expiresAt          到期时间（ISO 文本；模板创建时按模板有效期规则计算覆盖）
 */
@Schema(name = "ClientCreateRequest", description = "创建接入客户端")
public record ClientCreateRequest(
        @Schema(description = "客户端编码", required = true, examples = {"mobile-app"})
        String clientCode,
        @Schema(description = "显示名称", required = true)
        String displayName,
        @Schema(description = "标签")
        List<String> tags,
        @Schema(description = "来源模板编码", examples = {"team-default"})
        String sourceTemplateCode,
        @Schema(description = "授权路由 ID")
        List<String> routeIds,
        @Schema(description = "到期时间（ISO 文本）")
        String expiresAt) {
}
