package com.lumira.saas.modules.system.export;

import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExcelExportServiceTest {

    @Test
    void exportShouldPreserveHeadersListsAndLongTextValues() throws Exception {
        ExcelExportService service = new ExcelExportService();
        byte[] content = service.export(
                "用户管理",
                List.of(
                        new ExportColumn<>("username", "用户名", true, UserRow::username),
                        new ExportColumn<>("mobile", "手机号", true, UserRow::mobile),
                        new ExportColumn<>("roles", "角色", true, UserRow::roles)
                ),
                List.of(new UserRow("zhangsan", "138001380001234", List.of("管理员", "审计员")))
        );

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            var sheet = workbook.getSheetAt(0);
            assertEquals("用户名", sheet.getRow(0).getCell(0).getStringCellValue());
            assertEquals("手机号", sheet.getRow(0).getCell(1).getStringCellValue());
            assertEquals("角色", sheet.getRow(0).getCell(2).getStringCellValue());
            assertEquals(CellType.STRING, sheet.getRow(1).getCell(1).getCellType());
            assertEquals("138001380001234", sheet.getRow(1).getCell(1).getStringCellValue());
            assertEquals("管理员、审计员", sheet.getRow(1).getCell(2).getStringCellValue());
        }
    }

    private record UserRow(String username, String mobile, List<String> roles) {
    }
}
