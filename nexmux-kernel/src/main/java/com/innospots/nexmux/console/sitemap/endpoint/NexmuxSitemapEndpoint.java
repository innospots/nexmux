package com.innospots.nexmux.console.sitemap.endpoint;

import com.innospots.nexus.base.domain.response.R;
import com.innospots.nexus.console.config.ConsoleConstant;
import com.innospots.nexus.console.sitemap.domain.vo.SitemapResource;
import com.innospots.nexus.console.sitemap.endpoint.SitemapEndpoint;
import com.innospots.nexmux.console.entry.NexmuxConsoleEntryPlugin;
import com.innospots.nexmux.console.sitemap.service.NexmuxSitemapService;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * Nexmux 领域 sitemap：从 {@code ui-pages/nexmux/sitemap.yaml} 加载并渲染。
 */
@Path(ConsoleConstant.PUBLIC_API_PREFIX + "/sitemap/" + NexmuxConsoleEntryPlugin.DOMAIN_KEY)
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "UiSitemap", description = "动态页面 Sitemap 加载与渲染")
public final class NexmuxSitemapEndpoint implements SitemapEndpoint {

    private final NexmuxSitemapService nexmuxSitemapService;

    public NexmuxSitemapEndpoint(NexmuxSitemapService nexmuxSitemapService) {
        this.nexmuxSitemapService = nexmuxSitemapService;
    }

    @GET
    @Operation(
            operationId = "uiSitemapRenderNexmux",
            summary = "加载并渲染 Nexmux Sitemap",
            description = "固定加载 ui-pages/nexmux/sitemap.yaml；当前返回配置全量。")
    public R<SitemapResource> renderHttp() {
        return R.ok(render());
    }

    @Override
    public SitemapResource render() {
        return nexmuxSitemapService.render();
    }
}
