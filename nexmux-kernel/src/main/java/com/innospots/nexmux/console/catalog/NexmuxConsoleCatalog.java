package com.innospots.nexmux.console.catalog;

import com.innospots.nexus.core.plugin.contribution.console.ConsolePluginContribution;
import com.innospots.nexus.core.plugin.contribution.console.MenuDeclaration;
import com.innospots.nexus.core.plugin.contribution.console.UiSpecPageDeclaration;
import com.innospots.nexmux.console.entry.NexmuxConsoleEntryPlugin;
import java.util.List;

/** 委托至 {@link NexmuxConsoleEntryPlugin}，便于测试与历史引用。 */
public final class NexmuxConsoleCatalog {

    public static final String DOMAIN_KEY = NexmuxConsoleEntryPlugin.DOMAIN_KEY;
    public static final String MODULE_PROVIDER = NexmuxConsoleEntryPlugin.MODULE_PROVIDER;
    public static final String MODULE_MODEL = NexmuxConsoleEntryPlugin.MODULE_MODEL;
    public static final String MODULE_ROUTE = NexmuxConsoleEntryPlugin.MODULE_ROUTE;
    public static final String MODULE_CLIENT = NexmuxConsoleEntryPlugin.MODULE_CLIENT;
    public static final String MODULE_OPS = NexmuxConsoleEntryPlugin.MODULE_OPS;
    public static final String MODULE_DEBUG = NexmuxConsoleEntryPlugin.MODULE_DEBUG;
    public static final String MODULE_AUDIT = NexmuxConsoleEntryPlugin.MODULE_AUDIT;

    private NexmuxConsoleCatalog() {
    }

    public static ConsolePluginContribution contribution() {
        return NexmuxConsoleEntryPlugin.contribution();
    }

    public static List<UiSpecPageDeclaration> allPages() {
        return NexmuxConsoleEntryPlugin.allPages();
    }

    public static List<MenuDeclaration> allMenuTrees() {
        return NexmuxConsoleEntryPlugin.allMenuTrees();
    }
}
