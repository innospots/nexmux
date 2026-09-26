package com.innospots.nexmux.console.catalog;

import com.innospots.nexus.core.plugin.contribution.console.ConsoleModuleDeclaration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NexmuxConsolePagePathTest {

    @Test
    void pagePathsStartWithNexmuxPrefixWithoutQuery() {
        ConsoleModuleDeclaration module = NexmuxConsoleCatalog.contribution().modules().getFirst();
        assertThat(module.pages()).allSatisfy(page -> {
            assertThat(page.pagePath()).startsWith("/page/nexmux");
            assertThat(page.pagePath()).doesNotContain("?");
        });
    }
}
