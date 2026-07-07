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

    default PaymentOrderDTO createOrder(
            Long operatorId,
            String operatorUuid,
            PaymentCreateOrderRequestDTO request
    ) {
        return createOrder(operatorId, operatorUuid, null, request);
    }

    @PostExchange("/internal/payment/orders")
    PaymentOrderDTO createOrder(
            @RequestParam("operatorId") Long operatorId,
            @RequestParam("operatorUuid") String operatorUuid,
            @RequestParam(name = "simulatedRoleId", required = false) Long simulatedRoleId,
            @RequestBody PaymentCreateOrderRequestDTO request
    );

    default PaymentOrderDTO getOrder(
            Long operatorId,
            String operatorUuid,
            String orderNo
    ) {
        return getOrder(operatorId, operatorUuid, null, orderNo);
    }

    @GetExchange("/internal/payment/orders/{orderNo}")
    PaymentOrderDTO getOrder(
            @RequestParam("operatorId") Long operatorId,
            @RequestParam("operatorUuid") String operatorUuid,
            @RequestParam(name = "simulatedRoleId", required = false) Long simulatedRoleId,
            @PathVariable("orderNo") String orderNo
    );
}
