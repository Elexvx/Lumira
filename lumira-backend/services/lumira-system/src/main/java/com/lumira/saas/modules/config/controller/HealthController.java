package com.lumira.saas.modules.config.controller;

import com.lumira.common.api.ApiResponse;
import com.lumira.common.web.TraceContext;
import com.lumira.saas.modules.system.dict.app.DictionaryDatasetBootstrap;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/health")
public class HealthController {
    private final ObjectProvider<DictionaryDatasetBootstrap> dictionaryDatasetBootstrapProvider;

    public HealthController(ObjectProvider<DictionaryDatasetBootstrap> dictionaryDatasetBootstrapProvider) {
        this.dictionaryDatasetBootstrapProvider = dictionaryDatasetBootstrapProvider;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, String>>> health() {
        DictionaryDatasetBootstrap bootstrap = dictionaryDatasetBootstrapProvider.getIfAvailable();
        boolean ready = bootstrap == null || bootstrap.isReady();
        ApiResponse<Map<String, String>> body = ApiResponse.success(
                Map.of("status", ready ? "UP" : "STARTING"),
                TraceContext.getRequestId()
        );
        return ready
                ? ResponseEntity.ok(body)
                : ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }
}
