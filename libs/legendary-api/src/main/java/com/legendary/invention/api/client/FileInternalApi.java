package com.legendary.invention.api.client;

import com.legendary.invention.api.file.FileObjectDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

@FeignClient(name = "file-service", contextId = "fileInternalApi", url = "${FILE_SERVICE_BASE_URL:}", path = "/internal/files")
public interface FileInternalApi {

    @PostMapping(value = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    FileObjectDTO uploadImage(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "remark", required = false) String remark
    );

    @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    FileObjectDTO uploadDocument(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "tags", required = false) String tags,
            @RequestParam(value = "remark", required = false) String remark
    );
}
