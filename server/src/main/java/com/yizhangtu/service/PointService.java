package com.yizhangtu.service;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yizhangtu.common.BizException;
import com.yizhangtu.common.PageResult;
import com.yizhangtu.controller.dto.PointRequest;
import com.yizhangtu.entity.PointObject;
import com.yizhangtu.mapper.PointObjectMapper;
import com.yizhangtu.security.LoginUser;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 点位服务：分页筛选 / 增改停启 / GeoJSON 导出。
 */
@Service
public class PointService {

    private final PointObjectMapper pointMapper;

    public PointService(PointObjectMapper pointMapper) {
        this.pointMapper = pointMapper;
    }

    /** 分页列表（分类 / 关键词 / 区域 / 状态筛选） */
    public PageResult<PointObject> page(String keyword, String category, String region, String status,
                                        int page, int size) {
        IPage<PointObject> result = pointMapper.selectPage(new Page<>(page, size), buildQuery(keyword, category, region, status));
        return PageResult.of(result.getTotal(), result.getRecords());
    }

    /** 全量列表（GeoJSON / 导出 / 地图用，复用同一套筛选） */
    public List<PointObject> list(String keyword, String category, String region, String status) {
        return pointMapper.selectList(buildQuery(keyword, category, region, status));
    }

    private LambdaQueryWrapper<PointObject> buildQuery(String keyword, String category, String region, String status) {
        LambdaQueryWrapper<PointObject> wrapper = new LambdaQueryWrapper<PointObject>()
                .eq(StringUtils.hasText(category), PointObject::getCategory, category)
                .eq(StringUtils.hasText(region), PointObject::getRegion, region)
                .eq(StringUtils.hasText(status), PointObject::getStatus, status)
                .orderByDesc(PointObject::getId);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(PointObject::getName, keyword)
                    .or().like(PointObject::getRiverSegment, keyword));
        }
        return wrapper;
    }

    /** 新增点位 */
    public PointObject create(PointRequest req, LoginUser loginUser) {
        validate(req);
        PointObject point = new PointObject();
        apply(point, req);
        point.setStatus(StringUtils.hasText(req.getStatus()) ? req.getStatus() : "NORMAL");
        point.setCreatedBy(loginUser == null ? null : loginUser.getUserId());
        pointMapper.insert(point);
        return point;
    }

    /** 修改点位 */
    public PointObject update(Long id, PointRequest req) {
        PointObject point = pointMapper.selectById(id);
        if (point == null) {
            throw new BizException(404, "点位不存在");
        }
        validate(req);
        apply(point, req);
        if (StringUtils.hasText(req.getStatus())) {
            point.setStatus(req.getStatus());
        }
        pointMapper.updateById(point);
        return point;
    }

    /** 停用 / 恢复 */
    public PointObject updateStatus(Long id, String status) {
        if (!"NORMAL".equals(status) && !"DISABLED".equals(status)) {
            throw new BizException(400, "状态只能是 NORMAL 或 DISABLED");
        }
        PointObject point = pointMapper.selectById(id);
        if (point == null) {
            throw new BizException(404, "点位不存在");
        }
        point.setStatus(status);
        pointMapper.updateById(point);
        return point;
    }

    /** 删除点位 */
    public void delete(Long id) {
        if (pointMapper.selectById(id) == null) {
            throw new BizException(404, "点位不存在");
        }
        pointMapper.deleteById(id);
    }

    /** 导出 FeatureCollection（按当前筛选） */
    public Map<String, Object> toGeoJson(String keyword, String category, String region, String status) {
        List<PointObject> points = list(keyword, category, region, status);
        List<Map<String, Object>> features = new ArrayList<>();
        for (PointObject p : points) {
            Map<String, Object> feature = new LinkedHashMap<>();
            feature.put("type", "Feature");
            Map<String, Object> geometry = new LinkedHashMap<>();
            geometry.put("type", "Point");
            geometry.put("coordinates", new Object[]{p.getLng(), p.getLat()});
            feature.put("geometry", geometry);

            Map<String, Object> properties = new LinkedHashMap<>();
            properties.put("id", p.getId());
            properties.put("name", p.getName());
            properties.put("category", p.getCategory());
            properties.put("region", p.getRegion());
            properties.put("river_segment", p.getRiverSegment());
            properties.put("attrs", p.getAttrsJson());
            properties.put("status", p.getStatus());
            feature.put("properties", properties);
            features.add(feature);
        }
        Map<String, Object> collection = new LinkedHashMap<>();
        collection.put("type", "FeatureCollection");
        collection.put("features", features);
        return collection;
    }

    /** 校验经纬度与属性 JSON */
    private void validate(PointRequest req) {
        if (req.getLng() == null || req.getLng().compareTo(BigDecimal.valueOf(-180)) < 0
                || req.getLng().compareTo(BigDecimal.valueOf(180)) > 0) {
            throw new BizException(400, "经度必须在 -180 ~ 180 之间");
        }
        if (req.getLat() == null || req.getLat().compareTo(BigDecimal.valueOf(-90)) < 0
                || req.getLat().compareTo(BigDecimal.valueOf(90)) > 0) {
            throw new BizException(400, "纬度必须在 -90 ~ 90 之间");
        }
        if (StringUtils.hasText(req.getAttrsJson())) {
            try {
                JSONObject json = JSONUtil.parseObj(req.getAttrsJson());
                if (json.isEmpty()) {
                    throw new BizException(400, "属性 JSON 不能为空对象");
                }
            } catch (BizException e) {
                throw e;
            } catch (Exception e) {
                throw new BizException(400, "属性 JSON 格式不正确，示例：{\"water_level\":6.8}");
            }
        }
    }

    private void apply(PointObject point, PointRequest req) {
        point.setName(req.getName().trim());
        point.setCategory(req.getCategory());
        point.setRegion(req.getRegion());
        point.setRiverSegment(req.getRiverSegment());
        point.setLng(req.getLng());
        point.setLat(req.getLat());
        point.setAttrsJson(req.getAttrsJson());
        point.setPhoto(req.getPhoto());
    }
}
