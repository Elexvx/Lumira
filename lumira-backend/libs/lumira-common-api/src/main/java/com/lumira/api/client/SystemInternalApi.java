package com.lumira.api.client;

/**
 * Compatibility alias for the legacy internal-system client.
 *
 * <p>New application code must depend on the focused ports in
 * {@code com.lumira.api.system.port}. HTTP annotations live only in
 * {@link SystemInternalHttpApi}. This alias intentionally declares no methods
 * so the former God Interface cannot continue to grow.</p>
 */
@Deprecated(forRemoval = false)
public interface SystemInternalApi extends SystemInternalHttpApi {
}
