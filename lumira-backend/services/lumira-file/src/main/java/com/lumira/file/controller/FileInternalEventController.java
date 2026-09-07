package com.lumira.file.controller;

import com.lumira.api.file.FileObjectUploadedEventCommand;
import com.lumira.common.api.ApiResponse;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.runtime.ConditionalOnLumiraControlPlaneEnabled;
import com.lumira.common.web.InternalJobTokenValidator;
import com.lumira.common.web.internal.InternalHttpClientFactory;
import com.lumira.file.event.FileEventApplicationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Narrow owner command endpoint for the File lifecycle consumer. */
@RestController
@RequestMapping("/file/internal/events")
@ConditionalOnLumiraControlPlaneEnabled
public class FileInternalEventController {

    private static final String CONSUMER_NAME = FileEventApplicationService.CONSUMER_NAME;

    private final FileEventApplicationService fileEventApplicationService;
    private final String fileInternalToken;

    public FileInternalEventController(
            FileEventApplicationService fileEventApplicationService,
            @Value("${saas.internal.file-token:${SAAS_INTERNAL_FILE_TOKEN:}}") String fileInternalToken
    ) {
        this.fileEventApplicationService = fileEventApplicationService;
        this.fileInternalToken = fileInternalToken;
    }

    @PostMapping("/file-object-uploaded")
    public ApiResponse<Boolean> handleFileObjectUploaded(
            @RequestBody FileObjectUploadedEventCommand command,
            @RequestHeader(name = InternalHttpClientFactory.FILE_OWNER_TOKEN_HEADER, required = false) String token,
            @RequestHeader(name = "X-Lumira-Event-Id", required = false) String eventId,
            @RequestHeader(name = "X-Lumira-Producer", required = false) String producer,
            @RequestHeader(name = "X-Lumira-Consumer", required = false) String consumer,
            @RequestHeader(name = InternalHttpClientFactory.RELEASE_ID_HEADER, required = false) String releaseId
    ) {
        ensureAuthorized(token);
        requireHeaderMatches(eventId, command == null ? null : command.eventId(), "X-Lumira-Event-Id");
        requireHeaderMatches(producer, command == null ? null : command.producer(), "X-Lumira-Producer");
        requireHeaderPresent(releaseId, InternalHttpClientFactory.RELEASE_ID_HEADER);
        if (!CONSUMER_NAME.equals(consumer)) {
            throw new BizException(ErrorCode.FORBIDDEN, "File event consumer is not authorized");
        }
        return ApiResponse.success(fileEventApplicationService.handleFileObjectUploaded(command), null);
    }

    private void ensureAuthorized(String token) {
        if (!InternalJobTokenValidator.isConfigured(fileInternalToken)
                || !InternalJobTokenValidator.isAuthorized(token, fileInternalToken)) {
            throw new BizException(ErrorCode.FORBIDDEN, "Unauthorized File event command access");
        }
    }

    private void requireHeaderMatches(String header, String value, String name) {
        if (header == null || header.isBlank() || value == null || !header.trim().equals(value.trim())) {
            throw new BizException(ErrorCode.BAD_REQUEST, name + " must match the event envelope");
        }
    }

    private void requireHeaderPresent(String header, String name) {
        if (header == null || header.isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, name + " is required");
        }
    }
}
