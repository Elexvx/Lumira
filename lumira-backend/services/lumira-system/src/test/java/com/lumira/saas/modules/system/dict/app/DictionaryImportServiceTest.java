package com.lumira.saas.modules.system.dict.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.lumira.saas.modules.system.dict.repository.DictionaryDatasetRepository;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class DictionaryImportServiceTest {

    private final DictionaryImportService service = new DictionaryImportService(mock(DictionaryDatasetRepository.class));

    @Test
    void previewsUtf8TxtAndReportsDuplicateAndOrphanRows() {
        String content = "itemValue\titemLabel\tparentItemValue\tlevelNo\tsortNo\tstatus\tremark\tleaf\n"
                + "100000\t示例省\t\t1\t10\tENABLED\t\tfalse\n"
                + "100100\t示例市\t100000\t2\t20\tENABLED\t\tfalse\n"
                + "100101\t示例区\t100100\t3\t30\tENABLED\t\ttrue\n"
                + "100101\t重复值\t100100\t3\t40\tENABLED\t\ttrue\n"
                + "200101\t悬空区\t200100\t3\t50\tENABLED\t\ttrue\n";

        var preview = service.preview(new MockMultipartFile(
                "file", "dictionary.txt", "text/plain", content.getBytes(StandardCharsets.UTF_8)
        ), "TREE");

        assertThat(preview.totalRows()).isEqualTo(5);
        assertThat(preview.validRows()).isEqualTo(3);
        assertThat(preview.invalidRows()).isEqualTo(2);
        assertThat(preview.errors()).extracting(DictionaryImportService.ImportError::message)
                .anyMatch(message -> message.contains("Duplicate itemValue"))
                .anyMatch(message -> message.contains("Parent item does not exist"));
    }

    @Test
    void previewsLegacyXlsAndModernXlsx() throws Exception {
        for (var spreadsheet : new Object[][] {
                {"dictionary.xls", new HSSFWorkbook()},
                {"dictionary.xlsx", new XSSFWorkbook()}
        }) {
            String fileName = (String) spreadsheet[0];
            try (Workbook workbook = (Workbook) spreadsheet[1];
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                var sheet = workbook.createSheet("dictionary");
                var header = sheet.createRow(0);
                header.createCell(0).setCellValue("itemValue");
                header.createCell(1).setCellValue("itemLabel");
                var row = sheet.createRow(1);
                row.createCell(0).setCellValue("ENABLED");
                row.createCell(1).setCellValue("启用");
                workbook.write(output);

                var preview = service.preview(new MockMultipartFile(
                        "file", fileName, "application/octet-stream", output.toByteArray()
                ), "FLAT");

                assertThat(preview.totalRows()).as(fileName).isEqualTo(1);
                assertThat(preview.validRows()).as(fileName).isEqualTo(1);
                assertThat(preview.invalidRows()).as(fileName).isZero();
            }
        }
    }

    @Test
    void bundledDatasetsMatchManifestCountsAndStructuralContracts() throws Exception {
        Path root = findRepositoryRoot().resolve("reference-data/dictionaries");
        var schools = service.parse(
                Files.readAllBytes(root.resolve("schools/2026-06-17/sys_school.xlsx")),
                "sys_school.xlsx",
                "FLAT"
        );
        assertThat(schools.rows()).hasSize(3196);
        assertThat(schools.rows().stream().map(DictionaryImportService.DictionaryRow::itemValue).collect(Collectors.toSet()))
                .hasSize(3196);
        assertThat(schools.rows()).filteredOn(row -> row.remark().contains("类别=普通高校")).hasSize(2952);
        assertThat(schools.rows()).filteredOn(row -> row.remark().contains("类别=成人高校")).hasSize(244);

        var divisions = service.parse(
                Files.readAllBytes(root.resolve("cn-administrative-divisions/2025-12-31/sys_cn_administrative_division.xlsx")),
                "sys_cn_administrative_division.xlsx",
                "TREE"
        );
        assertThat(divisions.preview().errors()).isEmpty();
        assertThat(divisions.preview().invalidRows()).isZero();
        assertThat(divisions.rows()).hasSize(3217);
        assertThat(divisions.rows()).filteredOn(row -> row.levelNo() == 1).hasSize(31);
        assertThat(divisions.rows()).filteredOn(row -> row.levelNo() == 1)
                .allSatisfy(row -> assertThat(row.parentItemValue()).isNull());
        assertThat(divisions.rows())
                .allSatisfy(row -> assertThat(row.itemValue()).matches("\\d{6}"))
                .noneSatisfy(row -> assertThat(row.itemLabel()).isEqualTo("台湾省"));
        assertThat(divisions.rows()).anySatisfy(row -> {
            assertThat(row.itemValue()).isEqualTo("110100");
            assertThat(row.parentItemValue()).isEqualTo("110000");
            assertThat(row.leaf()).isFalse();
        });
        assertThat(divisions.rows()).anySatisfy(row -> {
            assertThat(row.itemValue()).isEqualTo("110101");
            assertThat(row.parentItemValue()).isEqualTo("110100");
            assertThat(row.leaf()).isTrue();
        });
        assertThat(divisions.rows()).anySatisfy(row -> {
            assertThat(row.itemValue()).isEqualTo("419000");
            assertThat(row.parentItemValue()).isEqualTo("410000");
            assertThat(row.leaf()).isFalse();
        });
        assertThat(divisions.rows()).anySatisfy(row -> {
            assertThat(row.itemValue()).isEqualTo("419001");
            assertThat(row.parentItemValue()).isEqualTo("419000");
            assertThat(row.leaf()).isTrue();
        });
    }

    private Path findRepositoryRoot() {
        Path current = Path.of("").toAbsolutePath();
        for (int depth = 0; depth < 5 && current != null; depth += 1, current = current.getParent()) {
            if (Files.isDirectory(current.resolve("reference-data/dictionaries"))) {
                return current;
            }
        }
        throw new IllegalStateException("Could not locate repository reference-data directory");
    }
}
