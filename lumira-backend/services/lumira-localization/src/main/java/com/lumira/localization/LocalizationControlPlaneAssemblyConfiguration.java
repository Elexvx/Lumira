package com.lumira.localization;

import com.lumira.common.runtime.ConditionalOnLumiraControlPlaneEnabled;
import com.lumira.localization.security.JwtTokenService;
import com.lumira.localization.security.LocalizationJwtAuthFilter;
import com.lumira.localization.security.SecurityProperties;
import com.lumira.saas.modules.localization.app.LocalizationManagementAppService;
import com.lumira.saas.modules.localization.controller.LocalizationController;
import com.lumira.saas.modules.localization.controller.LocalizationReadinessV2Controller;
import com.lumira.saas.modules.localization.controller.LocalizationV2Controller;
import com.lumira.saas.modules.localization.mapper.LocalizationManagementMapper;
import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@ConditionalOnLumiraControlPlaneEnabled
@EnableConfigurationProperties(SecurityProperties.class)
@MapperScan(
        basePackageClasses = LocalizationManagementMapper.class,
        annotationClass = Mapper.class
)
@Import({
        LocalizationSecurityConfig.class,
        JwtTokenService.class,
        LocalizationJwtAuthFilter.class,
        LocalizationManagementAppService.class,
        LocalizationController.class,
        LocalizationReadinessV2Controller.class,
        LocalizationV2Controller.class
})
public class LocalizationControlPlaneAssemblyConfiguration {
}
