package com.lumira.asyncruntime;

import com.fasterxml.jackson.core.type.TypeReference;
import com.lumira.api.file.FileEventCommandPort;
import com.lumira.api.file.FileObjectUploadedEventCommand;
import com.lumira.common.api.ApiResponse;
import com.lumira.common.runtime.ConditionalOnLumiraAsyncEnabled;
import com.lumira.common.web.internal.InternalHttpClientFactory;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Delegates File lifecycle side effects to the File owner runtime. */
@Component
@ConditionalOnLumiraAsyncEnabled
public class RemoteFileEventCommandPort implements FileEventCommandPort {

    private static final TypeReference<ApiResponse<Boolean>> RESPONSE = new TypeReference<>() {
    };
    private static final String CONSUMER_NAME = "file-lifecycle-projection";

    private final InternalHttpClientFactory.InternalHttpClient client;

    public RemoteFileEventCommandPort(
            InternalHttpClientFactory clientFactory,
            @Value("${lumira.async.owner-relay.control-plane-base-url:${LUMIRA_ASYNC_CONTROL_PLANE_BASE_URL:http://api-proxy:80}}")
            String baseUrl,
            @Value("${saas.internal.file-token:${SAAS_INTERNAL_FILE_TOKEN:}}") String fileToken
    ) {
        if (!StringUtils.hasText(fileToken)) {
            throw new IllegalArgumentException("file internal token is required");
        }
        this.client = clientFactory.create(
                baseUrl,
                fileToken.trim(),
                InternalHttpClientFactory.FILE_OWNER_TOKEN_HEADER
        );
    }

    @Override
    public boolean handleUploaded(FileObjectUploadedEventCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("File event command is required");
        }
        ApiResponse<Boolean> response = client.post(
                "/file/internal/events/file-object-uploaded",
                command,
                RESPONSE,
                InternalHttpClientFactory.RetryMode.IDEMPOTENT,
                Map.of(
                        "X-Lumira-Event-Id", command.eventId(),
                        "X-Lumira-Producer", command.producer(),
                        "X-Lumira-Consumer", CONSUMER_NAME
                )
        );
        if (response == null || response.getData() == null) {
            throw new IllegalStateException("File owner returned no lifecycle decision");
        }
        return response.getData();
    }
}
