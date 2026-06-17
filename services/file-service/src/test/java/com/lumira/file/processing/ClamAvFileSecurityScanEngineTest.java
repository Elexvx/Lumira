package com.lumira.file.processing;

import static org.assertj.core.api.Assertions.assertThat;

import com.lumira.file.config.FileSecurityScanProperties;
import java.io.DataInputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ClamAvFileSecurityScanEngineTest {

    @TempDir
    Path tempDir;

    @Test
    void scan_shouldReturnCleanWhenClamAvRespondsOk() throws Exception {
        Path source = writeSource("hello Lumira");
        try (FakeClamAvServer server = FakeClamAvServer.start("stream: OK\0")) {
            ClamAvFileSecurityScanEngine engine = engine(server.port());

            SecurityScanEngineResult result = engine.scan(new FileSecurityScanRequest(3001L, source, "txt"));

            assertThat(result.engine()).isEqualTo(ClamAvFileSecurityScanEngine.ENGINE_NAME);
            assertThat(result.verdict()).isEqualTo(FileSecurityScanProcessor.VERDICT_CLEAN);
            assertThat(result.scannedBytes()).isEqualTo("hello Lumira".getBytes(StandardCharsets.ISO_8859_1).length);
            assertThat(server.scannedBytes()).isEqualTo(result.scannedBytes());
        }
    }

    @Test
    void scan_shouldReturnThreatWhenClamAvRespondsFound() throws Exception {
        Path source = writeSource("virus placeholder");
        try (FakeClamAvServer server = FakeClamAvServer.start("stream: Eicar-Test-Signature FOUND\0")) {
            ClamAvFileSecurityScanEngine engine = engine(server.port());

            SecurityScanEngineResult result = engine.scan(new FileSecurityScanRequest(3001L, source, "txt"));

            assertThat(result.verdict()).isEqualTo(FileSecurityScanProcessor.VERDICT_THREAT_DETECTED);
            assertThat(result.reason()).isEqualTo("CLAMAV_Eicar-Test-Signature");
            assertThat(server.scannedBytes()).isEqualTo(result.scannedBytes());
        }
    }

    private Path writeSource(String content) throws Exception {
        Path source = tempDir.resolve("source.txt");
        Files.writeString(source, content, StandardCharsets.ISO_8859_1);
        return source;
    }

    private ClamAvFileSecurityScanEngine engine(int port) {
        FileSecurityScanProperties properties = new FileSecurityScanProperties();
        properties.setClamavPort(port);
        properties.setTimeoutMillis(1000);
        return new ClamAvFileSecurityScanEngine(properties);
    }

    private static final class FakeClamAvServer implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final ExecutorService executorService;
        private final Future<Long> scannedBytes;

        private FakeClamAvServer(ServerSocket serverSocket, ExecutorService executorService, Future<Long> scannedBytes) {
            this.serverSocket = serverSocket;
            this.executorService = executorService;
            this.scannedBytes = scannedBytes;
        }

        static FakeClamAvServer start(String response) throws Exception {
            ServerSocket serverSocket = new ServerSocket(0);
            ExecutorService executorService = Executors.newSingleThreadExecutor();
            Future<Long> scannedBytes = executorService.submit(() -> handleOne(serverSocket, response));
            return new FakeClamAvServer(serverSocket, executorService, scannedBytes);
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        long scannedBytes() throws Exception {
            return scannedBytes.get();
        }

        @Override
        public void close() throws Exception {
            serverSocket.close();
            executorService.shutdownNow();
        }

        private static long handleOne(ServerSocket serverSocket, String response) throws Exception {
            try (Socket socket = serverSocket.accept()) {
                DataInputStream input = new DataInputStream(socket.getInputStream());
                readCommand(input);
                long totalBytes = 0L;
                while (true) {
                    int chunkLength = input.readInt();
                    if (chunkLength == 0) {
                        break;
                    }
                    totalBytes += chunkLength;
                    input.readNBytes(chunkLength);
                }
                OutputStream output = socket.getOutputStream();
                output.write(response.getBytes(StandardCharsets.UTF_8));
                output.flush();
                return totalBytes;
            }
        }

        private static void readCommand(DataInputStream input) throws Exception {
            int value;
            do {
                value = input.read();
            } while (value > 0);
        }
    }
}
