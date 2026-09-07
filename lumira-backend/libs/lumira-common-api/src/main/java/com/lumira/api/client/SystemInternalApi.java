package com.lumira.api.client;

/**
 * Compatibility alias for the legacy internal-system client.
 *
 * <p>New application code must depend on the focused ports in
 * {@code com.lumira.api.system.port}. HTTP annotations live only in
 * {@link SystemInternalHttpApi}. This alias intentionally declares no methods
 * so the former God Interface cannot continue to grow. It remains available
 * only to the System assembly and the remote compatibility client while
 * existing deployments migrate to capability-specific ports.</p>
 */
@Deprecated(forRemoval = false)
public interface SystemInternalApi extends SystemInternalHttpApi {
}
