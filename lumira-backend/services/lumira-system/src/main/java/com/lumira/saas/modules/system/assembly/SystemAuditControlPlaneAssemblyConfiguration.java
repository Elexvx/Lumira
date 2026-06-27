package com.lumira.saas.modules.system.assembly;

import com.lumira.common.runtime.ConditionalOnLumiraControlPlaneEnabled;
import com.lumira.saas.modules.audit.app.LoginAuditService;
import com.lumira.saas.modules.audit.app.OperationAuditService;
import com.lumira.saas.modules.audit.controller.AuditController;
import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@ConditionalOnLumiraControlPlaneEnabled
@MapperScan(basePackages = "com.lumira.saas.modules.audit.mapper", annotationClass = Mapper.class)
@Import({
        AuditController.class,
        LoginAuditService.class,
        OperationAuditService.class
})
public class SystemAuditControlPlaneAssemblyConfiguration {
}
