package com.yizhangtu.controller;

import com.yizhangtu.common.PageResult;
import com.yizhangtu.common.Result;
import com.yizhangtu.controller.dto.UserRequest;
import com.yizhangtu.entity.SysUser;
import com.yizhangtu.service.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 用户管理接口（全部 ADMIN，由 Security 统一控制）。
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /** 用户分页列表 */
    @GetMapping
    public Result<PageResult<SysUser>> page(@RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(userService.page(page, size));
    }

    /** 新增用户 */
    @PostMapping
    public Result<SysUser> create(@Valid @RequestBody UserRequest req) {
        return Result.ok(userService.create(req));
    }

    /** 修改用户 */
    @PutMapping("/{id}")
    public Result<SysUser> update(@PathVariable Long id, @RequestBody UserRequest req) {
        return Result.ok(userService.update(id, req));
    }

    /** 删除用户 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return Result.ok();
    }

    /** 重置密码：{ "password": "xxx" } */
    @PostMapping("/{id}/reset-password")
    public Result<Void> resetPassword(@PathVariable Long id, @RequestBody Map<String, String> body) {
        userService.resetPassword(id, body.get("password"));
        return Result.ok();
    }
}
