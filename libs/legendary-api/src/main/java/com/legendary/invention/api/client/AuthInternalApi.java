package com.legendary.invention.api.client;

import com.legendary.invention.api.auth.CurrentUserDTO;

public interface AuthInternalApi {

    
    CurrentUserDTO currentUser( String sessionId);

}
