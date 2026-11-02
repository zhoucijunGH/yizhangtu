package com.yizhangtu.service;

import com.yizhangtu.common.BizException;
import com.yizhangtu.entity.PointObject;
import com.yizhangtu.mapper.PointObjectMapper;
import cn.hutool.json.JSONUtil;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 点位 Excel 导入导出服务（Apache POI，沿用模板 ExportController 的文件流方案）。
 */
@Service
public class PointExcelService {

    private static final String[] HEADERS = {
            "点位名称", "分类", "区域", "河段", "经度", "纬度", "属性JSON", "状态"
    };
    private static final Map<String, String> CATEGORY_LABELS = Map.of(
            "MONITOR", "监测", "HAZARD", "隐患", "PROJECT", "工程", "PATROL", "巡查");
    private static final Map<String, String> STATUS_LABELS = Map.of(
            "NORMAL", "正常", "DISABLED", "停用");

    private final PointObjectMapper pointMapper;

    public PointExcelService(PointObjectMapper pointMapper) {
        this.pointMapper = pointMapper;
    }

    /** 导出点位列表为 xlsx 字节流 */
    public byte[] export(List<PointObject> points) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("点位台账");
            CellStyle headStyle = headStyle(workbook);

            Row head = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell cell = head.createCell(i);
                cell.setCellValue(HEADERS[i]);
                cell.setCellStyle(headStyle);
            }

            int rowIdx = 1;
            for (PointObject p : points) {
                Row row = sheet.createRow(rowIdx++);
                int c = 0;
                row.createCell(c++).setCellValue(nvl(p.getName()));
                row.createCell(c++).setCellValue(CATEGORY_LABELS.getOrDefault(p.getCategory(), p.getCategory()));
                row.createCell(c++).setCellValue(nvl(p.getRegion()));
                row.createCell(c++).setCellValue(nvl(p.getRiverSegment()));
                row.createCell(c++).setCellValue(p.getLng() == null ? 0 : p.getLng().doubleValue());
                row.createCell(c++).setCellValue(p.getLat() == null ? 0 : p.getLat().doubleValue());
                row.createCell(c++).setCellValue(nvl(p.getAttrsJson()));
                row.createCell(c).setCellValue(STATUS_LABELS.getOrDefault(p.getStatus(), p.getStatus()));
            }

            for (int i = 0; i < HEADERS.length; i++) {
                sheet.autoSizeColumn(i);
            }
            return toBytes(workbook);
        }
    }

    /** 导入模板：表头 + 一行示例 */
    public byte[] template() throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("点位导入模板");
            CellStyle headStyle = headStyle(workbook);

            Row head = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell cell = head.createCell(i);
                cell.setCellValue(HEADERS[i]);
                cell.setCellStyle(headStyle);
            }

            Row sample = sheet.createRow(1);
            int c = 0;
            sample.createCell(c++).setCellValue("示例：那洪闸监测站");
            sample.createCell(c++).setCellValue("监测");
            sample.createCell(c++).setCellValue("邕江上游段示范区");
            sample.createCell(c++).setCellValue("邕江左岸 K7+500");
            sample.createCell(c++).setCellValue(108.360000);
            sample.createCell(c++).setCellValue(22.850000);
            sample.createCell(c++).setCellValue("{\"water_level\":5.6,\"rainfall\":20}");
            sample.createCell(c).setCellValue("正常");

            for (int i = 0; i < HEADERS.length; i++) {
                sheet.autoSizeColumn(i);
            }
            return toBytes(workbook);
        }
    }

    /**
     * 解析上传的 xlsx 并逐行校验入库。
     *
     * @return {imported: n, failed: [{row, reason}]}
     */
    public Map<String, Object> importExcel(InputStream in, Long userId) throws IOException {
        List<Map<String, Object>> failed = new ArrayList<>();
        int imported = 0;

        try (XSSFWorkbook workbook = new XSSFWorkbook(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                throw new BizException(400, "模板内容为空");
            }
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }
                int line = i + 1;
                try {
                    String name = cellString(row.getCell(0));
                    if (!StringUtils.hasText(name)) {
                        throw new BizException(400, "点位名称不能为空");
                    }
                    String category = parseCategory(cellString(row.getCell(1)));
                    String region = cellString(row.getCell(2));
                    String riverSegment = cellString(row.getCell(3));
                    BigDecimal lng = parseNumber(row.getCell(4), "经度");
                    BigDecimal lat = parseNumber(row.getCell(5), "纬度");
                    if (lng.compareTo(BigDecimal.valueOf(-180)) < 0 || lng.compareTo(BigDecimal.valueOf(180)) > 0) {
                        throw new BizException(400, "经度必须在 -180 ~ 180 之间");
                    }
                    if (lat.compareTo(BigDecimal.valueOf(-90)) < 0 || lat.compareTo(BigDecimal.valueOf(90)) > 0) {
                        throw new BizException(400, "纬度必须在 -90 ~ 90 之间");
                    }
                    String attrsJson = cellString(row.getCell(6));
                    if (StringUtils.hasText(attrsJson) && !JSONUtil.isTypeJSONObject(attrsJson)) {
                        throw new BizException(400, "属性JSON格式不正确");
                    }
                    String status = parseStatus(cellString(row.getCell(7)));

                    PointObject point = new PointObject();
                    point.setName(name.trim());
                    point.setCategory(category);
                    point.setRegion(StringUtils.hasText(region) ? region.trim() : null);
                    point.setRiverSegment(StringUtils.hasText(riverSegment) ? riverSegment.trim() : null);
                    point.setLng(lng);
                    point.setLat(lat);
                    point.setAttrsJson(StringUtils.hasText(attrsJson) ? attrsJson.trim() : null);
                    point.setStatus(status);
                    point.setCreatedBy(userId);
                    pointMapper.insert(point);
                    imported++;
                } catch (BizException e) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("row", line);
                    item.put("reason", e.getMessage());
                    failed.add(item);
                }
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("imported", imported);
        result.put("failed", failed);
        return result;
    }

    /** 分类支持中文标签与英文代码 */
    private String parseCategory(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw new BizException(400, "分类不能为空");
        }
        String v = raw.trim().toUpperCase();
        for (Map.Entry<String, String> e : CATEGORY_LABELS.entrySet()) {
            if (e.getKey().equals(v) || e.getValue().equals(raw.trim())) {
                return e.getKey();
            }
        }
        throw new BizException(400, "分类只能是 监测/隐患/工程/巡查");
    }

    /** 状态支持中文标签与英文代码，默认 NORMAL */
    private String parseStatus(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "NORMAL";
        }
        String v = raw.trim().toUpperCase();
        for (Map.Entry<String, String> e : STATUS_LABELS.entrySet()) {
            if (e.getKey().equals(v) || e.getValue().equals(raw.trim())) {
                return e.getKey();
            }
        }
        throw new BizException(400, "状态只能是 正常/停用");
    }

    private BigDecimal parseNumber(Cell cell, String label) {
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            throw new BizException(400, label + "不能为空");
        }
        try {
            if (cell.getCellType() == CellType.NUMERIC) {
                return BigDecimal.valueOf(cell.getNumericCellValue());
            }
            return new BigDecimal(cell.toString().trim().replace(",", ""));
        } catch (NumberFormatException e) {
            throw new BizException(400, label + "格式不正确");
        }
    }

    private String cellString(Cell cell) {
        if (cell == null) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            double v = cell.getNumericCellValue();
            if (v == Math.floor(v) && !Double.isInfinite(v)) {
                return String.valueOf((long) v);
            }
            return String.valueOf(v);
        }
        return cell.toString();
    }

    private CellStyle headStyle(XSSFWorkbook workbook) {
        CellStyle headStyle = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        headStyle.setFont(font);
        headStyle.setAlignment(HorizontalAlignment.CENTER);
        return headStyle;
    }

    private byte[] toBytes(XSSFWorkbook workbook) throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private String nvl(String v) {
        return v == null ? "" : v;
    }
}
