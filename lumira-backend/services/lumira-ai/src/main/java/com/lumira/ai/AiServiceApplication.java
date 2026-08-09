package com.lumira.ai;

/**
 * Compatibility marker retained for callers that referenced the former
 * standalone launcher.
 *
 * <p>AI is assembled into {@code lumira-server} through
 * {@code AiControlPlaneAssemblyConfiguration}.  It must not be launched as a
 * fourth production runtime.</p>
 */
@Deprecated(since = "0.1.0", forRemoval = false)
public final class AiServiceApplication {

    private AiServiceApplication() {
    }

    public static void main(String[] args) {
        throw new IllegalStateException(
                "ai-service is an Admin control-plane module; start lumira-server instead of a standalone AI runtime"
        );
    }
}
