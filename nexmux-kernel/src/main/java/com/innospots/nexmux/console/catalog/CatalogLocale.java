package com.innospots.nexmux.console.catalog;

import java.util.Locale;
import java.util.Optional;

/**
 * 供应商模板目录的请求语言环境解析。
 *
 * <p>控制台接口由 {@code Accept-Language} 声明语言（Shell 的模块代理按语言偏好 Cookie
 * 注入该请求头）。目前仅支持中文与英文，其余回退 {@code zh-CN}。</p>
 */
public final class CatalogLocale {

    private static final Locale DEFAULT_LOCALE = Locale.SIMPLIFIED_CHINESE;

    private CatalogLocale() {
    }

    /**
     * 解析 Accept-Language。
     *
     * @param acceptLanguage 请求头原始值，可空
     * @return 语言环境；无法识别时返回默认 zh-CN
     */
    public static Locale resolve(String acceptLanguage) {
        if (acceptLanguage == null || acceptLanguage.isBlank()) {
            return DEFAULT_LOCALE;
        }
        return firstSupported(acceptLanguage).orElse(DEFAULT_LOCALE);
    }

    private static Optional<Locale> firstSupported(String acceptLanguage) {
        for (String part : acceptLanguage.split(",")) {
            String languageTag = part.split(";", 2)[0].trim().replace('_', '-');
            if (languageTag.isEmpty()) {
                continue;
            }
            String lower = languageTag.toLowerCase(Locale.ROOT);
            if (lower.equals("zh") || lower.startsWith("zh-")) {
                return Optional.of(Locale.SIMPLIFIED_CHINESE);
            }
            if (lower.equals("en") || lower.startsWith("en-")) {
                return Optional.of(Locale.US);
            }
        }
        return Optional.empty();
    }
}
