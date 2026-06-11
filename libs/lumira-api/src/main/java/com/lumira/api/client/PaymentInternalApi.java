package com.lumira.api.client;

import com.lumira.api.payment.PaymentProviderSettingsDTO;
import com.lumira.api.payment.PaymentProviderTestResultDTO;

import java.util.List;

public interface PaymentInternalApi {

    List<PaymentProviderSettingsDTO> listPaymentProviderSettings(Long tenantId);

    PaymentProviderSettingsDTO paymentProviderSettings(Long tenantId, String providerCode);

    PaymentProviderSettingsDTO updatePaymentProviderSettings(Long tenantId, Long operatorId, String providerCode, PaymentProviderSettingsDTO request);

    PaymentProviderTestResultDTO testPaymentProvider(Long tenantId, Long operatorId, String providerCode);
}
