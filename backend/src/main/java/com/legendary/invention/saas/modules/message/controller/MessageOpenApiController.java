package com.legendary.invention.saas.modules.message.controller;

import com.legendary.invention.saas.common.api.ApiResponse;
import com.legendary.invention.saas.infrastructure.observability.TraceContext;
import com.legendary.invention.saas.modules.audit.app.OperationAuditService;
import com.legendary.invention.saas.modules.message.app.MessageAppService;
import com.legendary.invention.saas.modules.message.dto.MessageDTO;
import com.legendary.invention.saas.modules.message.service.OpenApiSignatureService;
import com.legendary.invention.saas.modules.message.vo.MessageVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/openapi/v1/message")
public class MessageOpenApiController {

    private final MessageAppService messageAppService;
    private final OperationAuditService operationAuditService;

    public MessageOpenApiController(MessageAppService messageAppService, OperationAuditService operationAuditService) {
        this.messageAppService = messageAppService;
        this.operationAuditService = operationAuditService;
    }

    @PostMapping("/tenants/{tenantId}/announcements")
    public ApiResponse<MessageVO.NoticeVO> createAnnouncement(
            @PathVariable("tenantId") Long tenantId,
            @Valid @RequestBody MessageDTO.AnnouncementCreateRequest request,
            HttpServletRequest httpServletRequest
    ) {
        String appId = resolveAppId(httpServletRequest);
        try {
            return ApiResponse.success(messageAppService.createAnnouncement(tenantId, 0L, appId, request, "OPENAPI"), TraceContext.getRequestId());
        } catch (RuntimeException exception) {
            operationAuditService.log(
                    tenantId,
                    0L,
                    appId,
                    "message-openapi",
                    "publish-announcement",
                    "OPENAPI",
                    "FAIL",
                    exception.getMessage()
            );
            throw exception;
        }
    }

    @PostMapping("/tenants/{tenantId}/messages")
    public ApiResponse<MessageVO.NoticeVO> createMessage(
            @PathVariable("tenantId") Long tenantId,
            @Valid @RequestBody MessageDTO.MessageCreateRequest request,
            HttpServletRequest httpServletRequest
    ) {
        String appId = resolveAppId(httpServletRequest);
        try {
            return ApiResponse.success(messageAppService.createMessage(tenantId, 0L, appId, request, "OPENAPI"), TraceContext.getRequestId());
        } catch (RuntimeException exception) {
            operationAuditService.log(
                    tenantId,
                    0L,
                    appId,
                    "message-openapi",
                    "send-message",
                    "OPENAPI",
                    "FAIL",
                    exception.getMessage()
            );
            throw exception;
        }
    }

    private String resolveAppId(HttpServletRequest request) {
        Object appId = request.getAttribute(OpenApiSignatureService.REQUEST_ATTR_APP_ID);
        if (appId instanceof String value && !value.isBlank()) {
            return value;
        }
        return "unknown";
    }
}
