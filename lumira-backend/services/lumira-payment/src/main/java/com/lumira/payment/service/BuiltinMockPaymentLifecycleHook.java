package com.lumira.payment.service;

import com.lumira.api.plugin.BuiltinPluginLifecycleHook;
import org.springframework.stereotype.Component;

@Component
public class BuiltinMockPaymentLifecycleHook implements BuiltinPluginLifecycleHook {

    private final PaymentManagementAppService paymentManagementAppService;
    private final BuiltinMockPaymentService builtinMockPaymentService;

    public BuiltinMockPaymentLifecycleHook(
            PaymentManagementAppService paymentManagementAppService,
            BuiltinMockPaymentService builtinMockPaymentService
    ) {
        this.paymentManagementAppService = paymentManagementAppService;
        this.builtinMockPaymentService = builtinMockPaymentService;
    }

    @Override
    public String pluginCode() {
        return BuiltinMockPaymentAvailability.PLUGIN_CODE;
    }

    @Override
    public void onEnable(PluginLifecycleContext context) {
        paymentManagementAppService.provisionBuiltinMockProvider(context.operatorId(), context.operatorUuid());
    }

    @Override
    public void onDisable(PluginLifecycleContext context) {
        builtinMockPaymentService.cancelPendingForPluginDisable(context.operatorId(), context.operatorUuid());
        paymentManagementAppService.disableBuiltinMockProvider(context.operatorId(), context.operatorUuid());
    }
}
