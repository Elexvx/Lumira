package com.lumira.saas.modules.iam.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class AuthorizationVersionVectorTest {

    @Test
    void roundTripsIndependentAuthorizationDimensions() {
        AuthorizationVersionVector vector = new AuthorizationVersionVector(
                "user-uuid-42", 3, 7, Map.of(20L, 5L, 10L, 4L), Map.of(20L, 9L)
        );

        AuthorizationVersionVector parsed = AuthorizationVersionVector.parse(vector.encode());

        assertThat(parsed.subject()).isEqualTo("user-uuid-42");
        assertThat(parsed.subjectVersion()).isEqualTo(3);
        assertThat(parsed.bindingVersion()).isEqualTo(7);
        assertThat(parsed.roleVersions()).containsExactlyInAnyOrderEntriesOf(Map.of(10L, 4L, 20L, 5L));
        assertThat(parsed.dataPolicyVersions()).containsExactlyInAnyOrderEntriesOf(Map.of(20L, 9L));
    }

    @Test
    void rejectsLegacyOrMalformedVersions() {
        assertThatThrownBy(() -> AuthorizationVersionVector.parse("v12:data-scope-cache-v4"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AuthorizationVersionVector.parse("authz-v1;s=bad:b=1;r=;d="))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
