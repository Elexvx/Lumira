package com.legendary.invention.message.controller;

import com.legendary.invention.common.api.ApiResponse;
import com.legendary.invention.common.enums.ErrorCode;
import com.legendary.invention.common.exception.BizException;
import com.legendary.invention.common.vo.PageResponse;
import com.legendary.invention.common.web.TraceContext;
import com.legendary.invention.common.security.SecurityContextFacade;
import com.legendary.invention.message.app.MessageAppService;
import com.legendary.invention.message.dto.MessageDTO;
import com.legendary.invention.message.service.MessageWebSocketTicketService;
import com.legendary.invention.message.vo.MessageVO;
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

    public MessageController(
            MessageAppService messageAppService,
            SecurityContextFacade securityContextFacade,
            MessageWebSocketTicketService webSocketTicketService
    ) {
        this.messageAppService = messageAppService;
        this.securityContextFacade = securityContextFacade;
        this.webSocketTicketService = webSocketTicketService;
    }

    @GetMapping("/messages")
    public ApiResponse<PageResponse<MessageVO.NoticeVO>> listMessages(
            @RequestParam(name = "pageNo", defaultValue = "1") long pageNo,
            @RequestParam(name = "pageSize", defaultValue = "10") long pageSize
    ) {
        requireAny("message:message:view", "system:notification:view");
        return ApiResponse.success(messageAppService.listMessages(securityContextFacade.getCurrentUser(), pageNo, pageSize), TraceContext.getRequestId());
    }

    @PostMapping("/messages")
    public ApiResponse<MessageVO.NoticeVO> createMessage(@Valid @RequestBody MessageDTO.MessageCreateRequest request) {
        requireAny("message:message:write", "system:notification:write");
        return ApiResponse.success(messageAppService.createMessage(securityContextFacade.getCurrentUser(), request), TraceContext.getRequestId());
    }

    @PostMapping("/messages/{id}/retract")
    public ApiResponse<MessageVO.NoticeVO> retractMessage(@PathVariable("id") Long id) {
        requireAny("message:message:retract", "system:notification:write");
        return ApiResponse.success(messageAppService.retractMessage(securityContextFacade.getCurrentUser(), id), TraceContext.getRequestId());
    }

    @PostMapping("/messages/{id}/read")
    public ApiResponse<MessageVO.NoticeVO> readMessage(@PathVariable("id") Long id) {
        requireAny("message:message:read", "system:notification:view");
        return ApiResponse.success(messageAppService.markMessageRead(securityContextFacade.getCurrentUser(), id), TraceContext.getRequestId());
    }

    @PostMapping("/read-all")
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

    @GetMapping("/archive")
    public ApiResponse<PageResponse<MessageVO.NoticeVO>> listArchive(@Valid MessageDTO.MessageArchiveQueryRequest request) {
        requireAny("message:message:view", "system:notification:view");
        return ApiResponse.success(messageAppService.listArchive(securityContextFacade.getCurrentUser(), request), TraceContext.getRequestId());
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

}
