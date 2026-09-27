package com.innospots.nexmux.console.catalog;

import com.innospots.nexus.console.entry.ConsoleModuleDescriptor;
import com.innospots.nexus.core.plugin.contribution.console.ConsoleModuleDeclaration;
import com.innospots.nexmux.console.entry.NexmuxConsoleEntryPlugin;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NexmuxConsolePagePathTest {

    @Test
    void pagePathsFollowUnifiedRouteFormula() {
        for (ConsoleModuleDeclaration module : NexmuxConsoleEntryPlugin.contribution().modules()) {
            assertThat(module.domainKey()).isEqualTo(NexmuxConsoleEntryPlugin.DOMAIN_KEY);
            for (var page : module.pages()) {
                assertThat(page.pagePath())
                        .isEqualTo(ConsoleModuleDescriptor.pagePath(
                                module.domainKey(), module.moduleKey(), page.pageKey()));
                assertThat(page.pagePath()).startsWith(ConsoleModuleDescriptor.PAGE_ROUTE_PREFIX + "/nexmux/");
                assertThat(page.pagePath()).doesNotContain("?");
            }
        }
    }
}
