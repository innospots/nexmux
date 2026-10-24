package com.innospots.nexmux.core.access.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.innospots.nexmux.core.access.domain.entity.ClientEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ClientDao extends BaseMapper<ClientEntity> {
}
