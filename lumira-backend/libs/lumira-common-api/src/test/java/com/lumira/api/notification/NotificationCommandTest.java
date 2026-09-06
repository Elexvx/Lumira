package com.lumira.api.notification;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class NotificationCommandTest {

    @Test
    void validatesAndNormalizesTheOwnerCommand() {
        NotificationCommand command = new NotificationCommand(
                "event-1",
                "PAYMENT_ORDER_PAID",
                "payment",
                "ORDER-1",
                1001L,
                " user-uuid ",
                " 支付成功 ",
                " 订单 ORDER-1 已支付成功。 "
        );

        assertThat(command.targetUserUuid()).isEqualTo("user-uuid");
        assertThat(command.title()).isEqualTo("支付成功");
        assertThat(command.content()).isEqualTo("订单 ORDER-1 已支付成功。");
    }

    @Test
    void rejectsMissingTargetIdentityAndOversizedContent() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new NotificationCommand(
                        "event-1", "PAYMENT_ORDER_PAID", "payment", "ORDER-1", 0L,
                        "user-uuid", "title", "content"
                ))
                .withMessage("targetUserId must be positive");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new NotificationCommand(
                        "event-1", "PAYMENT_ORDER_PAID", "payment", "ORDER-1", 1001L,
                        "user-uuid", "title", "x".repeat(8_001)
                ))
                .withMessage("content exceeds 8000 characters");
    }
}
