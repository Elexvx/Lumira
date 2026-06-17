package com.lumira.message.controller;

import com.lumira.common.api.ApiResponse;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.common.web.TraceContext;
import com.lumira.common.web.repeatsubmit.RepeatSubmit;
import com.lumira.message.app.MessageAppService;
import com.lumira.message.dto.MessageDTO;
import com.lumira.message.vo.MessageVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/message")
public class MessageV2Controller {

    private final MessageAppService messageAppService;
    private final SecurityContextFacade securityContextFacade;

    public MessageV2Controller(MessageAppService messageAppService, SecurityContextFacade securityContextFacade) {
        this.messageAppService = messageAppService;
        this.securityContextFacade = securityContextFacade;
    }

    @GetMapping("/messages")
    public ApiResponse<MessageVO.NoticePageResponse> listMessages(
            @RequestParam(name = "pageNo", defaultValue = "1") long pageNo,
            @RequestParam(name = "pageSize", defaultValue = "10") long pageSize
    ) {
        requireAny("message:message:view", "system:notification:view");
        return ApiResponse.success(messageAppService.listMessages(securityContextFacade.getCurrentUser(), pageNo, pageSize), TraceContext.getRequestId());
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

    @GetMapping("/unread-count")
    public ApiResponse<MessageVO.UnreadCountVO> unreadCount() {
        requireAny("message:message:view", "system:notification:view");
        MessageVO.UnreadCountVO unreadCountVO = new MessageVO.UnreadCountVO();
        unreadCountVO.setUnreadCount(messageAppService.countUnread(securityContextFacade.getCurrentUser()));
        return ApiResponse.success(unreadCountVO, TraceContext.getRequestId());
    }

    @PostMapping("/read-all")
    @RepeatSubmit
    public ApiResponse<MessageVO.UnreadCountVO> readAll() {
        requireAny("message:message:read", "system:notification:view");
        return ApiResponse.success(messageAppService.markAllRead(securityContextFacade.getCurrentUser()), TraceContext.getRequestId());
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
