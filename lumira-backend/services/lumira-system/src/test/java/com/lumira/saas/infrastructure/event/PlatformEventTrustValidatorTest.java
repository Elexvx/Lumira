package com.lumira.saas.infrastructure.event;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlatformEventTrustValidatorTest {

    @Test
    void acceptsOnlyTheExplicitlyVersionedIamContractEventTypesOutsideLegacyUppercaseWireNames() {
        assertThat(PlatformEventTrustValidator.requireTrustedEventType(PlatformEventTypes.IAM_ROLE_CHANGED))
                .isEqualTo(PlatformEventTypes.IAM_ROLE_CHANGED);
        assertThat(PlatformEventTrustValidator.requireTrustedEventType(PlatformEventTypes.IAM_PERMISSION_POLICY_CHANGED))
                .isEqualTo(PlatformEventTypes.IAM_PERMISSION_POLICY_CHANGED);
    }

    @Test
    void rejectsAnArbitraryCamelCasePlatformEventType() {
        assertThatThrownBy(() -> PlatformEventTrustValidator.requireTrustedEventType("UnexpectedEvent"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("platform eventType is invalid");
    }
}
