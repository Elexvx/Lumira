package com.lumira.saas.modules.competition.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.PermissionGuard;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.common.web.WebProperties;
import com.lumira.common.web.repeatsubmit.ClientIpResolver;
import com.lumira.saas.modules.competition.app.CertificateAppService;
import com.lumira.saas.modules.competition.vo.CertificateVO;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;

class CertificateV2ControllerTest {

    @Test
    void publicVerifyIgnoresForwardedForFromUntrustedPeer() {
        CertificateAppService appService = mock(CertificateAppService.class);
        when(appService.verifyByToken(anyString(), anyString(), anyString()))
                .thenReturn(new CertificateVO.PublicVerifyResult());
        CertificateV2Controller controller = controller(appService, false, List.of());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/public/certificates/verify/token-1");
        request.setRemoteAddr("203.0.113.10");
        request.addHeader("X-Forwarded-For", "198.51.100.7");

        controller.verifyByToken("token-1", request);

        ArgumentCaptor<String> clientIp = ArgumentCaptor.forClass(String.class);
        verify(appService).verifyByToken(anyString(), clientIp.capture(), nullable(String.class));
        assertThat(clientIp.getValue()).isEqualTo("203.0.113.10");
    }

    @Test
    void publicVerifyUsesForwardedForFromTrustedPeer() {
        CertificateAppService appService = mock(CertificateAppService.class);
        when(appService.verifyByToken(anyString(), anyString(), anyString()))
                .thenReturn(new CertificateVO.PublicVerifyResult());
        CertificateV2Controller controller = controller(appService, true, List.of("10.0.0.0/8"));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/public/certificates/verify/token-1");
        request.setRemoteAddr("10.1.2.3");
        request.addHeader("X-Forwarded-For", "198.51.100.7, 10.1.2.3");

        controller.verifyByToken("token-1", request);

        ArgumentCaptor<String> clientIp = ArgumentCaptor.forClass(String.class);
        verify(appService).verifyByToken(anyString(), clientIp.capture(), nullable(String.class));
        assertThat(clientIp.getValue()).isEqualTo("198.51.100.7");
    }

    @Test
    void templatesRejectTrustedUserWhenSharedResolverCannotRefreshIt() {
        CertificateAppService appService = mock(CertificateAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        when(securityContextFacade.getCurrentUser()).thenReturn(trustedCurrentUser());
        CertificateV2Controller controller = new CertificateV2Controller(
                appService,
                securityContextFacade,
                mock(PermissionGuard.class),
                new ClientIpResolver(new WebProperties()),
                ignored -> null
        );

        BizException exception = assertThrows(BizException.class, () -> controller.templates(null, null, 1, 10));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
        verifyNoInteractions(appService);
    }

    private CertificateV2Controller controller(
            CertificateAppService appService,
            boolean trustForwardedHeaders,
            List<String> trustedProxyCidrs
    ) {
        WebProperties webProperties = new WebProperties();
        webProperties.setTrustForwardedHeaders(trustForwardedHeaders);
        webProperties.setTrustedProxyCidrs(trustedProxyCidrs);
        return new CertificateV2Controller(
                appService,
                mock(SecurityContextFacade.class),
                mock(PermissionGuard.class),
                new ClientIpResolver(webProperties)
        );
    }

    private static CurrentUser trustedCurrentUser() {
        CurrentUser currentUser = new CurrentUser();
        currentUser.setUserId(1001L);
        currentUser.setUserUuid("user-uuid-1001");
        currentUser.setUsername("operator");
        currentUser.setSessionId("session-1");
        currentUser.setSessionVersion(1);
        currentUser.setPermissionsVersion("permissions-1");
        currentUser.setAuthenticated(true);
        currentUser.setPermissions(java.util.Set.of("aiadc:certificate-template:view"));
        return currentUser;
    }
}
