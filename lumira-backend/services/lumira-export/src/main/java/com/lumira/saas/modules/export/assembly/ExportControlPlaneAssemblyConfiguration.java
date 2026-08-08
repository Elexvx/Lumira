package com.lumira.saas.modules.export.assembly;

import com.lumira.common.runtime.ConditionalOnLumiraControlPlaneEnabled;
import com.lumira.saas.modules.export.ExcelExportService;
import com.lumira.saas.modules.export.ExportTaskService;
import com.lumira.saas.modules.export.JdbcExportTaskQueueRepository;
import com.lumira.saas.modules.export.controller.internal.InternalUserExportJobController;
import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/** Explicit Export ownership assembly for the modular-monolith server. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnLumiraControlPlaneEnabled
@MapperScan(basePackages = "com.lumira.saas.modules.export", annotationClass = Mapper.class)
@Import({ExcelExportService.class, ExportTaskService.class, JdbcExportTaskQueueRepository.class, InternalUserExportJobController.class})
public class ExportControlPlaneAssemblyConfiguration {
}
