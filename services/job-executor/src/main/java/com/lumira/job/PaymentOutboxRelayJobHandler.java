package com.lumira.job;

import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.springframework.stereotype.Component;

@Component
public class PaymentOutboxRelayJobHandler {

    private final BackendJobClient backendJobClient;

    public PaymentOutboxRelayJobHandler(BackendJobClient backendJobClient) {
        this.backendJobClient = backendJobClient;
    }

    @XxlJob("paymentOutboxRelayJob")
    public void execute() {
        XxlJobHelper.log("dispatch payment outbox relay");
        backendJobClient.relayPaymentOutbox();
    }
}
