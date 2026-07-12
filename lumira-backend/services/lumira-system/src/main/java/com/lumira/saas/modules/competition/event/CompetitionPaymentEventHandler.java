package com.lumira.saas.modules.competition.event;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.saas.infrastructure.event.EventConsumptionGuard;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Locale;

/** Owns the competition-side reaction to a successfully paid payment order. */
@Service
public class CompetitionPaymentEventHandler {
    static final String CONSUMER_NAME = "competition-payment-order-paid-v1";

    private final JdbcTemplate jdbcTemplate;
    private final EventConsumptionGuard consumptionGuard;

    public CompetitionPaymentEventHandler(JdbcTemplate jdbcTemplate, EventConsumptionGuard consumptionGuard) {
        this.jdbcTemplate = jdbcTemplate;
        this.consumptionGuard = consumptionGuard;
    }

    public boolean handleOrderPaid(
            String eventId,
            String orderNo,
            Long registrationId,
            Long ownerUserId,
            String ownerUserUuid
    ) {
        requireText(eventId, "eventId");
        requireText(orderNo, "orderNo");
        requirePositive(registrationId, "registrationId");
        requirePositive(ownerUserId, "ownerUserId");
        requireText(ownerUserUuid, "ownerUserUuid");

        return consumptionGuard.executeOnce(
                new EventConsumptionGuard.EventIdentity(
                        CONSUMER_NAME,
                        eventId,
                        "PAYMENT_ORDER_PAID",
                        "payment",
                        orderNo
                ),
                () -> confirmRegistration(orderNo.trim(), registrationId, ownerUserId, ownerUserUuid.trim())
        );
    }

    private void confirmRegistration(String orderNo, Long registrationId, Long ownerUserId, String ownerUserUuid) {
        RegistrationPaymentTarget target = jdbcTemplate.queryForObject(
                """
                        select cr.competition_id, c.code, cr.participant_no
                        from competition_registration cr
                        join aiadc_competition c on c.id = cr.competition_id and c.deleted = 0
                        where cr.id = ? and cr.payment_order_no = ?
                          and cr.owner_user_id = ? and cr.owner_user_uuid = ?
                          and cr.deleted = 0
                        limit 1
                        """,
                (rs, rowNum) -> new RegistrationPaymentTarget(
                        rs.getLong("competition_id"),
                        rs.getString("code"),
                        rs.getString("participant_no")
                ),
                registrationId,
                orderNo,
                ownerUserId,
                ownerUserUuid
        );
        if (target == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Competition registration for payment order does not exist");
        }
        if (StringUtils.hasText(target.participantNo())) {
            return;
        }
        Long next = jdbcTemplate.queryForObject(
                "select count(1) + 1 from competition_registration where competition_id = ? and participant_no is not null and deleted = 0",
                Long.class,
                target.competitionId()
        );
        String participantNo = target.competitionCode().trim().toUpperCase(Locale.ROOT)
                + "-" + String.format("%04d", next == null ? 1L : next);
        int updated = jdbcTemplate.update(
                """
                        update competition_registration
                        set status = 'CONFIRMED', participant_no = ?, updated_at = current_timestamp(6)
                        where id = ? and payment_order_no = ?
                          and owner_user_id = ? and owner_user_uuid = ?
                          and participant_no is null and deleted = 0
                        """,
                participantNo,
                registrationId,
                orderNo,
                ownerUserId,
                ownerUserUuid
        );
        if (updated != 1) {
            throw new BizException(ErrorCode.BIZ_ERROR, "Competition registration state changed while consuming payment event");
        }
    }

    private void requirePositive(Long value, String name) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    private void requireText(String value, String name) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }

    private record RegistrationPaymentTarget(Long competitionId, String competitionCode, String participantNo) {
    }
}
