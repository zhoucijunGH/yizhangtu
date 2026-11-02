package com.yizhangtu.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yizhangtu.entity.AlarmRule;
import org.apache.ibatis.annotations.Mapper;

/**
 * 告警阈值规则 Mapper。
 */
@Mapper
public interface AlarmRuleMapper extends BaseMapper<AlarmRule> {
}
