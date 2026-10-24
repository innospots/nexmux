package com.innospots.nexmux.inbound.runtime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.innospots.nexus.core.persistence.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * Binding 健康探测结果（数据面运行表，nexmux-runtime-design §6.1）：
 * Runtime 主动探活写责，Console 只读（供应商可用性页数据源）。
 * 健康状态不回写配置表 `mux_model.extra_bindings`（ADR-0001 §3）。
 */
@Getter
@Setter
@Entity
@Table(name = BindingHealthEntity.TABLE_NAME, indexes = {
        @Index(name = "uk_mux_binding_health_target",
                columnList = "provider_id, provider_model_id", unique = true)
})
@TableName(BindingHealthEntity.TABLE_NAME)
public class BindingHealthEntity extends BaseEntity {

    public static final String TABLE_NAME = "mux_binding_health";

    @TableId(type = IdType.ASSIGN_UUID)
    @Id
    @Column(length = 32, nullable = false)
    private String probeId;

    @Override
    public String idPrefix() {
        return "bh";
    }

    @Column(length = 32, nullable = false)
    private String providerId;

    @Column(length = 256, nullable = false)
    private String providerModelId;

    /** 冗余：便于 Console 按模型过滤（同一绑定服务多模型时以最近探测为准）。 */
    @Column(length = 64)
    private String modelCode;

    /** {@link HealthStatus} 名。 */
    @Column(length = 16, nullable = false)
    private String healthStatus;

    @Column(nullable = false)
    private Integer consecutiveFailures;

    private LocalDateTime lastProbeAt;

    private Long lastLatencyMs;

    @Column(length = 512)
    private String lastError;
}
