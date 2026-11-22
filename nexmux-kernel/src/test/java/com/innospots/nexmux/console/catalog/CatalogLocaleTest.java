package com.innospots.nexmux.console.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.Test;

class CatalogLocaleTest {

    @Test
    void resolvesChineseAndEnglishTags() {
        assertThat(CatalogLocale.resolve("zh-CN,zh;q=0.9")).isEqualTo(Locale.SIMPLIFIED_CHINESE);
        assertThat(CatalogLocale.resolve("en-US,en;q=0.9")).isEqualTo(Locale.US);
        assertThat(CatalogLocale.resolve("en")).isEqualTo(Locale.US);
    }

    @Test
    void fallsBackToChineseForUnknownOrEmptyHeader() {
        assertThat(CatalogLocale.resolve(null)).isEqualTo(Locale.SIMPLIFIED_CHINESE);
        assertThat(CatalogLocale.resolve("  ")).isEqualTo(Locale.SIMPLIFIED_CHINESE);
        assertThat(CatalogLocale.resolve("ja-JP")).isEqualTo(Locale.SIMPLIFIED_CHINESE);
    }

    @Test
    void picksFirstSupportedLanguageFromChain() {
        assertThat(CatalogLocale.resolve("ja-JP,en-GB;q=0.8,zh;q=0.5")).isEqualTo(Locale.US);
    }

    @Test
    void derivesChatUrlOnlyWhenBaseUrlIsConcrete() {
        assertThat(PresetEndpointUrls.chatUrl("https://api.openai.com/v1", "openai-completions"))
                .isEqualTo("https://api.openai.com/v1/chat/completions");
        assertThat(PresetEndpointUrls.chatUrl("https://api.anthropic.com/", "anthropic-messages"))
                .isEqualTo("https://api.anthropic.com/v1/messages");
        assertThat(PresetEndpointUrls.chatUrl("https://{resource}.openai.azure.com", "azure-openai-responses"))
                .isNull();
        assertThat(PresetEndpointUrls.chatUrl("https://bedrock-runtime.us-east-1.amazonaws.com",
                "bedrock-converse-stream")).isNull();
    }
}
