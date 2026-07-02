package com.lumira.file.controller;

import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.file.app.FileManagementAppService;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class InternalFileControllerTest {

    @Test
    void internalUserFileOperationsDoNotReceiveWildcardPermission() {
        FileManagementAppService appService = mock(FileManagementAppService.class);
        InternalFileController controller = new InternalFileController(appService, mock(SecurityContextFacade.class));
        MultipartFile file = mock(MultipartFile.class);

        controller.uploadDocumentForUser(file, "knowledge", "ai", "remark", null, 42L, "alice");

        org.mockito.ArgumentCaptor<CurrentUser> userCaptor = org.mockito.ArgumentCaptor.forClass(CurrentUser.class);
        verify(appService).uploadDocument(userCaptor.capture(), eq(file), eq("knowledge"), eq("ai"), eq("remark"), any());
        CurrentUser currentUser = userCaptor.getValue();
        assertThat(currentUser.getUserId()).isEqualTo(42L);
        assertThat(currentUser.getUsername()).isEqualTo("alice");
        assertThat(currentUser.getPermissions())
                .contains("system:file:upload", "system:file:view", "download:center:view")
                .doesNotContain("*");
    }
}
