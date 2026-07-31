package com.lumira.auth.controller;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class AuthWechatCallbackControllerTest {

    private final AuthWechatCallbackController controller = new AuthWechatCallbackController();

    @Test
    void callbackShouldKeepRedirectRelativeBehindTlsProxy() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.callback("code value", "state value", response);

        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getHeader("Location"))
                .isEqualTo("/user/login?code=code+value&state=state+value");
    }

    @Test
    void callbackShouldDiscardIncompleteProviderResponse() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.callback("code-only", null, response);

        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getHeader("Location")).isEqualTo("/user/login");
    }
}
