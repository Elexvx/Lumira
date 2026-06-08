package com.legendary.invention.payment.service;

public interface PaymentOutboxDispatcher {
    void dispatch(PaymentOutboxRow row);
}
