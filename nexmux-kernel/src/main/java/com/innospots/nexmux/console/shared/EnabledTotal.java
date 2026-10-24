package com.innospots.nexmux.console.shared;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * 启用/总数计数对（列表页 counts 构成口径）。
 *
 * @param enabled 启用数
 * @param total   总数
 */
@Schema(name = "EnabledTotal", description = "启用/总数计数对")
public record EnabledTotal(
        @Schema(description = "启用数", required = true)
        long enabled,
        @Schema(description = "总数", required = true)
        long total) {
}
