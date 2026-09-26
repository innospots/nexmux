package com.innospots.nexmux.console.catalog;

import com.innospots.nexus.core.plugin.contribution.console.ConsoleModuleDeclaration;
import com.innospots.nexus.core.plugin.contribution.console.MenuDeclaration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NexmuxConsoleCatalogMenuTest {

    private static final Set<String> P0_MENU_KEYS = Set.of(
            "nexmux-asset-provider",
            "nexmux-asset-model",
            "nexmux-route",
            "nexmux-access-client",
            "nexmux-ops-request-log",
            "nexmux-debug-client",
            "nexmux-debug-model",
            "nexmux-system-audit");

    @Test
    void includesP0MenuKeys() {
        ConsoleModuleDeclaration module = NexmuxConsoleCatalog.contribution().modules().getFirst();
        Set<String> keys = collectMenuKeys(module.menuTree());
        assertThat(keys).containsAll(P0_MENU_KEYS);
    }

    private static Set<String> collectMenuKeys(List<MenuDeclaration> nodes) {
        List<String> keys = new ArrayList<>();
        for (MenuDeclaration node : nodes) {
            keys.add(node.menuKey());
            keys.addAll(collectMenuKeys(node.children()));
        }
        return keys.stream().collect(Collectors.toSet());
    }
}
