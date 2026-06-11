package com.lumira.common.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FieldCryptoServiceTest {

    @Test
    void encryptsWithVersionedRandomizedAesGcmPayload() {
        FieldCryptoService cryptoService = new FieldCryptoService("field-secret-with-at-least-32-characters");

        String firstCipher = cryptoService.encrypt("sensitive-value");
        String secondCipher = cryptoService.encrypt("sensitive-value");

        assertThat(firstCipher).startsWith(FieldCryptoService.PREFIX);
        assertThat(secondCipher).startsWith(FieldCryptoService.PREFIX);
        assertThat(firstCipher).isNotEqualTo(secondCipher);
        assertThat(cryptoService.decrypt(firstCipher)).isEqualTo("sensitive-value");
        assertThat(cryptoService.decrypt(secondCipher)).isEqualTo("sensitive-value");
    }

    @Test
    void keepsLegacyPlainTextReadableAndRequiresSecretOnlyWhenEncrypting() {
        FieldCryptoService cryptoService = new FieldCryptoService("");

        assertThat(cryptoService.decrypt("legacy-plain-text")).isEqualTo("legacy-plain-text");
        assertThatThrownBy(() -> cryptoService.encrypt("sensitive-value"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FIELD_SECRET");
    }
}
