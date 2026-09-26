package com.innospots.nexmux.core.ops.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.innospots.nexmux.core.ops.domain.entity.RequestLogEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RequestLogDao extends BaseMapper<RequestLogEntity> {
}
