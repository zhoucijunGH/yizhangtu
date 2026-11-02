package com.yizhangtu.controller;

import com.yizhangtu.common.Result;
import com.yizhangtu.controller.dto.RuleRequest;
import com.yizhangtu.entity.AlarmRule;
import com.yizhangtu.service.RuleService;
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

import java.util.List;

/**
 * 阈值规则接口：查询登录即可，维护需管理员（由 Security 统一控制）。
 */
@RestController
@RequestMapping("/api/rules")
public class RuleController {

    private final RuleService ruleService;

    public RuleController(RuleService ruleService) {
        this.ruleService = ruleService;
    }

    /** 规则列表（可按点位过滤） */
    @GetMapping
    public Result<List<AlarmRule>> list(@RequestParam(required = false) Long pointId) {
        return Result.ok(ruleService.list(pointId));
    }

    /** 新增规则（ADMIN） */
    @PostMapping
    public Result<AlarmRule> create(@Valid @RequestBody RuleRequest req) {
        return Result.ok(ruleService.create(req));
    }

    /** 修改规则（ADMIN） */
    @PutMapping("/{id}")
    public Result<AlarmRule> update(@PathVariable Long id, @Valid @RequestBody RuleRequest req) {
        return Result.ok(ruleService.update(id, req));
    }

    /** 删除规则（ADMIN） */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        ruleService.delete(id);
        return Result.ok();
    }
}
