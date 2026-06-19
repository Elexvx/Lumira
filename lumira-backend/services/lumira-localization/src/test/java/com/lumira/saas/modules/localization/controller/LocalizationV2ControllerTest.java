package com.lumira.saas.modules.localization.controller;

import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.PermissionGuard;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.saas.modules.localization.app.LocalizationManagementAppService;
import com.lumira.saas.modules.localization.dto.LocalizationDTO;
import com.lumira.saas.modules.localization.vo.LocalizationVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LocalizationV2ControllerTest {

    private LocalizationManagementAppService localizationManagementAppService;
    private SecurityContextFacade securityContextFacade;
    private PermissionGuard permissionGuard;
    private LocalizationV2Controller controller;

    @BeforeEach
    void setUp() {
        localizationManagementAppService = mock(LocalizationManagementAppService.class);
        securityContextFacade = mock(SecurityContextFacade.class);
        permissionGuard = mock(PermissionGuard.class);
        controller = new LocalizationV2Controller(localizationManagementAppService, securityContextFacade, permissionGuard);
    }

    @Test
    void runtime_shouldDelegateWithoutPermissionGate() {
        LocalizationVO.RuntimeBundleVO bundle = new LocalizationVO.RuntimeBundleVO();
        bundle.setLocaleCode("zh-CN");
        bundle.setReleaseVersion(7L);
        bundle.setMessages(Map.of("common.ok", "确定"));
        when(localizationManagementAppService.runtimeBundle("zh-CN")).thenReturn(bundle);

        var response = controller.runtime("zh-CN");

        assertThat(response.getData()).isSameAs(bundle);
        verify(localizationManagementAppService).runtimeBundle("zh-CN");
        verify(permissionGuard, never()).requirePermission(null, "localization:view");
    }

    @Test
    void listLanguages_shouldCheckPermissionAndDelegate() {
        CurrentUser currentUser = currentUser("localization:view");
        LocalizationVO.LanguageVO language = new LocalizationVO.LanguageVO();
        language.setLocaleCode("zh-CN");
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(localizationManagementAppService.listLanguages()).thenReturn(List.of(language));

        var response = controller.listLanguages();

        assertThat(response.getData()).containsExactly(language);
        verify(permissionGuard).requirePermission(currentUser, "localization:view");
        verify(localizationManagementAppService).listLanguages();
    }

    @Test
    void listEntries_shouldPassBoundedQueryToApplicationService() {
        CurrentUser currentUser = currentUser("localization:view");
        LocalizationVO.EntryPageResponse page = new LocalizationVO.EntryPageResponse();
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(localizationManagementAppService.listEntries("en-US", "common", "ok", "ENABLED", "TRANSLATED", 2L, 50L, "updatedAt", "descend"))
                .thenReturn(page);

        var response = controller.listEntries("en-US", "common", "ok", "ENABLED", "TRANSLATED", "updatedAt", "descend", 2L, 50L);

        assertThat(response.getData()).isSameAs(page);
        verify(localizationManagementAppService).listEntries("en-US", "common", "ok", "ENABLED", "TRANSLATED", 2L, 50L, "updatedAt", "descend");
    }

    @Test
    void publish_shouldCheckPermissionAndDelegateWithCurrentUser() {
        CurrentUser currentUser = currentUser("localization:publish");
        LocalizationDTO.PublishRequest request = new LocalizationDTO.PublishRequest();
        request.setLocaleCode("zh-CN");
        LocalizationVO.ReleaseVO release = new LocalizationVO.ReleaseVO();
        release.setReleaseVersion(8L);
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(localizationManagementAppService.publish(request, currentUser)).thenReturn(release);

        var response = controller.publish(request);

        assertThat(response.getData()).isSameAs(release);
        verify(permissionGuard).requirePermission(currentUser, "localization:publish");
        verify(localizationManagementAppService).publish(request, currentUser);
    }

    @Test
    void rollback_shouldCheckPermissionAndDelegateWithCurrentUser() {
        CurrentUser currentUser = currentUser("localization:rollback");
        LocalizationDTO.RollbackRequest request = new LocalizationDTO.RollbackRequest();
        request.setReleaseId(9L);
        LocalizationVO.ReleaseVO release = new LocalizationVO.ReleaseVO();
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(localizationManagementAppService.rollback(request, currentUser)).thenReturn(release);

        var response = controller.rollback(request);

        assertThat(response.getData()).isSameAs(release);
        verify(permissionGuard).requirePermission(currentUser, "localization:rollback");
        verify(localizationManagementAppService).rollback(request, currentUser);
    }

    @Test
    void updateEntry_shouldCopyPathIdIntoRequestBeforeDelegating() {
        CurrentUser currentUser = currentUser("localization:update");
        LocalizationDTO.EntryUpsertRequest request = new LocalizationDTO.EntryUpsertRequest();
        LocalizationVO.EntryVO entry = new LocalizationVO.EntryVO();
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(localizationManagementAppService.saveEntry(request)).thenReturn(entry);

        var response = controller.updateEntry(33L, request);

        assertThat(response.getData()).isSameAs(entry);
        assertThat(request.getId()).isEqualTo(33L);
        verify(permissionGuard).requirePermission(currentUser, "localization:update");
        verify(localizationManagementAppService).saveEntry(request);
    }

    @Test
    void listLanguages_shouldRejectMissingPermissionBeforeApplicationService() {
        CurrentUser currentUser = currentUser("localization:other");
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        doThrow(new BizException(com.lumira.common.enums.ErrorCode.FORBIDDEN, "缺少权限: localization:view"))
                .when(permissionGuard).requirePermission(currentUser, "localization:view");

        assertThatThrownBy(() -> controller.listLanguages())
                .isInstanceOf(BizException.class)
                .hasMessageContaining("缺少权限");
    }

    private CurrentUser currentUser(String permission) {
        return new CurrentUser(100L, "alice", 1001L, "session-1", 1, true, Set.of(permission));
    }
}
