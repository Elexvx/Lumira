package com.legendary.invention.payment.controller;

import com.legendary.invention.api.client.PaymentInternalApi;
import com.legendary.invention.api.payment.PaymentProviderSettingsDTO;
import com.legendary.invention.api.payment.PaymentProviderTestResultDTO;
import com.legendary.invention.payment.service.PaymentManagementAppService;
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
    public List<PaymentProviderSettingsDTO> listPaymentProviderSettings(@RequestParam("tenantId") Long tenantId) {
        return paymentManagementAppService.listProviderSettings(tenantId);
    }

    @GetMapping("/providers/{providerCode}")
    public PaymentProviderSettingsDTO paymentProviderSettings(@RequestParam("tenantId") Long tenantId, @PathVariable String providerCode) {
        return paymentManagementAppService.paymentProviderSettings(tenantId, providerCode);
    }

    @PutMapping("/providers/{providerCode}")
    public PaymentProviderSettingsDTO updatePaymentProviderSettings(
            @RequestParam("tenantId") Long tenantId,
            @RequestParam("operatorId") Long operatorId,
            @PathVariable String providerCode,
            @RequestBody PaymentProviderSettingsDTO request
    ) {
        return paymentManagementAppService.updatePaymentProviderSettings(tenantId, operatorId, providerCode, request);
    }

    @GetMapping("/providers/{providerCode}/test")
    public PaymentProviderTestResultDTO testPaymentProvider(@RequestParam("tenantId") Long tenantId, @RequestParam("operatorId") Long operatorId, @PathVariable String providerCode) {
        return paymentManagementAppService.testPaymentProvider(tenantId, operatorId, providerCode);
    }
}
