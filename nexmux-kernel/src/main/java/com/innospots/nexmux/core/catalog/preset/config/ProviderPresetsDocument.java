package com.innospots.nexmux.core.catalog.preset.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code provider-models/provider-presets.yaml} 根文档。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProviderPresetsDocument {

    private String apiVersion;
    private String updatedAt;
    private int providerCount;
    private int modelCount;
    private List<ProviderPresetItem> providers = new ArrayList<>();

    public String getApiVersion() {
        return apiVersion;
    }

    public void setApiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    public int getProviderCount() {
        return providerCount;
    }

    public void setProviderCount(int providerCount) {
        this.providerCount = providerCount;
    }

    public int getModelCount() {
        return modelCount;
    }

    public void setModelCount(int modelCount) {
        this.modelCount = modelCount;
    }

    public List<ProviderPresetItem> getProviders() {
        return providers;
    }

    public void setProviders(List<ProviderPresetItem> providers) {
        this.providers = providers == null ? new ArrayList<>() : providers;
    }
}