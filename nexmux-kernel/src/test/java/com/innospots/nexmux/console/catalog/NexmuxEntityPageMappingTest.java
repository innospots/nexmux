package com.innospots.nexmux.console.catalog;

import com.innospots.nexus.core.plugin.contribution.console.ConsoleModuleDeclaration;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NexmuxEntityPageMappingTest {

    @Test
    void eachP0EntityHasListPageKey() {
        ConsoleModuleDeclaration module = NexmuxConsoleCatalog.contribution().modules().getFirst();
        Set<String> pageKeys = module.pages().stream()
                .map(page -> page.pageKey())
                .collect(java.util.stream.Collectors.toSet());
        assertThat(pageKeys).contains(
                "nexmux-provider-list",
                "nexmux-model-list",
                "nexmux-route-list",
                "nexmux-client-list",
                "nexmux-request-log-list",
                "nexmux-audit-list");
    }
}
