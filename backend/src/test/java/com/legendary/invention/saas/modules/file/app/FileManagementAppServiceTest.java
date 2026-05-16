package com.legendary.invention.saas.modules.file.app;

import com.legendary.invention.saas.infrastructure.security.CurrentUser;
import com.legendary.invention.saas.infrastructure.upload.DocumentUploadService;
import com.legendary.invention.saas.infrastructure.upload.UploadProperties;
import com.legendary.invention.saas.modules.file.vo.FileVO;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FileManagementAppServiceTest {

    @Test
    void listFilesDefaultsToCurrentUserScope() {
        JdbcTemplate jdbcTemplate = mockJdbcTemplate();
        FileManagementAppService service = newService(jdbcTemplate);

        service.listFiles(currentUser(), null, null, null, null, null, null, 1, 10, null, null);

        String countSql = capturedCountSql(jdbcTemplate);
        assertTrue(countSql.contains("f.uploaded_by = ?"));
    }

    @Test
    void listFilesTenantScopeDoesNotFilterUploadedBy() {
        JdbcTemplate jdbcTemplate = mockJdbcTemplate();
        FileManagementAppService service = newService(jdbcTemplate);

        service.listFiles(currentUser(), null, null, null, null, null, FileManagementAppService.SCOPE_TENANT, 1, 10, null, null);

        String countSql = capturedCountSql(jdbcTemplate);
        assertFalse(countSql.contains("f.uploaded_by = ?"));
    }

    private FileManagementAppService newService(JdbcTemplate jdbcTemplate) {
        UploadProperties uploadProperties = new UploadProperties();
        return new FileManagementAppService(jdbcTemplate, uploadProperties, mock(DocumentUploadService.class));
    }

    private JdbcTemplate mockJdbcTemplate() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
        when(jdbcTemplate.query(anyString(), any(BeanPropertyRowMapper.class), any(Object[].class))).thenReturn(List.<FileVO.FileObjectVO>of());
        return jdbcTemplate;
    }

    private String capturedCountSql(JdbcTemplate jdbcTemplate) {
        return org.mockito.Mockito.mockingDetails(jdbcTemplate)
                .getInvocations()
                .stream()
                .filter((invocation) -> "queryForObject".equals(invocation.getMethod().getName()))
                .map((invocation) -> invocation.getArgument(0, String.class))
                .findFirst()
                .orElse("");
    }

    private CurrentUser currentUser() {
        return new CurrentUser(1001L, "admin", 1001L, "session", 1, true, Set.of("system:file:view"));
    }
}
