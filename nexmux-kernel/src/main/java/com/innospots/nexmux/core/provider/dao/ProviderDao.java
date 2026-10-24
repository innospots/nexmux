package com.innospots.nexmux.core.provider.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.innospots.nexmux.core.provider.domain.entity.ProviderEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProviderDao extends BaseMapper<ProviderEntity> {
}
