package com.lumira.common.web;

import com.lumira.api.client.FileInternalApi;
import java.util.Iterator;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class FileInternalClientConfigurationTest {

    @Test
    void fileInternalApiUploadsDocumentForUserWithScopedFileToken() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        FileInternalApi api = fileInternalApi(builder, "file-token-2026");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "knowledge.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "hello lumira".getBytes()
        );
        server.expect(requestTo("http://file-service:8084/internal/files/documents/as-user"))
                .andExpect(header("X-Job-Token", "file-token-2026"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.MULTIPART_FORM_DATA))
                .andExpect(clientRequest -> {
                    String body = ((MockClientHttpRequest) clientRequest).getBodyAsString();
                    assertThat(body).contains("name=\"category\"");
                    assertThat(body).contains("knowledge");
                    assertThat(body).contains("name=\"tags\"");
                    assertThat(body).contains("ai,faq");
                    assertThat(body).contains("name=\"userId\"");
                    assertThat(body).contains("1001");
                    assertThat(body).contains("filename=\"knowledge.txt\"");
                })
                .andRespond(withSuccess(
                        "{\"id\":301,\"originalFileName\":\"knowledge.txt\",\"publicUrl\":\"/api/uploads/knowledge.txt\"}",
                        MediaType.APPLICATION_JSON
                ));

        var uploaded = api.uploadDocumentForUser(file, "knowledge", "ai,faq", "remark", "bucket-a", 1001L, "user-uuid-1001", "alice");

        assertThat(uploaded.id()).isEqualTo(301L);
        assertThat(uploaded.originalFileName()).isEqualTo("knowledge.txt");
        server.verify();
    }

    @Test
    void fileInternalApiReadsMetadataWithScopedFileToken() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        FileInternalApi api = fileInternalApi(builder, "file-token-2026");
        server.expect(requestTo("http://file-service:8084/internal/files/metadata?fileId=2001&userId=1001&userUuid=user-uuid-1001&username=alice&sharedScope=false&downloadCenterScope=true"))
                .andExpect(header("X-Job-Token", "file-token-2026"))
                .andRespond(withSuccess(
                        "{\"id\":2001,\"originalFileName\":\"avatar.png\",\"mimeType\":\"image/png\"}",
                        MediaType.APPLICATION_JSON
                ));

        var file = api.getFileForUser(2001L, 1001L, "user-uuid-1001", "alice", false, true);

        assertThat(file.id()).isEqualTo(2001L);
        assertThat(file.originalFileName()).isEqualTo("avatar.png");
        server.verify();
    }

    @Test
    void fileInternalApiRequiresFileToken() {
        assertThatThrownBy(() -> fileInternalApi(RestClient.builder(), " "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("saas.internal.file-token is required");
    }

    @Test
    void fileInternalApiRejectsBaseUrlWithQuery() {
        assertThatThrownBy(() -> new FileInternalClientConfiguration().fileInternalApi(
                "http://file-service:8084?trace=1",
                "file-token-2026",
                provider(RestClient.builder())
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("saas.file.service-base-url")
                .hasMessageContaining("must not include query or fragment");
    }

    private static FileInternalApi fileInternalApi(RestClient.Builder builder, String fileToken) {
        return new FileInternalClientConfiguration().fileInternalApi(
                "http://file-service:8084",
                fileToken,
                provider(builder)
        );
    }

    private static ObjectProvider<RestClient.Builder> provider(RestClient.Builder builder) {
        return new ObjectProvider<>() {
            @Override
            public RestClient.Builder getObject(Object... args) {
                return builder;
            }

            @Override
            public RestClient.Builder getIfAvailable() {
                return builder;
            }

            @Override
            public RestClient.Builder getIfUnique() {
                return builder;
            }

            @Override
            public RestClient.Builder getObject() {
                return builder;
            }

            @Override
            public Iterator<RestClient.Builder> iterator() {
                return Stream.of(builder).iterator();
            }
        };
    }
}
