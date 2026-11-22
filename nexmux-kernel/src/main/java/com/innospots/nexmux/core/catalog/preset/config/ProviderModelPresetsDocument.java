package com.innospots.nexmux.core.catalog.preset.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.List;

/**
 * 按 provider 分文件的模型预设文档。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProviderModelPresetsDocument {

    private String apiVersion;
    private String providerCode;
    private int modelCount;
    private List<ModelPresetItem> models = new ArrayList<>();

    public String getApiVersion() {
        return apiVersion;
    }

    public void setApiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
    }

    public String getProviderCode() {
        return providerCode;
    }

    public void setProviderCode(String providerCode) {
        this.providerCode = providerCode;
    }

    public int getModelCount() {
        return modelCount;
    }

    public void setModelCount(int modelCount) {
        this.modelCount = modelCount;
    }

    public List<ModelPresetItem> getModels() {
        return models;
    }

    public void setModels(List<ModelPresetItem> models) {
        this.models = models == null ? new ArrayList<>() : models;
    }
}