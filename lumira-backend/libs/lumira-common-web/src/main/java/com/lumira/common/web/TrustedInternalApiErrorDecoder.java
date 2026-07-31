package com.lumira.common.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

final class TrustedInternalApiErrorDecoder {

    private static final int MAX_ERROR_PAYLOAD_BYTES = 64 * 1024;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Map<String, ErrorCode> ERROR_CODES = Arrays.stream(ErrorCode.values())
            .collect(Collectors.toUnmodifiableMap(ErrorCode::getCode, Function.identity()));

    private TrustedInternalApiErrorDecoder() {
    }

    static void handle(ClientHttpResponse response) throws IOException {
        ErrorCode trustedErrorCode = decodeTrustedErrorCode(response);
        if (trustedErrorCode == null) {
            throw new BizException(
                    ErrorCode.DEPENDENCY_UNAVAILABLE,
                    "Internal service returned an untrusted error response",
                    ErrorCode.DEPENDENCY_UNAVAILABLE.getDefaultUserMessage()
            );
        }
        throw new BizException(
                trustedErrorCode,
                trustedErrorCode.getDefaultMessage(),
                trustedErrorCode.getDefaultUserMessage()
        );
    }

    private static ErrorCode decodeTrustedErrorCode(ClientHttpResponse response) throws IOException {
        MediaType contentType = response.getHeaders().getContentType();
        if (contentType == null || !MediaType.APPLICATION_JSON.isCompatibleWith(contentType)) {
            return null;
        }
        byte[] payload = response.getBody().readNBytes(MAX_ERROR_PAYLOAD_BYTES + 1);
        if (payload.length == 0 || payload.length > MAX_ERROR_PAYLOAD_BYTES) {
            return null;
        }

        JsonNode root;
        try {
            root = OBJECT_MAPPER.readTree(payload);
        } catch (IOException | RuntimeException exception) {
            return null;
        }
        if (root == null || !root.isObject()) {
            return null;
        }

        JsonNode codeNode = root.get("code");
        JsonNode statusNode = root.get("httpStatus");
        if (codeNode == null || !codeNode.isTextual() || statusNode == null || !statusNode.isIntegralNumber()) {
            return null;
        }
        ErrorCode errorCode = ERROR_CODES.get(codeNode.textValue());
        int actualStatus = response.getStatusCode().value();
        if (errorCode == null
                || errorCode == ErrorCode.SUCCESS
                || errorCode.getHttpStatus() != actualStatus
                || statusNode.intValue() != actualStatus) {
            return null;
        }
        return errorCode;
    }
}
