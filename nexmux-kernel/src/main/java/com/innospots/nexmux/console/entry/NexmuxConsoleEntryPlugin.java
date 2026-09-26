package com.innospots.nexmux.console.entry;

import com.innospots.nexus.base.i18n.I18nObject;
import com.innospots.nexmux.console.catalog.NexmuxConsoleCatalog;
import com.innospots.nexus.core.plugin.capability.Tags;
import com.innospots.nexus.core.plugin.contract.Plugin;
import com.innospots.nexus.core.plugin.declaration.PluginDefinition;
import java.util.Map;

public final class NexmuxConsoleEntryPlugin implements Plugin {

    public static final String PLUGIN_ID = "com.innospots.nexmux.console";

    @Override
    public PluginDefinition definition() {
        return PluginDefinition.builder(PLUGIN_ID)
                .displayName(I18nObject.of("en", "Nexmux", "zh", "Nexmux 模型管理"))
                .description(I18nObject.of("en", "Model console for Nexmux.", "zh", "Nexmux 模型管理控制台。"))
                .version("1.0.0")
                .tags(Tags.from(Map.of("scope", "console", "product", "nexmux")))
                .contribute(NexmuxConsoleCatalog.contribution())
                .build();
    }
}
