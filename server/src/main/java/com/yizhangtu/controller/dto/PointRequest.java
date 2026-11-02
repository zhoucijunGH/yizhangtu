package com.yizhangtu.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 点位新增/修改请求。
 */
@Data
public class PointRequest {

    /** 点位名称 */
    @NotBlank(message = "请填写点位名称")
    private String name;

    /** MONITOR / HAZARD / PROJECT / PATROL */
    @NotBlank(message = "请选择分类")
    @Pattern(regexp = "MONITOR|HAZARD|PROJECT|PATROL", message = "分类只能是 MONITOR/HAZARD/PROJECT/PATROL")
    private String category;

    /** 所属区域 */
    private String region;

    /** 河段 */
    private String riverSegment;

    /** 经度 */
    @NotNull(message = "请选择经度")
    private BigDecimal lng;

    /** 纬度 */
    @NotNull(message = "请选择纬度")
    private BigDecimal lat;

    /** 属性 JSON */
    private String attrsJson;

    /** 现场照片 URL */
    private String photo;

    /** NORMAL / DISABLED */
    @Pattern(regexp = "NORMAL|DISABLED", message = "状态只能是 NORMAL 或 DISABLED")
    private String status;
}
