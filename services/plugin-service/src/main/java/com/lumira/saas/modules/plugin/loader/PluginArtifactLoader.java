package com.lumira.saas.modules.plugin.loader;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.saas.modules.plugin.dto.PluginDTO;
import com.lumira.saas.modules.plugin.runtime.PluginProperties;
import com.lumira.saas.modules.plugin.service.PluginSemver;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class PluginArtifactLoader {

    private static final TypeReference<LinkedHashMap<String, String>> CHECKSUM_TYPE = new TypeReference<>() {
    };
    private static final String CHECKSUM_ALGORITHM = "SHA-256";
    private static final long MAX_EXTRACTED_BYTES = 200L * 1024L * 1024L;
    private static final long MAX_ENTRY_BYTES = 120L * 1024L * 1024L;
    private static final int MAX_EXTRACTED_FILES = 500;
    private static final Pattern SAFE_PLUGIN_CODE = Pattern.compile("^[A-Za-z][A-Za-z0-9_-]{0,63}$");
    private static final Set<String> BLOCKED_ARCHIVE_EXTENSIONS = Set.of(".zip", ".7z", ".rar", ".tar", ".gz", ".bz2", ".xz");
    private static final Set<String> ALLOWED_FILE_EXTENSIONS = Set.of(
            ".json", ".sig", ".jar", ".js", ".mjs", ".cjs", ".css", ".map",
            ".png", ".jpg", ".jpeg", ".gif", ".webp", ".svg", ".ico", ".woff", ".woff2", ".ttf", ".txt", ".md"
    );
    private static final List<String> REQUIRED_PATHS = List.of(
            "plugin.json",
            "checksums.json",
            "signature.sig",
            "backend/plugin.jar",
            "frontend/manifest.json"
    );

    private final ObjectMapper objectMapper;
    private final PluginSemver pluginSemver;
    private final PluginProperties pluginProperties;

    public PluginArtifactLoader(ObjectMapper objectMapper, PluginSemver pluginSemver, PluginProperties pluginProperties) {
        this.objectMapper = objectMapper;
        this.pluginSemver = pluginSemver;
        this.pluginProperties = pluginProperties;
    }

    public UploadedArtifact stage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.PLUGIN_PACKAGE_INVALID, "插件包不能为空");
        }
        try {
            Path stagingRoot = Path.of(pluginProperties.getStagingRoot()).toAbsolutePath().normalize();
            Files.createDirectories(stagingRoot);
            Path artifactDir = stagingRoot.resolve(UUID.randomUUID().toString());
            Path extractedDir = artifactDir.resolve("extracted");
            Files.createDirectories(extractedDir);
            Path zipPath = artifactDir.resolve("plugin.zip").normalize();
            if (!zipPath.startsWith(artifactDir)) {
                throw new BizException(ErrorCode.PLUGIN_PACKAGE_INVALID, "invalid plugin package path");
            }
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, zipPath, StandardCopyOption.REPLACE_EXISTING);
            }
            unzip(zipPath, extractedDir);
            Path packageRoot = resolvePackageRoot(extractedDir);
            for (String requiredPath : REQUIRED_PATHS) {
                if (!Files.exists(packageRoot.resolve(requiredPath))) {
                    throw new BizException(ErrorCode.PLUGIN_PACKAGE_INVALID, "缺少插件制品文件: " + requiredPath);
                }
            }
            PluginDTO.PluginPackageMetadata metadata = objectMapper.readValue(
                    packageRoot.resolve("plugin.json").toFile(),
                    PluginDTO.PluginPackageMetadata.class
            );
            validateMetadata(metadata);
            PluginDTO.FrontendPluginManifest frontendManifest = objectMapper.readValue(
                    packageRoot.resolve("frontend/manifest.json").toFile(),
                    PluginDTO.FrontendPluginManifest.class
            );
            validateFrontendManifest(metadata, frontendManifest);
            String checksumsRaw = Files.readString(packageRoot.resolve("checksums.json"));
            Map<String, String> checksums = objectMapper.readValue(checksumsRaw, CHECKSUM_TYPE);
            verifySignature(checksumsRaw, Files.readString(packageRoot.resolve("signature.sig")).trim());
            verifyChecksums(packageRoot, checksums);
            String packageChecksum = digest(Files.readAllBytes(zipPath));
            Map<String, Object> validationReport = new LinkedHashMap<>();
            validationReport.put("pluginCode", metadata.getPluginCode());
            validationReport.put("pluginName", metadata.getPluginName());
            validationReport.put("version", metadata.getVersion());
            validationReport.put("verified", Boolean.TRUE);
            validationReport.put("sharedDeps", frontendManifest.getSharedDeps());
            validationReport.put("routes", frontendManifest.getRoutes());
            return new UploadedArtifact(
                    metadata,
                    frontendManifest,
                    zipPath,
                    extractedDir,
                    packageRoot,
                    packageRoot.resolve("signature.sig"),
                    packageChecksum,
                    objectMapper.writeValueAsString(validationReport)
            );
        } catch (BizException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BizException(ErrorCode.PLUGIN_PACKAGE_INVALID, "插件包解析失败: " + exception.getMessage());
        }
    }

    public Path installToVersionHome(UploadedArtifact uploadedArtifact) {
        return installToVersionHome(
                uploadedArtifact.metadata().getPluginCode(),
                uploadedArtifact.metadata().getVersion(),
                uploadedArtifact.extractedDir()
        );
    }

    public Path installToVersionHome(String pluginCode, String version, Path extractedDir) {
        try {
            validateSafePluginCode(pluginCode);
            Path storageRoot = Path.of(pluginProperties.getStorageRoot())
                    .toAbsolutePath()
                    .normalize();
            Path versionHome = storageRoot
                    .resolve(pluginCode)
                    .resolve(version)
                    .normalize();
            if (!versionHome.startsWith(storageRoot)) {
                throw new BizException(ErrorCode.PLUGIN_RUNTIME_ERROR, "invalid plugin storage path");
            }
            if (Files.exists(versionHome)) {
                deleteRecursively(versionHome);
            }
            Files.createDirectories(versionHome);
            copyDirectory(extractedDir, versionHome);
            return versionHome;
        } catch (IOException exception) {
            throw new BizException(ErrorCode.PLUGIN_RUNTIME_ERROR, "插件落盘失败: " + exception.getMessage());
        }
    }

    private void validateSafePluginCode(String pluginCode) {
        if (!StringUtils.hasText(pluginCode) || !SAFE_PLUGIN_CODE.matcher(pluginCode).matches()) {
            throw new BizException(ErrorCode.PLUGIN_PACKAGE_INVALID, "invalid pluginCode");
        }
    }

    public void removeVersionHome(Path versionHome) {
        removePath(versionHome);
    }

    public void removePath(Path path) {
        try {
            deleteRecursively(resolveSafeRemovalPath(path));
        } catch (IOException exception) {
            throw new BizException(ErrorCode.PLUGIN_RUNTIME_ERROR, "插件目录清理失败: " + exception.getMessage());
        }
    }

    private Path resolveSafeRemovalPath(Path path) {
        if (path == null) {
            throw new BizException(ErrorCode.PLUGIN_RUNTIME_ERROR, "invalid plugin cleanup path");
        }
        Path target = path.toAbsolutePath().normalize();
        Path storageRoot = Path.of(pluginProperties.getStorageRoot()).toAbsolutePath().normalize();
        Path stagingRoot = Path.of(pluginProperties.getStagingRoot()).toAbsolutePath().normalize();
        if (!target.startsWith(storageRoot) && !target.startsWith(stagingRoot)) {
            throw new BizException(ErrorCode.PLUGIN_RUNTIME_ERROR, "invalid plugin cleanup path");
        }
        if (target.equals(storageRoot) || target.equals(stagingRoot)) {
            throw new BizException(ErrorCode.PLUGIN_RUNTIME_ERROR, "plugin cleanup path cannot be a storage root");
        }
        return target;
    }

    private void validateMetadata(PluginDTO.PluginPackageMetadata metadata) {
        if (metadata == null) {
            throw new BizException(ErrorCode.PLUGIN_PACKAGE_INVALID, "plugin.json 不存在或格式错误");
        }
        validateSafePluginCode(metadata.getPluginCode());
        pluginSemver.requireValid(metadata.getVersion(), "插件版本");
        pluginSemver.requireValid(metadata.getPluginApiVersion(), "插件 API 版本");
        pluginSemver.requireValid(metadata.getMinPlatformVersion(), "最小平台版本");
        if (!pluginSemver.isCompatible(pluginProperties.getPlatformVersion(), metadata.getMinPlatformVersion())) {
            throw new BizException(ErrorCode.PLUGIN_VERSION_INCOMPATIBLE, "插件要求平台版本不低于 " + metadata.getMinPlatformVersion());
        }
        if (!pluginSemver.isCompatible(metadata.getPluginApiVersion(), pluginProperties.getApiVersion())
                && !pluginSemver.isCompatible(pluginProperties.getApiVersion(), metadata.getPluginApiVersion())) {
            throw new BizException(ErrorCode.PLUGIN_VERSION_INCOMPATIBLE, "插件 API 版本与平台不兼容");
        }
        if (!CHECKSUM_ALGORITHM.equalsIgnoreCase(metadata.getChecksumAlgorithm())) {
            throw new BizException(ErrorCode.PLUGIN_PACKAGE_INVALID, "当前仅支持 SHA-256 校验算法");
        }
        if (metadata.getDependencyPlugins() != null) {
            for (PluginDTO.PluginDependencyDeclaration dependency : metadata.getDependencyPlugins()) {
                pluginSemver.requireValid(dependency.getMinVersion(), "依赖插件最小版本");
            }
        }
    }

    private void validateFrontendManifest(
            PluginDTO.PluginPackageMetadata metadata,
            PluginDTO.FrontendPluginManifest frontendManifest
    ) {
        if (frontendManifest == null) {
            throw new BizException(ErrorCode.PLUGIN_PACKAGE_INVALID, "前端 manifest 缺失");
        }
        if (!metadata.getPluginCode().equals(frontendManifest.getPluginCode())) {
            throw new BizException(ErrorCode.PLUGIN_PACKAGE_INVALID, "前端 manifest 的 pluginCode 与 plugin.json 不一致");
        }
        if (!metadata.getVersion().equals(frontendManifest.getVersion())) {
            throw new BizException(ErrorCode.PLUGIN_PACKAGE_INVALID, "前端 manifest 的 version 与 plugin.json 不一致");
        }
        if (!StringUtils.hasText(frontendManifest.getEntry())) {
            throw new BizException(ErrorCode.PLUGIN_PACKAGE_INVALID, "前端 manifest 缺少 entry");
        }
        if (frontendManifest.getSharedDeps() != null && frontendManifest.getSharedDeps().stream().noneMatch("react"::equalsIgnoreCase)) {
            throw new BizException(ErrorCode.PLUGIN_PACKAGE_INVALID, "前端 manifest 必须声明 react 共享依赖");
        }
    }

    private void verifyChecksums(Path extractedDir, Map<String, String> checksums) throws Exception {
        for (Map.Entry<String, String> entry : checksums.entrySet()) {
            Path filePath = extractedDir.resolve(entry.getKey()).normalize();
            if (!filePath.startsWith(extractedDir) || !Files.exists(filePath)) {
                throw new BizException(ErrorCode.PLUGIN_CHECKSUM_INVALID, "校验文件不存在: " + entry.getKey());
            }
            String actual = digest(Files.readAllBytes(filePath));
            if (!actual.equalsIgnoreCase(entry.getValue())) {
                throw new BizException(ErrorCode.PLUGIN_CHECKSUM_INVALID, "校验和不匹配: " + entry.getKey());
            }
        }
    }

    private void verifySignature(String checksumsRaw, String signature) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(pluginProperties.getSignatureSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String expected = HexFormat.of().formatHex(mac.doFinal(checksumsRaw.getBytes(StandardCharsets.UTF_8)));
        if (!expected.equalsIgnoreCase(signature)) {
            throw new BizException(ErrorCode.PLUGIN_SIGNATURE_INVALID, "插件签名校验失败");
        }
    }

    private String digest(byte[] content) throws Exception {
        MessageDigest digest = MessageDigest.getInstance(CHECKSUM_ALGORITHM);
        return HexFormat.of().formatHex(digest.digest(content));
    }

    private void unzip(Path zipPath, Path targetDir) throws IOException {
        long totalBytes = 0L;
        int fileCount = 0;
        byte[] buffer = new byte[8192];
        try (ZipInputStream zipInputStream = new ZipInputStream(Files.newInputStream(zipPath))) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                validateZipEntry(entry);
                Path resolved = targetDir.resolve(entry.getName()).normalize();
                if (!resolved.startsWith(targetDir)) {
                    throw new BizException(ErrorCode.PLUGIN_PACKAGE_INVALID, "插件压缩包存在非法路径");
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(resolved);
                } else {
                    fileCount++;
                    if (fileCount > MAX_EXTRACTED_FILES) {
                        throw new BizException(ErrorCode.PLUGIN_PACKAGE_INVALID, "插件压缩包文件数量超过限制: " + MAX_EXTRACTED_FILES);
                    }
                    Files.createDirectories(resolved.getParent());
                    long entryBytes = copyZipEntry(zipInputStream, resolved, buffer);
                    if (entryBytes > MAX_ENTRY_BYTES) {
                        throw new BizException(ErrorCode.PLUGIN_PACKAGE_INVALID, "插件压缩包单文件超过限制: " + entry.getName());
                    }
                    totalBytes += entryBytes;
                    if (totalBytes > MAX_EXTRACTED_BYTES) {
                        throw new BizException(ErrorCode.PLUGIN_PACKAGE_INVALID, "插件压缩包解压总大小超过限制");
                    }
                }
                zipInputStream.closeEntry();
            }
        }
    }

    private void validateZipEntry(ZipEntry entry) {
        String name = entry.getName();
        if (!StringUtils.hasText(name)) {
            throw new BizException(ErrorCode.PLUGIN_PACKAGE_INVALID, "插件压缩包存在空文件名");
        }
        String normalizedName = name.replace('\\', '/');
        if (normalizedName.startsWith("/") || normalizedName.contains("../")) {
            throw new BizException(ErrorCode.PLUGIN_PACKAGE_INVALID, "插件压缩包存在非法路径: " + name);
        }
        if (entry.isDirectory()) {
            return;
        }
        String lowerName = normalizedName.toLowerCase();
        if (BLOCKED_ARCHIVE_EXTENSIONS.stream().anyMatch(lowerName::endsWith)) {
            throw new BizException(ErrorCode.PLUGIN_PACKAGE_INVALID, "插件压缩包禁止包含嵌套压缩文件: " + name);
        }
        if (ALLOWED_FILE_EXTENSIONS.stream().noneMatch(lowerName::endsWith)) {
            throw new BizException(ErrorCode.PLUGIN_PACKAGE_INVALID, "插件压缩包包含不允许的文件类型: " + name);
        }
        if (entry.getSize() > MAX_ENTRY_BYTES) {
            throw new BizException(ErrorCode.PLUGIN_PACKAGE_INVALID, "插件压缩包单文件超过限制: " + name);
        }
    }

    private long copyZipEntry(ZipInputStream zipInputStream, Path target, byte[] buffer) throws IOException {
        long entryBytes = 0L;
        try (var outputStream = Files.newOutputStream(target)) {
            int read;
            while ((read = zipInputStream.read(buffer)) != -1) {
                entryBytes += read;
                if (entryBytes > MAX_ENTRY_BYTES) {
                    throw new BizException(ErrorCode.PLUGIN_PACKAGE_INVALID, "插件压缩包单文件超过限制: " + target.getFileName());
                }
                outputStream.write(buffer, 0, read);
            }
        }
        return entryBytes;
    }

    private Path resolvePackageRoot(Path extractedDir) throws IOException {
        if (Files.exists(extractedDir.resolve("plugin.json"))) {
            return extractedDir;
        }
        List<Path> topLevelEntries;
        try (var stream = Files.list(extractedDir)) {
            topLevelEntries = stream.toList();
        }
        List<Path> topLevelDirectories = topLevelEntries.stream().filter(Files::isDirectory).toList();
        if (topLevelDirectories.size() == 1) {
            Path candidate = topLevelDirectories.get(0);
            if (Files.exists(candidate.resolve("plugin.json"))) {
                return candidate;
            }
        }
        throw new BizException(ErrorCode.PLUGIN_PACKAGE_INVALID, "缺少插件制品文件: plugin.json");
    }

    private void copyDirectory(Path source, Path target) throws IOException {
        Files.walk(source).forEach(path -> {
            try {
                Path relative = source.relativize(path);
                Path destination = target.resolve(relative);
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                } else {
                    Files.createDirectories(destination.getParent());
                    Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException exception) {
                throw new IllegalStateException(exception);
            }
        });
    }

    private void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        List<Path> paths = Files.walk(root).sorted((left, right) -> right.getNameCount() - left.getNameCount()).toList();
        for (Path path : paths) {
            Files.deleteIfExists(path);
        }
    }

    public record UploadedArtifact(
            PluginDTO.PluginPackageMetadata metadata,
            PluginDTO.FrontendPluginManifest frontendManifest,
            Path zipPath,
            Path extractedDir,
            Path packageRoot,
            Path signaturePath,
            String packageChecksum,
            String validationReportJson
    ) {
    }
}
