package com.lumira.api.client;

import com.lumira.api.file.FileObjectDTO;
import org.springframework.web.multipart.MultipartFile;

public interface FileInternalApi {

    
    FileObjectDTO uploadImage(
             MultipartFile file,
             String category,
             String remark
    );

    
    default FileObjectDTO uploadDocument(
             MultipartFile file,
             String category,
             String tags,
             String remark
    ) {
        return uploadDocument(file, category, tags, remark, null);
    }

    FileObjectDTO uploadDocument(
             MultipartFile file,
             String category,
             String tags,
             String remark,
             String bucket
    );
}
