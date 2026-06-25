package com.lumira.payment.controller;

import com.lumira.api.client.PaymentInternalApi;
import com.lumira.api.payment.PaymentProviderSettingsDTO;
import com.lumira.api.payment.PaymentProviderTestResultDTO;
import com.lumira.payment.service.PaymentManagementAppService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/internal/payment")
public class InternalPaymentController implements PaymentInternalApi {

    private final PaymentManagementAppService paymentManagementAppService;

    public InternalPaymentController(PaymentManagementAppService paymentManagementAppService) {
        this.paymentManagementAppService = paymentManagementAppService;
    }

    @GetMapping("/providers")
    public List<PaymentProviderSettingsDTO> listPaymentProviderSettings() {
        return paymentManagementAppService.listProviderSettings();
    }

    @GetMapping("/providers/{providerCode}")
    public PaymentProviderSettingsDTO paymentProviderSettings(@PathVariable String providerCode) {
        return paymentManagementAppService.paymentProviderSettings(providerCode);
    }

    @PutMapping("/providers/{providerCode}")
    public PaymentProviderSettingsDTO updatePaymentProviderSettings(
            @RequestParam("operatorId") Long operatorId,
            @PathVariable String providerCode,
            @RequestBody PaymentProviderSettingsDTO request
    ) {
        return paymentManagementAppService.updatePaymentProviderSettings(operatorId, providerCode, request);
    }

    @GetMapping("/providers/{providerCode}/test")
    public PaymentProviderTestResultDTO testPaymentProvider(@RequestParam("operatorId") Long operatorId, @PathVariable String providerCode) {
        return paymentManagementAppService.testPaymentProvider(operatorId, providerCode);
    }
}
