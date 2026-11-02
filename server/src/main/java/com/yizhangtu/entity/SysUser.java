package com.yizhangtu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 账号（老板 ADMIN / 开单员 STAFF）。
 */
@Data
@TableName("sys_user")
public class SysUser {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 登录名 */
    private String username;

    /** BCrypt 密码（不回传前端） */
    @JsonIgnore
    private String password;

    /** 姓名 */
    private String nickname;

    /** 手机号 */
    private String phone;

    /** ADMIN 老板 / STAFF 开单员 */
    private String role;

    /** 1 正常 0 停用 */
    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
