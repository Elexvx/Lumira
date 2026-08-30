package com.lumira.asyncruntime;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@ConditionalOnBean(AsyncRuntimeDrainCoordinator.class)
@RequestMapping("/internal/runtime")
public class AsyncRuntimeControlController {
    private final AsyncRuntimeDrainCoordinator drain;
    private final PaymentEventStreamConsumer paymentConsumer;
    private final String controlToken;
    private final String releaseId;
    private final String commit;
    private final int eventReadMin;
    private final int eventReadMax;
    private final int eventWriteVersion;

    public AsyncRuntimeControlController(
            AsyncRuntimeDrainCoordinator drain,
            ObjectProvider<PaymentEventStreamConsumer> paymentConsumer,
            @Value("${lumira.runtime.control-token:${LUMIRA_RUNTIME_CONTROL_TOKEN:}}") String controlToken,
            @Value("${lumira.release-id:${LUMIRA_RELEASE_ID:unknown}}") String releaseId,
            @Value("${git.commit:${GIT_COMMIT:unknown}}") String commit,
            @Value("${lumira.event.schema.read-min:${LUMIRA_EVENT_SCHEMA_READ_MIN:1}}") int eventReadMin,
            @Value("${lumira.event.schema.read-max:${LUMIRA_EVENT_SCHEMA_READ_MAX:1}}") int eventReadMax,
            @Value("${lumira.event.schema.write-version:${LUMIRA_EVENT_SCHEMA_WRITE_VERSION:1}}") int eventWriteVersion
    ) {
        this.drain = drain;
        this.paymentConsumer = paymentConsumer.getIfAvailable();
        this.controlToken = controlToken == null ? "" : controlToken;
        this.releaseId = releaseId;
        this.commit = commit;
        this.eventReadMin = eventReadMin;
        this.eventReadMax = eventReadMax;
        this.eventWriteVersion = eventWriteVersion;
    }

    @PostMapping("/quiesce")
    public Map<String, Object> quiesce(@RequestHeader("X-Lumira-Runtime-Control-Token") String token) {
        requireToken(token);
        drain.quiesce();
        if (paymentConsumer != null) paymentConsumer.quiesce();
        return statusBody();
    }

    @PostMapping("/resume")
    public Map<String, Object> resume(@RequestHeader("X-Lumira-Runtime-Control-Token") String token) {
        requireToken(token);
        if (paymentConsumer != null) paymentConsumer.resume();
        drain.resume();
        return statusBody();
    }

    @GetMapping("/drain-status")
    public Map<String, Object> drainStatus(@RequestHeader("X-Lumira-Runtime-Control-Token") String token) {
        requireToken(token);
        return statusBody();
    }

    @GetMapping("/version")
    public Map<String, Object> version(@RequestHeader("X-Lumira-Runtime-Control-Token") String token) {
        requireToken(token);
        return Map.of("serviceName", "lumira-async", "releaseId", releaseId, "commit", commit,
                "eventReadMin", eventReadMin, "eventReadMax", eventReadMax, "eventWriteVersion", eventWriteVersion);
    }

    @GetMapping("/health")
    public Map<String, Object> health(@RequestHeader("X-Lumira-Runtime-Control-Token") String token) {
        requireToken(token);
        return Map.of("status", paymentConsumer == null || paymentConsumer.isRunning() || !drain.snapshot().acceptingNewWork() ? "UP" : "DOWN",
                "releaseId", releaseId, "commit", commit, "checkedAt", Instant.now().toString());
    }

    private Map<String, Object> statusBody() {
        var snapshot = drain.snapshot();
        long pending = paymentConsumer == null ? 0L : paymentConsumer.pendingMessageCount();
        return Map.ofEntries(
                Map.entry("acceptingNewWork", snapshot.acceptingNewWork()),
                Map.entry("inflightTasks", snapshot.inflightTasks()),
                Map.entry("leasedTasks", snapshot.inflightTasks()),
                Map.entry("unackedMessages", pending),
                Map.entry("oldestInflightAgeSeconds", snapshot.oldestInflightAgeSeconds()),
                Map.entry("safeToStop", snapshot.safeToStop()),
                Map.entry("releaseId", releaseId),
                Map.entry("commit", commit),
                Map.entry("eventReadMin", eventReadMin),
                Map.entry("eventReadMax", eventReadMax),
                Map.entry("eventWriteVersion", eventWriteVersion)
        );
    }

    private void requireToken(String provided) {
        byte[] expectedBytes = controlToken.getBytes(StandardCharsets.UTF_8);
        byte[] providedBytes = String.valueOf(provided).getBytes(StandardCharsets.UTF_8);
        if (expectedBytes.length < 24 || !MessageDigest.isEqual(expectedBytes, providedBytes)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid runtime control token");
        }
    }
}
