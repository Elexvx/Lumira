package com.lumira.saas.modules.system.export;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.StringJoiner;

@Service
public class ExcelExportService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public <T> byte[] export(String sheetName, List<ExportColumn<T>> columns, List<T> rows) {
        if (columns == null || columns.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "请至少选择一个导出字段");
        }
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(safeSheetName(sheetName));
            CellStyle headerStyle = headerStyle(workbook);

            Row header = sheet.createRow(0);
            for (int columnIndex = 0; columnIndex < columns.size(); columnIndex += 1) {
                Cell cell = header.createCell(columnIndex);
                cell.setCellValue(columns.get(columnIndex).label());
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(columnIndex, Math.min(40, Math.max(12, columns.get(columnIndex).label().length() + 8)) * 256);
            }

            for (int rowIndex = 0; rowIndex < rows.size(); rowIndex += 1) {
                Row row = sheet.createRow(rowIndex + 1);
                T source = rows.get(rowIndex);
                for (int columnIndex = 0; columnIndex < columns.size(); columnIndex += 1) {
                    Object value = columns.get(columnIndex).valueExtractor().apply(source);
                    row.createCell(columnIndex).setCellValue(formatValue(value));
                }
            }

            workbook.write(outputStream);
            return outputStream.toByteArray();
        } catch (BizException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BizException(ErrorCode.BIZ_ERROR, "导出文件生成失败");
        }
    }

    private CellStyle headerStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private String formatValue(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof LocalDateTime dateTime) {
            return DATE_TIME_FORMATTER.format(dateTime);
        }
        if (value instanceof LocalDate date) {
            return DATE_FORMATTER.format(date);
        }
        if (value instanceof Collection<?> collection) {
            StringJoiner joiner = new StringJoiner("、");
            for (Object item : collection) {
                if (item != null) {
                    joiner.add(String.valueOf(item));
                }
            }
            return joiner.toString();
        }
        return String.valueOf(value);
    }

    private String safeSheetName(String sheetName) {
        if (sheetName == null || sheetName.isBlank()) {
            return "Export";
        }
        String sanitized = sheetName.replaceAll("[\\\\/?*\\[\\]:]", "");
        if (sanitized.isBlank()) {
            return "Export";
        }
        return sanitized.substring(0, Math.min(31, sanitized.length()));
    }
}
