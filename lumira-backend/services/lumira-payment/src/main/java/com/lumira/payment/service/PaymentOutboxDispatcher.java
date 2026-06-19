package com.lumira.payment.service;

public interface PaymentOutboxDispatcher {
    void dispatch(PaymentOutboxRow row);
}
