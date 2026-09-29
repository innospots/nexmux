package com.innospots.nexmux.console.sitemap;

import static org.assertj.core.api.Assertions.assertThat;

import com.innospots.nexus.console.sitemap.config.SitemapYamlConfig;
import com.innospots.nexus.console.sitemap.domain.config.SitemapConfig;
import com.innospots.nexus.console.sitemap.domain.vo.SitemapResource;
import com.innospots.nexus.console.sitemap.loader.SitemapConfigLoader;
import com.innospots.nexus.console.sitemap.parser.SitemapYamlParser;
import com.innospots.nexus.console.sitemap.service.SitemapMapper;
import com.innospots.nexmux.console.sitemap.service.NexmuxSitemapService;
import com.innospots.nexus.console.sitemap.domain.model.SitemapPageDescriptor;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class NexmuxSitemapLoadTest {

    @Test
    void loadsNexmuxSitemapYaml() {
        SitemapConfigLoader loader = new SitemapConfigLoader(
                SitemapYamlConfig.defaults(),
                new SitemapYamlParser(),
                NexmuxSitemapLoadTest.class.getClassLoader());
        SitemapConfig config = loader.load("nexmux");

        assertThat(config.getId()).isEqualTo("site");
        assertThat(config.getResourceType()).isEqualTo("sitemap");
        assertThat(config.getPages()).isNotEmpty();
        assertThat(config.getMenus()).isNotEmpty();
        assertThat(config.getLayouts()).containsKeys("admin", "login", "blank");
    }

    @Test
    void pageIdsMatchClasspathPageYamlBasenames() throws IOException, URISyntaxException {
        SitemapConfigLoader loader = new SitemapConfigLoader(
                SitemapYamlConfig.defaults(),
                new SitemapYamlParser(),
                NexmuxSitemapLoadTest.class.getClassLoader());
        SitemapConfig config = loader.load("nexmux");

        Set<String> pageKeys = new HashSet<>();
        URL root = NexmuxSitemapLoadTest.class.getResource("/ui-pages/nexmux");
        assertThat(root).isNotNull();
        try (Stream<Path> paths = Files.walk(Path.of(root.toURI()))) {
            paths.filter(path -> path.toString().endsWith(".yaml"))
                    .filter(path -> !path.getFileName().toString().equals("sitemap.yaml"))
                    .forEach(path -> pageKeys.add(
                            path.getFileName().toString().replace(".yaml", "")));
        }

        assertThat(config.getPages()).extracting(SitemapPageDescriptor::getId).containsExactlyInAnyOrderElementsOf(pageKeys);
    }

    @Test
    void rendersThroughNexmuxSitemapService() {
        NexmuxSitemapService service = new NexmuxSitemapService(
                new SitemapConfigLoader(
                        SitemapYamlConfig.defaults(),
                        new SitemapYamlParser(),
                        NexmuxSitemapLoadTest.class.getClassLoader()),
                new SitemapMapper());
        SitemapResource resource = service.render();

        assertThat(resource.getId()).isEqualTo("site");
        assertThat(resource.getPages()).isNotEmpty();
        assertThat(resource.getLayouts().get("admin").getBody().getType()).isEqualTo("Layout");
    }
}
