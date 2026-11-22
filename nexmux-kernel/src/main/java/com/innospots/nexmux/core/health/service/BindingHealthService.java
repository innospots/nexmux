package com.innospots.nexmux.core.health.service;

import com.innospots.nexmux.core.health.domain.entity.BindingHealthEntity;
import com.innospots.nexmux.core.health.domain.enums.HealthStatus;
import com.innospots.nexmux.core.health.operator.BindingHealthOperator;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;

/**
 * Binding 健康工作流：进程内状态机 + 持久化结果的组合查询。
 *
 * <p>Console 侧以 {@link #listAvailability()} 读取探活结果；Runtime 侧探测完成后通过
 * {@link #recordProbe} 写入（状态由 {@link BindingHealthRegistry} 判定）。</p>
 *
 * @author Smars
 * @date 2026/11/22
 * @see BindingHealthRegistry
 * @see BindingHealthOperator
 */
@RequiredArgsConstructor
public class BindingHealthService {

    private final BindingHealthRegistry bindingHealthRegistry;
    private final BindingHealthOperator bindingHealthOperator;

    /**
     * 记录一轮探测结果并落库。
     *
     * @param target     探测目标
     * @param modelCode  本地模型编码（冗余列，可空）
     * @param probeOk    探测是否成功
     * @param latencyMs  探测延迟（毫秒）
     * @param error      失败原因（成功时传 {@code null}）
     */
    public void recordProbe(BindingHealthRegistry.Target target, String modelCode,
                            boolean probeOk, long latencyMs, String error) {
        HealthStatus status = probeOk
                ? bindingHealthRegistry.recordSuccess(target, latencyMs)
                : bindingHealthRegistry.recordFailure(target, error);
        BindingHealthRegistry.State state = bindingHealthRegistry.stateOf(target)
                .orElseGet(() -> new BindingHealthRegistry.State(
                        status, 0, probeOk ? latencyMs : null, error, LocalDateTime.now()));
        String probeId = bindingHealthOperator
                .findByTarget(target.providerId(), target.providerModelId())
                .map(BindingHealthEntity::getProbeId)
                .orElse(null);
        bindingHealthOperator.saveProbe(
                probeId,
                target.providerId(),
                target.providerModelId(),
                modelCode,
                state.status().name(),
                state.consecutiveFailures(),
                state.lastProbeAt(),
                state.lastLatencyMs(),
                state.lastError());
    }

    /**
     * 全部健康记录（Console 可用性页数据源）。
     *
     * @return 记录列表
     */
    public List<BindingHealthEntity> listAvailability() {
        return bindingHealthOperator.listAll();
    }
}
