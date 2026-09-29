package com.innospots.nexmux.console.sitemap.service;

import com.innospots.nexus.base.exception.NexusException;
import com.innospots.nexus.base.status.NexusStatusCode;
import com.innospots.nexus.console.sitemap.domain.config.SitemapConfig;
import com.innospots.nexus.console.sitemap.domain.vo.SitemapResource;
import com.innospots.nexus.console.sitemap.loader.SitemapConfigLoader;
import com.innospots.nexus.console.sitemap.service.SitemapMapper;
import com.innospots.nexmux.console.entry.NexmuxConsoleEntryPlugin;

/**
 * Nexmux 领域 sitemap 渲染（加载 {@code ui-pages/nexmux/sitemap.yaml}，当前全量返回）。
 */
public final class NexmuxSitemapService {

    private final SitemapConfigLoader configLoader;
    private final SitemapMapper mapper;

    public NexmuxSitemapService(SitemapConfigLoader configLoader, SitemapMapper mapper) {
        if (configLoader == null || mapper == null) {
            throw NexusException.build(
                    NexusStatusCode.CONFIG_ERROR.fullCode(),
                    "Nexmux sitemap configLoader and mapper are required");
        }
        this.configLoader = configLoader;
        this.mapper = mapper;
    }

    /**
     * 渲染 Nexmux 产品 sitemap。
     */
    public SitemapResource render() {
        SitemapConfig config = configLoader.load(NexmuxConsoleEntryPlugin.DOMAIN_KEY);
        return mapper.toResource(config);
    }
}
