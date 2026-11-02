package com.yizhangtu.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yizhangtu.entity.AlarmRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 告警记录 Mapper。
 */
@Mapper
public interface AlarmRecordMapper extends BaseMapper<AlarmRecord> {
}
