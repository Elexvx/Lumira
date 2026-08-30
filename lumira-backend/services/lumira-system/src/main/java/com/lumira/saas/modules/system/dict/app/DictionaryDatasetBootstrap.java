package com.lumira.saas.modules.system.dict.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.saas.modules.system.dict.repository.DictionaryDatasetRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "lumira.dictionary-datasets.bootstrap-enabled", havingValue = "true")
public class DictionaryDatasetBootstrap implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DictionaryDatasetBootstrap.class);
    private static final String DATABASE_LOCK = "lumira:dictionary-dataset-bootstrap:v1";

    private final Path datasetRoot;
    private final ObjectMapper objectMapper;
    private final DictionaryDatasetRepository repository;
    private final DictionaryImportService importService;
    private volatile boolean ready;

    public DictionaryDatasetBootstrap(
            @Value("${lumira.dictionary-datasets.root:reference-data/dictionaries}") String datasetRoot,
            ObjectMapper objectMapper,
            DictionaryDatasetRepository repository,
            DictionaryImportService importService
    ) {
        this.datasetRoot = resolveDatasetRoot(datasetRoot);
        this.objectMapper = objectMapper;
        this.repository = repository;
        this.importService = importService;
    }

    private static Path resolveDatasetRoot(String configuredRoot) {
        Path configured = Path.of(configuredRoot);
        if (configured.isAbsolute()) {
            return configured.normalize();
        }
        Path current = Path.of("").toAbsolutePath().normalize();
        Path firstCandidate = current.resolve(configured).normalize();
        for (Path candidateRoot = current; candidateRoot != null; candidateRoot = candidateRoot.getParent()) {
            Path candidate = candidateRoot.resolve(configured).normalize();
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
        }
        return firstCandidate;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!Files.isDirectory(datasetRoot)) {
            throw new IllegalStateException("Dictionary dataset directory is missing: " + datasetRoot);
        }
        if (!repository.acquireInitializationLock(DATABASE_LOCK, 60)) {
            throw new IllegalStateException("Could not acquire dictionary dataset bootstrap lock");
        }
        try {
            List<Path> manifests;
            try (var paths = Files.walk(datasetRoot)) {
                manifests = paths
                        .filter(path -> path.getFileName().toString().equals("manifest.json"))
                        .sorted(Comparator.comparing(Path::toString))
                        .toList();
            }
            if (manifests.isEmpty()) {
                throw new IllegalStateException("No dictionary dataset manifests found under " + datasetRoot);
            }
            for (Path manifestPath : manifests) {
                installIfMissing(manifestPath);
            }
            ready = true;
        } finally {
            repository.releaseInitializationLock(DATABASE_LOCK);
        }
    }

    public boolean isReady() {
        return ready;
    }

    private void installIfMissing(Path manifestPath) throws Exception {
        DatasetManifest manifest = objectMapper.readValue(Files.readAllBytes(manifestPath), DatasetManifest.class);
        validateManifest(manifest, manifestPath);
        DictionaryDatasetRepository.Installation current = repository.findInstallation(manifest.datasetCode());
        if (current != null) {
            if (current.version().equals(manifest.version())
                    && current.sha256().equalsIgnoreCase(manifest.fileSha256())
                    && current.rowCount() == manifest.rowCount()) {
                log.info("Dictionary dataset {} {} is already installed", manifest.datasetCode(), manifest.version());
            } else {
                log.warn("Dictionary dataset {} has bundled version {} but installed version {}; automatic replacement is disabled",
                        manifest.datasetCode(), manifest.version(), current.version());
            }
            return;
        }
        if (repository.countActiveTypes(manifest.dictCode()) > 0) {
            throw new IllegalStateException("Dictionary dataset conflicts with existing dictionary: " + manifest.dictCode());
        }
        Path dataFile = manifestPath.getParent().resolve(manifest.dataFile()).normalize();
        if (!dataFile.startsWith(manifestPath.getParent()) || !Files.isRegularFile(dataFile)) {
            throw new IllegalStateException("Dictionary dataset file is missing: " + dataFile);
        }
        byte[] bytes = Files.readAllBytes(dataFile);
        importService.installBuiltInDataset(
                new DictionaryImportService.DatasetDefinition(
                        manifest.datasetCode(), manifest.dictCode(), manifest.dictName(), manifest.structureType(),
                        manifest.version(), dataFile.getFileName().toString(), manifest.fileSha256(),
                        manifest.rowCount(), manifest.remark()
                ),
                bytes
        );
        log.info("Installed dictionary dataset {} {} with {} rows", manifest.datasetCode(), manifest.version(), manifest.rowCount());
    }

    private void validateManifest(DatasetManifest manifest, Path path) {
        if (manifest == null
                || blank(manifest.datasetCode())
                || blank(manifest.dictCode())
                || blank(manifest.dictName())
                || blank(manifest.structureType())
                || blank(manifest.version())
                || blank(manifest.dataFile())
                || blank(manifest.fileSha256())
                || manifest.rowCount() <= 0) {
            throw new IllegalStateException("Invalid dictionary dataset manifest: " + path);
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    public record DatasetManifest(
            String datasetCode,
            String dictCode,
            String dictName,
            String structureType,
            String version,
            String cutoffDate,
            String sourceUrl,
            String dataFile,
            String fileSha256,
            int rowCount,
            String remark
    ) {}
}
