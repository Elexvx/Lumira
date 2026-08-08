package com.lumira.payment;

import com.lumira.common.runtime.ConditionalOnLumiraControlPlaneEnabled;
import com.lumira.payment.controller.InternalJobController;
import com.lumira.payment.service.LoggingPaymentOutboxDispatcher;
import com.lumira.payment.service.PaymentOutboxRelay;
import com.lumira.payment.service.RedisStreamPaymentOutboxDispatcher;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/** Owner-side relay/replay surface used by the separate async runtime. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnLumiraControlPlaneEnabled
@Import({
        LoggingPaymentOutboxDispatcher.class,
        RedisStreamPaymentOutboxDispatcher.class,
        PaymentOutboxRelay.class,
        InternalJobController.class
})
public class PaymentOwnerAsyncAdapterControlPlaneAssemblyConfiguration {
}
