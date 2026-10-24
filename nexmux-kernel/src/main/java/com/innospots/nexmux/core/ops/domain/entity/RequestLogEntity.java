package com.innospots.nexmux.core.ops.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.innospots.nexus.core.persistence.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = RequestLogEntity.TABLE_NAME)
@TableName(RequestLogEntity.TABLE_NAME)
public class RequestLogEntity extends BaseEntity {

    public static final String TABLE_NAME = "mux_request_log";

    @TableId(type = IdType.ASSIGN_UUID)
    @Id
    @Column(length = 64, nullable = false)
    private String requestId;

    @Override
    public String idPrefix() {
        return "rq";
    }

    @Column(length = 32)
    private String clientId;

    @Column(length = 32)
    private String routeId;

    @Column(length = 64)
    private String modelCode;

    @Column(length = 16, nullable = false)
    private String status;

    private Long latencyMs;

    @Column(length = 64)
    private String errorCode;

    @Column(columnDefinition = "CLOB")
    private String routeTrace;

    private LocalDateTime requestedAt;

    @Column(length = 64)
    private String appKeyId;

    @Column(length = 128)
    private String userEmail;

    @Column(length = 64)
    private String providerCode;

    @Column(length = 64)
    private String protocol;

    @Column(length = 64)
    private String routeGroup;

    @Column(length = 128)
    private String application;

    @Column(length = 64)
    private String alias;

    private Integer httpStatus;

    private Long inputTokens;

    private Long outputTokens;

    private Long cacheReadTokens;

    private Long cacheWriteTokens;

    @Column(precision = 18, scale = 8)
    private java.math.BigDecimal standardUsd;

    @Column(precision = 18, scale = 8)
    private java.math.BigDecimal costUsd;

    @Column(precision = 18, scale = 8)
    private java.math.BigDecimal billingUsd;

    private Long ttftMs;

    @Column(length = 256)
    private String upstreamModel;

    @Column(length = 1024)
    private String upstreamEndpoint;

    @Column(length = 16)
    private String upstreamStatus;

    private Long upstreamLatencyMs;

    private Integer retries;

    @Column(length = 64)
    private String clientIp;

    @Column(length = 16)
    private String ingressMethod;

    @Column(length = 512)
    private String ingressPath;

    @Column(length = 64)
    private String traceId;

    @Column(length = 64)
    private String errorType;

    @Column(columnDefinition = "CLOB")
    private String pricingSnapshot;

    @Column(columnDefinition = "CLOB")
    private String promptSnapshot;

    /** 写入来源：{@code RUNTIME}（数据面真实流量，默认）/ {@code DEBUG}（Console 调试台，ADR-0001 审计 A1 修复）。 */
    @Column(length = 16)
    private String source;
}
