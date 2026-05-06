package com.legendary.invention.saas.infrastructure.repeatsubmit;

import com.legendary.invention.saas.common.annotation.RepeatSubmit;
import com.legendary.invention.saas.common.enums.ErrorCode;
import com.legendary.invention.saas.common.exception.BizException;
import com.legendary.invention.saas.infrastructure.config.WebProperties;
import com.legendary.invention.saas.infrastructure.redis.CacheTemplate;
import com.legendary.invention.saas.infrastructure.security.ClientIpResolver;
import com.legendary.invention.saas.infrastructure.security.CurrentUser;
import com.legendary.invention.saas.infrastructure.security.SecurityContextFacade;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

class RepeatSubmitAspectTest {

    private final Map<String, String> store = new HashMap<>();
    private final Map<String, Duration> ttlStore = new LinkedHashMap<>();
    private final RepeatSubmitAspect aspect = new RepeatSubmitAspect(
            new MapBackedCacheTemplate(store, ttlStore),
            new ClientIpResolver(new WebProperties()),
            new SecurityContextFacade(),
            new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules()
    );

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clearContext();
        store.clear();
        ttlStore.clear();
    }

    @Test
    void shouldBlockDuplicateRequestWithinIntervalForAuthenticatedUser() throws Throwable {
        MockHttpServletRequest request = buildRequest("/api/v1/profile", "POST");
        request.setQueryString("id=1");
        setRequest(request);
        setAuthenticatedUser(2001L, 1001L);

        SampleController controller = new SampleController();
        Method method = SampleController.class.getDeclaredMethod("update", SampleRequest.class);
        ProceedingJoinPoint joinPoint = buildJoinPoint(controller, method, new SampleRequest("alpha"), "ok");

        Object result = aspect.around(joinPoint);
        assertEquals("ok", result);

        BizException exception = assertThrows(BizException.class, () -> aspect.around(joinPoint));
        assertEquals(ErrorCode.REPEAT_SUBMIT, exception.getErrorCode());
        assertEquals("请求过于频繁", exception.getUserMessage());
    }

    @Test
    void shouldAllowRetryAfterFailureByReleasingKey() throws Throwable {
        MockHttpServletRequest request = buildRequest("/api/v1/profile/email", "PUT");
        setRequest(request);
        setAuthenticatedUser(2001L, 1001L);

        SampleController controller = new SampleController();
        Method method = SampleController.class.getDeclaredMethod("failingUpdate", SampleRequest.class);
        ProceedingJoinPoint failingJoinPoint = buildJoinPoint(controller, method, new SampleRequest("beta"), null);
        when(failingJoinPoint.proceed()).thenThrow(new IllegalStateException("boom"));

        assertThrows(IllegalStateException.class, () -> aspect.around(failingJoinPoint));

        ProceedingJoinPoint retryJoinPoint = buildJoinPoint(controller, method, new SampleRequest("beta"), "recovered");
        assertDoesNotThrow(() -> aspect.around(retryJoinPoint));
    }

    @Test
    void shouldUseClientIpWhenUserIsAnonymous() throws Throwable {
        MockHttpServletRequest request = buildRequest("/api/v1/auth/login", "POST");
        request.setRemoteAddr("127.0.0.1");
        setRequest(request);

        SampleController controller = new SampleController();
        Method method = SampleController.class.getDeclaredMethod("login", SampleRequest.class);
        ProceedingJoinPoint joinPoint = buildJoinPoint(controller, method, new SampleRequest("gamma"), "login-ok");

        assertDoesNotThrow(() -> aspect.around(joinPoint));
        assertThrows(BizException.class, () -> aspect.around(joinPoint));
    }

    private MockHttpServletRequest buildRequest(String path, String method) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRemoteAddr("10.0.0.8");
        request.addHeader("User-Agent", "JUnit");
        return request;
    }

    private void setRequest(HttpServletRequest request) {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private void setAuthenticatedUser(Long userId, Long tenantId) {
        CurrentUser currentUser = new CurrentUser();
        currentUser.setUserId(userId);
        currentUser.setUsername("admin");
        currentUser.setCurrentTenantId(tenantId);
        currentUser.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(currentUser, "N/A")
        );
    }

    private ProceedingJoinPoint buildJoinPoint(Object target, Method method, Object arg, Object proceedResult) {
        ProceedingJoinPoint joinPoint = Mockito.mock(ProceedingJoinPoint.class);
        MethodSignature methodSignature = Mockito.mock(MethodSignature.class);
        Signature signature = methodSignature;
        when(joinPoint.getSignature()).thenReturn(signature);
        when(methodSignature.getMethod()).thenReturn(method);
        when(joinPoint.getTarget()).thenReturn(target);
        when(joinPoint.getArgs()).thenReturn(new Object[] { arg });
        try {
            if (proceedResult != null) {
                when(joinPoint.proceed()).thenReturn(proceedResult);
            }
        } catch (Throwable throwable) {
            throw new IllegalStateException(throwable);
        }
        return joinPoint;
    }

    private static final class MapBackedCacheTemplate extends CacheTemplate {
        private final Map<String, String> store;
        private final Map<String, Duration> ttlStore;

        private MapBackedCacheTemplate(Map<String, String> store, Map<String, Duration> ttlStore) {
            super(null);
            this.store = store;
            this.ttlStore = ttlStore;
        }

        @Override
        public boolean putIfAbsent(String key, String value, Duration ttl) {
            if (store.containsKey(key)) {
                return false;
            }
            store.put(key, value);
            ttlStore.put(key, ttl);
            return true;
        }

        @Override
        public void remove(String key) {
            store.remove(key);
            ttlStore.remove(key);
        }
    }

    private static class SampleController {

        @RepeatSubmit(interval = 1000, message = "请求过于频繁")
        public String update(SampleRequest request) {
            return "ok";
        }

        @RepeatSubmit(interval = 1000, message = "请求过于频繁")
        public String failingUpdate(SampleRequest request) {
            return "ok";
        }

        @RepeatSubmit(interval = 1000, message = "请求过于频繁")
        public String login(SampleRequest request) {
            return "ok";
        }
    }

    private record SampleRequest(String value) {
    }
}
