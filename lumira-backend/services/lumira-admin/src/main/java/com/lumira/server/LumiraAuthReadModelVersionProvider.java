package com.lumira.server;

import com.lumira.auth.service.AuthReadModelVersionProvider;
import com.lumira.saas.infrastructure.readmodel.ReadModelVersionService;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class LumiraAuthReadModelVersionProvider implements AuthReadModelVersionProvider {

    private static final ReadModelVersionService.ReadModelScopeKey PUBLIC_BOOTSTRAP_SCOPE =
            new ReadModelVersionService.ReadModelScopeKey("platform", "public-bootstrap");
    private static final ReadModelVersionService.ReadModelScopeKey RUNTIME_APPEARANCE_SCOPE =
            new ReadModelVersionService.ReadModelScopeKey("platform", "runtime-appearance");
    private static final ReadModelVersionService.ReadModelScopeKey PLUGIN_BOOTSTRAP_SCOPE =
            new ReadModelVersionService.ReadModelScopeKey("plugin", "bootstrap");
    private static final ReadModelVersionService.ReadModelScopeKey PLATFORM_MENU_TREE_SCOPE =
            new ReadModelVersionService.ReadModelScopeKey("platform", "menu-tree");

    private final ReadModelVersionService readModelVersionService;

    public LumiraAuthReadModelVersionProvider(ReadModelVersionService readModelVersionService) {
        this.readModelVersionService = readModelVersionService;
    }

    @Override
    public AuthBootstrapReadModelVersions loadBootstrapVersions() {
        Map<ReadModelVersionService.ReadModelScopeKey, Long> versions = readModelVersionService.currentVersions(
                List.of(
                        PUBLIC_BOOTSTRAP_SCOPE,
                        RUNTIME_APPEARANCE_SCOPE,
                        PLUGIN_BOOTSTRAP_SCOPE,
                        PLATFORM_MENU_TREE_SCOPE
                )
        );
        return new AuthBootstrapReadModelVersions(
                versionOf(versions, PUBLIC_BOOTSTRAP_SCOPE),
                versionOf(versions, RUNTIME_APPEARANCE_SCOPE),
                versionOf(versions, PLUGIN_BOOTSTRAP_SCOPE),
                versionOf(versions, PLATFORM_MENU_TREE_SCOPE)
        );
    }

    private Long versionOf(
            Map<ReadModelVersionService.ReadModelScopeKey, Long> versions,
            ReadModelVersionService.ReadModelScopeKey scopeKey
    ) {
        if (versions == null || scopeKey == null) {
            return 0L;
        }
        Long version = versions.get(scopeKey);
        return version == null ? 0L : version;
    }
}
