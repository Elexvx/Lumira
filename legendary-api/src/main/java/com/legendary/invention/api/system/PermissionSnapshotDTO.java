package com.legendary.invention.api.system;

import java.util.List;

public record PermissionSnapshotDTO(String version, List<String> permissions) {
}
