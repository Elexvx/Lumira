package com.lumira.common.web;

import com.lumira.api.client.PaymentInternalApi;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

@Configuration(proxyBeanMethods = false)
public class PaymentInternalClientConfiguration {

    private static final String INTERNAL_TOKEN_HEADER = "X-Job-Token";

    @Bean
    @Lazy
    @ConditionalOnMissingBean(PaymentInternalApi.class)
    public PaymentInternalApi paymentInternalApi(
            @Value("${saas.payment.service-base-url:${PAYMENT_SERVICE_BASE_URL:http://localhost:8085}}") String paymentServiceBaseUrl,
            @Value("${saas.internal.payment-token:${SAAS_INTERNAL_PAYMENT_TOKEN:}}") String paymentToken,
            ObjectProvider<RestClient.Builder> restClientBuilderProvider
    ) {
        if (!StringUtils.hasText(paymentToken)) {
            throw new IllegalStateException("saas.internal.payment-token is required");
        }
        RestClient.Builder builder = restClientBuilderProvider.getIfAvailable(RestClient::builder).clone()
                .baseUrl(TrustedServiceBaseUrlValidator.requireHttpBaseUrl(paymentServiceBaseUrl, "saas.payment.service-base-url"))
                .defaultHeader(INTERNAL_TOKEN_HEADER, paymentToken.trim())
                .defaultHeader(HttpHeaders.ACCEPT, "application/json");
        RestClient restClient = builder.build();
        HttpServiceProxyFactory proxyFactory = HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient)).build();
        return proxyFactory.createClient(PaymentInternalApi.class);
    }
}
