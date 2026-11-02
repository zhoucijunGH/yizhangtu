package com.yizhangtu.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 阈值规则新增/修改请求。
 */
@Data
public class RuleRequest {

    /** 点位 id */
    @NotNull(message = "请选择点位")
    private Long pointId;

    /** 属性键，如 water_level */
    @NotBlank(message = "请填写属性键")
    private String attrKey;

    /** 属性名，如 水位 */
    @NotBlank(message = "请填写属性名")
    private String attrLabel;

    /** 上限 */
    private BigDecimal upperLimit;

    /** 下限 */
    private BigDecimal lowerLimit;

    /** 1 启用 0 停用 */
    private Integer enabled;

    private String remark;
}
