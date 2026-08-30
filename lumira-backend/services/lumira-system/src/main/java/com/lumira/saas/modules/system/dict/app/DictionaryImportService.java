package com.lumira.saas.modules.system.dict.app;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.saas.modules.system.dict.dto.DictionaryImportMetadataRequest;
import com.lumira.saas.modules.system.dict.repository.DictionaryDatasetRepository;
import java.io.ByteArrayInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DictionaryImportService {
    public static final int MAX_ROWS = 20_000;
    public static final long MAX_FILE_BYTES = 20L * 1024L * 1024L;
    private static final int MAX_PREVIEW_ROWS = 20;
    private static final int MAX_ERRORS = 100;
    private static final List<String> HEADERS = List.of(
            "itemValue", "itemLabel", "parentItemValue", "levelNo", "sortNo", "status", "remark", "leaf"
    );

    private final DictionaryDatasetRepository repository;

    public DictionaryImportService(DictionaryDatasetRepository repository) {
        this.repository = repository;
    }

    public ImportPreview preview(MultipartFile file, String structureType) {
        FilePayload payload = readFile(file);
        return parse(payload.bytes(), payload.filename(), normalizeStructureType(structureType)).preview();
    }

    @Transactional
    public ImportResult importNewType(
            DictionaryImportMetadataRequest metadata,
            MultipartFile file,
            Long actorId,
            String actorUuid
    ) {
        if (metadata == null) {
            throw badRequest("Dictionary import metadata is required");
        }
        FilePayload payload = readFile(file);
        String structureType = normalizeStructureType(metadata.getStructureType());
        ParsedFile parsed = parse(payload.bytes(), payload.filename(), structureType);
        requireValid(parsed);
        if (StringUtils.hasText(metadata.getExpectedSha256())
                && !metadata.getExpectedSha256().trim().equalsIgnoreCase(parsed.sha256())) {
            throw badRequest("Dictionary import file changed after preview");
        }
        Long typeId = insertType(metadata.getDictCode(), metadata.getDictName(), metadata.getStatus(), metadata.getRemark(),
                structureType, false, actorId, actorUuid);
        insertItems(typeId, parsed.rows(), actorId, actorUuid);
        return new ImportResult(typeId, metadata.getDictCode().trim(), parsed.rows().size(), parsed.sha256());
    }

    @Transactional
    public ImportResult installBuiltInDataset(DatasetDefinition definition, byte[] bytes) {
        ParsedFile parsed = parse(bytes, definition.filename(), normalizeStructureType(definition.structureType()));
        requireValid(parsed);
        if (!parsed.sha256().equalsIgnoreCase(definition.sha256())) {
            throw new IllegalStateException("Dictionary dataset checksum mismatch: " + definition.datasetCode());
        }
        if (parsed.rows().size() != definition.rowCount()) {
            throw new IllegalStateException("Dictionary dataset row count mismatch: " + definition.datasetCode());
        }
        Long typeId = insertType(definition.dictCode(), definition.dictName(), "ENABLED", definition.remark(),
                definition.structureType(), true, 0L, null);
        insertItems(typeId, parsed.rows(), 0L, null);
        repository.recordInstallation(new DictionaryDatasetRepository.Installation(
                definition.datasetCode(), definition.version(), definition.sha256(), definition.rowCount(),
                "INSTALLED", LocalDateTime.now()
        ));
        return new ImportResult(typeId, definition.dictCode(), parsed.rows().size(), parsed.sha256());
    }

    public ParsedFile parse(byte[] bytes, String filename, String structureType) {
        if (bytes == null || bytes.length == 0) {
            throw badRequest("Dictionary import file is required");
        }
        if (bytes.length > MAX_FILE_BYTES) {
            throw badRequest("Dictionary import file exceeds 20 MB");
        }
        String extension = extension(filename);
        List<RawRow> rawRows = switch (extension) {
            case "txt" -> parseTxt(bytes);
            case "xls", "xlsx" -> parseWorkbook(bytes);
            default -> throw badRequest("Only TXT, XLS and XLSX dictionary files are supported");
        };
        if (rawRows.size() > MAX_ROWS) {
            throw badRequest("Dictionary import file exceeds 20000 rows");
        }
        List<ImportError> errors = new ArrayList<>();
        List<DictionaryRow> rows = new ArrayList<>();
        Set<String> values = new LinkedHashSet<>();
        Set<Integer> invalidRows = new HashSet<>();
        for (RawRow raw : rawRows) {
            String value = text(raw.values().get("itemValue"));
            String label = text(raw.values().get("itemLabel"));
            if (!StringUtils.hasText(value) || !StringUtils.hasText(label)) {
                addError(errors, invalidRows, raw.rowNumber(), "itemValue and itemLabel are required");
                continue;
            }
            if (value.length() > 64 || label.length() > 128) {
                addError(errors, invalidRows, raw.rowNumber(), "itemValue or itemLabel is too long");
                continue;
            }
            if (!values.add(value)) {
                addError(errors, invalidRows, raw.rowNumber(), "Duplicate itemValue: " + value);
                continue;
            }
            try {
                String parent = nullableText(raw.values().get("parentItemValue"));
                int level = integer(raw.values().get("levelNo"), 1);
                int sort = integer(raw.values().get("sortNo"), raw.rowNumber() * 10);
                String status = text(raw.values().get("status"));
                status = StringUtils.hasText(status) ? status.toUpperCase(Locale.ROOT) : "ENABLED";
                if (!Set.of("ENABLED", "DISABLED").contains(status)) {
                    throw new IllegalArgumentException("status must be ENABLED or DISABLED");
                }
                boolean leaf = bool(raw.values().get("leaf"), true);
                if ("FLAT".equals(structureType)) {
                    parent = null;
                    level = 1;
                    leaf = true;
                }
                if (level < 1 || level > 9) {
                    throw new IllegalArgumentException("levelNo must be between 1 and 9");
                }
                rows.add(new DictionaryRow(value, label, parent, level, sort, status,
                        nullableText(raw.values().get("remark")), leaf, raw.rowNumber()));
            } catch (IllegalArgumentException exception) {
                addError(errors, invalidRows, raw.rowNumber(), exception.getMessage());
            }
        }
        if ("TREE".equals(structureType)) {
            validateTree(rows, errors, invalidRows);
        }
        String sha256 = sha256(bytes);
        List<DictionaryRow> previewRows = rows.stream().limit(MAX_PREVIEW_ROWS).toList();
        int validRows = (int) rows.stream().filter(row -> !invalidRows.contains(row.rowNumber())).count();
        ImportPreview preview = new ImportPreview(
                sha256, rawRows.size(), validRows, invalidRows.size(),
                List.copyOf(errors), previewRows
        );
        return new ParsedFile(sha256, List.copyOf(rows), preview);
    }

    private List<RawRow> parseTxt(byte[] bytes) {
        String content = new String(bytes, StandardCharsets.UTF_8);
        if (content.startsWith("\uFEFF")) {
            content = content.substring(1);
        }
        try (BufferedReader reader = new BufferedReader(new StringReader(content))) {
            String header = reader.readLine();
            if (header == null) {
                throw badRequest("Dictionary TXT file is empty");
            }
            Map<Integer, String> headers = parseHeaders(List.of(header.split("\\t", -1)));
            List<RawRow> rows = new ArrayList<>();
            String line;
            int rowNumber = 1;
            while ((line = reader.readLine()) != null) {
                rowNumber += 1;
                if (line.isBlank()) continue;
                if (rows.size() >= MAX_ROWS) {
                    throw badRequest("Dictionary import file exceeds 20000 rows");
                }
                rows.add(rawRow(rowNumber, headers, List.of(line.split("\\t", -1))));
            }
            return rows;
        } catch (IOException exception) {
            throw badRequest("Failed to read dictionary TXT file");
        }
    }

    private List<RawRow> parseWorkbook(byte[] bytes) {
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            if (workbook.getNumberOfSheets() == 0) {
                throw badRequest("Dictionary workbook has no worksheet");
            }
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) {
                throw badRequest("Dictionary workbook header is missing");
            }
            if ((long) sheet.getLastRowNum() - headerRow.getRowNum() > MAX_ROWS) {
                throw badRequest("Dictionary import file exceeds 20000 rows");
            }
            DataFormatter formatter = new DataFormatter(Locale.ROOT);
            List<String> headerValues = new ArrayList<>();
            for (int cell = 0; cell < headerRow.getLastCellNum(); cell++) {
                headerValues.add(formatter.formatCellValue(headerRow.getCell(cell)));
            }
            Map<Integer, String> headers = parseHeaders(headerValues);
            List<RawRow> rows = new ArrayList<>();
            for (int rowIndex = headerRow.getRowNum() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) continue;
                List<String> cells = new ArrayList<>();
                boolean any = false;
                for (int cell = 0; cell < headerValues.size(); cell++) {
                    String value = formatter.formatCellValue(row.getCell(cell));
                    cells.add(value);
                    any |= StringUtils.hasText(value);
                }
                if (any) {
                    if (rows.size() >= MAX_ROWS) {
                        throw badRequest("Dictionary import file exceeds 20000 rows");
                    }
                    rows.add(rawRow(rowIndex + 1, headers, cells));
                }
            }
            return rows;
        } catch (BizException exception) {
            throw exception;
        } catch (Exception exception) {
            throw badRequest("Failed to read dictionary workbook");
        }
    }

    private Map<Integer, String> parseHeaders(List<String> cells) {
        Map<String, String> normalized = HEADERS.stream().collect(Collectors.toMap(
                value -> value.toLowerCase(Locale.ROOT), value -> value
        ));
        Map<Integer, String> headers = new LinkedHashMap<>();
        for (int index = 0; index < cells.size(); index++) {
            String cell = text(cells.get(index));
            String header = normalized.get(cell.toLowerCase(Locale.ROOT));
            if (header != null) headers.put(index, header);
        }
        if (!headers.containsValue("itemValue") || !headers.containsValue("itemLabel")) {
            throw badRequest("Dictionary file must contain itemValue and itemLabel headers");
        }
        return headers;
    }

    private RawRow rawRow(int rowNumber, Map<Integer, String> headers, List<String> cells) {
        Map<String, String> values = new HashMap<>();
        headers.forEach((index, header) -> values.put(header, index < cells.size() ? cells.get(index) : ""));
        return new RawRow(rowNumber, values);
    }

    private void validateTree(List<DictionaryRow> rows, List<ImportError> errors, Set<Integer> invalidRows) {
        Map<String, DictionaryRow> byValue = rows.stream().collect(Collectors.toMap(
                DictionaryRow::itemValue, row -> row, (first, ignored) -> first, LinkedHashMap::new
        ));
        for (DictionaryRow row : rows) {
            if (row.levelNo() == 1 && StringUtils.hasText(row.parentItemValue())) {
                addError(errors, invalidRows, row.rowNumber(), "Level 1 item cannot have a parent");
                continue;
            }
            if (row.levelNo() > 1) {
                DictionaryRow parent = byValue.get(row.parentItemValue());
                if (parent == null) {
                    addError(errors, invalidRows, row.rowNumber(), "Parent item does not exist: " + row.parentItemValue());
                } else if (parent.levelNo() + 1 != row.levelNo()) {
                    addError(errors, invalidRows, row.rowNumber(), "Parent level does not match levelNo");
                } else if (parent.leaf()) {
                    addError(errors, invalidRows, parent.rowNumber(), "Parent item must not be marked as leaf");
                }
            }
        }
    }

    private Long insertType(String code, String name, String status, String remark, String structureType,
                            boolean system, Long actorId, String actorUuid) {
        if (!StringUtils.hasText(code) || !StringUtils.hasText(name)) {
            throw badRequest("Dictionary code and name are required");
        }
        if (repository.countActiveTypes(code.trim()) > 0) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "Dictionary code already exists: " + code.trim());
        }
        String normalizedStatus = StringUtils.hasText(status) ? status.trim().toUpperCase(Locale.ROOT) : "ENABLED";
        return repository.insertType(new DictionaryDatasetRepository.TypeInsert(
                code.trim(), name.trim(), normalizedStatus, system, nullableText(remark),
                normalizeStructureType(structureType), actorId == null ? 0L : actorId, actorUuid
        ));
    }

    private void insertItems(Long typeId, List<DictionaryRow> rows, Long actorId, String actorUuid) {
        repository.insertItems(typeId, rows, actorId, actorUuid);
    }

    private FilePayload readFile(MultipartFile file) {
        if (file == null || file.isEmpty()) throw badRequest("Dictionary import file is required");
        try {
            return new FilePayload(file.getOriginalFilename() == null ? "dictionary.txt" : file.getOriginalFilename(), file.getBytes());
        } catch (Exception exception) {
            throw badRequest("Failed to read dictionary import file");
        }
    }

    private void requireValid(ParsedFile parsed) {
        if (parsed.preview().invalidRows() > 0 || !parsed.preview().errors().isEmpty()) {
            throw badRequest("Dictionary import contains invalid rows");
        }
        if (parsed.rows().isEmpty()) throw badRequest("Dictionary import contains no items");
    }

    private void addError(List<ImportError> errors, Set<Integer> invalidRows, int rowNumber, String message) {
        invalidRows.add(rowNumber);
        if (errors.size() < MAX_ERRORS) errors.add(new ImportError(rowNumber, message));
    }

    private String normalizeStructureType(String value) {
        String normalized = StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "FLAT";
        if (!Set.of("FLAT", "TREE").contains(normalized)) throw badRequest("structureType must be FLAT or TREE");
        return normalized;
    }

    private String extension(String filename) {
        String value = filename == null ? "" : filename;
        int dot = value.lastIndexOf('.');
        return dot < 0 ? "" : value.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private int integer(String value, int fallback) {
        if (!StringUtils.hasText(value)) return fallback;
        try {
            return Integer.parseInt(value.trim().replaceFirst("\\.0+$", ""));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid integer value: " + value);
        }
    }

    private boolean bool(String value, boolean fallback) {
        if (!StringUtils.hasText(value)) return fallback;
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "1", "true", "yes", "是" -> true;
            case "0", "false", "no", "否" -> false;
            default -> throw new IllegalArgumentException("Invalid leaf value: " + value);
        };
    }

    private static String text(String value) { return value == null ? "" : value.trim(); }
    private static String nullableText(String value) { return StringUtils.hasText(value) ? value.trim() : null; }

    private static String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static BizException badRequest(String message) {
        return new BizException(ErrorCode.BAD_REQUEST, message);
    }

    private record FilePayload(String filename, byte[] bytes) {}
    private record RawRow(int rowNumber, Map<String, String> values) {}

    public record DictionaryRow(
            String itemValue, String itemLabel, String parentItemValue, int levelNo, int sortNo,
            String status, String remark, boolean leaf, int rowNumber
    ) {}
    public record ImportError(int rowNumber, String message) {}
    public record ImportPreview(
            String fileSha256, int totalRows, int validRows, int invalidRows,
            List<ImportError> errors, List<DictionaryRow> previewRows
    ) {}
    public record ImportResult(Long dictTypeId, String dictCode, int importedRows, String fileSha256) {}
    public record ParsedFile(String sha256, List<DictionaryRow> rows, ImportPreview preview) {}
    public record DatasetDefinition(
            String datasetCode, String dictCode, String dictName, String structureType, String version,
            String filename, String sha256, int rowCount, String remark
    ) {}
}
