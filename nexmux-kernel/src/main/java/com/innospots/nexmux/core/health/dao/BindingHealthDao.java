package com.innospots.nexmux.core.health.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.innospots.nexmux.core.health.domain.entity.BindingHealthEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * {@link BindingHealthEntity} 单表访问。
 *
 * <p>写责归 Runtime 进程，Console 侧只读；跨表编排由
 * {@link com.innospots.nexmux.core.health.operator.BindingHealthOperator} 承担。</p>
 *
 * @author Smars
 * @date 2026/11/22
 * @see BindingHealthEntity
 */
@Mapper
public interface BindingHealthDao extends BaseMapper<BindingHealthEntity> {
}
