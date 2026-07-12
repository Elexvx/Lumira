package com.lumira.payment;

import com.lumira.common.runtime.ConditionalOnLumiraAsyncEnabled;
import com.lumira.payment.service.LoggingPaymentOutboxDispatcher;
import com.lumira.payment.service.PaymentOutboxRelay;
import com.lumira.payment.service.RedisStreamPaymentOutboxDispatcher;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@ConditionalOnLumiraAsyncEnabled
@Import({
        PaymentRuntimeAssemblyConfiguration.class,
        LoggingPaymentOutboxDispatcher.class,
        RedisStreamPaymentOutboxDispatcher.class,
        PaymentOutboxRelay.class,
        com.lumira.payment.controller.InternalJobController.class
})
public class PaymentAsyncAssemblyConfiguration {
}
