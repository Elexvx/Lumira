package com.lumira.message.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class MessageNoticeMapperHotPathSqlTest {

    @Test
    void hotPathQueriesShouldForceVisibleRecentIndexAndUseCappedCounts() throws Exception {
        String xml = mapperXml();

        assertThat(normalizeSql(xml))
                .contains("n.target_user_id = #{userid} and n.target_user_uuid = #{useruuid}")
                .contains("n.target_user_id = #{query.userid} and n.target_user_uuid = #{query.useruuid}");

        assertStatementContains(xml, "listVisiblePublished", "from msg_notice n force index (idx_msg_notice_visible_recent)");
        assertStatementContains(xml, "listVisiblePublished", "limit #{limit} offset #{offset}");

        assertStatementContains(xml, "countUnread", "from msg_notice n force index (idx_msg_notice_visible_recent)");
        assertStatementContains(xml, "countUnread", "limit #{limit}");
        assertStatementContains(xml, "countUnread", "not exists");
        assertStatementContains(xml, "countUnread", "and r.user_uuid = #{userUuid}");

        assertStatementContains(xml, "markAllRead", "from msg_notice n force index (idx_msg_notice_visible_recent)");
        assertStatementContains(xml, "markAllRead", "not exists");
        assertStatementContains(xml, "markAllRead", "notice_id, user_id, user_uuid");
        assertStatementContains(xml, "upsertRead", "notice_id, user_id, user_uuid");
        assertStatementContains(xml, "upsertRead", "from msg_notice n force index (idx_msg_notice_visible_recent)");
        assertStatementContains(xml, "upsertRead", "<include refid=\"visiblePredicate\"/>");
        assertStatementContains(xml, "upsertRead", "n.publish_status = 'PUBLISHED'");

        assertStatementContains(xml, "countArchive", "from msg_notice n force index (idx_msg_notice_visible_recent)");
        assertStatementContains(xml, "countArchive", "limit #{query.countLimit}");
    }

    private static String mapperXml() throws Exception {
        try (InputStream input = MessageNoticeMapperHotPathSqlTest.class
                .getResourceAsStream("/mapper/MessageNoticeMapper.xml")) {
            assertThat(input)
                    .as("mapper XML is available on the test classpath")
                    .isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void assertStatementContains(String xml, String statementId, String expectedSql) {
        Pattern pattern = Pattern.compile(
                "<(?:select|insert|update|delete)\\s+id=\"" + Pattern.quote(statementId) + "\"[\\s\\S]*?</(?:select|insert|update|delete)>",
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
