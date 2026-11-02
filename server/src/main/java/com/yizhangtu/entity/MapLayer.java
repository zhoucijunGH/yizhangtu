package com.yizhangtu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 地图图层配置。
 */
@Data
@TableName("map_layer")
public class MapLayer {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 图层名 */
    private String name;

    /** 分组：基础点位 / 区域面 / 专题 */
    private String groupName;

    /** POINT / POLYGON / STATS */
    private String layerType;

    /** 默认显示 1/0 */
    private Integer defaultVisible;

    /** 排序号（小在前） */
    private Integer sortOrder;

    /** 样式配置 JSON，如 {"color":"#22d3ee","radius":9,"categories":["MONITOR"]} */
    private String styleJson;

    /** 1 启用 0 停用 */
    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
