package com.lumira.saas.modules.architecture.interfaces.rest;

import com.lumira.common.api.ApiResponse;
import com.lumira.common.web.TraceContext;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/architecture")
public class DddArchitectureCatalogController {

    @GetMapping("/contexts")
    public ApiResponse<ArchitectureCatalogResponse> contexts() {
        return ApiResponse.success(ArchitectureCatalogResponse.defaultCatalog(), TraceContext.getRequestId());
    }

    public record ArchitectureCatalogResponse(
            String architecture,
            List<BoundedContextResponse> contexts,
            List<String> invariants
    ) {

        static ArchitectureCatalogResponse defaultCatalog() {
            return new ArchitectureCatalogResponse(
                    "ddd-modular-monolith",
                    List.of(
                            context("IAM", "system-service", "core", "User, Role, Permission, Department, PermissionSnapshot"),
                            context("Auth", "auth-service", "supporting", "AuthSession, LoginChallenge, SecondFactor, PasskeyCredential"),
                            context("Platform", "system-service", "supporting", "Config, Dict, Audit, RuntimeAppearance, Monitor, OnlineSession"),
                            context("Message", "message-service", "supporting", "Notice, ReadState, DeliveryLog, RealtimeTicket"),
                            context("File", "file-service", "generic", "FileObject, StorageSpace, UploadSession, FileProcessingTask"),
                            context("Plugin", "plugin-service", "supporting", "PluginDefinition, PluginVersion, TenantPlugin, RuntimePolicy"),
                            context("Localization", "localization-service", "supporting", "Language, Namespace, Entry, Translation, Release"),
                            context("Payment", "payment-service", "supporting", "PaymentOrder, Refund, ProviderConfig, WebhookEvent"),
                            context("AI", "system-service", "enhancement", "AiEmployee, Skill, LlmService, KnowledgeBase, Conversation"),
                            context("Job", "job-executor", "adapter", "RelayTask")
                    ),
                    List.of(
                            "Commands write only owner aggregates and publish domain events.",
                            "Queries use read models, cache, or projections instead of loading full aggregates.",
                            "Domain code must not depend on Spring, MyBatis, Redis, HTTP SDKs, or servlet APIs.",
                            "Cross-context access must use contracts, events, projections, or cache snapshots."
                    )
            );
        }

        private static BoundedContextResponse context(
                String name,
                String ownerModule,
                String domainType,
                String primaryModels
        ) {
            return new BoundedContextResponse(
                    name,
                    ownerModule,
                    domainType,
                    primaryModels,
                    List.of(name.toUpperCase() + "_EVENTS"),
                    "tenantId:version:scope"
            );
        }
    }

    public record BoundedContextResponse(
            String name,
            String ownerModule,
            String domainType,
            String primaryModels,
            List<String> eventFamilies,
            String readModelCacheKey
    ) {
    }
}
