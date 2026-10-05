package com.innospots.nexmux.core.provider.domain.vo;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * 上游拉取的模型行（「拉取模型」弹层）。
 *
 * @param providerModelId 上游模型 ID
 * @param existsLocally   本地是否已建模（主 Binding 引用该上游 ID）
 */
@Schema(name = "UpstreamModelVo", description = "上游拉取的模型行")
public record UpstreamModelVo(
        @Schema(description = "上游模型 ID", required = true)
        String providerModelId,
        @Schema(description = "本地是否已建模", required = true)
        boolean existsLocally) {
}
