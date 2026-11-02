package com.yizhangtu.controller.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 告警处理/关闭请求。
 */
@Data
public class AlarmHandleRequest {

    /** 处理说明（必填，留痕） */
    @NotBlank(message = "请填写处理说明")
    private String handleNote;
}
