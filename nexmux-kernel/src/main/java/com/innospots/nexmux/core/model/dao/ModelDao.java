package com.innospots.nexmux.core.model.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.innospots.nexmux.core.model.domain.entity.ModelEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ModelDao extends BaseMapper<ModelEntity> {
}
