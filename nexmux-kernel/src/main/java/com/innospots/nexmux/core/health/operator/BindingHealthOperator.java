package com.innospots.nexmux.core.health.operator;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.innospots.nexmux.core.health.dao.BindingHealthDao;
import com.innospots.nexmux.core.health.domain.entity.BindingHealthEntity;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;

/**
 * Binding 健康结果的数据操作边界（单表 {@code mux_binding_health}）。
 *
 * <p>Runtime 侧以「探测结果 upsert」写入，Console 侧以只读查询消费；
 * 不承担探测与状态迁移（见 {@code core.health.service.BindingHealthRegistry}）。</p>
 *
 * @author Smars
 * @date 2026/11/22
 * @see BindingHealthEntity
 */
@RequiredArgsConstructor
public class BindingHealthOperator {

    private final BindingHealthDao bindingHealthDao;

    /**
     * 按探测目标查找记录。
     *
     * @param providerId      供应商 ID
     * @param providerModelId 上游模型 ID
     * @return 命中记录；不存在时为空
     */
    public Optional<BindingHealthEntity> findByTarget(String providerId, String providerModelId) {
        return Optional.ofNullable(bindingHealthDao.selectOne(new LambdaQueryWrapper<BindingHealthEntity>()
                .eq(BindingHealthEntity::getProviderId, providerId)
                .eq(BindingHealthEntity::getProviderModelId, providerModelId)));
    }

    /**
     * 列出全部健康记录（Console 可用性聚合用）。
     *
     * @return 记录列表
     */
    public List<BindingHealthEntity> listAll() {
        return bindingHealthDao.selectList(null);
    }

    /**
     * 写入或更新一条探测结果（按 {@code uk(provider_id, provider_model_id)} 幂等）。
     *
     * @param probeId             记录 ID；为空表示新增
     * @param providerId          供应商 ID
     * @param providerModelId     上游模型 ID
     * @param modelCode           本地模型编码（冗余列，可空）
     * @param healthStatus        健康状态枚举名
     * @param consecutiveFailures 连续失败次数
     * @param lastProbeAt         探测时间
     * @param lastLatencyMs       探测延迟（毫秒，可空）
     * @param lastError           最近错误（可空）
     */
    public void saveProbe(String probeId, String providerId, String providerModelId, String modelCode,
                          String healthStatus, int consecutiveFailures, LocalDateTime lastProbeAt,
                          Long lastLatencyMs, String lastError) {
        BindingHealthEntity entity = probeId == null ? null : bindingHealthDao.selectById(probeId);
        if (entity == null) {
            entity = new BindingHealthEntity();
            entity.setProviderId(providerId);
            entity.setProviderModelId(providerModelId);
        }
        entity.setModelCode(modelCode);
        entity.setHealthStatus(healthStatus);
        entity.setConsecutiveFailures(consecutiveFailures);
        entity.setLastProbeAt(lastProbeAt);
        entity.setLastLatencyMs(lastLatencyMs);
        entity.setLastError(lastError);
        if (entity.getProbeId() == null) {
            bindingHealthDao.insert(entity);
        } else {
            bindingHealthDao.updateById(entity);
        }
    }
}
