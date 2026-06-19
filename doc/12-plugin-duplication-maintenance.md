# Plugin duplication maintenance

> This document records the current maintenance boundary while plugin capability is transitioning from `system-service` to `plugin-service`.

## Current State

Plugin runtime code currently exists in two application modules:

- `services/lumira-system/src/main/java/com/lumira/saas/modules/plugin/`
- `services/lumira-plugin/src/main/java/com/lumira/saas/modules/plugin/`

This duplication is intentional during the microservice migration period. Do not delete either side until routing, persistence, permissions, plugin gateway, and startup ownership are fully moved to one runtime.

## Shared Files That Must Stay Identical

The following files are treated as shared contracts for now and are guarded by `PluginDuplicateContractTest`:

- `dto/PluginDTO.java`
- `entity/PluginEntities.java`
- `mapper/PluginRowMappers.java`
- `registry/PluginRuntimeDescriptor.java`
- `runtime/PluginSecurityPropertiesValidator.java`
- `runtime/runtime/PluginRuntimeContext.java`
- `runtime/runtime/PluginRuntimeModels.java`
- `runtime/spi/PluginBootstrap.java`
- `runtime/spi/PluginHealthIndicator.java`
- `runtime/spi/PluginHttpHandler.java`
- `runtime/spi/PluginMenuProvider.java`
- `runtime/spi/PluginPermissionProvider.java`
- `runtime/spi/PluginScheduledTaskProvider.java`
- `runtime/spi/PluginSecondFactorProvider.java`
- `service/PluginPersistenceService.java`
- `vo/PluginVO.java`

If any of these files must change, change both copies in the same patch or extract the contract into `libs/` first.

## Files That Already Diverged

The following files have service-specific behavior and should not be force-synced without a focused migration review:

- `app/PluginManagementAppService.java`
- `controller/PluginManagementController.java`
- `gateway/PluginGatewayController.java`
- `loader/PluginArtifactLoader.java`
- `loader/PluginRuntimeLoader.java`
- `registry/PluginRegistry.java`
- `runtime/PluginProperties.java`
- `service/PluginMigrationService.java`
- `service/PluginSemver.java`

## Guardrail

Run this check before changing plugin runtime contracts:

```bash
mvn -pl services/lumira-plugin -am -Dtest=PluginDuplicateContractTest -Dsurefire.failIfNoSpecifiedTests=false test
```

## Shared Foundation Already Extracted

The current duplication boundary no longer includes generic platform plumbing:

- JWT parsing and token claim models belong in `libs/lumira-common-security`.
- Trace ID propagation, MDC cleanup, and shared CORS properties belong in `libs/lumira-common-web`.
- The platform tenant identifier belongs in `libs/lumira-common-core`.

Plugin-specific security filters may still stay in `plugin-service`, but they should consume the shared foundation instead of copying JWT parsing or trace/CORS code back into the plugin module.

## Extraction Path

The safest extraction order is:

1. Move SPI and runtime model contracts into a shared library package.
2. Move DTO, VO, entity, and row mapper types after both applications compile against the shared package.
3. Keep app service, controller, loader, registry, gateway, and migration logic application-owned until one runtime becomes authoritative.
