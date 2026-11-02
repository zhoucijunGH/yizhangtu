package com.yizhangtu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 点位对象：监测 / 隐患 / 工程 / 巡查。
 */
@Data
@TableName("point_object")
public class PointObject {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 点位名称 */
    private String name;

    /** MONITOR 监测 / HAZARD 隐患 / PROJECT 工程 / PATROL 巡查 */
    private String category;

    /** 所属区域（= region_area.name） */
    private String region;

    /** 河段 */
    private String riverSegment;

    /** 经度 */
    private BigDecimal lng;

    /** 纬度 */
    private BigDecimal lat;

    /** 属性 JSON，如 {"water_level":6.8,"rainfall":32} */
    private String attrsJson;

    /** 现场照片 URL */
    private String photo;

    /** NORMAL 正常 / DISABLED 停用 */
    private String status;

    /** 创建人 id */
    private Long createdBy;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
