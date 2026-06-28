package com.lumira.saas.modules.system.assembly;

import com.lumira.common.runtime.ConditionalOnLumiraControlPlaneEnabled;
import com.lumira.saas.modules.account.app.AccountActivationService;
import com.lumira.saas.modules.account.controller.AccountActivationController;
import com.lumira.saas.modules.user.domain.UserDomainService;
import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@ConditionalOnLumiraControlPlaneEnabled
@MapperScan(basePackages = "com.lumira.saas.modules.user.mapper", annotationClass = Mapper.class)
@Import({
        AccountActivationController.class,
        AccountActivationService.class,
        UserDomainService.class
})
public class SystemUserControlPlaneAssemblyConfiguration {
}
