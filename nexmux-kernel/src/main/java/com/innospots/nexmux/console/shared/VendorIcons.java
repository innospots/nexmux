package com.innospots.nexmux.console.shared;

import java.util.Map;

/**
 * 厂商编码 → 前端展示资源映射（Icon 组件白名单图标名 / Chip 圆点色）。
 * 白名单外的厂商回退 zap 图标、无圆点。
 */
public final class VendorIcons {

    private static final Map<String, String> ICONS = Map.of(
            "openai", "zap",
            "anthropic", "brain",
            "gemini", "star",
            "dashscope", "cloud");

    private static final Map<String, String> DOTS = Map.of(
            "openai", "#3b82f6",
            "anthropic", "#b45309",
            "gemini", "#1d4ed8",
            "dashscope", "#8b5cf6");

    private VendorIcons() {
    }

    /** 厂商编码 → 图标名（未知厂商回退 zap）。 */
    public static String iconOf(String vendorCode) {
        return ICONS.getOrDefault(vendorCode == null ? "" : vendorCode, "zap");
    }

    /** 厂商编码 → Chip 圆点色（未配置返回 null）。 */
    public static String dotOf(String vendorCode) {
        return DOTS.get(vendorCode);
    }
}
