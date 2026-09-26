package com.innospots.nexmux.console.entry;

import com.innospots.nexmux.console.catalog.NexmuxConsoleCatalog;
import com.innospots.nexus.core.plugin.contribution.console.ConsoleModuleDeclaration;
import com.innospots.nexus.core.plugin.contribution.console.ConsolePluginContribution;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NexmuxConsoleEntryPluginTest {

    @Test
    void declaresConsoleContributionWithModuleKey() {
        var definition = new NexmuxConsoleEntryPlugin().definition();
        ConsolePluginContribution contribution = definition.contributions().stream()
                .filter(ConsolePluginContribution.class::isInstance)
                .map(ConsolePluginContribution.class::cast)
                .findFirst()
                .orElseThrow();
        ConsoleModuleDeclaration module = contribution.modules().getFirst();
        assertThat(module.moduleKey()).isEqualTo(NexmuxConsoleCatalog.MODULE_KEY);
    }
}
