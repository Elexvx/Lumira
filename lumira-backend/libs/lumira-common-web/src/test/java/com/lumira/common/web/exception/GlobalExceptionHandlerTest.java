package com.lumira.common.web.exception;

import com.lumira.common.api.ApiResponse;
import com.lumira.common.web.security.ErrorResponseSanitizer;
import com.lumira.common.web.security.RuntimeEnvironmentService;
import com.lumira.common.web.security.SensitiveErrorMessageSanitizer;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
class GlobalExceptionHandlerTest {

    @Test
    void pathVariableTypeMismatchReturnsStructured400InsteadOfSystemError() throws Exception {
        GlobalExceptionHandler handler = handler();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/files/storage-space-options");
        Method method = GlobalExceptionHandlerTest.class.getDeclaredMethod("longParameter", Long.class);
        MethodArgumentTypeMismatchException exception = new MethodArgumentTypeMismatchException(
                "storage-space-options",
                Long.class,
                "id",
                new MethodParameter(method, 0),
                new NumberFormatException("not a number")
        );

        ResponseEntity<ApiResponse<Void>> response = handler.handleMethodArgumentTypeMismatch(exception, request);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("A0400");
    }

    @Test
    void unsupportedMethodReturnsStructured405InsteadOfSystemError() {
        GlobalExceptionHandler handler = handler();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/refresh-token");

        ResponseEntity<ApiResponse<Void>> response = handler.handleMethodNotSupported(
                new HttpRequestMethodNotSupportedException("GET", List.of("POST")),
                request
        );

        assertThat(response.getStatusCode().value()).isEqualTo(405);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("A0410");
    }

    private GlobalExceptionHandler handler() {
        ErrorResponseSanitizer sanitizer = new ErrorResponseSanitizer(
                new RuntimeEnvironmentService(new MockEnvironment()),
                new SensitiveErrorMessageSanitizer()
        );
        return new GlobalExceptionHandler(sanitizer);
    }

    @SuppressWarnings("unused")
    private void longParameter(Long id) {
    }
}
