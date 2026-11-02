package com.yizhangtu.controller.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 图层新增/修改请求。
 */
@Data
public class LayerRequest {

    /** 图层名 */
    @NotBlank(message = "请填写图层名")
    private String name;

    /** 分组：基础点位 / 区域面 / 专题 */
    private String groupName;

    /** POINT / POLYGON / STATS */
    private String layerType;

    /** 默认显示 1/0 */
    private Integer defaultVisible;

    /** 排序号 */
    private Integer sortOrder;

    /** 样式配置 JSON */
    private String styleJson;

    /** 1 启用 0 停用 */
    private Integer status;
}
