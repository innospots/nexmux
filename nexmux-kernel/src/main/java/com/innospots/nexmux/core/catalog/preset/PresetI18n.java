package com.innospots.nexmux.core.catalog.preset;

import com.innospots.nexus.base.i18n.I18nObject;
import java.util.Locale;

/**
 * 预设 YAML 中 {@link I18nObject} 字段的解析辅助（落库/API 投影使用单字符串）。
 */
public final class PresetI18n {

    private PresetI18n() {
    }

    /**
     * 按当前线程语言环境解析文案。
     *
     * @param value 多语言文案
     * @return 解析结果；空对象返回 {@code null}
     */
    public static String localized(I18nObject value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        return value.defaultValue();
    }

    /**
     * 按指定语言环境解析文案（精确语言标签 → 仅语言 → 同语言变体 → 首值）。
     *
     * @param value  多语言文案
     * @param locale 目标语言环境
     * @return 解析结果；空对象返回 {@code null}
     */
    public static String localized(I18nObject value, Locale locale) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        return value.value(locale);
    }

    /**
     * 解析文案；空白时回退到 {@code fallback}。
     *
     * @param value    多语言文案
     * @param fallback 回退值
     * @return 解析结果
     */
    public static String localizedOr(I18nObject value, String fallback) {
        String resolved = localized(value);
        if (resolved == null || resolved.isBlank()) {
            return fallback;
        }
        return resolved;
    }

    /**
     * 按指定语言环境解析文案；空白时回退到 {@code fallback}。
     *
     * @param value    多语言文案
     * @param locale   目标语言环境
     * @param fallback 回退值
     * @return 解析结果
     */
    public static String localizedOr(I18nObject value, Locale locale, String fallback) {
        String resolved = localized(value, locale);
        if (resolved == null || resolved.isBlank()) {
            return fallback;
        }
        return resolved;
    }
}
