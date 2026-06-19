package com.lumira.file.processing;

public interface FileOcrEngine {

    String engineName();

    OcrEngineResult extract(FileOcrRequest request);
}
