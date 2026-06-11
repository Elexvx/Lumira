package com.lumira.api.auth;

public record LoginEncryptionKeyDTO(String algorithm, String keyId, String publicKey) {
}
