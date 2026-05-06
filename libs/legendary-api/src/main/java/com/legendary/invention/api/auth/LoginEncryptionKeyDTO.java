package com.legendary.invention.api.auth;

public record LoginEncryptionKeyDTO(String algorithm, String keyId, String publicKey) {
}
