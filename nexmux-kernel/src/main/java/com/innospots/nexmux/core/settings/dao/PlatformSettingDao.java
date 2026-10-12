package com.innospots.nexmux.core.settings.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.innospots.nexmux.core.settings.domain.entity.PlatformSettingEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PlatformSettingDao extends BaseMapper<PlatformSettingEntity> {
}
