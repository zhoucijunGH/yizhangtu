package com.yizhangtu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 示范区域面（演示虚构县区）。
 */
@Data
@TableName("region_area")
public class RegionArea {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 区域名 */
    private String name;

    /** 上级行政区（演示虚构） */
    private String parentName;

    /** Polygon GeoJSON 文本 */
    private String geojson;

    /** 中心经度 */
    private BigDecimal centroidLng;

    /** 中心纬度 */
    private BigDecimal centroidLat;
}
