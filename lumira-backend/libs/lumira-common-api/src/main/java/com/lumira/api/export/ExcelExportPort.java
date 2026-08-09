package com.lumira.api.export;

import java.util.List;

/**
 * Renders a bounded-context export payload without coupling callers to the
 * export service implementation.
 */
public interface ExcelExportPort {

    <T> byte[] export(String sheetName, List<ExportColumn<T>> columns, List<T> rows);
}
