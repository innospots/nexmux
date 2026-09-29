package com.innospots.nexmux.console.entry;

import com.innospots.nexus.base.i18n.I18nObject;
import com.innospots.nexus.console.entry.ConsoleModuleDescriptor;
import com.innospots.nexus.core.plugin.capability.Tags;
import com.innospots.nexus.core.plugin.contribution.console.ConsoleModuleDeclaration;
import com.innospots.nexus.core.plugin.contribution.console.ConsolePluginContribution;
import com.innospots.nexus.core.plugin.contribution.console.MenuDeclaration;
import com.innospots.nexus.core.plugin.contribution.console.UiSpecPageDeclaration;
import com.innospots.nexus.core.plugin.contract.Plugin;
import com.innospots.nexus.core.plugin.declaration.PluginDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Nexmux 控制台 Entry：{@link ConsoleModuleDescriptor} + 统一路由
 * {@link ConsoleModuleDescriptor#PAGE_ROUTE_PREFIX}/{domainKey}/{moduleKey}/{pageKey}。
 */
public final class NexmuxConsoleEntryPlugin implements Plugin {

    public static final String PLUGIN_ID = "com.innospots.nexmux.console";
    public static final String PLUGIN_VERSION = "1.0.0";
    public static final String DOMAIN_KEY = "nexmux";

    public static final String MODULE_PROVIDER = "provider";
    public static final String MODULE_MODEL = "model";
    public static final String MODULE_ROUTE = "route";
    public static final String MODULE_CLIENT = "client";
    public static final String MODULE_OPS = "ops";
    public static final String MODULE_DEBUG = "debug";
    public static final String MODULE_AUDIT = "audit";

    private static final I18nObject PLUGIN_DISPLAY_NAME =
            I18nObject.of("en", "Nexmux", "zh", "Nexmux 模型管理");
    private static final I18nObject PLUGIN_DESCRIPTION = I18nObject.of(
            "en", "Model console for Nexmux.", "zh", "Nexmux 模型管理控制台。");
    private static final Tags PLUGIN_TAGS = Tags.from(Map.of("scope", "console", "product", "nexmux"));

    private static final ConsolePluginContribution CONTRIBUTION =
            new ConsolePluginContribution(List.of(
                    providerModule(),
                    modelModule(),
                    routeModule(),
                    clientModule(),
                    opsModule(),
                    debugModule(),
                    auditModule()));

    @Override
    public PluginDefinition definition() {
        return PluginDefinition.builder(PLUGIN_ID)
                .displayName(PLUGIN_DISPLAY_NAME)
                .description(PLUGIN_DESCRIPTION)
                .version(PLUGIN_VERSION)
                .tags(PLUGIN_TAGS)
                .contribute(CONTRIBUTION)
                .build();
    }

    public static ConsolePluginContribution contribution() {
        return CONTRIBUTION;
    }

    public static List<UiSpecPageDeclaration> allPages() {
        List<UiSpecPageDeclaration> pages = new ArrayList<>();
        for (ConsoleModuleDeclaration module : CONTRIBUTION.modules()) {
            pages.addAll(module.pages());
        }
        return pages;
    }

    public static List<MenuDeclaration> allMenuTrees() {
        List<MenuDeclaration> menus = new ArrayList<>();
        for (ConsoleModuleDeclaration module : CONTRIBUTION.modules()) {
            menus.addAll(module.menuTree());
        }
        return menus;
    }

    private static ConsoleModuleDeclaration providerModule() {
        return module(
                descriptor(
                        MODULE_PROVIDER,
                        "nexmux-provider-list",
                        List.of("nexmux-provider-detail", "nexmux-provider-edit"),
                        I18nObject.of("en", "Provider", "zh", "提供商"),
                        I18nObject.of("en", "Upstream provider connections.", "zh", "上游提供商连接与凭证。")),
                List.of(MenuDeclaration.directory(
                        "nexmux-asset",
                        I18nObject.of("en", "Model assets", "zh", "模型资产"),
                        "database",
                        10,
                        List.of(MenuDeclaration.page(
                                "nexmux-asset-provider",
                                I18nObject.of("en", "Provider", "zh", "提供商"),
                                "cloud",
                                10,
                                "nexmux-provider-list")))));
    }

    private static ConsoleModuleDeclaration modelModule() {
        return module(
                descriptor(
                        MODULE_MODEL,
                        "nexmux-model-list",
                        List.of("nexmux-model-detail", "nexmux-model-edit"),
                        I18nObject.of("en", "Model", "zh", "模型"),
                        I18nObject.of("en", "Logical models and bindings.", "zh", "逻辑模型与绑定。")),
                List.of(MenuDeclaration.directory(
                        "nexmux-asset",
                        I18nObject.of("en", "Model assets", "zh", "模型资产"),
                        "database",
                        10,
                        List.of(MenuDeclaration.page(
                                "nexmux-asset-model",
                                I18nObject.of("en", "Model", "zh", "模型"),
                                "experiment",
                                20,
                                "nexmux-model-list")))));
    }

    private static ConsoleModuleDeclaration routeModule() {
        return module(
                descriptor(
                        MODULE_ROUTE,
                        "nexmux-route-list",
                        List.of("nexmux-route-detail", "nexmux-route-edit"),
                        I18nObject.of("en", "Route", "zh", "路由"),
                        I18nObject.of("en", "Model routing and publish.", "zh", "模型路由与发布。")),
                List.of(MenuDeclaration.directory(
                        "nexmux-route-dir",
                        I18nObject.of("en", "Routing", "zh", "模型路由"),
                        "fork",
                        20,
                        List.of(MenuDeclaration.page(
                                "nexmux-route",
                                I18nObject.of("en", "Route", "zh", "路由"),
                                "route",
                                10,
                                "nexmux-route-list")))));
    }

    private static ConsoleModuleDeclaration clientModule() {
        return module(
                descriptor(
                        MODULE_CLIENT,
                        "nexmux-client-list",
                        List.of("nexmux-client-detail", "nexmux-client-edit"),
                        I18nObject.of("en", "Client", "zh", "客户端"),
                        I18nObject.of("en", "API clients and grants.", "zh", "API 客户端与授权。")),
                List.of(MenuDeclaration.directory(
                        "nexmux-access",
                        I18nObject.of("en", "Access", "zh", "访问治理"),
                        "lock",
                        30,
                        List.of(MenuDeclaration.page(
                                "nexmux-access-client",
                                I18nObject.of("en", "Client", "zh", "客户端"),
                                "user",
                                10,
                                "nexmux-client-list")))));
    }

    private static ConsoleModuleDeclaration opsModule() {
        return module(
                descriptor(
                        MODULE_OPS,
                        "nexmux-ops-request-log-list",
                        List.of("nexmux-ops-request-log-detail"),
                        I18nObject.of("en", "Request logs", "zh", "调用日志"),
                        I18nObject.of("en", "Runtime request logs (read-only).", "zh", "运行调用日志（只读）。")),
                List.of(MenuDeclaration.directory(
                        "nexmux-ops",
                        I18nObject.of("en", "Operations", "zh", "运行管理"),
                        "monitor",
                        40,
                        List.of(MenuDeclaration.page(
                                "nexmux-ops-request-log",
                                I18nObject.of("en", "Request logs", "zh", "调用日志"),
                                "file-text",
                                10,
                                "nexmux-ops-request-log-list")))));
    }

    private static ConsoleModuleDeclaration debugModule() {
        return module(
                descriptor(
                        MODULE_DEBUG,
                        "nexmux-debug-client",
                        List.of("nexmux-debug-model"),
                        I18nObject.of("en", "Debug", "zh", "调试台"),
                        I18nObject.of("en", "Model and client debug workbench.", "zh", "模型与客户端调试。")),
                List.of(MenuDeclaration.directory(
                        "nexmux-debug",
                        I18nObject.of("en", "Debug", "zh", "调试台"),
                        "bug",
                        50,
                        List.of(
                                MenuDeclaration.page(
                                        "nexmux-debug-client",
                                        I18nObject.of("en", "Client debug", "zh", "客户端调试"),
                                        "api",
                                        10,
                                        "nexmux-debug-client"),
                                MenuDeclaration.page(
                                        "nexmux-debug-model",
                                        I18nObject.of("en", "Model debug", "zh", "模型调试"),
                                        "code",
                                        20,
                                        "nexmux-debug-model")))));
    }

    private static ConsoleModuleDeclaration auditModule() {
        return module(
                descriptor(
                        MODULE_AUDIT,
                        "nexmux-audit-list",
                        List.of("nexmux-audit-detail"),
                        I18nObject.of("en", "Audit", "zh", "审计"),
                        I18nObject.of("en", "Configuration audit trail.", "zh", "配置变更审计。")),
                List.of(MenuDeclaration.directory(
                        "nexmux-system",
                        I18nObject.of("en", "System", "zh", "系统管理"),
                        "setting",
                        60,
                        List.of(MenuDeclaration.page(
                                "nexmux-system-audit",
                                I18nObject.of("en", "Audit", "zh", "审计"),
                                "audit",
                                10,
                                "nexmux-audit-list")))));
    }

    private static ConsoleModuleDescriptor descriptor(
            String moduleKey,
            String entryPageKey,
            List<String> additionalPageKeys,
            I18nObject displayName,
            I18nObject description) {
        return ConsoleModuleDescriptor.builtin(
                PLUGIN_ID,
                DOMAIN_KEY,
                moduleKey,
                entryPageKey,
                null,
                0,
                displayName,
                description,
                displayName,
                additionalPageKeys,
                List.of());
    }

    private static ConsoleModuleDeclaration module(
            ConsoleModuleDescriptor descriptor, List<MenuDeclaration> menuTree) {
        List<UiSpecPageDeclaration> pages = new ArrayList<>();
        for (String pageKey : descriptor.allPageKeys()) {
            pages.add(new UiSpecPageDeclaration(
                    pageKey,
                    ConsoleModuleDescriptor.pagePath(descriptor.domainKey(), descriptor.moduleKey(), pageKey),
                    List.of()));
        }
        return new ConsoleModuleDeclaration(
                descriptor.domainKey(),
                descriptor.moduleKey(),
                descriptor.displayName(),
                descriptor.description(),
                List.copyOf(pages),
                menuTree);
    }
}
