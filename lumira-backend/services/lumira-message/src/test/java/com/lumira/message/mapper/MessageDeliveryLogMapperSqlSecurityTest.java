package com.lumira.message.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class MessageDeliveryLogMapperSqlSecurityTest {

    @Test
    void deliveryLogFiltersShouldScopeNonManagersToCreatedByUser() throws Exception {
        String xml = mapperXml();

        assertThat(normalizeSql(xml))
                .contains("query.managedeliverylogs == false")
                .contains("and l.created_by = #{query.userid}");
    }

    private static String mapperXml() throws Exception {
        try (InputStream input = MessageDeliveryLogMapperSqlSecurityTest.class
                .getResourceAsStream("/mapper/MessageDeliveryLogMapper.xml")) {
            assertThat(input)
                    .as("mapper XML is available on the test classpath")
                    .isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String normalizeSql(String sql) {
        return sql.replaceAll("\\s+", " ").trim().toLowerCase();
    }
}
