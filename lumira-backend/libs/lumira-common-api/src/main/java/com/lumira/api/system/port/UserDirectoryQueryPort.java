package com.lumira.api.system.port;

import com.lumira.api.system.SystemUserDirectoryEntryDTO;
import java.util.List;

/** Read-only local user directory contract for integration adapters. */
public interface UserDirectoryQueryPort {
    List<SystemUserDirectoryEntryDTO> enabledUserDirectory();
}
