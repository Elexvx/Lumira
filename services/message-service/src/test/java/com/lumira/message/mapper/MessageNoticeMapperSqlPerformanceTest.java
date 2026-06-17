package com.lumira.message.mapper;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

public class MessageNoticeMapperSqlPerformanceTest {

    @Test
    void countQueriesInMessageNoticeMapperShouldNotUseOrderByBeforeLimit() throws IOException {
        String sql = Files.readString(
                Path.of("src/main/resources/mapper/MessageNoticeMapper.xml"),
                StandardCharsets.UTF_8
        );

        assertFalse(
                sql.contains("select count(*)\n        from (\n            select n.id\n            from msg_notice n force index (idx_msg_notice_visible_recent)\n            where n.tenant_id = #{tenantId}\n              and n.deleted = 0\n              and n.publish_status = 'PUBLISHED'\n              and <include refid=\"visiblePredicate\"/>\n              and not exists (\n                    select 1\n                    from msg_notice_read r\n                    where r.notice_id = n.id\n                      and r.tenant_id = n.tenant_id\n                      and r.user_id = #{userId}\n                      and r.deleted = 0\n              )\n            order by n.id desc"),
                "countUnread SQL should not use order-by before limit; it may trigger unnecessary sort cost.");

        assertFalse(
                sql.contains("select count(*)\n        from (\n            select n.id\n            from msg_notice n force index (idx_msg_notice_visible_recent)\n            where <include refid=\"archiveFilters\"/>\n            order by n.id desc"),
                "countArchive SQL should not use order-by before limit; it may trigger unnecessary sort cost.");
    }
}
