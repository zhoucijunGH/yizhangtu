package com.yizhangtu.controller;

import com.yizhangtu.common.PageResult;
import com.yizhangtu.common.Result;
import com.yizhangtu.controller.dto.PointRequest;
import com.yizhangtu.entity.PointObject;
import com.yizhangtu.security.LoginUser;
import com.yizhangtu.service.PointExcelService;
import com.yizhangtu.service.PointService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 点位接口：查询/导出登录即可，增改停用需 OPERATOR 及以上，删除需 ADMIN（由 Security 统一控制）。
 */
@RestController
@RequestMapping("/api/points")
public class PointController {

    private static final String CONTENT_TYPE_XLSX =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final PointService pointService;
    private final PointExcelService excelService;

    public PointController(PointService pointService, PointExcelService excelService) {
        this.pointService = pointService;
        this.excelService = excelService;
    }

    /** 点位分页查询（分类 / 关键词 / 区域 / 状态筛选） */
    @GetMapping
    public Result<PageResult<PointObject>> page(@RequestParam(required = false) String keyword,
                                                @RequestParam(required = false) String category,
                                                @RequestParam(required = false) String region,
                                                @RequestParam(required = false) String status,
                                                @RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        return Result.ok(pointService.page(keyword, category, region, status, page, size));
    }

    /** 新增点位（OPERATOR+） */
    @PostMapping
    public Result<PointObject> create(@Valid @RequestBody PointRequest req,
                                      @AuthenticationPrincipal LoginUser loginUser) {
        return Result.ok(pointService.create(req, loginUser));
    }

    /** 修改点位（OPERATOR+） */
    @PutMapping("/{id}")
    public Result<PointObject> update(@PathVariable Long id, @Valid @RequestBody PointRequest req) {
        return Result.ok(pointService.update(id, req));
    }

    /** 停用 / 恢复（OPERATOR+）：body = {"status":"NORMAL"|"DISABLED"} */
    @PutMapping("/{id}/status")
    public Result<PointObject> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return Result.ok(pointService.updateStatus(id, body.get("status")));
    }

    /** 删除点位（ADMIN） */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        pointService.delete(id);
        return Result.ok();
    }

    /** GeoJSON 导出（FeatureCollection，按当前筛选） */
    @GetMapping("/geojson")
    public Result<Map<String, Object>> geojson(@RequestParam(required = false) String keyword,
                                               @RequestParam(required = false) String category,
                                               @RequestParam(required = false) String region,
                                               @RequestParam(required = false) String status) {
        return Result.ok(pointService.toGeoJson(keyword, category, region, status));
    }

    /** Excel 导出（按当前筛选，登录即可） */
    @GetMapping("/export")
    public void export(@RequestParam(required = false) String keyword,
                       @RequestParam(required = false) String category,
                       @RequestParam(required = false) String region,
                       @RequestParam(required = false) String status,
                       HttpServletResponse response) throws IOException {
        List<PointObject> points = pointService.list(keyword, category, region, status);
        byte[] bytes = excelService.export(points);
        writeXlsx(response, bytes, "点位台账.xlsx");
    }

    /** Excel 模板下载（OPERATOR+） */
    @GetMapping("/import-template")
    public void template(HttpServletResponse response) throws IOException {
        writeXlsx(response, excelService.template(), "点位导入模板.xlsx");
    }

    /** Excel 导入（OPERATOR+）：逐行校验，失败行返回原因 */
    @PostMapping("/import")
    public Result<Map<String, Object>> importExcel(@RequestParam("file") MultipartFile file,
                                                   @AuthenticationPrincipal LoginUser loginUser) throws IOException {
        if (file == null || file.isEmpty()) {
            return Result.fail(400, "请选择要上传的 xlsx 文件");
        }
        return Result.ok(excelService.importExcel(file.getInputStream(),
                loginUser == null ? null : loginUser.getUserId()));
    }

    private void writeXlsx(HttpServletResponse response, byte[] bytes, String filename) throws IOException {
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        response.setContentType(CONTENT_TYPE_XLSX);
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Disposition", "attachment;filename=" + encoded);
        response.setContentLength(bytes.length);
        response.getOutputStream().write(bytes);
        response.getOutputStream().flush();
    }
}
