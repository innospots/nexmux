package com.innospots.nexmux.core.catalog.preset.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.innospots.nexus.base.i18n.I18nObject;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * {@code provider-models/models/<providerCode>.yaml} 中的模型预设。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(name = "ModelPresetItem", description = "模型预设条目")
public class ModelPresetItem {

    private String modelCode;
    private I18nObject displayName;
    private String modelType;
    private String providerModelId;
    private String primaryUpstreamProtocol;
    private Long contextLength;
    private Long maxOutputTokens;
    private List<String> inputModalities = new ArrayList<>();
    private List<String> outputModalities = new ArrayList<>();
    private JsonNode capabilityTags;
    private List<String> tags = new ArrayList<>();
    private I18nObject description;
    private JsonNode metadataSpec;
    private JsonNode pricingSpec;
    private String releaseDate;

    public String getModelCode() {
        return modelCode;
    }

    public void setModelCode(String modelCode) {
        this.modelCode = modelCode;
    }

    public I18nObject getDisplayName() {
        return displayName;
    }

    public void setDisplayName(I18nObject displayName) {
        this.displayName = displayName;
    }

    public String getModelType() {
        return modelType;
    }

    public void setModelType(String modelType) {
        this.modelType = modelType;
    }

    public String getProviderModelId() {
        return providerModelId;
    }

    public void setProviderModelId(String providerModelId) {
        this.providerModelId = providerModelId;
    }

    public String getPrimaryUpstreamProtocol() {
        return primaryUpstreamProtocol;
    }

    public void setPrimaryUpstreamProtocol(String primaryUpstreamProtocol) {
        this.primaryUpstreamProtocol = primaryUpstreamProtocol;
    }

    public Long getContextLength() {
        return contextLength;
    }

    public void setContextLength(Long contextLength) {
        this.contextLength = contextLength;
    }

    public Long getMaxOutputTokens() {
        return maxOutputTokens;
    }

    public void setMaxOutputTokens(Long maxOutputTokens) {
        this.maxOutputTokens = maxOutputTokens;
    }

    public List<String> getInputModalities() {
        return inputModalities;
    }

    public void setInputModalities(List<String> inputModalities) {
        this.inputModalities = inputModalities == null ? new ArrayList<>() : inputModalities;
    }

    public List<String> getOutputModalities() {
        return outputModalities;
    }

    public void setOutputModalities(List<String> outputModalities) {
        this.outputModalities = outputModalities == null ? new ArrayList<>() : outputModalities;
    }

    public JsonNode getCapabilityTags() {
        return capabilityTags;
    }

    public void setCapabilityTags(JsonNode capabilityTags) {
        this.capabilityTags = capabilityTags;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags == null ? new ArrayList<>() : tags;
    }

    public I18nObject getDescription() {
        return description;
    }

    public void setDescription(I18nObject description) {
        this.description = description;
    }

    public JsonNode getMetadataSpec() {
        return metadataSpec;
    }

    public void setMetadataSpec(JsonNode metadataSpec) {
        this.metadataSpec = metadataSpec;
    }

    public JsonNode getPricingSpec() {
        return pricingSpec;
    }

    public void setPricingSpec(JsonNode pricingSpec) {
        this.pricingSpec = pricingSpec;
    }

    public String getReleaseDate() {
        return releaseDate;
    }

    public void setReleaseDate(String releaseDate) {
        this.releaseDate = releaseDate;
    }
}