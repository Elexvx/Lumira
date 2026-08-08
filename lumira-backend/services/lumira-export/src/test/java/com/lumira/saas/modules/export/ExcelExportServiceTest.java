package com.lumira.saas.modules.export;

import static org.assertj.core.api.Assertions.assertThat;

import com.lumira.api.export.ExportColumn;
import java.io.ByteArrayInputStream;
import java.util.List;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class ExcelExportServiceTest {

    @Test
    void exportsColumnLabelsAndFormattedValues() throws Exception {
        ExcelExportService service = new ExcelExportService();

        byte[] content = service.export(
                "users",
                List.of(
                        new ExportColumn<>("username", "Username", true, UserRow::username),
                        new ExportColumn<>("roles", "Roles", true, UserRow::roles)
                ),
                List.of(new UserRow("alice", List.of("admin", "editor")))
        );

        assertThat(content).isNotEmpty();
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            assertThat(workbook.getSheetAt(0).getRow(0).getCell(0).getStringCellValue()).isEqualTo("Username");
            assertThat(workbook.getSheetAt(0).getRow(1).getCell(0).getStringCellValue()).isEqualTo("alice");
            assertThat(workbook.getSheetAt(0).getRow(1).getCell(1).getStringCellValue()).isEqualTo("admin、editor");
        }
    }

    private record UserRow(String username, List<String> roles) {
    }
}
