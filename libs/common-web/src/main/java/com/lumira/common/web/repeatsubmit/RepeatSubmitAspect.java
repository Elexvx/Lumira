package com.lumira.common.web.repeatsubmit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.lumira.common.constant.CacheKeyConstants;
import com.lumira.common.constant.PlatformConstants;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.common.web.RequestContextUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.validation.Errors;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class RepeatSubmitAspect {

    private static final long DEFAULT_INTERVAL_MILLIS = 5000L;

    private final RepeatSubmitStore repeatSubmitStore;
    private final ClientIpResolver clientIpResolver;
    private final ObjectProvider<SecurityContextFacade> securityContextFacadeProvider;
    private final ObjectMapper objectMapper;

    public RepeatSubmitAspect(
            RepeatSubmitStore repeatSubmitStore,
            ClientIpResolver clientIpResolver,
            ObjectProvider<SecurityContextFacade> securityContextFacadeProvider,
            ObjectMapper objectMapper
    ) {
        this.repeatSubmitStore = repeatSubmitStore;
        this.clientIpResolver = clientIpResolver;
        this.securityContextFacadeProvider = securityContextFacadeProvider;
        this.objectMapper = objectMapper;
    }

    @Around("@annotation(com.lumira.common.web.repeatsubmit.RepeatSubmit)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        Method method = resolveMethod(joinPoint);
        RepeatSubmit repeatSubmit = method.getAnnotation(RepeatSubmit.class);
        if (repeatSubmit == null) {
            return joinPoint.proceed();
        }

        HttpServletRequest request = RequestContextUtils.currentRequest();
        if (request == null) {
            return joinPoint.proceed();
        }

        String key = buildKey(request, joinPoint.getArgs());
        Duration ttl = Duration.ofMillis(normalizeInterval(repeatSubmit.interval()));
        if (!repeatSubmitStore.putIfAbsent(key, ttl)) {
            throw new BizException(ErrorCode.REPEAT_SUBMIT, repeatSubmit.message(), repeatSubmit.message());
        }

        try {
            return joinPoint.proceed();
        } catch (Throwable throwable) {
            repeatSubmitStore.remove(key);
            throw throwable;
        }
    }

    private Method resolveMethod(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Class<?> targetClass = joinPoint.getTarget() == null ? method.getDeclaringClass() : joinPoint.getTarget().getClass();
        return AopUtils.getMostSpecificMethod(method, targetClass);
    }

    private String buildKey(HttpServletRequest request, Object[] arguments) {
        String scope = resolveScope(request);
        String method = safePart(request.getMethod());
        String path = safePart(buildRequestPath(request));
        String digest = sha256Hex(buildPayloadSnapshot(arguments));
        return CacheKeyConstants.repeatSubmitKey(scope, method, path, digest);
    }

    private String resolveScope(HttpServletRequest request) {
        SecurityContextFacade securityContextFacade = securityContextFacadeProvider.getIfAvailable();
        CurrentUser currentUser = securityContextFacade == null ? null : securityContextFacade.getCurrentUserOrNull();
        if (currentUser != null && currentUser.isAuthenticated() && currentUser.getUserId() != null) {
            Long tenantId = currentUser.getCurrentTenantId() == null ? PlatformConstants.PLATFORM_TENANT_ID : currentUser.getCurrentTenantId();
            return String.join(":", "user", String.valueOf(currentUser.getUserId()), String.valueOf(tenantId));
        }
        return String.join(":", "ip", clientIpResolver.resolve(request));
    }

    private String buildRequestPath(HttpServletRequest request) {
        String queryString = request.getQueryString();
        if (!StringUtils.hasText(queryString)) {
            return request.getRequestURI();
        }
        return request.getRequestURI() + "?" + queryString;
    }

    private String buildPayloadSnapshot(Object[] arguments) {
        List<Object> payload = new ArrayList<>();
        if (arguments != null) {
            for (Object argument : arguments) {
                Object sanitized = sanitizeArgument(argument);
                if (sanitized != null) {
                    payload.add(sanitized);
                }
            }
        }
        try {
            return objectMapper.copy()
                    .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
                    .writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            return String.valueOf(payload);
        }
    }

    private Object sanitizeArgument(Object argument) {
        if (argument == null) {
            return null;
        }
        if (argument instanceof HttpServletRequest
                || argument instanceof jakarta.servlet.http.HttpServletResponse
                || argument instanceof jakarta.servlet.ServletRequest
                || argument instanceof jakarta.servlet.ServletResponse
                || argument instanceof BindingResult
                || argument instanceof Errors
                || argument instanceof MultipartHttpServletRequest
                || argument instanceof InputStream
                || argument instanceof OutputStream
                || argument instanceof Reader
                || argument instanceof Writer) {
            return null;
        }
        if (argument instanceof MultipartFile multipartFile) {
            return multipartFileDescriptor(multipartFile);
        }
        if (argument instanceof MultipartFile[] multipartFiles) {
            return Arrays.stream(multipartFiles).map(this::multipartFileDescriptor).toList();
        }
        if (argument instanceof Collection<?> collection) {
            return collection.stream().map(this::sanitizeNestedArgument).toList();
        }
        if (argument instanceof Map<?, ?> map) {
            Map<String, Object> ordered = new LinkedHashMap<>();
            map.forEach((key, value) -> ordered.put(String.valueOf(key), sanitizeNestedArgument(value)));
            return ordered;
        }
        return sanitizeNestedArgument(argument);
    }

    private Object sanitizeNestedArgument(Object argument) {
        if (argument == null) {
            return null;
        }
        if (argument instanceof MultipartFile multipartFile) {
            return multipartFileDescriptor(multipartFile);
        }
        if (argument instanceof Collection<?> collection) {
            return collection.stream().map(this::sanitizeNestedArgument).toList();
        }
        if (argument instanceof Map<?, ?> map) {
            Map<String, Object> ordered = new LinkedHashMap<>();
            map.forEach((key, value) -> ordered.put(String.valueOf(key), sanitizeNestedArgument(value)));
            return ordered;
        }
        return argument;
    }

    private Map<String, Object> multipartFileDescriptor(MultipartFile multipartFile) {
        Map<String, Object> descriptor = new LinkedHashMap<>();
        descriptor.put("name", multipartFile.getName());
        descriptor.put("originalFilename", multipartFile.getOriginalFilename());
        descriptor.put("size", multipartFile.getSize());
        descriptor.put("contentType", multipartFile.getContentType());
        return descriptor;
    }

    private long normalizeInterval(int interval) {
        return interval > 0 ? interval : DEFAULT_INTERVAL_MILLIS;
    }

    private String safePart(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 不可用", ex);
        }
    }
}
