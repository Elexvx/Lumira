package com.lumira.file.processing;

public interface FileSecurityScanEngine {

    String engineName();

    SecurityScanEngineResult scan(FileSecurityScanRequest request);
}
