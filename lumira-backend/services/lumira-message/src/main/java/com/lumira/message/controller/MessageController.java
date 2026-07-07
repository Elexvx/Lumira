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
import com.lumira.message.service.MessageWebSocketRegistry;
import com.lumira.message.service.MessageWebSocketTicketService;
import com.lumira.message.vo.MessageVO;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
@RequestMapping("/api/v1/message")
public class MessageController {

    private final MessageAppService messageAppService;
    private final SecurityContextFacade securityContextFacade;
    private final MessageWebSocketTicketService webSocketTicketService;
    private final MessageWebSocketRegistry webSocketRegistry;
    private final MessageSessionAuthenticationService sessionAuthenticationService;
    private final boolean enforceTrustedUserResolution;

    public MessageController(
            MessageAppService messageAppService,
            SecurityContextFacade securityContextFacade,
            MessageWebSocketTicketService webSocketTicketService,
            MessageWebSocketRegistry webSocketRegistry
    ) {
        this(messageAppService, securityContextFacade, webSocketTicketService, webSocketRegistry, null, false);
    }

    @Autowired
    public MessageController(
            MessageAppService messageAppService,
            SecurityContextFacade securityContextFacade,
            MessageWebSocketTicketService webSocketTicketService,
            MessageWebSocketRegistry webSocketRegistry,
            MessageSessionAuthenticationService sessionAuthenticationService
    ) {
        this(messageAppService, securityContextFacade, webSocketTicketService, webSocketRegistry, sessionAuthenticationService, true);
    }

    private MessageController(
            MessageAppService messageAppService,
            SecurityContextFacade securityContextFacade,
            MessageWebSocketTicketService webSocketTicketService,
            MessageWebSocketRegistry webSocketRegistry,
            MessageSessionAuthenticationService sessionAuthenticationService,
            boolean enforceTrustedUserResolution
    ) {
        this.messageAppService = messageAppService;
        this.securityContextFacade = securityContextFacade;
        this.webSocketTicketService = webSocketTicketService;
        this.webSocketRegistry = webSocketRegistry;
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

    @PostMapping("/messages")
    @RepeatSubmit
    public ApiResponse<MessageVO.NoticeVO> createMessage(@Valid @RequestBody MessageDTO.MessageCreateRequest request) {
        CurrentUser currentUser = require("message:message:write", "system:notification:write");
        return ApiResponse.success(messageAppService.createMessage(currentUser, request), TraceContext.getRequestId());
    }

    @PostMapping("/messages/{id}/retract")
    @RepeatSubmit
    public ApiResponse<MessageVO.NoticeVO> retractMessage(@PathVariable("id") Long id) {
        CurrentUser currentUser = require("message:message:retract", "system:notification:write");
        return ApiResponse.success(messageAppService.retractMessage(currentUser, id), TraceContext.getRequestId());
    }

    @PostMapping("/messages/{id}/read")
    @RepeatSubmit
    public ApiResponse<MessageVO.NoticeVO> readMessage(@PathVariable("id") Long id) {
        CurrentUser currentUser = require("message:message:read", "system:notification:view");
        return ApiResponse.success(messageAppService.markMessageRead(currentUser, id), TraceContext.getRequestId());
    }

    @PostMapping("/read-all")
    @RepeatSubmit
    public ApiResponse<MessageVO.UnreadCountVO> readAll() {
        CurrentUser currentUser = require("message:message:read", "system:notification:view");
        return ApiResponse.success(messageAppService.markAllRead(currentUser), TraceContext.getRequestId());
    }

    @GetMapping("/unread-count")
    public ApiResponse<MessageVO.UnreadCountVO> unreadCount() {
        CurrentUser currentUser = require("message:message:view", "system:notification:view");
        MessageVO.UnreadCountVO unreadCountVO = new MessageVO.UnreadCountVO();
        unreadCountVO.setUnreadCount(messageAppService.countUnread(currentUser));
        return ApiResponse.success(unreadCountVO, TraceContext.getRequestId());
    }

    @PostMapping("/ws-ticket")
    public ApiResponse<MessageVO.WebSocketTicketVO> issueWebSocketTicket() {
        CurrentUser currentUser = require("message:message:view", "system:notification:view");
        return ApiResponse.success(webSocketTicketService.issue(currentUser), TraceContext.getRequestId());
    }

    @GetMapping("/ws-runtime")
    public ApiResponse<MessageVO.WebSocketRuntimeVO> webSocketRuntime() {
        require("message:message:view", "system:notification:view", "system:monitor:service:view");
        return ApiResponse.success(toWebSocketRuntimeVO(webSocketRegistry.snapshot()), TraceContext.getRequestId());
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

    private MessageVO.WebSocketRuntimeVO toWebSocketRuntimeVO(MessageWebSocketRegistry.Snapshot snapshot) {
        MessageVO.WebSocketRuntimeVO vo = new MessageVO.WebSocketRuntimeVO();
        vo.setActiveConnections(snapshot.activeConnections());
        vo.setUserCount(snapshot.userCount());
        vo.setEarliestConnectedAt(snapshot.earliestConnectedAt());
        vo.setSampledAt(snapshot.sampledAt());
        vo.setTopUsers(snapshot.topUsers().stream().map(item -> {
            MessageVO.UserConnectionVO user = new MessageVO.UserConnectionVO();
            user.setUserId(item.userId());
            user.setConnectionCount(item.connectionCount());
            return user;
        }).toList());
        return vo;
    }
}
