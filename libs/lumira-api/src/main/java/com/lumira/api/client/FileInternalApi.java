package com.lumira.api.client;

import com.lumira.api.file.FileObjectDTO;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

public interface FileInternalApi {

    
    FileObjectDTO uploadImage(
             MultipartFile file,
             String category,
             String remark
    );

    
    FileObjectDTO uploadDocument(
             MultipartFile file,
             String category,
             String tags,
             String remark
    );
}
