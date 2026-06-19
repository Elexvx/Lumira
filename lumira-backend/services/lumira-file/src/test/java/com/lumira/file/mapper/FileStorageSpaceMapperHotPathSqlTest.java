package com.lumira.file.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class FileStorageSpaceMapperHotPathSqlTest {

    @Test
    void storageSpaceHotQueriesShouldForceIndexesAndUseCappedPagination() throws Exception {
        String xml = mapperXml();

        assertStatementContains(xml, "listWithUsage", "<include refid=\"storageSpaceWithUsage\"/>");
        assertStatementContains(xml, "listWithUsage", "limit #{limit} offset #{offset}");
        assertStatementContains(xml, "storageSpaceWithUsage", "from file_storage_space s force index (idx_file_storage_space_tenant_deleted_default_id)");
        assertStatementContains(xml, "storageSpaceWithUsage", "from file_object force index (idx_file_object_tenant_deleted_bucket)");
        assertStatementContains(xml, "storageSpaceWithUsage", "group by bucket");
    }

    private static String mapperXml() throws Exception {
        try (InputStream input = FileStorageSpaceMapperHotPathSqlTest.class.getResourceAsStream("/mapper/FileStorageSpaceMapper.xml")) {
            assertThat(input)
                    .as("mapper XML is available on the test classpath")
                    .isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void assertStatementContains(String xml, String statementId, String expectedSql) {
        Pattern pattern = Pattern.compile(
                "<(?:select|update|insert|delete|sql)\\s+id=\"" + Pattern.quote(statementId) + "\"[\\s\\S]*?</(?:select|insert|update|delete|sql)>",
                Pattern.CASE_INSENSITIVE
        );
        Matcher matcher = pattern.matcher(xml);
        assertThat(matcher.find())
                .as("statement %s exists", statementId)
                .isTrue();
        assertThat(normalizeSql(matcher.group()))
                .as("statement %s SQL", statementId)
                .contains(normalizeSql(expectedSql));
    }

    private static String normalizeSql(String sql) {
        return sql.replaceAll("\\s+", " ").trim().toLowerCase();
    }
}
