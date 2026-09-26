package com.innospots.nexmux.core.model.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.innospots.nexus.core.persistence.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = ModelEntity.TABLE_NAME, indexes = {
        @Index(name = "uk_mux_model_code", columnList = "model_code", unique = true)
})
@TableName(ModelEntity.TABLE_NAME)
public class ModelEntity extends BaseEntity {

    public static final String TABLE_NAME = "mux_model";

    @TableId(type = IdType.ASSIGN_UUID)
    @Id
    @Column(length = 32, nullable = false)
    private String modelId;

    @Override
    public String idPrefix() {
        return "md";
    }

    @Column(length = 64, nullable = false)
    private String modelCode;

    @Column(length = 128, nullable = false)
    private String displayName;

    @Column(length = 32, nullable = false)
    private String modelType;

    @Column(length = 16, nullable = false)
    private String status;

    @Column(length = 1024)
    private String iconUri;

    private LocalDate releaseDate;

    private Long contextLength;

    private Long maxOutputTokens;

    @Column(length = 256)
    private String inputModalities;

    @Column(length = 256)
    private String outputModalities;

    @Column(columnDefinition = "CLOB")
    private String capabilityTags;

    @Column(columnDefinition = "CLOB")
    private String tags;

    @Column(length = 2000)
    private String description;

    @Column(name = "metadata", columnDefinition = "CLOB")
    private String metadataSpec;

    @Column(columnDefinition = "CLOB")
    private String defaultParams;

    @Column(length = 32)
    private String primaryProviderId;

    @Column(length = 256)
    private String primaryProviderModelId;

    @Column(length = 32)
    private String primaryUpstreamProtocol;

    private Integer primaryBindingWeight;

    @Column(precision = 10, scale = 4)
    private BigDecimal primaryCostMultiplier;

    @Column(precision = 10, scale = 4)
    private BigDecimal primaryReferenceMultiplier;

    @Column(nullable = false)
    private Boolean primaryBindingEnabled;

    @Column(columnDefinition = "CLOB", nullable = false)
    private String extraBindings;

    @Column(columnDefinition = "CLOB")
    private String pricingSpec;
}
