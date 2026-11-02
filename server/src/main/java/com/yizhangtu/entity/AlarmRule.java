package com.yizhangtu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 告警阈值规则：点位属性的上/下限。
 */
@Data
@TableName("alarm_rule")
public class AlarmRule {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 点位 id */
    private Long pointId;

    /** 属性键，如 water_level */
    private String attrKey;

    /** 属性名，如 水位 */
    private String attrLabel;

    /** 上限（超过告警） */
    private BigDecimal upperLimit;

    /** 下限（低于告警） */
    private BigDecimal lowerLimit;

    /** 1 启用 0 停用 */
    private Integer enabled;

    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
