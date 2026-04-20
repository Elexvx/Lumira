package com.yourcompany.saas.modules.message.controller;

import com.yourcompany.saas.common.api.ApiResponse;
import com.yourcompany.saas.common.enums.ErrorCode;
import com.yourcompany.saas.common.exception.BizException;
import com.yourcompany.saas.common.vo.PageResponse;
import com.yourcompany.saas.infrastructure.observability.TraceContext;
import com.yourcompany.saas.infrastructure.security.SecurityContextFacade;
import com.yourcompany.saas.modules.message.app.MessageAppService;
import com.yourcompany.saas.modules.message.dto.MessageDTO;
import com.yourcompany.saas.modules.message.vo.MessageVO;
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

    public MessageController(
            MessageAppService messageAppService,
            SecurityContextFacade securityContextFacade
    ) {
        this.messageAppService = messageAppService;
        this.securityContextFacade = securityContextFacade;
    }

    @GetMapping("/announcements")
    public ApiResponse<PageResponse<MessageVO.NoticeVO>> listAnnouncements(
            @RequestParam(name = "pageNo", defaultValue = "1") long pageNo,
            @RequestParam(name = "pageSize", defaultValue = "10") long pageSize
    ) {
        requireAny("message:message:view", "message:announcement:view");
        return ApiResponse.success(messageAppService.listAnnouncements(securityContextFacade.getCurrentUser(), pageNo, pageSize), TraceContext.getRequestId());
    }

    @PostMapping("/announcements")
    public ApiResponse<MessageVO.NoticeVO> createAnnouncement(@Valid @RequestBody MessageDTO.AnnouncementCreateRequest request) {
        requireAny("message:message:write", "message:announcement:write");
        return ApiResponse.success(messageAppService.createAnnouncement(securityContextFacade.getCurrentUser(), request), TraceContext.getRequestId());
    }

    @PostMapping("/announcements/{id}/retract")
    public ApiResponse<MessageVO.NoticeVO> retractAnnouncement(@PathVariable("id") Long id) {
        requireAny("message:message:retract", "message:announcement:retract");
        return ApiResponse.success(messageAppService.retractAnnouncement(securityContextFacade.getCurrentUser(), id), TraceContext.getRequestId());
    }

    @PostMapping("/announcements/{id}/read")
    public ApiResponse<MessageVO.NoticeVO> readAnnouncement(@PathVariable("id") Long id) {
        requireAny("message:message:read", "message:announcement:read");
        return ApiResponse.success(messageAppService.markAnnouncementRead(securityContextFacade.getCurrentUser(), id), TraceContext.getRequestId());
    }

    @GetMapping("/messages")
    public ApiResponse<PageResponse<MessageVO.NoticeVO>> listMessages(
            @RequestParam(name = "pageNo", defaultValue = "1") long pageNo,
            @RequestParam(name = "pageSize", defaultValue = "10") long pageSize
    ) {
        requireAny("message:message:view", "message:announcement:view");
        return ApiResponse.success(messageAppService.listMessages(securityContextFacade.getCurrentUser(), pageNo, pageSize), TraceContext.getRequestId());
    }

    @PostMapping("/messages")
    public ApiResponse<MessageVO.NoticeVO> createMessage(@Valid @RequestBody MessageDTO.MessageCreateRequest request) {
        requireAny("message:message:write", "message:announcement:write");
        return ApiResponse.success(messageAppService.createMessage(securityContextFacade.getCurrentUser(), request), TraceContext.getRequestId());
    }

    @PostMapping("/messages/{id}/retract")
    public ApiResponse<MessageVO.NoticeVO> retractMessage(@PathVariable("id") Long id) {
        requireAny("message:message:retract", "message:announcement:retract");
        return ApiResponse.success(messageAppService.retractMessage(securityContextFacade.getCurrentUser(), id), TraceContext.getRequestId());
    }

    @PostMapping("/messages/{id}/read")
    public ApiResponse<MessageVO.NoticeVO> readMessage(@PathVariable("id") Long id) {
        requireAny("message:message:read", "message:announcement:read");
        return ApiResponse.success(messageAppService.markMessageRead(securityContextFacade.getCurrentUser(), id), TraceContext.getRequestId());
    }

    @PostMapping("/read-all")
    public ApiResponse<MessageVO.UnreadCountVO> readAll() {
        requireAny("message:message:read", "message:announcement:read");
        return ApiResponse.success(messageAppService.markAllRead(securityContextFacade.getCurrentUser()), TraceContext.getRequestId());
    }

    @GetMapping("/unread-count")
    public ApiResponse<MessageVO.UnreadCountVO> unreadCount() {
        requireAny("message:message:view", "message:announcement:view");
        MessageVO.UnreadCountVO unreadCountVO = new MessageVO.UnreadCountVO();
        unreadCountVO.setUnreadCount(messageAppService.countUnread(securityContextFacade.getCurrentUser()));
        return ApiResponse.success(unreadCountVO, TraceContext.getRequestId());
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
