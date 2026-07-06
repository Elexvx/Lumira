package com.lumira.asyncruntime;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.file.app.FileManagementAppService;
import com.lumira.file.service.FileInternalApiService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Service
@Primary
class LocalFileInternalApiAdapter extends FileInternalApiService {

    LocalFileInternalApiAdapter(
            @Lazy FileManagementAppService fileManagementAppService,
            SecurityContextFacade securityContextFacade,
            ObjectProvider<SystemInternalApi> systemInternalApi
    ) {
        super(fileManagementAppService, securityContextFacade, systemInternalApi);
    }
}
