package com.innospots.nexmux.console.shared;

import com.innospots.nexmux.core.settings.service.PlatformSettingService;
import lombok.RequiredArgsConstructor;

/**
 * DisplayFormats 解析器：每次 current() 读取最新设置（设置保存后即生效，
 * 无需重启）。settings 为 null 时恒为默认值（测试与降级用）。
 */
@RequiredArgsConstructor
public class DisplayFormatsProvider {

    private final PlatformSettingService settings;

    public DisplayFormats current() {
        return settings == null ? DisplayFormats.DEFAULTS : DisplayFormats.from(settings.getAll());
    }

    /** 固定默认值（测试用）。 */
    public static DisplayFormatsProvider defaults() {
        return new DisplayFormatsProvider(null);
    }
}
