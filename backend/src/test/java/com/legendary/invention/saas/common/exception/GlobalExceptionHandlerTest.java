package com.legendary.invention.saas.common.exception;

import com.legendary.invention.saas.common.api.ApiResponse;
import com.legendary.invention.saas.common.enums.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    @Test
    void handleNoResourceFoundShouldReturnUnifiedNotFoundResponse() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/v1/not-exists");
        NoResourceFoundException exception = new NoResourceFoundException(
                HttpMethod.GET,
                "api/v1/not-exists",
                "No static resource api/v1/not-exists"
        );

        ResponseEntity<ApiResponse<Void>> response = new GlobalExceptionHandler().handleNoResourceFound(exception, request);

        assertEquals(404, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(ErrorCode.NOT_FOUND.getHttpStatus(), response.getBody().getHttpStatus());
        assertEquals(ErrorCode.NOT_FOUND.getCode(), response.getBody().getCode());
        assertEquals(ErrorCode.NOT_FOUND.getDefaultMessage(), response.getBody().getMessage());
        assertEquals(ErrorCode.NOT_FOUND.getDefaultUserMessage(), response.getBody().getUserMessage());
        assertEquals("/api/v1/not-exists", response.getBody().getPath());
    }
}
