package com.lumira.common.web;

import com.lumira.api.client.PaymentInternalApi;
import com.lumira.api.payment.PaymentCreateOrderRequestDTO;
import java.util.Iterator;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PaymentInternalClientConfigurationTest {

    @Test
    void paymentInternalApiSendsScopedPaymentToken() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PaymentInternalApi api = paymentInternalApi(builder, "payment-token-2026");
        PaymentCreateOrderRequestDTO paymentRequest = new PaymentCreateOrderRequestDTO(
                "stripe",
                "ORD-1",
                "Competition Registration",
                8800L,
                "CNY",
                "127.0.0.1",
                null,
                null,
                Map.of("registrationId", 1L),
                "idem-1"
        );
        server.expect(requestTo("http://payment-service:8085/internal/payment/orders?operatorId=1001&operatorUuid=user-uuid-1001"))
                .andExpect(header("X-Job-Token", "payment-token-2026"))
                .andExpect(clientRequest -> {
                    String body = ((MockClientHttpRequest) clientRequest).getBodyAsString();
                    assertThat(body).contains("\"providerCode\":\"stripe\"");
                    assertThat(body).contains("\"orderNo\":\"ORD-1\"");
                    assertThat(body).contains("\"registrationId\":1");
                })
                .andRespond(withSuccess(
                        "{\"orderNo\":\"ORD-1\",\"providerCode\":\"stripe\",\"status\":\"PENDING\"}",
                        MediaType.APPLICATION_JSON
                ));

        var order = api.createOrder(1001L, "user-uuid-1001", paymentRequest);

        assertThat(order.orderNo()).isEqualTo("ORD-1");
        assertThat(order.providerCode()).isEqualTo("stripe");
        server.verify();
    }

    @Test
    void paymentInternalApiReadsOrderWithScopedPaymentToken() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PaymentInternalApi api = paymentInternalApi(builder, "payment-token-2026");
        server.expect(requestTo("http://payment-service:8085/internal/payment/orders/ORD-1?operatorId=1001&operatorUuid=user-uuid-1001"))
                .andExpect(header("X-Job-Token", "payment-token-2026"))
                .andRespond(withSuccess(
                        "{\"orderNo\":\"ORD-1\",\"providerCode\":\"stripe\",\"status\":\"PAID\"}",
                        MediaType.APPLICATION_JSON
                ));

        var order = api.getOrder(1001L, "user-uuid-1001", "ORD-1");

        assertThat(order.status()).isEqualTo("PAID");
        server.verify();
    }

    @Test
    void paymentInternalApiCarriesSimulatedRoleIdWhenPresent() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PaymentInternalApi api = paymentInternalApi(builder, "payment-token-2026");
        server.expect(requestTo("http://payment-service:8085/internal/payment/orders/ORD-1?operatorId=1001&operatorUuid=user-uuid-1001&simulatedRoleId=9"))
                .andExpect(header("X-Job-Token", "payment-token-2026"))
                .andRespond(withSuccess(
                        "{\"orderNo\":\"ORD-1\",\"providerCode\":\"stripe\",\"status\":\"PAID\"}",
                        MediaType.APPLICATION_JSON
                ));

        var order = api.getOrder(1001L, "user-uuid-1001", 9L, "ORD-1");

        assertThat(order.status()).isEqualTo("PAID");
        server.verify();
    }

    @Test
    void paymentInternalApiRequiresPaymentToken() {
        assertThatThrownBy(() -> paymentInternalApi(RestClient.builder(), " "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("saas.internal.payment-token is required");
    }

    @Test
    void paymentInternalApiRejectsUntrustedBaseUrl() {
        assertThatThrownBy(() -> new PaymentInternalClientConfiguration().remotePaymentInternalApi(
                "ftp://payment-service",
                "payment-token-2026",
                provider(RestClient.builder())
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("saas.payment.service-base-url")
                .hasMessageContaining("must use http or https");
    }

    private static PaymentInternalApi paymentInternalApi(RestClient.Builder builder, String paymentToken) {
        return new PaymentInternalClientConfiguration().remotePaymentInternalApi(
                "http://payment-service:8085",
                paymentToken,
                provider(builder)
        );
    }

    private static ObjectProvider<RestClient.Builder> provider(RestClient.Builder builder) {
        return new ObjectProvider<>() {
            @Override
            public RestClient.Builder getObject(Object... args) {
                return builder;
            }

            @Override
            public RestClient.Builder getIfAvailable() {
                return builder;
            }

            @Override
            public RestClient.Builder getIfUnique() {
                return builder;
            }

            @Override
            public RestClient.Builder getObject() {
                return builder;
            }

            @Override
            public Iterator<RestClient.Builder> iterator() {
                return Stream.of(builder).iterator();
            }
        };
    }
}
