package lingzhou.agent.backend.business.skill.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import lingzhou.agent.backend.app.SkillFilesystemSupport;
import lingzhou.agent.backend.app.SkillProperties;
import lingzhou.agent.backend.capability.skillruntime.registry.SkillRuntimeRegistry;
import lingzhou.agent.backend.capability.tool.registry.GlobalToolRegistry;
import lingzhou.agent.backend.business.skill.domain.SkillCatalog;
import lingzhou.agent.backend.business.skill.domain.SkillPackageFile;
import lingzhou.agent.backend.business.skill.domain.SkillPackageInstall;
import lingzhou.agent.backend.business.skill.domain.SkillToolBinding;
import lingzhou.agent.backend.business.skill.mapper.SkillCatalogMapper;
import lingzhou.agent.backend.business.skill.mapper.SkillPackageFileMapper;
import lingzhou.agent.backend.business.skill.mapper.SkillPackageInstallMapper;
import lingzhou.agent.backend.business.skill.mapper.SkillToolBindingMapper;
import lingzhou.agent.backend.common.lzException.TaskException;
import lingzhou.agent.spring.ai.skill.core.SkillKit;
import lingzhou.agent.spring.ai.skill.core.SkillPoolManager;
import lingzhou.agent.spring.ai.skill.support.SimpleSkillBox;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.AesKeyStrength;
import net.lingala.zip4j.model.enums.CompressionMethod;
import net.lingala.zip4j.model.enums.EncryptionMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class SkillPackageService {

    private static final Logger logger = LoggerFactory.getLogger(SkillPackageService.class);

    private static final int PACKAGE_FORMAT_VERSION = 1;
    private static final Pattern SAFE_PACKAGE_ID = Pattern.compile("[A-Za-z0-9._-]+");
    private static final Set<String> EXCLUDED_TOP_LEVEL_NAMES = Set.of(
            "__pycache__",
            ".venv",
            "outputs",
            "logs",
            "data_collection");
    private static final Set<String> EXCLUDED_FILE_NAMES = Set.of(".DS_Store", ".env");

    private final SkillCatalogMapper skillCatalogMapper;
    private final SkillToolBindingMapper skillToolBindingMapper;
    private final SkillPackageInstallMapper skillPackageInstallMapper;
    private final SkillPackageFileMapper skillPackageFileMapper;
    private final SkillRuntimeRegistry skillRuntimeRegistry;
    private final SkillProperties skillProperties;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final GlobalToolRegistry globalToolRegistry;
    private final SkillKit skillKit;
    private final SkillPoolManager skillPoolManager;
    private final SimpleSkillBox skillBox;
    private final SkillCatalogService skillCatalogService;

    public SkillPackageService(
            SkillCatalogMapper skillCatalogMapper,
            SkillToolBindingMapper skillToolBindingMapper,
            SkillPackageInstallMapper skillPackageInstallMapper,
            SkillPackageFileMapper skillPackageFileMapper,
            SkillRuntimeRegistry skillRuntimeRegistry,
            SkillProperties skillProperties,
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            GlobalToolRegistry globalToolRegistry,
            SkillKit skillKit,
            SkillPoolManager skillPoolManager,
            SimpleSkillBox skillBox,
            SkillCatalogService skillCatalogService) {
        this.skillCatalogMapper = skillCatalogMapper;
        this.skillToolBindingMapper = skillToolBindingMapper;
        this.skillPackageInstallMapper = skillPackageInstallMapper;
        this.skillPackageFileMapper = skillPackageFileMapper;
        this.skillRuntimeRegistry = skillRuntimeRegistry;
        this.skillProperties = skillProperties;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper.copy()
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.globalToolRegistry = globalToolRegistry;
        this.skillKit = skillKit;
        this.skillPoolManager = skillPoolManager;
        this.skillBox = skillBox;
        this.skillCatalogService = skillCatalogService;
    }

    @PostConstruct
    public void initializeSchema() {
        ensurePackageSchema();
    }

    public ExportedPackage exportSkillPackage(Long skillId, Long userId) throws TaskException {
        ensurePackageSchema();
        SkillCatalog catalog = skillCatalogMapper.selectById(skillId);
        if (catalog == null) {
            throw new TaskException("技能不存在", TaskException.Code.UNKNOWN);
        }
        SkillRuntimeRegistry.FilesystemSkillDescriptor descriptor =
                skillRuntimeRegistry.findFilesystemSkill(catalog.getRuntimeSkillName());
        if (descriptor == null) {
            throw new TaskException("当前技能不是可导出的文件系统技能", TaskException.Code.UNKNOWN);
        }

        Path stagingDir = null;
        Path zipFilePath = null;
        try {
            stagingDir = Files.createTempDirectory("skill-package-export-");
            Path skillStageDir = stagingDir.resolve("skill");
            Files.createDirectories(skillStageDir);

            List<ManifestFile> manifestFiles = new ArrayList<>();
            for (Path sourceFile : collectExportFiles(descriptor.directoryPath())) {
                Path relative = descriptor.directoryPath().relativize(sourceFile);
                Path target = skillStageDir.resolve(relative.toString());
                copyFile(sourceFile, target);
                manifestFiles.add(toManifestFile("skill/" + toUnixPath(relative), sourceFile));
            }

            Path requirementsPath = descriptor.directoryPath().resolve("requirements.txt");
            if (Files.isRegularFile(requirementsPath) && !Files.exists(skillStageDir.resolve("requirements.txt"))) {
                Path target = skillStageDir.resolve("requirements.txt");
                copyFile(requirementsPath, target);
                manifestFiles.add(toManifestFile("skill/requirements.txt", requirementsPath));
            }
            if (Files.isRegularFile(requirementsPath)) {
                Path dependenciesDir = stagingDir.resolve("dependencies");
                Files.createDirectories(dependenciesDir);
                Path dependencyTarget = dependenciesDir.resolve("requirements.txt");
                copyFile(requirementsPath, dependencyTarget);
                manifestFiles.add(toManifestFile("dependencies/requirements.txt", requirementsPath));
            }

            ConfigSnapshot configSnapshot = buildConfigSnapshot(catalog);
            Path configPath = stagingDir.resolve("config.json");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(configPath.toFile(), configSnapshot);
            manifestFiles.add(toManifestFile("config.json", configPath));

            String packageVersion = resolveExportVersion(descriptor.directoryPath());
            Manifest manifest = new Manifest(
                    descriptor.runtimeSkillName(),
                    descriptor.runtimeSkillName(),
                    catalog.getDisplayName(),
                    packageVersion,
                    PACKAGE_FORMAT_VERSION,
                    null,
                    null,
                    OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                    userId == null ? null : String.valueOf(userId),
                    "skill",
                    manifestFiles.stream()
                            .sorted(Comparator.comparing(ManifestFile::path))
                            .toList());
            Path manifestPath = stagingDir.resolve("manifest.json");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(manifestPath.toFile(), manifest);

            zipFilePath = Files.createTempFile(
                    sanitizeFileName(descriptor.runtimeSkillName() + "-" + packageVersion),
                    ".zip");
            writeEncryptedZip(stagingDir, zipFilePath);
            return new ExportedPackage(
                    buildExportFilename(descriptor.runtimeSkillName(), packageVersion),
                    Files.readAllBytes(zipFilePath));
        } catch (IOException | TaskException ex) {
            throw new TaskException("导出技能包失败：" + ex.getMessage(), TaskException.Code.UNKNOWN);
        } finally {
            deleteQuietly(stagingDir);
            deleteQuietly(zipFilePath);
        }
    }

    public PreviewResult previewImport(MultipartFile file) throws TaskException {
        ensurePackageSchema();
        PreparedImport prepared = prepareImport(file);
        try {
            return buildPreview(prepared);
        } finally {
            prepared.cleanup();
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public ImportResult confirmImport(MultipartFile file, boolean confirmDowngrade, Long userId) throws TaskException {
        ensurePackageSchema();
        PreparedImport prepared = prepareImport(file);
        try {
            PreviewResult preview = buildPreview(prepared);
            if (preview.requiresDowngradeConfirmation() && !confirmDowngrade) {
                throw new TaskException("当前导入包为降级包，请确认后再执行导入", TaskException.Code.UNKNOWN);
            }

            Path skillRoot = SkillFilesystemSupport.resolveSkillRoot();
            Path targetSkillDir = skillRoot.resolve(prepared.manifest.packageId()).normalize();
            Files.createDirectories(targetSkillDir);

            Path backupDir = skillRoot.resolve(".backups")
                    .resolve(prepared.manifest.packageId())
                    .resolve(new SimpleDateFormat("yyyyMMddHHmmss", Locale.ROOT).format(new Date()));
            List<FileChange> fileChanges = preview.fileChanges();
            Map<String, InstalledFileSeed> importedFiles = collectImportedFiles(prepared);
            backupManagedFiles(targetSkillDir, backupDir, fileChanges);
            applyImportedFiles(targetSkillDir, importedFiles);
            removeManagedFiles(targetSkillDir, fileChanges);

            SkillCatalog catalog = upsertCatalog(prepared);
            List<String> appliedManualBindings = syncManualBindings(catalog.getId(), prepared.config.toolBindings());

            DependencyInstallResult dependencyResult = installDependencies(prepared, targetSkillDir);
            String installStatus = "SUCCESS";
            if ("FAILED".equals(dependencyResult.status()) && skillProperties.getInstaller().isContinueOnDependencyError()) {
                installStatus = "PARTIAL_SUCCESS";
            }

            SkillPackageInstall install = new SkillPackageInstall();
            install.setPackageId(prepared.manifest.packageId());
            install.setRuntimeSkillName(prepared.manifest.runtimeSkillName());
            install.setPackageVersion(prepared.manifest.version());
            install.setPackageFormatVersion(prepared.manifest.packageFormatVersion());
            install.setSourceFilename(prepared.sourceFilename);
            install.setPackageSha256(prepared.packageSha256);
            install.setInstallMode(preview.installMode());
            install.setInstallStatus(installStatus);
            install.setDependencyStatus(dependencyResult.status());
            install.setInstalledBy(userId);
            install.setInstalledAt(new Date());
            install.setSummaryJson(toSummaryJson(Map.of(
                    "warnings", preview.warnings(),
                    "backupDir", Files.isDirectory(backupDir) ? backupDir.toString() : "",
                    "dependencyMessage", dependencyResult.message(),
                    "appliedManualBindings", appliedManualBindings)));
            skillPackageInstallMapper.insert(install);

            recordInstalledFiles(install.getId(), prepared.manifest.packageId(), fileChanges, importedFiles);

            return new ImportResult(
                    prepared.manifest.packageId(),
                    prepared.manifest.runtimeSkillName(),
                    preview.installMode(),
                    installStatus,
                    dependencyResult.status(),
                    Files.isDirectory(backupDir) ? backupDir.toString() : "",
                    preview.warnings());
        } catch (IOException ex) {
            throw new TaskException("导入技能包失败：" + ex.getMessage(), TaskException.Code.UNKNOWN);
        } finally {
            prepared.cleanup();
        }
    }

    public RefreshResult refreshSkillRuntime() {
        ensurePackageSchema();
        skillRuntimeRegistry.reload(skillKit, skillPoolManager, skillBox);
        skillCatalogService.initializeCatalogData();
        List<SkillRuntimeRegistry.FilesystemSkillDescriptor> filesystemSkills = skillRuntimeRegistry.listFilesystemSkills();
        return new RefreshResult(
                filesystemSkills.size(),
                skillCatalogService.listRuntimeSkills().size(),
                filesystemSkills.stream().map(SkillRuntimeRegistry.FilesystemSkillDescriptor::runtimeSkillName).toList());
    }

    private PreviewResult buildPreview(PreparedImport prepared) throws TaskException {
        validateManifest(prepared.manifest);
        SkillPackageInstall currentInstall = skillPackageInstallMapper.selectLatestSuccessful(prepared.manifest.packageId());
        Map<String, String> previousManagedFiles = loadPreviousManagedFiles(currentInstall);
        Map<String, InstalledFileSeed> importedFiles = collectImportedFiles(prepared);
        List<FileChange> fileChanges = buildFileChanges(previousManagedFiles, importedFiles);
        int unmanagedFileCount = countUnmanagedExistingFiles(prepared.manifest.packageId(), previousManagedFiles.keySet());
        ComparisonResult comparison = compareVersions(
                currentInstall == null ? null : currentInstall.getPackageVersion(),
                prepared.manifest.version());

        List<String> warnings = new ArrayList<>();
        if (comparison.downgrade()) {
            warnings.add("导入包版本低于当前已安装版本，将按降级流程处理。");
        }
        if (unmanagedFileCount > 0) {
            warnings.add("检测到 " + unmanagedFileCount + " 个非受管本地文件，导入时将保留。");
        }
        warnings.addAll(prepared.validationWarnings);

        String installMode = determineInstallMode(currentInstall, comparison, fileChanges);
        return new PreviewResult(
                prepared.manifest.packageId(),
                prepared.manifest.runtimeSkillName(),
                prepared.manifest.displayName(),
                prepared.manifest.version(),
                currentInstall == null ? null : currentInstall.getPackageVersion(),
                installMode,
                comparison.downgrade(),
                comparison.downgrade(),
                unmanagedFileCount,
                fileChanges,
                warnings);
    }

    private PreparedImport prepareImport(MultipartFile file) throws TaskException {
        if (file == null || file.isEmpty()) {
            throw new TaskException("请上传技能包 ZIP 文件", TaskException.Code.UNKNOWN);
        }
        ensurePasswordConfigured();
        Path uploadPath = null;
        Path extractDir = null;
        try {
            uploadPath = Files.createTempFile("skill-package-upload-", ".zip");
            file.transferTo(uploadPath);
            extractDir = Files.createTempDirectory("skill-package-import-");
            ZipFile zipFile = new ZipFile(uploadPath.toFile(), skillProperties.getPackageConfig().getPassword().toCharArray());
            zipFile.extractAll(extractDir.toString());

            Manifest manifest = objectMapper.readValue(extractDir.resolve("manifest.json").toFile(), Manifest.class);
            ConfigSnapshot config = Files.exists(extractDir.resolve("config.json"))
                    ? objectMapper.readValue(extractDir.resolve("config.json").toFile(), ConfigSnapshot.class)
                    : ConfigSnapshot.empty(manifest.runtimeSkillName());
            PreparedImport prepared = new PreparedImport(
                    uploadPath,
                    extractDir,
                    manifest,
                    config,
                    file.getOriginalFilename(),
                    sha256(uploadPath));
            validatePreparedImport(prepared);
            return prepared;
        } catch (TaskException ex) {
            deleteQuietly(uploadPath);
            deleteQuietly(extractDir);
            throw ex;
        } catch (Exception ex) {
            deleteQuietly(uploadPath);
            deleteQuietly(extractDir);
            throw new TaskException("读取技能包失败：" + ex.getMessage(), TaskException.Code.UNKNOWN);
        }
    }

    private void validatePreparedImport(PreparedImport prepared) throws TaskException {
        Path skillMarkdown = prepared.extractDir.resolve("skill").resolve("SKILL.md");
        if (!Files.isRegularFile(skillMarkdown)) {
            throw new TaskException("技能包缺少 skill/SKILL.md", TaskException.Code.UNKNOWN);
        }
        String runtimeNameInSkill = resolveSkillNameFromMarkdown(skillMarkdown);
        if (StringUtils.hasText(runtimeNameInSkill)
                && !Objects.equals(runtimeNameInSkill.trim(), prepared.manifest.runtimeSkillName())) {
            throw new TaskException("manifest.runtimeSkillName 与 skill/SKILL.md 中的 name 不一致", TaskException.Code.UNKNOWN);
        }
    }

    private void validateManifest(Manifest manifest) throws TaskException {
        if (manifest == null) {
            throw new TaskException("技能包缺少 manifest.json", TaskException.Code.UNKNOWN);
        }
        if (!StringUtils.hasText(manifest.packageId()) || !SAFE_PACKAGE_ID.matcher(manifest.packageId().trim()).matches()) {
            throw new TaskException("manifest.packageId 非法", TaskException.Code.UNKNOWN);
        }
        if (!StringUtils.hasText(manifest.runtimeSkillName())) {
            throw new TaskException("manifest.runtimeSkillName 不能为空", TaskException.Code.UNKNOWN);
        }
        if (!StringUtils.hasText(manifest.version())) {
            throw new TaskException("manifest.version 不能为空", TaskException.Code.UNKNOWN);
        }
    }

    private List<Path> collectExportFiles(Path skillDir) throws IOException {
        try (Stream<Path> stream = Files.walk(skillDir)) {
            return stream.filter(Files::isRegularFile)
                    .filter(path -> shouldExport(skillDir, path))
                    .sorted()
                    .toList();
        }
    }

    private boolean shouldExport(Path skillDir, Path file) {
        Path relative = skillDir.relativize(file);
        String unix = toUnixPath(relative);
        if (unix.isEmpty()) {
            return false;
        }
        String firstSegment = unix.contains("/") ? unix.substring(0, unix.indexOf('/')) : unix;
        String fileName = file.getFileName() == null ? "" : file.getFileName().toString();
        if (EXCLUDED_TOP_LEVEL_NAMES.contains(firstSegment)) {
            return false;
        }
        if (EXCLUDED_FILE_NAMES.contains(fileName) || fileName.endsWith(".pyc")) {
            return false;
        }
        return !unix.startsWith(".venv/");
    }

    private ConfigSnapshot buildConfigSnapshot(SkillCatalog catalog) {
        List<String> manualBindings = skillToolBindingMapper.selectBySkillIdAndBindingType(catalog.getId(), "MANUAL")
                .stream()
                .map(SkillToolBinding::getToolName)
                .filter(StringUtils::hasText)
                .sorted()
                .toList();
        return new ConfigSnapshot(
                new SkillCatalogSnapshot(
                        catalog.getRuntimeSkillName(),
                        catalog.getDisplayName(),
                        catalog.getDescription(),
                        catalog.getCategory(),
                        catalog.getVisible() == null || catalog.getVisible() == 1,
                        catalog.getSortOrder() == null ? 0 : catalog.getSortOrder()),
                manualBindings);
    }

    private String resolveExportVersion(Path skillDir) {
        Path skillJsonPath = skillDir.resolve("skill.json");
        if (Files.isRegularFile(skillJsonPath)) {
            try {
                var tree = objectMapper.readTree(skillJsonPath.toFile());
                String version = tree.path("version").asText("");
                if (StringUtils.hasText(version)) {
                    return version.trim();
                }
            } catch (IOException ex) {
                logger.warn("读取 skill.json 版本失败：path={}, error={}", skillJsonPath, ex.getMessage(), ex);
            }
        }
        return DateTimeFormatter.ofPattern("yyyy.MM.dd.HHmmss").format(OffsetDateTime.now());
    }

    private void writeEncryptedZip(Path sourceDir, Path zipFilePath) throws TaskException {
        ensurePasswordConfigured();
        char[] password = skillProperties.getPackageConfig().getPassword().toCharArray();
        ZipFile zipFile = new ZipFile(zipFilePath.toFile(), password);
        try (Stream<Path> stream = Files.walk(sourceDir)) {
            for (Path file : stream.filter(Files::isRegularFile).sorted().toList()) {
                ZipParameters parameters = new ZipParameters();
                parameters.setCompressionMethod(CompressionMethod.DEFLATE);
                parameters.setEncryptFiles(true);
                parameters.setEncryptionMethod(EncryptionMethod.AES);
                parameters.setAesKeyStrength(AesKeyStrength.KEY_STRENGTH_256);
                parameters.setFileNameInZip(toUnixPath(sourceDir.relativize(file)));
                zipFile.addFile(file.toFile(), parameters);
            }
        } catch (IOException ex) {
            throw new TaskException("写入技能包 ZIP 失败：" + ex.getMessage(), TaskException.Code.UNKNOWN);
        }
    }

    private Map<String, InstalledFileSeed> collectImportedFiles(PreparedImport prepared) throws TaskException {
        Path skillDir = prepared.extractDir.resolve("skill");
        if (!Files.isDirectory(skillDir)) {
            throw new TaskException("技能包缺少 skill 目录", TaskException.Code.UNKNOWN);
        }
        Map<String, InstalledFileSeed> files = new LinkedHashMap<>();
        try (Stream<Path> stream = Files.walk(skillDir)) {
            for (Path file : stream.filter(Files::isRegularFile).sorted().toList()) {
                Path relative = skillDir.relativize(file);
                String relativePath = toUnixPath(relative);
                files.put(relativePath, new InstalledFileSeed(
                        relativePath,
                        file,
                        sha256(file),
                        Files.size(file),
                        "requirements.txt".equals(relativePath) ? "DEPENDENCY" : "SKILL_CONTENT"));
            }
        } catch (IOException ex) {
            throw new TaskException("读取技能包文件失败：" + ex.getMessage(), TaskException.Code.UNKNOWN);
        }
        Path dependencyRequirements = prepared.extractDir.resolve("dependencies").resolve("requirements.txt");
        if (Files.isRegularFile(dependencyRequirements) && !files.containsKey("requirements.txt")) {
            try {
                files.put("requirements.txt", new InstalledFileSeed(
                        "requirements.txt",
                        dependencyRequirements,
                        sha256(dependencyRequirements),
                        Files.size(dependencyRequirements),
                        "DEPENDENCY"));
            } catch (IOException ex) {
                throw new TaskException("读取依赖清单失败：" + ex.getMessage(), TaskException.Code.UNKNOWN);
            }
        }
        return files;
    }

    private List<FileChange> buildFileChanges(
            Map<String, String> previousManagedFiles, Map<String, InstalledFileSeed> importedFiles) {
        Set<String> allPaths = new LinkedHashSet<>();
        allPaths.addAll(previousManagedFiles.keySet());
        allPaths.addAll(importedFiles.keySet());
        List<FileChange> changes = new ArrayList<>();
        for (String path : allPaths.stream().sorted().toList()) {
            String oldHash = previousManagedFiles.get(path);
            InstalledFileSeed current = importedFiles.get(path);
            if (oldHash == null && current != null) {
                changes.add(new FileChange(path, "ADDED", current.fileRole()));
            } else if (oldHash != null && current == null) {
                changes.add(new FileChange(path, "REMOVED", "SKILL_CONTENT"));
            } else if (Objects.equals(oldHash, current.sha256())) {
                changes.add(new FileChange(path, "UNCHANGED", current.fileRole()));
            } else {
                changes.add(new FileChange(path, "UPDATED", current.fileRole()));
            }
        }
        return changes;
    }

    private ComparisonResult compareVersions(String installedVersion, String importedVersion) {
        if (!StringUtils.hasText(installedVersion) || !StringUtils.hasText(importedVersion)) {
            return new ComparisonResult(0, false);
        }
        int value = compareVersionText(installedVersion, importedVersion);
        return new ComparisonResult(value, value > 0);
    }

    private int compareVersionText(String left, String right) {
        String[] leftParts = left.trim().split("[^A-Za-z0-9]+");
        String[] rightParts = right.trim().split("[^A-Za-z0-9]+");
        int length = Math.max(leftParts.length, rightParts.length);
        for (int i = 0; i < length; i++) {
            String leftPart = i < leftParts.length ? leftParts[i] : "0";
            String rightPart = i < rightParts.length ? rightParts[i] : "0";
            int compare = compareVersionPart(leftPart, rightPart);
            if (compare != 0) {
                return compare;
            }
        }
        return 0;
    }

    private int compareVersionPart(String left, String right) {
        boolean leftNumeric = left.chars().allMatch(Character::isDigit);
        boolean rightNumeric = right.chars().allMatch(Character::isDigit);
        if (leftNumeric && rightNumeric) {
            return Integer.compare(Integer.parseInt(left), Integer.parseInt(right));
        }
        return left.compareToIgnoreCase(right);
    }

    private String determineInstallMode(SkillPackageInstall currentInstall, ComparisonResult comparison, List<FileChange> changes) {
        if (currentInstall == null) {
            return "INSTALL";
        }
        if (comparison.downgrade()) {
            return "DOWNGRADE";
        }
        if (comparison.compareValue() < 0) {
            return "UPGRADE";
        }
        boolean hasMutations = changes.stream().anyMatch(change -> !"UNCHANGED".equals(change.operation()));
        return hasMutations ? "REPAIR" : "REPAIR";
    }

    private int countUnmanagedExistingFiles(String packageId, Collection<String> managedPaths) {
        Path targetDir = SkillFilesystemSupport.resolveSkillRoot().resolve(packageId);
        if (!Files.isDirectory(targetDir)) {
            return 0;
        }
        try (Stream<Path> stream = Files.walk(targetDir)) {
            return (int) stream.filter(Files::isRegularFile)
                    .map(path -> toUnixPath(targetDir.relativize(path)))
                    .filter(path -> !managedPaths.contains(path))
                    .count();
        } catch (IOException ex) {
            logger.warn("统计非受管文件失败：packageId={}, error={}", packageId, ex.getMessage(), ex);
            return 0;
        }
    }

    private Map<String, String> loadPreviousManagedFiles(SkillPackageInstall currentInstall) {
        Map<String, String> fileMap = new LinkedHashMap<>();
        if (currentInstall == null || currentInstall.getId() == null) {
            return fileMap;
        }
        for (SkillPackageFile file : skillPackageFileMapper.selectByInstallId(currentInstall.getId())) {
            if (StringUtils.hasText(file.getRelativePath()) && StringUtils.hasText(file.getFileSha256())) {
                fileMap.put(file.getRelativePath(), file.getFileSha256());
            }
        }
        return fileMap;
    }

    private void backupManagedFiles(Path targetSkillDir, Path backupDir, List<FileChange> fileChanges) throws IOException {
        for (FileChange change : fileChanges) {
            if (!Set.of("UPDATED", "REMOVED").contains(change.operation())) {
                continue;
            }
            Path source = targetSkillDir.resolve(change.relativePath()).normalize();
            if (!Files.isRegularFile(source)) {
                continue;
            }
            Path target = backupDir.resolve(change.relativePath()).normalize();
            copyFile(source, target);
        }
    }

    private void applyImportedFiles(Path targetSkillDir, Map<String, InstalledFileSeed> importedFiles) throws IOException {
        for (InstalledFileSeed file : importedFiles.values()) {
            Path target = targetSkillDir.resolve(file.relativePath()).normalize();
            copyFile(file.sourcePath(), target);
        }
    }

    private void removeManagedFiles(Path targetSkillDir, List<FileChange> fileChanges) throws IOException {
        for (FileChange change : fileChanges) {
            if (!"REMOVED".equals(change.operation())) {
                continue;
            }
            Files.deleteIfExists(targetSkillDir.resolve(change.relativePath()).normalize());
        }
    }

    private SkillCatalog upsertCatalog(PreparedImport prepared) {
        SkillCatalogSnapshot snapshot = prepared.config.skillCatalog();
        SkillCatalog catalog = skillCatalogMapper.selectByRuntimeSkillName(prepared.manifest.runtimeSkillName());
        if (catalog == null) {
            catalog = new SkillCatalog();
            catalog.setRuntimeSkillName(prepared.manifest.runtimeSkillName());
            catalog.setVisible(1);
            catalog.setSortOrder(0);
            catalog.setSource("filesystem");
            applyCatalogSnapshot(catalog, snapshot, prepared.manifest);
            skillCatalogMapper.insert(catalog);
            return catalog;
        }
        applyCatalogSnapshot(catalog, snapshot, prepared.manifest);
        skillCatalogMapper.updateById(catalog);
        return catalog;
    }

    private void applyCatalogSnapshot(SkillCatalog catalog, SkillCatalogSnapshot snapshot, Manifest manifest) {
        catalog.setDisplayName(normalize(snapshot == null ? null : snapshot.displayName(), manifest.displayName()));
        catalog.setDescription(normalize(snapshot == null ? null : snapshot.description(), manifest.displayName()));
        catalog.setCategory(normalize(snapshot == null ? null : snapshot.category(), "通用能力"));
        catalog.setVisible(snapshot != null && !snapshot.visible() ? 0 : 1);
        catalog.setSortOrder(snapshot == null || snapshot.sortOrder() == null ? 0 : snapshot.sortOrder());
        catalog.setSource("filesystem");
    }

    private List<String> syncManualBindings(Long skillId, List<String> requestedToolNames) {
        List<String> applied = new ArrayList<>();
        skillToolBindingMapper.deleteBySkillIdAndBindingType(skillId, "MANUAL");
        if (requestedToolNames == null) {
            return applied;
        }
        for (String toolName : requestedToolNames.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .sorted()
                .toList()) {
            if (!globalToolRegistry.contains(toolName)) {
                logger.warn("跳过未知公共工具绑定：toolName={}", toolName);
                continue;
            }
            SkillToolBinding binding = new SkillToolBinding();
            binding.setSkillId(skillId);
            binding.setToolName(toolName);
            binding.setBindingType("MANUAL");
            skillToolBindingMapper.insert(binding);
            applied.add(toolName);
        }
        return applied;
    }

    private DependencyInstallResult installDependencies(PreparedImport prepared, Path targetSkillDir) {
        SkillProperties.InstallerProperties installer = skillProperties.getInstaller();
        if (!installer.isEnableDependencyInstall()) {
            return new DependencyInstallResult("SKIPPED", "已关闭依赖安装");
        }
        Path requirementsPath = prepared.extractDir.resolve("dependencies").resolve("requirements.txt");
        if (!Files.isRegularFile(requirementsPath)) {
            requirementsPath = targetSkillDir.resolve("requirements.txt");
        }
        if (!Files.isRegularFile(requirementsPath)) {
            return new DependencyInstallResult("SKIPPED", "未检测到 requirements.txt");
        }
        if (!StringUtils.hasText(installer.getPythonCommand())) {
            return new DependencyInstallResult("SKIPPED", "未配置 pythonCommand");
        }
        List<String> command = new ArrayList<>();
        command.add(installer.getPythonCommand().trim());
        if (StringUtils.hasText(installer.getPipArgs())) {
            for (String part : installer.getPipArgs().trim().split("\\s+")) {
                command.add(part);
            }
        }
        command.add(requirementsPath.toAbsolutePath().normalize().toString());
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(targetSkillDir.toFile());
        builder.redirectErrorStream(true);
        try {
            Process process = builder.start();
            String output;
            try (InputStream input = process.getInputStream()) {
                output = new String(input.readAllBytes(), StandardCharsets.UTF_8).trim();
            }
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                return new DependencyInstallResult("SUCCESS", output.isEmpty() ? "依赖安装完成" : output);
            }
            logger.warn("技能依赖安装失败：packageId={}, exitCode={}, output={}",
                    prepared.manifest.packageId(), exitCode, output);
            return new DependencyInstallResult(
                    "FAILED",
                    output.isEmpty() ? "依赖安装失败，exitCode=" + exitCode : output);
        } catch (IOException ex) {
            logger.warn("执行依赖安装失败：packageId={}, error={}", prepared.manifest.packageId(), ex.getMessage(), ex);
            return new DependencyInstallResult("SKIPPED", "依赖安装器不可用：" + ex.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return new DependencyInstallResult("FAILED", "依赖安装被中断");
        }
    }

    private void recordInstalledFiles(
            Long installId,
            String packageId,
            List<FileChange> fileChanges,
            Map<String, InstalledFileSeed> importedFiles) {
        for (FileChange change : fileChanges) {
            SkillPackageFile row = new SkillPackageFile();
            row.setInstallId(installId);
            row.setPackageId(packageId);
            row.setRelativePath(change.relativePath());
            InstalledFileSeed imported = importedFiles.get(change.relativePath());
            row.setFileSha256(imported == null ? null : imported.sha256());
            row.setFileSize(imported == null ? null : imported.fileSize());
            row.setFileRole(imported == null ? change.fileRole() : imported.fileRole());
            row.setOperation(change.operation());
            row.setManaged(1);
            skillPackageFileMapper.insert(row);
        }
    }

    private ManifestFile toManifestFile(String relativePath, Path sourceFile) throws IOException, TaskException {
        return new ManifestFile(relativePath, sha256(sourceFile), Files.size(sourceFile));
    }

    private String resolveSkillNameFromMarkdown(Path skillMarkdown) {
        try {
            String content = Files.readString(skillMarkdown);
            if (!content.startsWith("---")) {
                return "";
            }
            int end = content.indexOf("\n---", 3);
            if (end < 0) {
                return "";
            }
            String header = content.substring(3, end);
            for (String line : header.split("\n")) {
                String trimmed = line.trim();
                if (trimmed.startsWith("name:")) {
                    return trimmed.substring("name:".length()).trim();
                }
            }
        } catch (IOException ex) {
            logger.warn("解析导入 skill 名称失败：path={}, error={}", skillMarkdown, ex.getMessage(), ex);
        }
        return "";
    }

    private void ensurePasswordConfigured() throws TaskException {
        if (!StringUtils.hasText(skillProperties.getPackageConfig().getPassword())) {
            throw new TaskException("未配置技能包密码", TaskException.Code.UNKNOWN);
        }
    }

    private void ensurePackageSchema() {
        jdbcTemplate.execute(
                """
                CREATE TABLE IF NOT EXISTS skill_package_install (
                  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '技能包安装主键',
                  package_id varchar(120) NOT NULL COMMENT '包唯一标识',
                  runtime_skill_name varchar(120) NOT NULL COMMENT '运行时技能名称',
                  package_version varchar(120) NOT NULL COMMENT '包版本',
                  package_format_version int DEFAULT 1 COMMENT '包格式版本',
                  source_filename varchar(255) DEFAULT NULL COMMENT '上传文件名',
                  package_sha256 varchar(64) DEFAULT NULL COMMENT '包文件 SHA-256',
                  install_mode varchar(32) NOT NULL COMMENT '安装模式',
                  install_status varchar(32) NOT NULL COMMENT '安装状态',
                  dependency_status varchar(32) DEFAULT NULL COMMENT '依赖安装状态',
                  installed_by bigint DEFAULT NULL COMMENT '安装用户',
                  installed_at datetime DEFAULT CURRENT_TIMESTAMP COMMENT '安装时间',
                  summary_json longtext COMMENT '安装摘要 JSON',
                  PRIMARY KEY (id),
                  KEY idx_skill_package_install_package (package_id, id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='技能包安装记录表'
                """);
        jdbcTemplate.execute(
                """
                CREATE TABLE IF NOT EXISTS skill_package_file (
                  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '技能包文件主键',
                  install_id BIGINT NOT NULL COMMENT '所属安装记录 ID',
                  package_id varchar(120) NOT NULL COMMENT '包唯一标识',
                  relative_path varchar(500) NOT NULL COMMENT '相对技能目录路径',
                  file_sha256 varchar(64) DEFAULT NULL COMMENT '文件 SHA-256',
                  file_size bigint DEFAULT NULL COMMENT '文件大小',
                  file_role varchar(32) DEFAULT NULL COMMENT '文件角色',
                  operation varchar(32) DEFAULT NULL COMMENT '本次安装操作类型',
                  managed tinyint DEFAULT 1 COMMENT '是否受管',
                  created_at datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                  PRIMARY KEY (id),
                  KEY idx_skill_package_file_install (install_id),
                  KEY idx_skill_package_file_package (package_id, relative_path)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='技能包受管文件记录表'
                """);
    }

    private static void copyFile(Path source, Path target) throws IOException {
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
    }

    private String sha256(Path path) throws TaskException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(path)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    digest.update(buffer, 0, read);
                }
            }
            StringBuilder builder = new StringBuilder();
            for (byte value : digest.digest()) {
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException | IOException ex) {
            throw new TaskException("计算文件摘要失败：" + ex.getMessage(), TaskException.Code.UNKNOWN);
        }
    }

    private static String toUnixPath(Path relativePath) {
        return relativePath.toString().replace('\\', '/');
    }

    private static String normalize(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private static String sanitizeFileName(String value) {
        return value.replaceAll("[^A-Za-z0-9._-]+", "-");
    }

    private static String buildExportFilename(String runtimeSkillName, String version) {
        return sanitizeFileName(runtimeSkillName) + "-" + sanitizeFileName(version) + ".zip";
    }

    private String toSummaryJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (IOException ex) {
            return "{}";
        }
    }

    private static void deleteQuietly(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try {
            Files.walkFileTree(path, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.deleteIfExists(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ex) {
            LoggerFactory.getLogger(SkillPackageService.class)
                    .warn("清理临时目录失败：path={}, error={}", path, ex.getMessage(), ex);
        }
    }

    public record ExportedPackage(String filename, byte[] content) {}

    public record PreviewResult(
            String packageId,
            String runtimeSkillName,
            String displayName,
            String importedVersion,
            String installedVersion,
            String installMode,
            boolean downgrade,
            boolean requiresDowngradeConfirmation,
            int unmanagedFileCount,
            List<FileChange> fileChanges,
            List<String> warnings) {}

    public record ImportResult(
            String packageId,
            String runtimeSkillName,
            String installMode,
            String installStatus,
            String dependencyStatus,
            String backupDir,
            List<String> warnings) {}

    public record RefreshResult(int filesystemSkillCount, int runtimeSkillCount, List<String> runtimeSkillNames) {}

    public record FileChange(String relativePath, String operation, String fileRole) {}

    public record Manifest(
            String packageId,
            String runtimeSkillName,
            String displayName,
            String version,
            Integer packageFormatVersion,
            String minPlatformVersion,
            String pythonRequirement,
            String exportedAt,
            String exportedBy,
            String skillRootDir,
            List<ManifestFile> files) {}

    public record ManifestFile(String path, String sha256, Long size) {}

    public record ConfigSnapshot(SkillCatalogSnapshot skillCatalog, List<String> toolBindings) {
        static ConfigSnapshot empty(String runtimeSkillName) {
            return new ConfigSnapshot(
                    new SkillCatalogSnapshot(runtimeSkillName, runtimeSkillName, "", "通用能力", true, 0),
                    List.of());
        }
    }

    public record SkillCatalogSnapshot(
            String runtimeSkillName,
            String displayName,
            String description,
            String category,
            boolean visible,
            Integer sortOrder) {}

    private record PreparedImport(
            Path uploadPath,
            Path extractDir,
            Manifest manifest,
            ConfigSnapshot config,
            String sourceFilename,
            String packageSha256,
            List<String> validationWarnings) {

        private PreparedImport(
                Path uploadPath,
                Path extractDir,
                Manifest manifest,
                ConfigSnapshot config,
                String sourceFilename,
                String packageSha256) {
            this(uploadPath, extractDir, manifest, config, sourceFilename, packageSha256, new ArrayList<>());
        }

        private void cleanup() {
            deleteQuietly(uploadPath);
            deleteQuietly(extractDir);
        }
    }

    private record InstalledFileSeed(
            String relativePath, Path sourcePath, String sha256, Long fileSize, String fileRole) {}

    private record DependencyInstallResult(String status, String message) {}

    private record ComparisonResult(int compareValue, boolean downgrade) {}
}
