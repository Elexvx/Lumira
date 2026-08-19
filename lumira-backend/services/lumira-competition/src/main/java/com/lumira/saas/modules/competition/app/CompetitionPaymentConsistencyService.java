package com.lumira.saas.modules.competition.app;

import com.lumira.api.client.PaymentInternalApi;
import com.lumira.api.payment.PaymentOrderDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.saas.modules.competition.repository.RegistrationQueryRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Detects the cross-owner invariant where a paid order has not yet confirmed its competition
 * registration. Competition reads only its own registration rows and asks Payment through the
 * internal API; recovery replays the durable Payment event instead of editing either owner table.
 */
@Service
public class CompetitionPaymentConsistencyService {
    private static final Set<String> PAID_STATUSES = Set.of("PAID", "SUCCESS", "SETTLED");
    private static final int MAX_SAMPLES = 20;
    private static final int MAX_SCAN_LIMIT = 500;

    private final RegistrationQueryRepository registrationQueryRepository;
    private final ObjectProvider<PaymentInternalApi> paymentInternalApiProvider;
    private final Duration gracePeriod;
    private final int scanLimit;
    private final AtomicLong candidateGauge = new AtomicLong();
    private final AtomicLong mismatchGauge = new AtomicLong();
    private final AtomicLong dependencyFailureGauge = new AtomicLong();
    private volatile Snapshot lastSnapshot = Snapshot.empty();

    @Autowired
    public CompetitionPaymentConsistencyService(
            RegistrationQueryRepository registrationQueryRepository,
            ObjectProvider<PaymentInternalApi> paymentInternalApiProvider,
            MeterRegistry meterRegistry,
            @Value("${lumira.competition.payment-consistency.grace-seconds:60}") long graceSeconds,
            @Value("${lumira.competition.payment-consistency.scan-limit:100}") int scanLimit
    ) {
        this.registrationQueryRepository = registrationQueryRepository;
        this.paymentInternalApiProvider = paymentInternalApiProvider;
        this.gracePeriod = Duration.ofSeconds(Math.max(1L, graceSeconds));
        this.scanLimit = Math.max(1, Math.min(scanLimit, MAX_SCAN_LIMIT));
        Gauge.builder("competition.payment.consistency.candidates", candidateGauge, AtomicLong::get)
                .description("Stale pending-payment registrations checked against Payment")
                .register(meterRegistry);
        Gauge.builder("competition.payment.paid.registration.pending", mismatchGauge, AtomicLong::get)
                .description("Paid payment orders whose competition registrations remain pending")
                .register(meterRegistry);
        Gauge.builder("competition.payment.consistency.dependency.failures", dependencyFailureGauge, AtomicLong::get)
                .description("Payment consistency checks that could not query the Payment owner")
                .register(meterRegistry);
    }

    @Scheduled(
            fixedDelayString = "${lumira.competition.payment-consistency.interval-ms:60000}",
            initialDelayString = "${lumira.competition.payment-consistency.initial-delay-ms:60000}"
    )
    public void refreshScheduled() {
        try {
            refresh();
        } catch (RuntimeException ignored) {
            dependencyFailureGauge.incrementAndGet();
        }
    }

    public synchronized Snapshot refresh() {
        LocalDateTime checkedAt = LocalDateTime.now();
        List<RegistrationQueryRepository.PendingPaymentCandidate> candidates =
                registrationQueryRepository.findStalePendingPaymentCandidates(
                        checkedAt.minus(gracePeriod),
                        scanLimit
                );
        PaymentInternalApi paymentApi = paymentInternalApiProvider == null
                ? null : paymentInternalApiProvider.getIfAvailable();
        List<Mismatch> mismatches = new ArrayList<>();
        int dependencyFailures = 0;

        if (paymentApi == null) {
            dependencyFailures = candidates.size();
        } else {
            for (RegistrationQueryRepository.PendingPaymentCandidate candidate : candidates) {
                try {
                    PaymentOrderDTO order = paymentApi.getOrder(
                            candidate.ownerUserId(),
                            candidate.ownerUserUuid(),
                            candidate.paymentOrderNo()
                    );
                    if (order != null && isPaid(order.status())) {
                        mismatches.add(toMismatch(candidate, order, checkedAt));
                    }
                } catch (RuntimeException ignored) {
                    dependencyFailures++;
                }
            }
        }

        candidateGauge.set(candidates.size());
        mismatchGauge.set(mismatches.size());
        dependencyFailureGauge.set(dependencyFailures);
        lastSnapshot = new Snapshot(
                dependencyFailures == 0 ? "UP" : "DEGRADED",
                checkedAt,
                gracePeriod.toSeconds(),
                candidates.size(),
                mismatches.size(),
                dependencyFailures,
                mismatches.stream().limit(MAX_SAMPLES).toList()
        );
        return lastSnapshot;
    }

    public Snapshot currentSnapshot() {
        return lastSnapshot;
    }

    public ReplayResult replayPaidRegistrationEvent(String orderNo) {
        String normalizedOrderNo = requireOrderNo(orderNo);
        RegistrationQueryRepository.PendingPaymentCandidate candidate =
                registrationQueryRepository.findPendingPaymentCandidateByOrder(normalizedOrderNo);
        if (candidate == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Pending-payment registration does not exist");
        }
        PaymentInternalApi paymentApi = paymentInternalApiProvider == null
                ? null : paymentInternalApiProvider.getIfAvailable();
        if (paymentApi == null) {
            throw new BizException(ErrorCode.DEPENDENCY_UNAVAILABLE, "Payment owner is unavailable");
        }
        PaymentOrderDTO order = paymentApi.getOrder(
                candidate.ownerUserId(),
                candidate.ownerUserUuid(),
                candidate.paymentOrderNo()
        );
        if (order == null || !isPaid(order.status())) {
            throw new BizException(ErrorCode.BIZ_ERROR, "Payment order is not paid; replay was refused");
        }
        boolean replayed = paymentApi.replayPaidOrderEvent(normalizedOrderNo);
        if (!replayed) {
            throw new BizException(ErrorCode.BIZ_ERROR, "Paid-order event is unavailable or already being replayed");
        }
        return new ReplayResult(
                candidate.registrationId(),
                candidate.registrationNo(),
                normalizedOrderNo,
                "REPLAYED"
        );
    }

    private Mismatch toMismatch(
            RegistrationQueryRepository.PendingPaymentCandidate candidate,
            PaymentOrderDTO order,
            LocalDateTime checkedAt
    ) {
        long ageSeconds = candidate.updatedAt() == null
                ? 0L : Math.max(0L, Duration.between(candidate.updatedAt(), checkedAt).toSeconds());
        return new Mismatch(
                candidate.registrationId(),
                candidate.registrationNo(),
                candidate.competitionId(),
                candidate.competitionTitle(),
                candidate.paymentOrderNo(),
                order.status(),
                candidate.updatedAt(),
                ageSeconds
        );
    }

    private boolean isPaid(String status) {
        return StringUtils.hasText(status) && PAID_STATUSES.contains(status.trim().toUpperCase(Locale.ROOT));
    }

    private String requireOrderNo(String orderNo) {
        if (!StringUtils.hasText(orderNo)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "Payment order number is required");
        }
        String normalized = orderNo.trim();
        if (normalized.length() > 64) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "Payment order number is too long");
        }
        return normalized;
    }

    public record Snapshot(
            String status,
            LocalDateTime checkedAt,
            long graceSeconds,
            long candidatesChecked,
            long mismatchCount,
            long dependencyFailureCount,
            List<Mismatch> mismatches
    ) {
        static Snapshot empty() {
            return new Snapshot("NOT_CHECKED", null, 60L, 0L, 0L, 0L, List.of());
        }
    }

    public record Mismatch(
            Long registrationId,
            String registrationNo,
            Long competitionId,
            String competitionTitle,
            String paymentOrderNo,
            String paymentStatus,
            LocalDateTime registrationUpdatedAt,
            long ageSeconds
    ) {
    }

    public record ReplayResult(
            Long registrationId,
            String registrationNo,
            String paymentOrderNo,
            String status
    ) {
    }
}
