package com.yizhangtu.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yizhangtu.common.BizException;
import com.yizhangtu.common.PageResult;
import com.yizhangtu.controller.dto.UserRequest;
import com.yizhangtu.entity.SysUser;
import com.yizhangtu.mapper.SysUserMapper;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 用户管理服务（全部管理员操作）：分页 / 新增 / 修改 / 删除 / 重置密码。
 */
@Service
public class UserService {

    private final SysUserMapper userMapper;
    private final BCryptPasswordEncoder passwordEncoder;

    public UserService(SysUserMapper userMapper, BCryptPasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    /** 用户分页列表 */
    public PageResult<SysUser> page(int page, int size) {
        IPage<SysUser> result = userMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<SysUser>().orderByDesc(SysUser::getId));
        return PageResult.of(result.getTotal(), result.getRecords());
    }

    /** 新增用户（密码 BCrypt 加密存储） */
    public SysUser create(UserRequest req) {
        if (!StringUtils.hasText(req.getUsername())) {
            throw new BizException(400, "请填写登录名");
        }
        if (!StringUtils.hasText(req.getPassword()) || req.getPassword().length() < 6) {
            throw new BizException(400, "密码至少 6 位");
        }
        Long exists = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, req.getUsername().trim()));
        if (exists != null && exists > 0) {
            throw new BizException(400, "登录名已存在");
        }
        SysUser user = new SysUser();
        user.setUsername(req.getUsername().trim());
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setNickname(req.getNickname());
        user.setPhone(req.getPhone());
        user.setRole(StringUtils.hasText(req.getRole()) ? req.getRole() : "STAFF");
        user.setStatus(req.getStatus() == null ? 1 : req.getStatus());
        userMapper.insert(user);
        return user;
    }

    /** 修改用户：不传密码则不改密 */
    public SysUser update(Long id, UserRequest req) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BizException(404, "用户不存在");
        }
        if (StringUtils.hasText(req.getUsername()) && !req.getUsername().equals(user.getUsername())) {
            Long exists = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getUsername, req.getUsername().trim()));
            if (exists != null && exists > 0) {
                throw new BizException(400, "登录名已存在");
            }
            user.setUsername(req.getUsername().trim());
        }
        if (StringUtils.hasText(req.getPassword())) {
            user.setPassword(passwordEncoder.encode(req.getPassword()));
        }
        if (req.getNickname() != null) {
            user.setNickname(req.getNickname());
        }
        if (req.getPhone() != null) {
            user.setPhone(req.getPhone());
        }
        if (StringUtils.hasText(req.getRole())) {
            user.setRole(req.getRole());
        }
        if (req.getStatus() != null) {
            user.setStatus(req.getStatus());
        }
        userMapper.updateById(user);
        return user;
    }

    /** 删除用户 */
    public void delete(Long id) {
        if (userMapper.selectById(id) == null) {
            throw new BizException(404, "用户不存在");
        }
        userMapper.deleteById(id);
    }

    /** 重置密码 */
    public void resetPassword(Long id, String password) {
        if (userMapper.selectById(id) == null) {
            throw new BizException(404, "用户不存在");
        }
        if (!StringUtils.hasText(password) || password.length() < 6) {
            throw new BizException(400, "密码至少 6 位");
        }
        SysUser user = new SysUser();
        user.setId(id);
        user.setPassword(passwordEncoder.encode(password));
        userMapper.updateById(user);
    }
}
