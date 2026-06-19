package com.lumira.file.config;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.lumira.file.entity.FileObjectEntity;
import com.lumira.file.entity.FileStorageSpaceEntity;
import com.lumira.file.mapper.FileObjectMapper;
import com.lumira.file.mapper.FileStorageSpaceMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UploadResourceSecurityInterceptorTest {

    @Test
    void allowsRegisteredPublicUploadResource() throws Exception {
        FileObjectMapper mapper = mock(FileObjectMapper.class);
        FileStorageSpaceMapper storageSpaceMapper = mock(FileStorageSpaceMapper.class);
        when(mapper.selectOne(anyWrapper())).thenReturn(publicFile());
        when(storageSpaceMapper.findByStorageKey(1001L, "local")).thenReturn(storageSpace(true, "ENABLED"));
        UploadResourceSecurityInterceptor interceptor = new UploadResourceSecurityInterceptor(mapper, storageSpaceMapper);
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request("/api/uploads/2026/06/avatar.png"), response, new Object());

        assertTrue(allowed);
        assertEquals("nosniff", response.getHeader("X-Content-Type-Options"));
    }

    @Test
    void rejectsPersonalUploadResourceWithoutPublicRecord() throws Exception {
        FileObjectMapper mapper = mock(FileObjectMapper.class);
        FileStorageSpaceMapper storageSpaceMapper = mock(FileStorageSpaceMapper.class);
        when(mapper.selectOne(anyWrapper())).thenReturn(null);
        UploadResourceSecurityInterceptor interceptor = new UploadResourceSecurityInterceptor(mapper, storageSpaceMapper);
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request("/api/uploads/2026/06/private.pdf"), response, new Object());

        assertFalse(allowed);
        assertEquals(HttpServletResponse.SC_FORBIDDEN, response.getStatus());
    }

    @Test
    void rejectsDownloadCenterDirectResourceAccess() throws Exception {
        FileObjectMapper mapper = mock(FileObjectMapper.class);
        FileStorageSpaceMapper storageSpaceMapper = mock(FileStorageSpaceMapper.class);
        when(mapper.selectOne(anyWrapper())).thenReturn(publicFile());
        when(storageSpaceMapper.findByStorageKey(1001L, "local")).thenReturn(storageSpace(true, "ENABLED"));
        UploadResourceSecurityInterceptor interceptor = new UploadResourceSecurityInterceptor(mapper, storageSpaceMapper);
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request("/api/uploads/download_center/report.pdf"), response, new Object());

        assertFalse(allowed);
        assertEquals(HttpServletResponse.SC_FORBIDDEN, response.getStatus());
    }

    @Test
    void rejectsPublicFileWhenStorageSpaceDisallowsAnonymousAccess() throws Exception {
        FileObjectMapper mapper = mock(FileObjectMapper.class);
        FileStorageSpaceMapper storageSpaceMapper = mock(FileStorageSpaceMapper.class);
        when(mapper.selectOne(anyWrapper())).thenReturn(publicFile());
        when(storageSpaceMapper.findByStorageKey(1001L, "local")).thenReturn(storageSpace(false, "ENABLED"));
        UploadResourceSecurityInterceptor interceptor = new UploadResourceSecurityInterceptor(mapper, storageSpaceMapper);
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request("/api/uploads/2026/06/avatar.png"), response, new Object());

        assertFalse(allowed);
        assertEquals(HttpServletResponse.SC_FORBIDDEN, response.getStatus());
    }

    @SuppressWarnings("unchecked")
    private static Wrapper<FileObjectEntity> anyWrapper() {
        return any(Wrapper.class);
    }

    private static MockHttpServletRequest request(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setRequestURI(uri);
        return request;
    }

    private static FileObjectEntity publicFile() {
        FileObjectEntity entity = new FileObjectEntity();
        entity.setTenantId(1001L);
        entity.setBucket("local");
        return entity;
    }

    private static FileStorageSpaceEntity storageSpace(boolean anonymousAccessAllowed, String status) {
        FileStorageSpaceEntity entity = new FileStorageSpaceEntity();
        entity.setAnonymousAccessAllowed(anonymousAccessAllowed ? 1 : 0);
        entity.setStatus(status);
        return entity;
    }
}
