package com.innospots.nexmux.core.catalog.preset.loader;

import com.innospots.nexmux.core.catalog.preset.config.ProviderModelPresetsDocument;
import com.innospots.nexmux.core.catalog.preset.config.ProviderPresetsDocument;
import com.innospots.nexmux.core.catalog.preset.parser.ProviderModelPresetYamlParser;
import com.innospots.nexus.base.exception.NexusException;
import com.innospots.nexus.base.status.NexusStatusCode;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.apache.commons.io.IOUtils;

/**
 * 从 classpath {@code provider-models/} 加载供应商与模型预设。
 */
public final class ProviderModelPresetLoader {

    public static final String PROVIDER_PRESETS_RESOURCE = "provider-models/provider-presets.yaml";
    public static final String MODELS_RESOURCE_PATTERN = "provider-models/models/%s.yaml";

    private final ProviderModelPresetYamlParser parser;
    private final ClassLoader classLoader;

    public ProviderModelPresetLoader(ProviderModelPresetYamlParser parser) {
        this(parser, Thread.currentThread().getContextClassLoader());
    }

    public ProviderModelPresetLoader(ProviderModelPresetYamlParser parser, ClassLoader classLoader) {
        if (parser == null) {
            throw NexusException.build(NexusStatusCode.CONFIG_ERROR.fullCode(), "Provider model preset parser is required");
        }
        if (classLoader == null) {
            throw NexusException.build(NexusStatusCode.CONFIG_ERROR.fullCode(), "ClassLoader is required");
        }
        this.parser = parser;
        this.classLoader = classLoader;
    }

    public ProviderPresetsDocument loadProviderPresets() {
        return parser.parseProviderPresets(readResource(PROVIDER_PRESETS_RESOURCE));
    }

    public ProviderModelPresetsDocument loadModelPresets(String providerCode) {
        if (providerCode == null || providerCode.isBlank()) {
            throw NexusException.build(NexusStatusCode.CONFIG_ERROR.fullCode(), "providerCode is required");
        }
        String resource = String.format(MODELS_RESOURCE_PATTERN, providerCode.trim());
        return parser.parseModelPresets(readResource(resource));
    }

    private String readResource(String resourcePath) {
        try (InputStream inputStream = classLoader.getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw NexusException.build(
                        NexusStatusCode.CONFIG_ERROR.fullCode(),
                        "Preset resource not found: " + resourcePath);
            }
            return IOUtils.toString(inputStream, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw NexusException.build(
                    NexusStatusCode.CONFIG_ERROR.fullCode(),
                    "Cannot read preset resource: " + resourcePath,
                    exception);
        }
    }
}