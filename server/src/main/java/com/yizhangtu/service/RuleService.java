package com.yizhangtu.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yizhangtu.common.BizException;
import com.yizhangtu.controller.dto.RuleRequest;
import com.yizhangtu.entity.AlarmRule;
import com.yizhangtu.entity.PointObject;
import com.yizhangtu.mapper.AlarmRuleMapper;
import com.yizhangtu.mapper.PointObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 阈值规则服务（ADMIN 维护）。
 */
@Service
public class RuleService {

    private final AlarmRuleMapper ruleMapper;
    private final PointObjectMapper pointMapper;

    public RuleService(AlarmRuleMapper ruleMapper, PointObjectMapper pointMapper) {
        this.ruleMapper = ruleMapper;
        this.pointMapper = pointMapper;
    }

    /** 规则列表（可按点位过滤） */
    public List<AlarmRule> list(Long pointId) {
        return ruleMapper.selectList(new LambdaQueryWrapper<AlarmRule>()
                .eq(pointId != null, AlarmRule::getPointId, pointId)
                .orderByDesc(AlarmRule::getId));
    }

    /** 新增规则 */
    public AlarmRule create(RuleRequest req) {
        validate(req);
        AlarmRule rule = new AlarmRule();
        apply(rule, req);
        ruleMapper.insert(rule);
        return rule;
    }

    /** 修改规则 */
    public AlarmRule update(Long id, RuleRequest req) {
        AlarmRule rule = ruleMapper.selectById(id);
        if (rule == null) {
            throw new BizException(404, "规则不存在");
        }
        validate(req);
        apply(rule, req);
        ruleMapper.updateById(rule);
        return rule;
    }

    /** 删除规则 */
    public void delete(Long id) {
        if (ruleMapper.selectById(id) == null) {
            throw new BizException(404, "规则不存在");
        }
        ruleMapper.deleteById(id);
    }

    private void validate(RuleRequest req) {
        PointObject point = pointMapper.selectById(req.getPointId());
        if (point == null) {
            throw new BizException(404, "点位不存在");
        }
        boolean hasUpper = req.getUpperLimit() != null;
        boolean hasLower = req.getLowerLimit() != null;
        if (!hasUpper && !hasLower) {
            throw new BizException(400, "上限与下限至少填写一项");
        }
        if (hasUpper && hasLower && req.getUpperLimit().compareTo(req.getLowerLimit()) <= 0) {
            throw new BizException(400, "上限必须大于下限");
        }
    }

    private void apply(AlarmRule rule, RuleRequest req) {
        rule.setPointId(req.getPointId());
        rule.setAttrKey(req.getAttrKey().trim());
        rule.setAttrLabel(req.getAttrLabel().trim());
        rule.setUpperLimit(req.getUpperLimit());
        rule.setLowerLimit(req.getLowerLimit());
        rule.setEnabled(req.getEnabled() == null ? 1 : req.getEnabled());
        rule.setRemark(StringUtils.hasText(req.getRemark()) ? req.getRemark().trim() : null);
    }
}
