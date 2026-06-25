package com.lumira.api.client;

import com.lumira.api.payment.PaymentProviderSettingsDTO;
import com.lumira.api.payment.PaymentProviderTestResultDTO;

import java.util.List;

public interface PaymentInternalApi {

    List<PaymentProviderSettingsDTO> listPaymentProviderSettings();

    PaymentProviderSettingsDTO paymentProviderSettings(String providerCode);

    PaymentProviderSettingsDTO updatePaymentProviderSettings(Long operatorId, String providerCode, PaymentProviderSettingsDTO request);

    PaymentProviderTestResultDTO testPaymentProvider(Long operatorId, String providerCode);
}
