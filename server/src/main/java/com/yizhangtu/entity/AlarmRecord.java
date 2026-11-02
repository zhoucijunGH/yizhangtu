package com.yizhangtu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 告警记录（触发快照 + 处理留痕）。
 */
@Data
@TableName("alarm_record")
public class AlarmRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long pointId;

    private String pointName;

    private Long ruleId;

    /** 属性键 + _UPPER/_LOWER，如 water_level_UPPER */
    private String alarmType;

    /** 实测值快照 */
    private String valueStr;

    /** 阈值快照 */
    private String thresholdStr;

    /** UNPROCESSED / PROCESSED / CLOSED */
    private String status;

    /** 处理说明（留痕） */
    private String handleNote;

    /** 处理人 */
    private String handler;

    private LocalDateTime handledAt;

    private LocalDateTime createdAt;

    /** 规则属性键（联表查询用，非表字段） */
    @TableField(exist = false)
    private String attrKey;

    /** 规则属性名（联表查询用，非表字段） */
    @TableField(exist = false)
    private String attrLabel;
}
