package com.lumira.common.web.exception;

import com.lumira.common.api.ApiResponse;
import com.lumira.common.web.security.ErrorResponseSanitizer;
import com.lumira.common.web.security.RuntimeEnvironmentService;
import com.lumira.common.web.security.SensitiveErrorMessageSanitizer;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
class GlobalExceptionHandlerTest {

    @Test
    void unsupportedMethodReturnsStructured405InsteadOfSystemError() {
        ErrorResponseSanitizer sanitizer = new ErrorResponseSanitizer(
                new RuntimeEnvironmentService(new MockEnvironment()),
                new SensitiveErrorMessageSanitizer()
        );
        GlobalExceptionHandler handler = new GlobalExceptionHandler(sanitizer);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/refresh-token");

        ResponseEntity<ApiResponse<Void>> response = handler.handleMethodNotSupported(
                new HttpRequestMethodNotSupportedException("GET", List.of("POST")),
                request
        );

        assertThat(response.getStatusCode().value()).isEqualTo(405);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("A0410");
    }
}
