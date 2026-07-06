package com.lumira.api.client;

import com.lumira.api.auth.CurrentUserDTO;

public interface AuthInternalApi {

    CurrentUserDTO currentUser(
            String sessionId,
            Long expectedUserId,
            String expectedUserUuid,
            Integer expectedSessionVersion,
            String expectedPermissionsVersion,
            Long expectedSimulatedRoleId
    );

}
