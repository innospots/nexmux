package com.innospots.nexmux.console.catalog;

import com.innospots.nexmux.console.entry.NexmuxConsoleEntryPlugin;
import com.innospots.nexus.core.plugin.contribution.console.ConsoleModuleDeclaration;
import com.innospots.nexus.core.plugin.contribution.console.ui.spec.PageDsl;
import com.innospots.nexus.core.plugin.contribution.console.ui.spec.config.PageDslConfig;
import com.innospots.nexus.core.plugin.contribution.console.ui.spec.loader.ClasspathPageDslLoader;
import com.innospots.nexus.core.plugin.contribution.console.ui.spec.parser.JacksonPageDslParser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NexmuxPageDslClasspathTest {

    private final ClasspathPageDslLoader loader = new ClasspathPageDslLoader(
            PageDslConfig.defaults(),
            new JacksonPageDslParser(PageDslConfig.defaults()),
            NexmuxPageDslClasspathTest.class.getClassLoader());

    @Test
    void loadsEveryDeclaredPageFromModuleClasspath() {
        for (ConsoleModuleDeclaration module : NexmuxConsoleEntryPlugin.contribution().modules()) {
            assertThat(module.domainKey()).isEqualTo(NexmuxConsoleEntryPlugin.DOMAIN_KEY);
            assertThat(module.moduleKey()).isNotEqualTo(NexmuxConsoleEntryPlugin.DOMAIN_KEY);
            for (var page : module.pages()) {
                PageDsl document = loader.load(module.domainKey(), module.moduleKey(), page.pageKey());
                assertThat(document.getPage().getId()).isEqualTo(page.pageKey());
                assertThat(document.getDsl()).isEqualTo("1.0");
            }
        }
    }
}
