package com.lumira.server;

import com.lumira.auth.AuthControlPlaneAssemblyConfiguration;
import com.lumira.alerting.AlertingControlPlaneAssemblyConfiguration;
import com.lumira.ai.assembly.AiControlPlaneAssemblyConfiguration;
import com.lumira.localization.LocalizationControlPlaneAssemblyConfiguration;
import com.lumira.message.MessageControlPlaneAssemblyConfiguration;
import com.lumira.payment.PaymentControlPlaneAssemblyConfiguration;
import com.lumira.plugin.PluginControlPlaneAssemblyConfiguration;
import com.lumira.saas.modules.activity.assembly.ActivityControlPlaneAssemblyConfiguration;
import com.lumira.saas.modules.competition.assembly.CompetitionControlPlaneAssemblyConfiguration;
import com.lumira.saas.modules.eventcatalog.assembly.EventCatalogControlPlaneAssemblyConfiguration;
import com.lumira.saas.modules.expert.assembly.ExpertControlPlaneAssemblyConfiguration;
import com.lumira.saas.modules.export.assembly.ExportControlPlaneAssemblyConfiguration;
import com.lumira.saas.modules.project.assembly.ProjectControlPlaneAssemblyConfiguration;
import com.lumira.saas.modules.workflow.assembly.WorkflowControlPlaneAssemblyConfiguration;
import com.lumira.saas.modules.system.assembly.SystemAuditControlPlaneAssemblyConfiguration;
import com.lumira.saas.modules.system.assembly.SystemBusinessControlPlaneAssemblyConfiguration;
import com.lumira.saas.modules.system.assembly.SystemIamControlPlaneAssemblyConfiguration;
import com.lumira.saas.modules.system.assembly.SystemInfrastructureControlPlaneAssemblyConfiguration;
import com.lumira.saas.modules.system.assembly.SystemOperationsControlPlaneAssemblyConfiguration;
import com.lumira.saas.modules.system.assembly.SystemPlatformControlPlaneAssemblyConfiguration;
import com.lumira.saas.modules.system.assembly.SystemUserControlPlaneAssemblyConfiguration;
import com.lumira.team.TeamControlPlaneAssemblyConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@Import({
        LumiraCommonRuntimeAssemblyConfiguration.class,
        LumiraAdminControlPlaneSchedulingConfiguration.class,
        AiControlPlaneAssemblyConfiguration.class,
        SystemInfrastructureControlPlaneAssemblyConfiguration.class,
        SystemBusinessControlPlaneAssemblyConfiguration.class,
        ActivityControlPlaneAssemblyConfiguration.class,
        CompetitionControlPlaneAssemblyConfiguration.class,
        EventCatalogControlPlaneAssemblyConfiguration.class,
        ExpertControlPlaneAssemblyConfiguration.class,
        ProjectControlPlaneAssemblyConfiguration.class,
        WorkflowControlPlaneAssemblyConfiguration.class,
        ExportControlPlaneAssemblyConfiguration.class,
        SystemAuditControlPlaneAssemblyConfiguration.class,
        SystemIamControlPlaneAssemblyConfiguration.class,
        SystemPlatformControlPlaneAssemblyConfiguration.class,
        SystemOperationsControlPlaneAssemblyConfiguration.class,
        SystemUserControlPlaneAssemblyConfiguration.class,
        LumiraFileControlPlaneAssemblyConfiguration.class,
        AuthControlPlaneAssemblyConfiguration.class,
        MessageControlPlaneAssemblyConfiguration.class,
        AlertingControlPlaneAssemblyConfiguration.class,
        PaymentControlPlaneAssemblyConfiguration.class,
        PluginControlPlaneAssemblyConfiguration.class,
        LocalizationControlPlaneAssemblyConfiguration.class,
        TeamControlPlaneAssemblyConfiguration.class
})
public class LumiraAdminRuntimeAssemblyConfiguration {
}
