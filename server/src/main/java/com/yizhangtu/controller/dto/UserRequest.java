package com.yizhangtu.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 用户新增/修改请求。
 */
@Data
public class UserRequest {

    /** 登录名 */
    private String username;

    /** 明文密码（仅新增时必填） */
    private String password;

    /** 姓名 */
    private String nickname;

    /** 手机号 */
    private String phone;

    /** ADMIN / STAFF */
    @Pattern(regexp = "ADMIN|STAFF", message = "角色只能是 ADMIN 或 STAFF")
    private String role;

    /** 1 正常 0 停用 */
    private Integer status;
}
