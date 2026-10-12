package com.innospots.nexmux.inbound.runtime;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * {@link BindingHealthEntity} Dao（数据面写责；Console 侧仅读）。
 */
@Mapper
public interface BindingHealthDao extends BaseMapper<BindingHealthEntity> {
}
