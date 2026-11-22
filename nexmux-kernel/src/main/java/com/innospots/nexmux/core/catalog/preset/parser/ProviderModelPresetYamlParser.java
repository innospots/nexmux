package com.innospots.nexmux.core.catalog.preset.parser;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.innospots.nexmux.core.catalog.preset.config.ProviderModelPresetsDocument;
import com.innospots.nexmux.core.catalog.preset.config.ProviderPresetsDocument;
import com.innospots.nexus.base.exception.NexusException;
import com.innospots.nexus.base.json.I18nModule;
import com.innospots.nexus.base.status.NexusStatusCode;

/**
 * 解析 classpath provider-models YAML。
 */
public final class ProviderModelPresetYamlParser {

    private final ObjectMapper yamlMapper;

    public ProviderModelPresetYamlParser() {
        this.yamlMapper = new ObjectMapper(new YAMLFactory()
                .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER));
        this.yamlMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.yamlMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.yamlMapper.registerModule(new I18nModule());
    }

    public ProviderPresetsDocument parseProviderPresets(String content) {
        return parse(content, ProviderPresetsDocument.class, "provider-presets");
    }

    public ProviderModelPresetsDocument parseModelPresets(String content) {
        return parse(content, ProviderModelPresetsDocument.class, "model-presets");
    }

    private <T> T parse(String content, Class<T> type, String label) {
        if (content == null || content.isBlank()) {
            throw NexusException.build(NexusStatusCode.CONFIG_ERROR.fullCode(), label + " YAML is empty");
        }
        try {
            return yamlMapper.readValue(content, type);
        } catch (Exception exception) {
            throw NexusException.build(
                    NexusStatusCode.CONFIG_ERROR.fullCode(),
                    "Cannot parse " + label + " YAML",
                    exception);
        }
    }
}