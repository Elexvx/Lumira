package com.lumira.saas.modules.system.monitor.controller;

import com.lumira.common.api.ApiResponse;
import com.lumira.common.web.TraceContext;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.common.security.PermissionGuard;
import com.lumira.saas.modules.system.monitor.app.SystemMonitorAppService;
import com.lumira.saas.modules.system.monitor.vo.SystemMonitorVO;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system/monitor")
public class SystemMonitorController {

    private static final List<String> API_DOCS_CANDIDATE_PATHS = List.of("/api-docs", "/v3/api-docs");
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    private final SystemMonitorAppService systemMonitorAppService;
    private final SecurityContextFacade securityContextFacade;
    private final PermissionGuard permissionGuard;

    public SystemMonitorController(
            SystemMonitorAppService systemMonitorAppService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard
    ) {
        this.systemMonitorAppService = systemMonitorAppService;
        this.securityContextFacade = securityContextFacade;
        this.permissionGuard = permissionGuard;
    }

    @GetMapping("/service")
    public ApiResponse<SystemMonitorVO.ServiceMonitorVO> serviceMonitor() {
        require("system:monitor:service:view");
        return ApiResponse.success(systemMonitorAppService.getServiceMonitor(), TraceContext.getRequestId());
    }

    @GetMapping("/redis")
    public ApiResponse<SystemMonitorVO.RedisMonitorVO> redisMonitor() {
        require("system:monitor:redis:view");
        return ApiResponse.success(systemMonitorAppService.getRedisMonitor(), TraceContext.getRequestId());
    }

    @GetMapping("/api-docs")
    public void apiDocs(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        require("system:monitor:docs:view");
        String authorization = request.getHeader("Authorization");
        int localPort = request.getLocalPort();

        for (String candidatePath : API_DOCS_CANDIDATE_PATHS) {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:" + localPort + candidatePath))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .header("Accept", "application/json");
            if (authorization != null && !authorization.isBlank()) {
                builder.header("Authorization", authorization);
            }

            HttpResponse<String> candidateResponse;
            try {
                candidateResponse = HTTP_CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IOException("加载接口文档失败", ex);
            }

            if (candidateResponse.statusCode() == HttpServletResponse.SC_OK && candidateResponse.body() != null) {
                response.setStatus(HttpServletResponse.SC_OK);
                response.setContentType(candidateResponse.headers().firstValue("content-type").orElse("application/json"));
                response.getWriter().write(candidateResponse.body());
                return;
            }
        }

        response.sendError(HttpServletResponse.SC_NOT_FOUND, "接口文档不存在");
    }

    private void require(String permissionKey) {
        permissionGuard.requirePermission(securityContextFacade.getCurrentUser(), permissionKey);
    }
}
