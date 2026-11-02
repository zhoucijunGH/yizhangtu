package com.yizhangtu.controller;

import com.yizhangtu.common.Result;
import com.yizhangtu.entity.SysUser;
import com.yizhangtu.security.LoginUser;
import com.yizhangtu.service.AuthService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 认证接口：登录 / 当前用户。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** 登录：BCrypt 校验，返回 token 与用户信息 */
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        return Result.ok(authService.login(body.get("username"), body.get("password")));
    }

    /** 当前登录用户信息 */
    @GetMapping("/me")
    public Result<SysUser> me(@AuthenticationPrincipal LoginUser loginUser) {
        return Result.ok(authService.me(loginUser.getUserId()));
    }
}
