package com.lumira.message.controller;

import com.lumira.common.api.ApiResponse;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.web.TraceContext;
import com.lumira.common.web.repeatsubmit.RepeatSubmit;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.message.app.MessageAppService;
import com.lumira.message.dto.MessageDTO;
import com.lumira.message.service.MessageWebSocketRegistry;
import com.lumira.message.service.MessageWebSocketTicketService;
import com.lumira.message.vo.MessageVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/message")
public class MessageController {

    private final MessageAppService messageAppService;
    private final SecurityContextFacade securityContextFacade;
    private final MessageWebSocketTicketService webSocketTicketService;
    private final MessageWebSocketRegistry webSocketRegistry;

    public MessageController(
            MessageAppService messageAppService,
            SecurityContextFacade securityContextFacade,
            MessageWebSocketTicketService webSocketTicketService,
            MessageWebSocketRegistry webSocketRegistry
    ) {
        this.messageAppService = messageAppService;
        this.securityContextFacade = securityContextFacade;
        this.webSocketTicketService = webSocketTicketService;
        this.webSocketRegistry = webSocketRegistry;
    }

    @GetMapping("/messages")
    public ApiResponse<MessageVO.NoticePageResponse> listMessages(
            @RequestParam(name = "pageNo", defaultValue = "1") long pageNo,
            @RequestParam(name = "pageSize", defaultValue = "10") long pageSize
    ) {
        requireAny("message:message:view", "system:notification:view");
        return ApiResponse.success(messageAppService.listMessages(securityContextFacade.getCurrentUser(), pageNo, pageSize), TraceContext.getRequestId());
    }

    @PostMapping("/messages")
    @RepeatSubmit
    public ApiResponse<MessageVO.NoticeVO> createMessage(@Valid @RequestBody MessageDTO.MessageCreateRequest request) {
        requireAny("message:message:write", "system:notification:write");
        return ApiResponse.success(messageAppService.createMessage(securityContextFacade.getCurrentUser(), request), TraceContext.getRequestId());
    }

    @PostMapping("/messages/{id}/retract")
    @RepeatSubmit
    public ApiResponse<MessageVO.NoticeVO> retractMessage(@PathVariable("id") Long id) {
        requireAny("message:message:retract", "system:notification:write");
        return ApiResponse.success(messageAppService.retractMessage(securityContextFacade.getCurrentUser(), id), TraceContext.getRequestId());
    }

    @PostMapping("/messages/{id}/read")
    @RepeatSubmit
    public ApiResponse<MessageVO.NoticeVO> readMessage(@PathVariable("id") Long id) {
        requireAny("message:message:read", "system:notification:view");
        return ApiResponse.success(messageAppService.markMessageRead(securityContextFacade.getCurrentUser(), id), TraceContext.getRequestId());
    }

    @PostMapping("/read-all")
    @RepeatSubmit
    public ApiResponse<MessageVO.UnreadCountVO> readAll() {
        requireAny("message:message:read", "system:notification:view");
        return ApiResponse.success(messageAppService.markAllRead(securityContextFacade.getCurrentUser()), TraceContext.getRequestId());
    }

    @GetMapping("/unread-count")
    public ApiResponse<MessageVO.UnreadCountVO> unreadCount() {
        requireAny("message:message:view", "system:notification:view");
        MessageVO.UnreadCountVO unreadCountVO = new MessageVO.UnreadCountVO();
        unreadCountVO.setUnreadCount(messageAppService.countUnread(securityContextFacade.getCurrentUser()));
        return ApiResponse.success(unreadCountVO, TraceContext.getRequestId());
    }

    @PostMapping("/ws-ticket")
    public ApiResponse<MessageVO.WebSocketTicketVO> issueWebSocketTicket() {
        requireAny("message:message:view", "system:notification:view");
        return ApiResponse.success(webSocketTicketService.issue(securityContextFacade.getCurrentUser()), TraceContext.getRequestId());
    }

    @GetMapping("/ws-runtime")
    public ApiResponse<MessageVO.WebSocketRuntimeVO> webSocketRuntime() {
        requireAny("message:message:view", "system:notification:view", "system:monitor:service:view");
        return ApiResponse.success(toWebSocketRuntimeVO(webSocketRegistry.snapshot()), TraceContext.getRequestId());
    }

    @GetMapping("/archive")
    public ApiResponse<MessageVO.NoticeArchivePageResponse> listArchive(@Valid MessageDTO.MessageArchiveQueryRequest request) {
        requireAny("message:message:view", "system:notification:view");
        return ApiResponse.success(messageAppService.listArchive(securityContextFacade.getCurrentUser(), request), TraceContext.getRequestId());
    }

    @GetMapping("/delivery-logs")
    public ApiResponse<MessageVO.DeliveryLogPageResponse> listDeliveryLogs(@Valid MessageDTO.MessageArchiveQueryRequest request) {
        requireAny("message:message:view", "system:notification:view");
        return ApiResponse.success(messageAppService.listDeliveryLogs(securityContextFacade.getCurrentUser(), request), TraceContext.getRequestId());
    }

    private void requireAny(String... permissionKeys) {
        var currentUser = securityContextFacade.getCurrentUser();
        if (currentUser != null && currentUser.getPermissions() != null) {
            for (String permissionKey : permissionKeys) {
                if (currentUser.getPermissions().contains(permissionKey)) {
                    return;
                }
            }
            if (currentUser.getPermissions().contains("*")) {
                return;
            }
        }
        throw new BizException(ErrorCode.FORBIDDEN, "缺少权限: " + String.join(" 或 ", permissionKeys));
    }

    private MessageVO.WebSocketRuntimeVO toWebSocketRuntimeVO(MessageWebSocketRegistry.Snapshot snapshot) {
        MessageVO.WebSocketRuntimeVO vo = new MessageVO.WebSocketRuntimeVO();
        vo.setActiveConnections(snapshot.activeConnections());
        vo.setTenantCount(snapshot.tenantCount());
        vo.setUserCount(snapshot.userCount());
        vo.setEarliestConnectedAt(snapshot.earliestConnectedAt());
        vo.setSampledAt(snapshot.sampledAt());
        vo.setTenants(snapshot.tenants().stream().map(item -> {
            MessageVO.TenantConnectionVO tenant = new MessageVO.TenantConnectionVO();
            tenant.setTenantId(item.tenantId());
            tenant.setConnectionCount(item.connectionCount());
            return tenant;
        }).toList());
        vo.setTopUsers(snapshot.topUsers().stream().map(item -> {
            MessageVO.UserConnectionVO user = new MessageVO.UserConnectionVO();
            user.setUserId(item.userId());
            user.setConnectionCount(item.connectionCount());
            return user;
        }).toList());
        return vo;
    }

}
