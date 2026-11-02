package com.yizhangtu.controller;

import com.yizhangtu.common.PageResult;
import com.yizhangtu.common.Result;
import com.yizhangtu.controller.dto.AlarmHandleRequest;
import com.yizhangtu.entity.AlarmRecord;
import com.yizhangtu.security.LoginUser;
import com.yizhangtu.service.AlarmExcelService;
import com.yizhangtu.service.AlarmService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 告警接口：查询/导出登录即可，处理需 OPERATOR 及以上，关闭需 ADMIN（由 Security 统一控制）。
 */
@RestController
@RequestMapping("/api/alarms")
public class AlarmController {

    private static final String CONTENT_TYPE_XLSX =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final AlarmService alarmService;
    private final AlarmExcelService excelService;

    public AlarmController(AlarmService alarmService, AlarmExcelService excelService) {
        this.alarmService = alarmService;
        this.excelService = excelService;
    }

    /** 告警分页查询（状态 / 点位 / 关键词 / 日期区间筛选） */
    @GetMapping
    public Result<PageResult<AlarmRecord>> page(@RequestParam(required = false) String status,
                                                @RequestParam(required = false) Long pointId,
                                                @RequestParam(required = false) String keyword,
                                                @RequestParam(required = false)
                                                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
                                                @RequestParam(required = false)
                                                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
                                                @RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        return Result.ok(alarmService.page(status, pointId, keyword, dateFrom, dateTo, page, size));
    }

    /** 处理告警（OPERATOR+）：处理说明必填，留痕 */
    @PostMapping("/{id}/handle")
    public Result<AlarmRecord> handle(@PathVariable Long id,
                                      @Valid @RequestBody AlarmHandleRequest req,
                                      @AuthenticationPrincipal LoginUser loginUser) {
        return Result.ok(alarmService.handle(id, req.getHandleNote(),
                loginUser == null ? null : loginUser.getUsername()));
    }

    /** 关闭告警（ADMIN）：说明可选 */
    @PostMapping("/{id}/close")
    public Result<AlarmRecord> close(@PathVariable Long id,
                                     @RequestBody(required = false) Map<String, String> body,
                                     @AuthenticationPrincipal LoginUser loginUser) {
        String note = body == null ? null : body.get("handleNote");
        return Result.ok(alarmService.close(id, note,
                loginUser == null ? null : loginUser.getUsername()));
    }

    /** Excel 导出（按当前筛选，登录即可） */
    @GetMapping("/export")
    public void export(@RequestParam(required = false) String status,
                       @RequestParam(required = false) Long pointId,
                       @RequestParam(required = false) String keyword,
                       @RequestParam(required = false)
                       @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
                       @RequestParam(required = false)
                       @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
                       HttpServletResponse response) throws IOException {
        List<AlarmRecord> records = alarmService.list(status, pointId, keyword, dateFrom, dateTo);
        byte[] bytes = excelService.export(records);
        String filename = URLEncoder.encode("告警记录.xlsx", StandardCharsets.UTF_8).replace("+", "%20");
        response.setContentType(CONTENT_TYPE_XLSX);
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Disposition", "attachment;filename=" + filename);
        response.setContentLength(bytes.length);
        response.getOutputStream().write(bytes);
        response.getOutputStream().flush();
    }
}
