package com.lumira.file.controller;

import com.lumira.api.file.FileObjectDTO;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.file.app.FileManagementAppService;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/internal/files")
public class InternalFileController implements com.lumira.api.client.FileInternalApi {

    private final FileManagementAppService fileManagementAppService;
    private final SecurityContextFacade securityContextFacade;

    public InternalFileController(FileManagementAppService fileManagementAppService, SecurityContextFacade securityContextFacade) {
        this.fileManagementAppService = fileManagementAppService;
        this.securityContextFacade = securityContextFacade;
    }

    @PostMapping(value = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public FileObjectDTO uploadImage(
            @RequestParam("file") @NotNull MultipartFile file,
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "remark", required = false) String remark
    ) {
        return fileManagementAppService.uploadImage(securityContextFacade.getCurrentUser(), file, category, remark);
    }

    @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public FileObjectDTO uploadDocument(
            @RequestParam("file") @NotNull MultipartFile file,
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "tags", required = false) String tags,
            @RequestParam(name = "remark", required = false) String remark
    ) {
        return fileManagementAppService.uploadDocument(securityContextFacade.getCurrentUser(), file, category, tags, remark);
    }
}
