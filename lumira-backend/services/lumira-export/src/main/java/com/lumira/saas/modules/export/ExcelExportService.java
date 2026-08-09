package com.lumira.saas.modules.export;

import com.lumira.api.export.ExcelExportPort;
import com.lumira.api.export.ExportColumn;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.runtime.ConditionalOnLumiraControlPlaneEnabled;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.StringJoiner;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnLumiraControlPlaneEnabled
public class ExcelExportService implements ExcelExportPort {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Override
    public <T> byte[] export(String sheetName, List<ExportColumn<T>> columns, List<T> rows) {
        if (columns == null || columns.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "At least one export field is required");
        }
        List<T> safeRows = rows == null ? List.of() : rows;
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(safeSheetName(sheetName));
            CellStyle headerStyle = headerStyle(workbook);
            Row header = sheet.createRow(0);
            for (int columnIndex = 0; columnIndex < columns.size(); columnIndex += 1) {
                ExportColumn<T> column = columns.get(columnIndex);
                Cell cell = header.createCell(columnIndex);
                cell.setCellValue(column.label());
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(columnIndex, Math.min(40, Math.max(12, column.label().length() + 8)) * 256);
            }
            for (int rowIndex = 0; rowIndex < safeRows.size(); rowIndex += 1) {
                Row row = sheet.createRow(rowIndex + 1);
                T source = safeRows.get(rowIndex);
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
            throw new BizException(ErrorCode.BIZ_ERROR, "Export file generation failed");
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
