package com.lumira.localization;

import com.lumira.common.runtime.ConditionalOnLumiraControlPlaneEnabled;
import com.lumira.localization.security.JwtTokenService;
import com.lumira.localization.security.LocalizationJwtAuthFilter;
import com.lumira.localization.security.SecurityProperties;
import com.lumira.localization.app.LocalizationManagementAppService;
import com.lumira.localization.app.DatabaseLocalizationCatalogInitializer;
import com.lumira.localization.controller.LocalizationController;
import com.lumira.localization.controller.LocalizationReadinessV2Controller;
import com.lumira.localization.controller.LocalizationV2Controller;
import com.lumira.localization.mapper.LocalizationManagementMapper;
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
        DatabaseLocalizationCatalogInitializer.class,
        LocalizationManagementAppService.class,
        LocalizationController.class,
        LocalizationReadinessV2Controller.class,
        LocalizationV2Controller.class
})
public class LocalizationControlPlaneAssemblyConfiguration {
}
