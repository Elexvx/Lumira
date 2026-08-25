package com.lumira.alerting.controller;

import com.lumira.alerting.app.AlertingAppService;
import com.lumira.alerting.infrastructure.AlertDeliveryGateway;
import com.lumira.alerting.model.AlertingModels;
import com.lumira.common.api.ApiResponse;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.PermissionGuard;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.common.web.TraceContext;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v2/alerting")
public class AlertingController {
    private final AlertingAppService service;
    private final SecurityContextFacade securityContext;
    private final PermissionGuard permissionGuard;

    public AlertingController(AlertingAppService service, SecurityContextFacade securityContext, PermissionGuard permissionGuard) {
        this.service = service;
        this.securityContext = securityContext;
        this.permissionGuard = permissionGuard;
    }

    @GetMapping("/catalog")
    public ApiResponse<List<AlertingModels.CatalogSignal>> catalog() {
        require("plugin:alerting:view");
        return ok(service.catalog());
    }

    @GetMapping("/health")
    public ApiResponse<AlertingModels.HealthView> health() {
        require("plugin:alerting:view");
        return ok(service.health());
    }

    @GetMapping("/channels")
    public ApiResponse<List<AlertingModels.ChannelView>> channels() {
        require("plugin:alerting:view");
        return ok(service.channels());
    }

    @PostMapping("/channels")
    public ApiResponse<AlertingModels.ChannelView> createChannel(@Valid @RequestBody AlertingModels.ChannelRequest request) {
        require("plugin:alerting:channel-manage");
        return ok(service.saveChannel(null, request, currentUser()));
    }

    @PutMapping("/channels/{id}")
    public ApiResponse<AlertingModels.ChannelView> updateChannel(@PathVariable long id,
                                                                 @Valid @RequestBody AlertingModels.ChannelRequest request) {
        require("plugin:alerting:channel-manage");
        return ok(service.saveChannel(id, request, currentUser()));
    }

    @DeleteMapping("/channels/{id}")
    public ApiResponse<Boolean> deleteChannel(@PathVariable long id) {
        require("plugin:alerting:channel-manage");
        service.deleteChannel(id, currentUser());
        return ok(Boolean.TRUE);
    }

    @PostMapping("/channels/{id}/test")
    public ApiResponse<AlertDeliveryGateway.ProviderResult> testChannel(@PathVariable long id) {
        require("plugin:alerting:channel-manage");
        return ok(service.testChannel(id));
    }

    @GetMapping("/contact-groups")
    public ApiResponse<List<AlertingModels.ContactGroupView>> contactGroups() {
        require("plugin:alerting:view");
        return ok(service.contactGroups());
    }

    @PostMapping("/contact-groups")
    public ApiResponse<AlertingModels.ContactGroupView> createContactGroup(
            @Valid @RequestBody AlertingModels.ContactGroupRequest request) {
        require("plugin:alerting:manage");
        return ok(service.saveContactGroup(null, request, currentUser()));
    }

    @PutMapping("/contact-groups/{id}")
    public ApiResponse<AlertingModels.ContactGroupView> updateContactGroup(
            @PathVariable long id, @Valid @RequestBody AlertingModels.ContactGroupRequest request) {
        require("plugin:alerting:manage");
        return ok(service.saveContactGroup(id, request, currentUser()));
    }

    @DeleteMapping("/contact-groups/{id}")
    public ApiResponse<Boolean> deleteContactGroup(@PathVariable long id) {
        require("plugin:alerting:manage");
        service.deleteContactGroup(id, currentUser());
        return ok(Boolean.TRUE);
    }

    @GetMapping("/rules")
    public ApiResponse<List<AlertingModels.RuleView>> rules() {
        require("plugin:alerting:view");
        return ok(service.rules());
    }

    @PostMapping("/rules/preview")
    public ApiResponse<Map<String, Object>> previewRule(@Valid @RequestBody AlertingModels.RuleRequest request) {
        require("plugin:alerting:manage");
        return ok(service.previewRule(request));
    }

    @PostMapping("/rules")
    public ApiResponse<AlertingModels.RuleView> createRule(@Valid @RequestBody AlertingModels.RuleRequest request) {
        require("plugin:alerting:manage");
        return ok(service.saveRule(null, request, currentUser()));
    }

    @PutMapping("/rules/{id}")
    public ApiResponse<AlertingModels.RuleView> updateRule(@PathVariable long id,
                                                           @Valid @RequestBody AlertingModels.RuleRequest request) {
        require("plugin:alerting:manage");
        return ok(service.saveRule(id, request, currentUser()));
    }

    @DeleteMapping("/rules/{id}")
    public ApiResponse<Boolean> deleteRule(@PathVariable long id) {
        require("plugin:alerting:manage");
        service.deleteRule(id, currentUser());
        return ok(Boolean.TRUE);
    }

    @GetMapping("/instances")
    public ApiResponse<List<AlertingModels.AlertInstanceView>> instances(@RequestParam(required = false) String status) {
        require("plugin:alerting:view");
        return ok(service.instances(status));
    }

    @PostMapping("/instances/{id}/ack")
    public ApiResponse<Boolean> acknowledge(@PathVariable long id, @RequestParam long version) {
        require("plugin:alerting:ack");
        service.acknowledge(id, version, currentUser());
        return ok(Boolean.TRUE);
    }

    @GetMapping("/silences")
    public ApiResponse<List<AlertingModels.SilenceView>> silences() {
        require("plugin:alerting:view");
        return ok(service.silences());
    }

    @PostMapping("/silences")
    public ApiResponse<AlertingModels.SilenceView> createSilence(@Valid @RequestBody AlertingModels.SilenceRequest request) {
        require("plugin:alerting:silence");
        return ok(service.saveSilence(null, request, currentUser()));
    }

    @PutMapping("/silences/{id}")
    public ApiResponse<AlertingModels.SilenceView> updateSilence(@PathVariable long id,
                                                                 @Valid @RequestBody AlertingModels.SilenceRequest request) {
        require("plugin:alerting:silence");
        return ok(service.saveSilence(id, request, currentUser()));
    }

    @DeleteMapping("/silences/{id}")
    public ApiResponse<Boolean> deleteSilence(@PathVariable long id) {
        require("plugin:alerting:silence");
        service.deleteSilence(id, currentUser());
        return ok(Boolean.TRUE);
    }

    @GetMapping("/deliveries")
    public ApiResponse<List<AlertingModels.DeliveryView>> deliveries(@RequestParam(required = false) String status) {
        require("plugin:alerting:view");
        return ok(service.deliveries(status));
    }

    @PostMapping("/deliveries/{id}/retry")
    public ApiResponse<Boolean> retryDelivery(@PathVariable long id) {
        require("plugin:alerting:manage");
        service.retryDelivery(id);
        return ok(Boolean.TRUE);
    }

    @GetMapping("/directory/mappings")
    public ApiResponse<List<AlertingModels.DirectoryMappingView>> directoryMappings(
            @RequestParam(required = false) Long channelId) {
        require("plugin:alerting:view");
        return ok(service.directoryMappings(channelId));
    }

    @PostMapping("/directory/mappings")
    public ApiResponse<AlertingModels.DirectoryMappingView> saveDirectoryMapping(
            @Valid @RequestBody AlertingModels.DirectoryMappingRequest request) {
        require("plugin:alerting:directory-sync");
        return ok(service.saveDirectoryMapping(request, currentUser()));
    }

    @PostMapping("/directory/channels/{channelId}/sync")
    public ApiResponse<Map<String, Object>> syncDirectory(@PathVariable long channelId) {
        require("plugin:alerting:directory-sync");
        return ok(service.syncDirectory(channelId, currentUser()));
    }

    private CurrentUser currentUser() {
        return securityContext.getCurrentUser();
    }

    private void require(String permission) {
        permissionGuard.requirePermission(currentUser(), permission);
    }

    private static <T> ApiResponse<T> ok(T data) {
        return ApiResponse.success(data, TraceContext.getRequestId());
    }
}
