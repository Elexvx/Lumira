package com.lumira.api.client;

import com.lumira.api.payment.PaymentProviderSettingsDTO;
import com.lumira.api.payment.PaymentProviderTestResultDTO;
import com.lumira.api.payment.PaymentCreateOrderRequestDTO;
import com.lumira.api.payment.PaymentOrderDTO;

import java.util.List;

public interface PaymentInternalApi {

    List<PaymentProviderSettingsDTO> listPaymentProviderSettings();

    PaymentProviderSettingsDTO paymentProviderSettings(String providerCode);

    PaymentProviderSettingsDTO updatePaymentProviderSettings(Long operatorId, String providerCode, PaymentProviderSettingsDTO request);

    PaymentProviderTestResultDTO testPaymentProvider(Long operatorId, String providerCode);

    PaymentOrderDTO createOrder(Long operatorId, PaymentCreateOrderRequestDTO request);

    PaymentOrderDTO getOrder(String orderNo);
}
