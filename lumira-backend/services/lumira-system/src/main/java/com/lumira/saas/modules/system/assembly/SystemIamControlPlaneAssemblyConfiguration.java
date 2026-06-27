package com.lumira.saas.modules.system.assembly;

import com.lumira.common.runtime.ConditionalOnLumiraControlPlaneEnabled;
import com.lumira.saas.modules.iam.app.DefaultDelegationGrantEvaluator;
import com.lumira.saas.modules.iam.controller.IamReadinessV2Controller;
import com.lumira.saas.modules.iam.controller.IamV2Controller;
import com.lumira.saas.modules.iam.service.IamUserService;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@ConditionalOnLumiraControlPlaneEnabled
@MapperScan(basePackages = "com.lumira.saas.modules.iam.mapper", annotationClass = Mapper.class)
@Import({
        DefaultDelegationGrantEvaluator.class,
        IamReadinessV2Controller.class,
        IamV2Controller.class,
        IamUserService.class,
        PermissionSnapshotService.class
})
public class SystemIamControlPlaneAssemblyConfiguration {
}
