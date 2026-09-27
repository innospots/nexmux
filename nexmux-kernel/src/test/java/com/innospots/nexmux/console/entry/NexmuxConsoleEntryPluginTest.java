package com.innospots.nexmux.console.entry;

import com.innospots.nexus.core.plugin.contribution.console.ConsoleModuleDeclaration;
import com.innospots.nexus.core.plugin.contribution.console.ConsolePluginContribution;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NexmuxConsoleEntryPluginTest {

    @Test
    void declaresConsoleContributionWithDomainAndBusinessModules() {
        var definition = new NexmuxConsoleEntryPlugin().definition();
        assertThat(definition.pluginId()).isEqualTo(NexmuxConsoleEntryPlugin.PLUGIN_ID);

        ConsolePluginContribution contribution = definition.contributions().stream()
                .filter(ConsolePluginContribution.class::isInstance)
                .map(ConsolePluginContribution.class::cast)
                .findFirst()
                .orElseThrow();

        assertThat(contribution.modules()).extracting(ConsoleModuleDeclaration::domainKey)
                .containsOnly(NexmuxConsoleEntryPlugin.DOMAIN_KEY);
        assertThat(contribution.modules()).extracting(ConsoleModuleDeclaration::moduleKey)
                .containsExactlyInAnyOrder(
                        NexmuxConsoleEntryPlugin.MODULE_PROVIDER,
                        NexmuxConsoleEntryPlugin.MODULE_MODEL,
                        NexmuxConsoleEntryPlugin.MODULE_ROUTE,
                        NexmuxConsoleEntryPlugin.MODULE_CLIENT,
                        NexmuxConsoleEntryPlugin.MODULE_OPS,
                        NexmuxConsoleEntryPlugin.MODULE_DEBUG,
                        NexmuxConsoleEntryPlugin.MODULE_AUDIT);
    }

    @Test
    void entryPageKeyIsFirstPagePerModule() {
        for (ConsoleModuleDeclaration module : NexmuxConsoleEntryPlugin.contribution().modules()) {
            assertThat(module.pages()).isNotEmpty();
            String entry = module.pages().getFirst().pageKey();
            assertThat(entry).startsWith("nexmux-");
        }
    }
}
