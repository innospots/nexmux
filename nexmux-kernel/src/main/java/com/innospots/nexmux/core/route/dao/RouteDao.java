package com.innospots.nexmux.core.route.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.innospots.nexmux.core.route.domain.entity.RouteEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RouteDao extends BaseMapper<RouteEntity> {
}
