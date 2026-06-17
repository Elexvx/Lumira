package com.lumira.file.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

class FileV2ControllerTest {

    @Test
    void fileV2Controller_shouldExposeFileOwnerManagementAdapter() {
        RequestMapping requestMapping = FileV2Controller.class.getAnnotation(RequestMapping.class);

        assertThat(requestMapping).isNotNull();
        assertThat(requestMapping.value()).containsExactly("/api/v2/files");

        Set<String> getEndpoints = Arrays.stream(FileV2Controller.class.getDeclaredMethods())
                .filter(method -> method.getAnnotation(GetMapping.class) != null)
                .map(method -> method.getName() + ":" + String.join(",", method.getAnnotation(GetMapping.class).value()))
                .collect(Collectors.toSet());
        Set<String> postEndpoints = Arrays.stream(FileV2Controller.class.getDeclaredMethods())
                .filter(method -> method.getAnnotation(PostMapping.class) != null)
                .map(method -> method.getName() + ":" + String.join(",", method.getAnnotation(PostMapping.class).value()))
                .collect(Collectors.toSet());
        Set<String> putEndpoints = Arrays.stream(FileV2Controller.class.getDeclaredMethods())
                .filter(method -> method.getAnnotation(PutMapping.class) != null)
                .map(method -> method.getName() + ":" + String.join(",", method.getAnnotation(PutMapping.class).value()))
                .collect(Collectors.toSet());
        Set<String> deleteEndpoints = Arrays.stream(FileV2Controller.class.getDeclaredMethods())
                .filter(method -> method.getAnnotation(DeleteMapping.class) != null)
                .map(method -> method.getName() + ":" + String.join(",", method.getAnnotation(DeleteMapping.class).value()))
                .collect(Collectors.toSet());

        assertThat(getEndpoints)
                .contains(
                        "list:",
                        "storageSpaces:/storage-spaces",
                        "storageSpace:/storage-spaces/{storageKey}",
                        "detail:/{id}",
                        "download:/{id}/download",
                        "preview:/{id}/preview"
                );
        assertThat(postEndpoints)
                .contains(
                        "createStorageSpace:/storage-spaces",
                        "testStorageSpace:/storage-spaces/{id}/test",
                        "upload:/upload"
                );
        assertThat(putEndpoints).contains("updateStorageSpace:/storage-spaces/{id}");
        assertThat(deleteEndpoints).contains("deleteStorageSpace:/storage-spaces/{id}", "delete:/{id}");
    }
}
