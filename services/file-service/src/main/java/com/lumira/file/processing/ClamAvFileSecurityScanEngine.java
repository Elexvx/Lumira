package com.lumira.file.processing;

import com.lumira.file.config.FileSecurityScanProperties;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class ClamAvFileSecurityScanEngine implements FileSecurityScanEngine {

    public static final String ENGINE_NAME = "CLAMAV_INSTREAM";
    private static final int CHUNK_BYTES = 8192;

    private final FileSecurityScanProperties properties;

    public ClamAvFileSecurityScanEngine(FileSecurityScanProperties properties) {
        this.properties = properties;
    }

    @Override
    public String engineName() {
        return ENGINE_NAME;
    }

    @Override
    public SecurityScanEngineResult scan(FileSecurityScanRequest request) {
        if (!Files.isRegularFile(request.sourcePath())) {
            throw new IllegalStateException("Security scan source file is unavailable: " + request.sourcePath());
        }
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(properties.getClamavHost(), properties.getClamavPort()), properties.getTimeoutMillis());
            socket.setSoTimeout(properties.getTimeoutMillis());
            DataOutputStream output = new DataOutputStream(socket.getOutputStream());
            output.write("zINSTREAM\0".getBytes(StandardCharsets.US_ASCII));
            long scannedBytes = streamFile(request, output);
            output.writeInt(0);
            output.flush();

            String response = readResponse(socket.getInputStream());
            String normalized = response.toUpperCase(Locale.ROOT);
            if (normalized.contains("FOUND")) {
                return new SecurityScanEngineResult(engineName(), FileSecurityScanProcessor.VERDICT_THREAT_DETECTED,
                        sanitizeReason(response), scannedBytes);
            }
            if (normalized.contains("OK")) {
                return new SecurityScanEngineResult(engineName(), FileSecurityScanProcessor.VERDICT_CLEAN, "", scannedBytes);
            }
            throw new IllegalStateException("Unexpected ClamAV response: " + response);
        } catch (IOException exception) {
            throw new IllegalStateException("ClamAV security scan failed: " + request.sourcePath(), exception);
        }
    }

    private long streamFile(FileSecurityScanRequest request, DataOutputStream output) throws IOException {
        byte[] buffer = new byte[CHUNK_BYTES];
        long scannedBytes = 0L;
        try (InputStream input = new BufferedInputStream(Files.newInputStream(request.sourcePath()))) {
            int read;
            while ((read = input.read(buffer)) >= 0) {
                scannedBytes += read;
                output.writeInt(read);
                output.write(buffer, 0, read);
            }
        }
        return scannedBytes;
    }

    private String readResponse(InputStream inputStream) throws IOException {
        ByteArrayOutputStream response = new ByteArrayOutputStream();
        int value;
        while ((value = inputStream.read()) >= 0) {
            if (value == 0 || value == '\n') {
                break;
            }
            response.write(value);
        }
        return response.toString(StandardCharsets.UTF_8).trim();
    }

    private String sanitizeReason(String response) {
        String trimmed = response == null ? "" : response.trim();
        int foundIndex = trimmed.toUpperCase(Locale.ROOT).indexOf(" FOUND");
        if (foundIndex > 0) {
            int colonIndex = trimmed.lastIndexOf(':', foundIndex);
            if (colonIndex >= 0 && colonIndex + 1 < foundIndex) {
                return "CLAMAV_" + trimmed.substring(colonIndex + 1, foundIndex).trim()
                        .replaceAll("[^A-Za-z0-9_.-]", "_");
            }
        }
        return "CLAMAV_THREAT_FOUND";
    }
}
