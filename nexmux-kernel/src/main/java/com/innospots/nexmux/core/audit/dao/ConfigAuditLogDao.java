package com.innospots.nexmux.core.audit.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.innospots.nexmux.core.audit.domain.entity.ConfigAuditLogEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ConfigAuditLogDao extends BaseMapper<ConfigAuditLogEntity> {
}
