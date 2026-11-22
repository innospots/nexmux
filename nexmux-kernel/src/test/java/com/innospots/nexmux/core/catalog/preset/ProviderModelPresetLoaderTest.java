package com.innospots.nexmux.core.catalog.preset;

import static org.assertj.core.api.Assertions.assertThat;

import com.innospots.nexmux.core.catalog.preset.config.ModelPresetItem;
import com.innospots.nexmux.core.catalog.preset.config.ProviderPresetItem;
import com.innospots.nexmux.core.catalog.preset.config.ProviderPresetsDocument;
import com.innospots.nexmux.core.catalog.preset.loader.ProviderModelPresetLoader;
import com.innospots.nexmux.core.catalog.preset.parser.ProviderModelPresetYamlParser;
import com.innospots.nexmux.core.catalog.preset.service.ProviderModelPresetService;
import com.innospots.nexus.base.i18n.I18nObject;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ProviderModelPresetLoaderTest {

    private final ProviderModelPresetService service =
            new ProviderModelPresetService(new ProviderModelPresetLoader(new ProviderModelPresetYamlParser()));

    @Test
    void loadsProviderAndModelPresetsFromClasspath() {
        ProviderPresetsDocument providers = service.catalogMeta();
        assertThat(providers.getProviderCount()).isGreaterThan(40);
        assertThat(providers.getProviders()).isNotEmpty();
        ProviderPresetItem first = providers.getProviders().get(0);
        assertThat(first.getProviderCode()).isNotBlank();
        assertThat(first.getDisplayName()).isInstanceOf(I18nObject.class);
        assertThat(first.getDisplayName().enValue()).isNotBlank();

        var deepseek = service.loadModels("deepseek");
        assertThat(deepseek.getProviderCode()).isEqualTo("deepseek");
        assertThat(deepseek.getModels()).isNotEmpty();
        assertThat(deepseek.getModels().get(0).getProviderModelId()).isNotBlank();
        ModelPresetItem withDesc = deepseek.getModels().stream()
                .filter(m -> m.getDescription() != null && !m.getDescription().isEmpty())
                .findFirst()
                .orElseThrow();
        assertThat(withDesc.getDescription().cnValue()).isNotBlank();
    }

    @Test
    void resolvesLocalizedTextByLocale() {
        ProviderPresetItem aliyun = service.getProvider("aliyun");
        assertThat(PresetI18n.localized(aliyun.getDisplayName(), Locale.SIMPLIFIED_CHINESE)).isEqualTo("阿里云百炼");
        assertThat(PresetI18n.localized(aliyun.getDisplayName(), Locale.US))
                .isEqualTo("Alibaba Cloud Model Studio");
    }

    @Test
    void protocolsCoverModelPresets() {
        List<String> protocols = service.protocolsOf("openai");
        assertThat(protocols).isNotEmpty();
        assertThat(protocols).contains("openai-responses");
    }

    @Test
    void appendsOrderedSuffixWhenCodeIsTaken() {
        Set<String> used = new LinkedHashSet<>(List.of("deepseek"));
        assertThat(ProviderModelPresetService.nextAvailableCode("deepseek", used)).isEqualTo("deepseek-2");

        used.add("deepseek-2");
        assertThat(ProviderModelPresetService.nextAvailableCode("deepseek", used)).isEqualTo("deepseek-3");
        assertThat(ProviderModelPresetService.nextAvailableCode("openai", used)).isEqualTo("openai");
    }

    @Test
    void unknownProviderCodeResolvesToNotFound() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.getProvider("missing-preset"))
                .isInstanceOf(com.innospots.nexus.base.exception.NexusException.class);
    }
}
