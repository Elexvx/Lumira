package com.lumira.api.client;

import com.lumira.api.payment.PaymentCreateOrderRequestDTO;
import com.lumira.api.payment.PaymentOrderDTO;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange(accept = MediaType.APPLICATION_JSON_VALUE)
public interface PaymentInternalApi {

    @PostExchange("/internal/payment/orders")
    PaymentOrderDTO createOrder(
            @RequestParam("operatorId") Long operatorId,
            @RequestParam("operatorUuid") String operatorUuid,
            @RequestBody PaymentCreateOrderRequestDTO request
    );

    @GetExchange("/internal/payment/orders/{orderNo}")
    PaymentOrderDTO getOrder(
            @RequestParam("operatorId") Long operatorId,
            @RequestParam("operatorUuid") String operatorUuid,
            @PathVariable("orderNo") String orderNo
    );
}
