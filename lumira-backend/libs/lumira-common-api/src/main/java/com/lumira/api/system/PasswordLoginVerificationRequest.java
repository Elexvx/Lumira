package com.lumira.api.system;

/** Sensitive internal login verification payload; never encode credentials in a query string. */
public record PasswordLoginVerificationRequest(String account, String password) {
}
