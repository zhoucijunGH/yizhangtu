package com.yizhangtu.service;

import com.yizhangtu.entity.AlarmRecord;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 告警记录 Excel 导出服务（Apache POI，沿用模板 ExportService 的文件流方案）。
 */
@Service
public class AlarmExcelService {

    private static final String[] HEADERS = {
            "告警时间", "点位", "告警类型", "实测值", "阈值", "状态", "处理说明", "处理人", "处理时间"
    };
    private static final Map<String, String> STATUS_LABELS = Map.of(
            "UNPROCESSED", "未处理", "PROCESSED", "已处理", "CLOSED", "已关闭");
    private static final DateTimeFormatter DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 导出告警记录为 xlsx 字节流 */
    public byte[] export(List<AlarmRecord> records) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("告警记录");

            CellStyle headStyle = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            headStyle.setFont(font);
            headStyle.setAlignment(HorizontalAlignment.CENTER);

            Row head = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell cell = head.createCell(i);
                cell.setCellValue(HEADERS[i]);
                cell.setCellStyle(headStyle);
            }

            int rowIdx = 1;
            for (AlarmRecord a : records) {
                Row row = sheet.createRow(rowIdx++);
                int c = 0;
                row.createCell(c++).setCellValue(a.getCreatedAt() == null ? "" : a.getCreatedAt().format(DATETIME));
                row.createCell(c++).setCellValue(nvl(a.getPointName()));
                row.createCell(c++).setCellValue(typeText(a));
                row.createCell(c++).setCellValue(nvl(a.getValueStr()));
                row.createCell(c++).setCellValue(nvl(a.getThresholdStr()));
                row.createCell(c++).setCellValue(STATUS_LABELS.getOrDefault(a.getStatus(), a.getStatus()));
                row.createCell(c++).setCellValue(nvl(a.getHandleNote()));
                row.createCell(c++).setCellValue(nvl(a.getHandler()));
                row.createCell(c).setCellValue(a.getHandledAt() == null ? "" : a.getHandledAt().format(DATETIME));
            }

            for (int i = 0; i < HEADERS.length; i++) {
                sheet.autoSizeColumn(i);
            }

            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                workbook.write(out);
                return out.toByteArray();
            }
        }
    }

    /** 告警类型展示：水位 超上限 / 雨量 低于下限 */
    private String typeText(AlarmRecord a) {
        String label = a.getAttrLabel() != null ? a.getAttrLabel()
                : (a.getAlarmType() == null ? "" : a.getAlarmType());
        String type = a.getAlarmType() == null ? "" : a.getAlarmType();
        if (type.endsWith("_UPPER")) {
            return label + " 超上限";
        }
        if (type.endsWith("_LOWER")) {
            return label + " 低于下限";
        }
        return type;
    }

    private String nvl(String v) {
        return v == null ? "" : v;
    }
}
