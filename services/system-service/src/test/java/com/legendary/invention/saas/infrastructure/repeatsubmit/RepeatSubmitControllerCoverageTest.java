package com.legendary.invention.saas.infrastructure.repeatsubmit;

import com.legendary.invention.saas.common.annotation.RepeatSubmit;
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

import static org.junit.jupiter.api.Assertions.assertTrue;

class RepeatSubmitControllerCoverageTest {

    @Test
    void userFacingWriteEndpointsShouldUseRepeatSubmit() throws Exception {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

        List<String> missing = new ArrayList<>();
        for (var beanDefinition : scanner.findCandidateComponents("com.legendary.invention.saas")) {
            Class<?> controllerClass = Class.forName(beanDefinition.getBeanClassName());
            if (isInternalController(controllerClass)) {
                continue;
            }
            for (Method method : controllerClass.getDeclaredMethods()) {
                if (isWriteEndpoint(method) && method.getAnnotation(RepeatSubmit.class) == null) {
                    missing.add(controllerClass.getSimpleName() + "#" + method.getName());
                }
            }
        }

        assertTrue(missing.isEmpty(), "Missing @RepeatSubmit on write endpoints: " + missing);
    }

    private boolean isInternalController(Class<?> controllerClass) {
        String className = controllerClass.getName();
        return controllerClass.getSimpleName().startsWith("Internal")
                || className.contains(".infrastructure.job.");
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
