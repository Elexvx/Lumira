package com.lumira.saas.modules.competition.controller;

import com.lumira.common.security.PermissionGuard;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.common.web.WebProperties;
import com.lumira.common.web.repeatsubmit.ClientIpResolver;
import com.lumira.saas.modules.competition.app.CertificateAppService;
import com.lumira.saas.modules.competition.vo.CertificateVO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CertificateV2ControllerTest {

    @Test
    void publicVerifyShouldIgnoreForwardedForFromUntrustedPeer() {
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
    void publicVerifyShouldUseForwardedForFromTrustedPeer() {
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

    private CertificateV2Controller controller(CertificateAppService appService, boolean trustForwardedHeaders, List<String> trustedProxyCidrs) {
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
}
