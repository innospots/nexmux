package com.innospots.nexmux.core.stats.service;

import com.innospots.nexmux.core.stats.dao.StatsQueryDao;
import com.innospots.nexmux.core.stats.domain.vo.UsageRowVo;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class UsageStatsService {

    private static final String DIM_MODEL = "model_code";
    private static final String DIM_PROVIDER = "provider_code";
    private static final String DIM_CLIENT = "client_id";

    private final StatsQueryDao statsQueryDao;

    public List<UsageRowVo> usageByModel(LocalDateTime start, LocalDateTime end) {
        return StatsMapping.toUsageRows(statsQueryDao.usageByDimension(start, end, DIM_MODEL));
    }

    public List<UsageRowVo> usageByProvider(LocalDateTime start, LocalDateTime end) {
        return StatsMapping.toUsageRows(statsQueryDao.usageByDimension(start, end, DIM_PROVIDER));
    }

    public List<UsageRowVo> usageByClient(LocalDateTime start, LocalDateTime end) {
        return StatsMapping.toUsageRows(statsQueryDao.usageByDimension(start, end, DIM_CLIENT));
    }
}
