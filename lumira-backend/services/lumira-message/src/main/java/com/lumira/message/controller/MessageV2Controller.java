package com.lumira.message.controller;

import com.lumira.common.api.ApiResponse;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.AuthenticationTrustSupport;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.common.web.TraceContext;
import com.lumira.common.web.repeatsubmit.RepeatSubmit;
import com.lumira.message.app.MessageAppService;
import com.lumira.message.dto.MessageDTO;
import com.lumira.message.infrastructure.security.MessageSessionAuthenticationService;
import com.lumira.message.vo.MessageVO;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
@RequestMapping("/api/v2/message")
public class MessageV2Controller {

    private final MessageAppService messageAppService;
    private final SecurityContextFacade securityContextFacade;
    private final MessageSessionAuthenticationService sessionAuthenticationService;
    private final boolean enforceTrustedUserResolution;

    public MessageV2Controller(MessageAppService messageAppService, SecurityContextFacade securityContextFacade) {
        this(messageAppService, securityContextFacade, null, false);
    }

    @Autowired
    public MessageV2Controller(
            MessageAppService messageAppService,
            SecurityContextFacade securityContextFacade,
            MessageSessionAuthenticationService sessionAuthenticationService
    ) {
        this(messageAppService, securityContextFacade, sessionAuthenticationService, true);
    }

    private MessageV2Controller(
            MessageAppService messageAppService,
            SecurityContextFacade securityContextFacade,
            MessageSessionAuthenticationService sessionAuthenticationService,
            boolean enforceTrustedUserResolution
    ) {
        this.messageAppService = messageAppService;
        this.securityContextFacade = securityContextFacade;
        this.sessionAuthenticationService = sessionAuthenticationService;
        this.enforceTrustedUserResolution = enforceTrustedUserResolution;
    }

    @GetMapping("/messages")
    public ApiResponse<MessageVO.NoticePageResponse> listMessages(
            @RequestParam(name = "pageNo", defaultValue = "1") long pageNo,
            @RequestParam(name = "pageSize", defaultValue = "10") long pageSize
    ) {
        CurrentUser currentUser = require("message:message:view", "system:notification:view");
        return ApiResponse.success(messageAppService.listMessages(currentUser, pageNo, pageSize), TraceContext.getRequestId());
    }

    @GetMapping("/archive")
    public ApiResponse<MessageVO.NoticeArchivePageResponse> listArchive(@Valid MessageDTO.MessageArchiveQueryRequest request) {
        CurrentUser currentUser = require("message:message:view", "system:notification:view");
        return ApiResponse.success(messageAppService.listArchive(currentUser, request), TraceContext.getRequestId());
    }

    @GetMapping("/delivery-logs")
    public ApiResponse<MessageVO.DeliveryLogPageResponse> listDeliveryLogs(@Valid MessageDTO.MessageArchiveQueryRequest request) {
        CurrentUser currentUser = require("message:message:view", "system:notification:view");
        return ApiResponse.success(messageAppService.listDeliveryLogs(currentUser, request), TraceContext.getRequestId());
    }

    @GetMapping("/unread-count")
    public ApiResponse<MessageVO.UnreadCountVO> unreadCount() {
        CurrentUser currentUser = require("message:message:view", "system:notification:view");
        MessageVO.UnreadCountVO unreadCountVO = new MessageVO.UnreadCountVO();
        unreadCountVO.setUnreadCount(messageAppService.countUnread(currentUser));
        return ApiResponse.success(unreadCountVO, TraceContext.getRequestId());
    }

    @PostMapping("/read-all")
    @RepeatSubmit
    public ApiResponse<MessageVO.UnreadCountVO> readAll() {
        CurrentUser currentUser = require("message:message:read", "system:notification:view");
        return ApiResponse.success(messageAppService.markAllRead(currentUser), TraceContext.getRequestId());
    }

    @PostMapping("/messages/{id}/read")
    @RepeatSubmit
    public ApiResponse<MessageVO.NoticeVO> readMessage(@PathVariable("id") Long id) {
        CurrentUser currentUser = require("message:message:read", "system:notification:view");
        return ApiResponse.success(messageAppService.markMessageRead(currentUser, id), TraceContext.getRequestId());
    }

    private CurrentUser require(String... permissionKeys) {
        CurrentUser currentUser = requireTrustedCurrentUser(securityContextFacade.getCurrentUser());
        requireAny(currentUser, permissionKeys);
        return currentUser;
    }

    private CurrentUser requireTrustedCurrentUser(CurrentUser currentUser) {
        if (AuthenticationTrustSupport.isTrustedCurrentUser(currentUser) && sessionAuthenticationService == null
                && enforceTrustedUserResolution) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user resolver is unavailable");
        }
        if (AuthenticationTrustSupport.isTrustedCurrentUser(currentUser) && sessionAuthenticationService != null) {
            MessageSessionAuthenticationService.AuthenticatedAccess authenticatedAccess =
                    sessionAuthenticationService.authenticateSessionTicket(
                            currentUser.getSessionId(),
                            currentUser.getUserId(),
                            currentUser.getUserUuid(),
                            currentUser.getSimulatedRoleId(),
                            currentUser.getSessionVersion(),
                            currentUser.getPermissionsVersion()
                    );
            currentUser = authenticatedAccess == null ? null : authenticatedAccess.currentUser();
        }
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(currentUser)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Login required");
        }
        return currentUser;
    }

    private void requireAny(CurrentUser currentUser, String... permissionKeys) {
        Set<String> permissions = currentUser.getPermissions();
        if (permissions != null) {
            for (String permissionKey : permissionKeys) {
                if (permissions.contains(permissionKey)) {
                    return;
                }
            }
            if (permissions.contains("*")) {
                return;
            }
        }
        throw new BizException(ErrorCode.FORBIDDEN, "Missing permission: " + String.join(" or ", permissionKeys));
    }
}
