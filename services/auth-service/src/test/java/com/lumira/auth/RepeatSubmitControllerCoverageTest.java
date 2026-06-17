package com.lumira.auth;

import com.lumira.common.web.repeatsubmit.RepeatSubmit;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RepeatSubmitControllerCoverageTest {

    private static final Set<String> INFRASTRUCTURE_WRITE_ENDPOINTS = Set.of(
            "AuthController#refreshToken",
            "AuthController#keepalive",
            "AuthV2Controller#keepalive"
    );

    @Test
    void userFacingWriteEndpointsShouldUseRepeatSubmit() throws Exception {
        List<String> missing = findMissingRepeatSubmit("com.lumira.auth");
        assertTrue(missing.isEmpty(), "Missing @RepeatSubmit on write endpoints: " + missing);
    }

    private List<String> findMissingRepeatSubmit(String basePackage) throws Exception {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        List<String> missing = new ArrayList<>();
        for (var beanDefinition : scanner.findCandidateComponents(basePackage)) {
            Class<?> controllerClass = Class.forName(beanDefinition.getBeanClassName());
            if (controllerClass.getSimpleName().contains("Internal")) {
                continue;
            }
            for (Method method : controllerClass.getDeclaredMethods()) {
                String endpointName = controllerClass.getSimpleName() + "#" + method.getName();
                if (isWriteEndpoint(method)
                        && method.getAnnotation(RepeatSubmit.class) == null
                        && !INFRASTRUCTURE_WRITE_ENDPOINTS.contains(endpointName)) {
                    missing.add(endpointName);
                }
            }
        }
        return missing;
    }

    private boolean isWriteEndpoint(Method method) {
        if (method.getAnnotation(PostMapping.class) != null
                || method.getAnnotation(PutMapping.class) != null
                || method.getAnnotation(PatchMapping.class) != null
                || method.getAnnotation(DeleteMapping.class) != null) {
            return true;
        }
        RequestMapping requestMapping = method.getAnnotation(RequestMapping.class);
        return requestMapping != null && Arrays.stream(requestMapping.method()).anyMatch(this::isWriteMethod);
    }

    private boolean isWriteMethod(RequestMethod method) {
        return method == RequestMethod.POST
                || method == RequestMethod.PUT
                || method == RequestMethod.PATCH
                || method == RequestMethod.DELETE;
    }
}
