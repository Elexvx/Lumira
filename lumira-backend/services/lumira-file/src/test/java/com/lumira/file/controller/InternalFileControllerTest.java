package com.lumira.file.controller;

import com.lumira.api.file.FileObjectDTO;
import com.lumira.common.security.CurrentUser;
import com.lumira.file.service.FileInternalApiService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InternalFileControllerTest {

    @BeforeEach
    void authenticateInternalService() {
        CurrentUser internalService = new CurrentUser(0L, "internal-service", null, "internal", 0, false, java.util.Set.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(internalService, "internal-token", java.util.Set.of())
        );
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void metadataReadsDefaultToPersonalScope() throws Exception {
        Method method = InternalFileController.class.getMethod(
                "getFileForUser",
                Long.class,
                Long.class,
                String.class,
                String.class,
                boolean.class,
                boolean.class,
                Long.class
        );

        RequestParam sharedScope = requestParam(method, "sharedScope");

        assertThat(sharedScope.defaultValue()).isEqualTo("false");
    }

    @Test
    void uploadImageForUserDelegatesToLocalInternalApiService() {
        FileInternalApiService fileInternalApiService = mock(FileInternalApiService.class);
        InternalFileController controller = new InternalFileController(fileInternalApiService);
        MultipartFile file = mock(MultipartFile.class);
        FileObjectDTO result = mock(FileObjectDTO.class);
        when(fileInternalApiService.uploadImageForUser(file, "avatar", "remark", null, 42L, "user-uuid-42", "alice", null))
                .thenReturn(result);

        FileObjectDTO actual = controller.uploadImageForUser(file, "avatar", "remark", null, 42L, "user-uuid-42", "alice", null);

        assertThat(actual).isSameAs(result);
        verify(fileInternalApiService).uploadImageForUser(file, "avatar", "remark", null, 42L, "user-uuid-42", "alice", null);
    }

    @Test
    void rejectsActingUserRequestWithoutInternalServicePrincipalBeforeDelegatingToService() {
        SecurityContextHolder.clearContext();
        FileInternalApiService fileInternalApiService = mock(FileInternalApiService.class);
        InternalFileController controller = new InternalFileController(fileInternalApiService);

        assertThatThrownBy(() -> controller.searchFilesForUser(42L, "user-uuid-42", "alice", null, null, null, false, 10, null))
                .isInstanceOf(com.lumira.common.exception.BizException.class)
                .hasMessageContaining("Internal service token is required");

        verify(fileInternalApiService, never()).searchFilesForUser(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    private RequestParam requestParam(Method method, String name) {
        for (Parameter parameter : method.getParameters()) {
            RequestParam requestParam = parameter.getAnnotation(RequestParam.class);
            if (requestParam != null && name.equals(requestParam.name())) {
                return requestParam;
            }
        }
        throw new AssertionError("Missing @RequestParam " + name);
    }
}
