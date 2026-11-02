package com.yizhangtu.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yizhangtu.common.BizException;
import com.yizhangtu.controller.dto.LayerRequest;
import com.yizhangtu.entity.MapLayer;
import com.yizhangtu.mapper.MapLayerMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 图层配置服务：列表按 sort_order 排序，ADMIN 维护。
 */
@Service
public class LayerService {

    private final MapLayerMapper layerMapper;

    public LayerService(MapLayerMapper layerMapper) {
        this.layerMapper = layerMapper;
    }

    /** 图层列表（排序后，管理页与地图面板共用） */
    public List<MapLayer> list() {
        return layerMapper.selectList(new LambdaQueryWrapper<MapLayer>()
                .orderByAsc(MapLayer::getSortOrder)
                .orderByAsc(MapLayer::getId));
    }

    /** 新增图层 */
    public MapLayer create(LayerRequest req) {
        MapLayer layer = new MapLayer();
        apply(layer, req);
        layerMapper.insert(layer);
        return layer;
    }

    /** 修改图层 */
    public MapLayer update(Long id, LayerRequest req) {
        MapLayer layer = layerMapper.selectById(id);
        if (layer == null) {
            throw new BizException(404, "图层不存在");
        }
        apply(layer, req);
        layerMapper.updateById(layer);
        return layer;
    }

    /** 删除图层 */
    public void delete(Long id) {
        if (layerMapper.selectById(id) == null) {
            throw new BizException(404, "图层不存在");
        }
        layerMapper.deleteById(id);
    }

    private void apply(MapLayer layer, LayerRequest req) {
        if (!StringUtils.hasText(req.getName())) {
            throw new BizException(400, "请填写图层名");
        }
        layer.setName(req.getName().trim());
        layer.setGroupName(StringUtils.hasText(req.getGroupName()) ? req.getGroupName().trim() : "基础点位");
        layer.setLayerType(StringUtils.hasText(req.getLayerType()) ? req.getLayerType() : "POINT");
        layer.setDefaultVisible(req.getDefaultVisible() == null ? 1 : req.getDefaultVisible());
        layer.setSortOrder(req.getSortOrder() == null ? 0 : req.getSortOrder());
        layer.setStyleJson(req.getStyleJson());
        layer.setStatus(req.getStatus() == null ? 1 : req.getStatus());
    }
}
