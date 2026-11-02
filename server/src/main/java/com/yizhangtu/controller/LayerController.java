package com.yizhangtu.controller;

import com.yizhangtu.common.Result;
import com.yizhangtu.controller.dto.LayerRequest;
import com.yizhangtu.entity.MapLayer;
import com.yizhangtu.service.LayerService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 图层配置接口：查询登录即可，维护需管理员（由 Security 统一控制）。
 */
@RestController
@RequestMapping("/api/layers")
public class LayerController {

    private final LayerService layerService;

    public LayerController(LayerService layerService) {
        this.layerService = layerService;
    }

    /** 图层列表（按 sort_order 排序） */
    @GetMapping
    public Result<List<MapLayer>> list() {
        return Result.ok(layerService.list());
    }

    /** 新增图层（ADMIN） */
    @PostMapping
    public Result<MapLayer> create(@Valid @RequestBody LayerRequest req) {
        return Result.ok(layerService.create(req));
    }

    /** 修改图层（ADMIN） */
    @PutMapping("/{id}")
    public Result<MapLayer> update(@PathVariable Long id, @Valid @RequestBody LayerRequest req) {
        return Result.ok(layerService.update(id, req));
    }

    /** 删除图层（ADMIN） */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        layerService.delete(id);
        return Result.ok();
    }
}
