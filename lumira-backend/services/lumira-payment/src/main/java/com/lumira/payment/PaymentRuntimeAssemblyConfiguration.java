package com.lumira.payment;

import com.lumira.domain.event.DomainEventPublisher;
import com.lumira.payment.event.domain.PaymentDomainEventPublisher;
import com.lumira.payment.service.PaymentConfigCryptoService;
import com.lumira.payment.service.PaymentManagementAppService;
import com.lumira.payment.service.PaymentOutboxService;
import com.lumira.payment.service.PaymentProviderCatalog;
import com.lumira.payment.service.PaymentTransactionService;
import com.lumira.payment.service.PaymentWebhookService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@Import({
        PaymentConfigCryptoService.class,
        PaymentManagementAppService.class,
        PaymentOutboxService.class,
        PaymentProviderCatalog.class,
        PaymentTransactionService.class,
        PaymentWebhookService.class
})
public class PaymentRuntimeAssemblyConfiguration {

    @Bean(name = "paymentDomainEventPublisher")
    public DomainEventPublisher paymentDomainEventPublisher(PaymentOutboxService paymentOutboxService) {
        return new PaymentDomainEventPublisher(paymentOutboxService);
    }
}
