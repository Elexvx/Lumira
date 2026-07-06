package com.lumira.saas.modules.system.sensitive.security;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.AuthenticationTrustSupport;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.saas.modules.system.sensitive.app.SensitiveWordPluginStateService;
import com.lumira.saas.modules.system.sensitive.app.SensitiveWordService;
import com.lumira.saas.modules.system.sensitive.vo.SensitiveWordVO;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

import java.lang.reflect.Type;

@ControllerAdvice(annotations = Controller.class)
public class SensitiveWordRequestBodyAdvice extends RequestBodyAdviceAdapter {

    private final SensitiveWordService sensitiveWordService;
    private final SecurityContextFacade securityContextFacade;
    private final SensitiveWordPluginStateService pluginStateService;

    public SensitiveWordRequestBodyAdvice(
            SensitiveWordService sensitiveWordService,
            SecurityContextFacade securityContextFacade,
            SensitiveWordPluginStateService pluginStateService
    ) {
        this.sensitiveWordService = sensitiveWordService;
        this.securityContextFacade = securityContextFacade;
        this.pluginStateService = pluginStateService;
    }

    @Override
    public boolean supports(MethodParameter methodParameter, Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object afterBodyRead(Object body, HttpInputMessage inputMessage, MethodParameter parameter, Type targetType,
                                Class<? extends HttpMessageConverter<?>> converterType) {
        CurrentUser currentUser = securityContextFacade.getCurrentUserOrNull();
        if (!isTrustedCurrentUser(currentUser)) {
            return body;
        }
        if (!pluginStateService.isEnabled(currentUser)) {
            return body;
        }
        if (inputMessage instanceof ServletServerHttpRequest request && shouldSkip(request)) {
            return body;
        }
        SensitiveWordVO.CheckResult result = sensitiveWordService.checkPayload(currentUser, body);
        if (result.isBlocked()) {
            throw buildException(result);
        }
        return body;
    }

    @Override
    public Object handleEmptyBody(Object body, HttpInputMessage inputMessage, MethodParameter parameter, Type targetType,
                                  Class<? extends HttpMessageConverter<?>> converterType) {
        return body;
    }

    private boolean shouldSkip(ServletServerHttpRequest request) {
        String path = request.getServletRequest().getRequestURI();
        if (SensitiveWordRequestSkipMatcher.shouldSkipPath(path)) {
            return true;
        }
        MediaType contentType = request.getHeaders().getContentType();
        return SensitiveWordRequestSkipMatcher.shouldSkipMultipart(contentType);
    }

    private boolean isTrustedCurrentUser(CurrentUser currentUser) {
        return AuthenticationTrustSupport.isTrustedCurrentUser(currentUser);
    }

    private BizException buildException(SensitiveWordVO.CheckResult result) {
        String userMessage = sensitiveWordService.formatMatchesForUser(result.getMatches());
        return new BizException(ErrorCode.VALIDATION_ERROR, userMessage, userMessage);
    }
}
