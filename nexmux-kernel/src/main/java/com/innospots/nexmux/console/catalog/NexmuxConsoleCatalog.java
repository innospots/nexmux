package com.innospots.nexmux.console.catalog;

import com.innospots.nexus.base.i18n.I18nObject;
import com.innospots.nexus.core.plugin.contribution.console.ConsoleModuleDeclaration;
import com.innospots.nexus.core.plugin.contribution.console.ConsolePluginContribution;
import com.innospots.nexus.core.plugin.contribution.console.MenuDeclaration;
import com.innospots.nexus.core.plugin.contribution.console.UiSpecPageDeclaration;
import java.util.ArrayList;
import java.util.List;

public final class NexmuxConsoleCatalog {

    public static final String MODULE_KEY = "nexmux";

    private NexmuxConsoleCatalog() {
    }

    public static ConsolePluginContribution contribution() {
        return new ConsolePluginContribution(List.of(module()));
    }

    private static ConsoleModuleDeclaration module() {
        return new ConsoleModuleDeclaration(
                MODULE_KEY,
                I18nObject.of("en", "Nexmux", "zh", "Nexmux 模型管理"),
                I18nObject.of("en", "Model console for Nexmux.", "zh", "Nexmux 模型管理控制台。"),
                pages(),
                menuTree());
    }

    private static List<UiSpecPageDeclaration> pages() {
        List<UiSpecPageDeclaration> pages = new ArrayList<>();
        pages.add(page("nexmux-provider-list", "/page/nexmux/provider"));
        pages.add(page("nexmux-provider-detail", "/page/nexmux/provider/{providerCode}"));
        pages.add(page("nexmux-provider-edit", "/page/nexmux/provider/{providerCode}/edit"));
        pages.add(page("nexmux-model-list", "/page/nexmux/model"));
        pages.add(page("nexmux-model-detail", "/page/nexmux/model/{modelCode}"));
        pages.add(page("nexmux-model-edit", "/page/nexmux/model/{modelCode}/edit"));
        pages.add(page("nexmux-route-list", "/page/nexmux/route"));
        pages.add(page("nexmux-route-detail", "/page/nexmux/route/{alias}"));
        pages.add(page("nexmux-route-edit", "/page/nexmux/route/{alias}/edit"));
        pages.add(page("nexmux-client-list", "/page/nexmux/access/client"));
        pages.add(page("nexmux-client-detail", "/page/nexmux/access/client/{clientCode}"));
        pages.add(page("nexmux-client-edit", "/page/nexmux/access/client/{clientCode}/edit"));
        pages.add(page("nexmux-request-log-list", "/page/nexmux/ops/request-log"));
        pages.add(page("nexmux-request-log-detail", "/page/nexmux/ops/request-log/{requestId}"));
        pages.add(page("nexmux-debug-client", "/page/nexmux/debug/client"));
        pages.add(page("nexmux-debug-model", "/page/nexmux/debug/model"));
        pages.add(page("nexmux-audit-list", "/page/nexmux/system/audit"));
        pages.add(page("nexmux-audit-detail", "/page/nexmux/system/audit/{auditId}"));
        return pages;
    }

    private static UiSpecPageDeclaration page(String pageKey, String pagePath) {
        return new UiSpecPageDeclaration(pageKey, pagePath, List.of());
    }

    private static List<MenuDeclaration> menuTree() {
        return List.of(MenuDeclaration.directory(
                "nexmux-root",
                I18nObject.of("en", "Nexmux", "zh", "Nexmux"),
                "appstore",
                10,
                List.of(
                        MenuDeclaration.directory(
                                "nexmux-asset",
                                I18nObject.of("en", "Assets", "zh", "模型资产"),
                                "database",
                                10,
                                List.of(
                                        MenuDeclaration.page(
                                                "nexmux-asset-provider",
                                                I18nObject.of("en", "Provider", "zh", "Provider"),
                                                "cloud",
                                                10,
                                                "nexmux-provider-list"),
                                        MenuDeclaration.page(
                                                "nexmux-asset-model",
                                                I18nObject.of("en", "Model", "zh", "模型"),
                                                "experiment",
                                                20,
                                                "nexmux-model-list"))),
                        MenuDeclaration.directory(
                                "nexmux-route-dir",
                                I18nObject.of("en", "Routing", "zh", "模型路由"),
                                "fork",
                                20,
                                List.of(MenuDeclaration.page(
                                        "nexmux-route",
                                        I18nObject.of("en", "Route", "zh", "路由"),
                                        "route",
                                        10,
                                        "nexmux-route-list"))),
                        MenuDeclaration.directory(
                                "nexmux-access",
                                I18nObject.of("en", "Access", "zh", "访问治理"),
                                "lock",
                                30,
                                List.of(MenuDeclaration.page(
                                        "nexmux-access-client",
                                        I18nObject.of("en", "Client", "zh", "客户端"),
                                        "user",
                                        10,
                                        "nexmux-client-list"))),
                        MenuDeclaration.directory(
                                "nexmux-ops",
                                I18nObject.of("en", "Operations", "zh", "运行管理"),
                                "monitor",
                                40,
                                List.of(MenuDeclaration.page(
                                        "nexmux-ops-request-log",
                                        I18nObject.of("en", "Request logs", "zh", "调用日志"),
                                        "file-text",
                                        10,
                                        "nexmux-request-log-list"))),
                        MenuDeclaration.directory(
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
                                                "nexmux-debug-model"))),
                        MenuDeclaration.directory(
                                "nexmux-system",
                                I18nObject.of("en", "System", "zh", "系统管理"),
                                "setting",
                                60,
                                List.of(MenuDeclaration.page(
                                        "nexmux-system-audit",
                                        I18nObject.of("en", "Audit", "zh", "审计"),
                                        "audit",
                                        10,
                                        "nexmux-audit-list"))))));
    }
}
