package lingzhou.agent.backend.business.skill.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import lingzhou.agent.backend.business.chat.execution.workspace.RuntimeExecutionProperties;
import lingzhou.agent.backend.business.datasets.domain.IntegrationDataset;
import lingzhou.agent.backend.business.datasets.domain.KnowledgeBase;
import lingzhou.agent.backend.business.integration.mapper.IntegrationConnectorApiMapper;
import lingzhou.agent.backend.business.datasets.mapper.IntegrationDatasetMapper;
import lingzhou.agent.backend.business.datasets.mapper.KnowledgeBaseMapper;
import lingzhou.agent.backend.business.skill.domain.LowcodeApiCatalog;
import lingzhou.agent.backend.business.skill.domain.McpServer;
import lingzhou.agent.backend.business.skill.domain.SkillCatalog;
import lingzhou.agent.backend.business.skill.domain.SkillPackageFile;
import lingzhou.agent.backend.business.skill.domain.SkillPackageInstall;
import lingzhou.agent.backend.business.skill.domain.SkillToolBinding;
import lingzhou.agent.backend.business.skill.mapper.LowcodeApiCatalogMapper;
import lingzhou.agent.backend.business.skill.mapper.McpServerMapper;
import lingzhou.agent.backend.business.skill.mapper.SkillCatalogMapper;
import lingzhou.agent.backend.business.skill.mapper.SkillPackageFileMapper;
import lingzhou.agent.backend.business.skill.mapper.SkillPackageInstallMapper;
import lingzhou.agent.backend.business.skill.mapper.SkillPublishBindingMapper;
import lingzhou.agent.backend.business.skill.mapper.SkillToolBindingMapper;
import lingzhou.agent.backend.business.tool.domain.ToolCatalog;
import lingzhou.agent.backend.business.tool.mapper.ToolCatalogMapper;
import lingzhou.agent.backend.capability.agentruntime.v2.contract.RuntimeV2ContractSupport;
import lingzhou.agent.backend.capability.agentruntime.v2.contract.RuntimeV2SkillContract;
import lingzhou.agent.backend.capability.agentruntime.v2.contract.RuntimeV2SkillContractBuilder;
import lingzhou.agent.backend.capability.mcp.naming.McpToolNaming;
import lingzhou.agent.backend.capability.skillruntime.registry.SkillRuntimeRegistry;
import lingzhou.agent.backend.capability.tool.registry.GlobalToolRegistry;
import lingzhou.agent.backend.common.lzException.TaskException;
import lingzhou.agent.backend.skillstudio.project.mapper.SkillStudioProjectMapper;
import lingzhou.agent.spring.ai.skill.core.Skill;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class SkillPackageService {

    private static final Logger logger = LoggerFactory.getLogger(SkillPackageService.class);

    private static final int PACKAGE_FORMAT_VERSION = 1;
    private static final String TOOL_BINDING_STATUS_READY = "READY";
    private static final String TOOL_BINDING_STATUS_MISSING_DEPENDENCY = "MISSING_DEPENDENCY";
    private static final String TOOL_BINDING_STATUS_NEEDS_REBIND = "NEEDS_REBIND";
    private static final String TOOL_BINDING_STATUS_UNSUPPORTED = "UNSUPPORTED";
    private static final Pattern SAFE_PACKAGE_ID = Pattern.compile("[A-Za-z0-9._-]+");
    private static final Set<String> EXCLUDED_TOP_LEVEL_NAMES =
            Set.of("__pycache__", ".venv", "outputs", "logs", "data_collection");
    private static final Set<String> EXCLUDED_FILE_NAMES = Set.of(".DS_Store", ".env");
    private static final String SKILL_SOURCE_SKILLSTUDIO = "skillstudio";
    private static final String SKILL_STUDIO_PROJECT_STATUS_DRAFT = "DRAFT";

    private final SkillCatalogMapper skillCatalogMapper;
    private final SkillToolBindingMapper skillToolBindingMapper;
    private final SkillPackageInstallMapper skillPackageInstallMapper;
    private final SkillPackageFileMapper skillPackageFileMapper;
    private final ToolCatalogMapper toolCatalogMapper;
    private final IntegrationConnectorApiMapper integrationConnectorApiMapper;
    private final LowcodeApiCatalogMapper lowcodeApiCatalogMapper;
    private final McpServerMapper mcpServerMapper;
    private final IntegrationDatasetMapper integrationDatasetMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final SkillRuntimeRegistry skillRuntimeRegistry;
    private final SkillProperties skillProperties;
    private final RuntimeExecutionProperties runtimeExecutionProperties;
    private final ObjectMapper objectMapper;
    private final GlobalToolRegistry globalToolRegistry;
    private final SkillKit skillKit;
    private final SkillPoolManager skillPoolManager;
    private final SimpleSkillBox skillBox;
    private final SkillCatalogService skillCatalogService;
    private final SkillPublishBindingMapper skillPublishBindingMapper;
    private final SkillStudioProjectMapper skillStudioProjectMapper;
    private final RuntimeV2SkillContractBuilder runtimeV2SkillContractBuilder;
    private final RuntimeV2ContractSupport runtimeV2ContractSupport;

    public SkillPackageService(
            SkillCatalogMapper skillCatalogMapper,
            SkillToolBindingMapper skillToolBindingMapper,
            SkillPackageInstallMapper skillPackageInstallMapper,
            SkillPackageFileMapper skillPackageFileMapper,
            ToolCatalogMapper toolCatalogMapper,
            IntegrationConnectorApiMapper integrationConnectorApiMapper,
            LowcodeApiCatalogMapper lowcodeApiCatalogMapper,
            McpServerMapper mcpServerMapper,
            IntegrationDatasetMapper integrationDatasetMapper,
            KnowledgeBaseMapper knowledgeBaseMapper,
            SkillRuntimeRegistry skillRuntimeRegistry,
            SkillProperties skillProperties,
            RuntimeExecutionProperties runtimeExecutionProperties,
            ObjectMapper objectMapper,
            GlobalToolRegistry globalToolRegistry,
            SkillKit skillKit,
            SkillPoolManager skillPoolManager,
            SimpleSkillBox skillBox,
            SkillCatalogService skillCatalogService,
            SkillPublishBindingMapper skillPublishBindingMapper,
            SkillStudioProjectMapper skillStudioProjectMapper,
            RuntimeV2SkillContractBuilder runtimeV2SkillContractBuilder,
            RuntimeV2ContractSupport runtimeV2ContractSupport) {
        this.skillCatalogMapper = skillCatalogMapper;
        this.skillToolBindingMapper = skillToolBindingMapper;
        this.skillPackageInstallMapper = skillPackageInstallMapper;
        this.skillPackageFileMapper = skillPackageFileMapper;
        this.toolCatalogMapper = toolCatalogMapper;
        this.integrationConnectorApiMapper = integrationConnectorApiMapper;
        this.lowcodeApiCatalogMapper = lowcodeApiCatalogMapper;
        this.mcpServerMapper = mcpServerMapper;
        this.integrationDatasetMapper = integrationDatasetMapper;
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.skillRuntimeRegistry = skillRuntimeRegistry;
        this.skillProperties = skillProperties;
        this.runtimeExecutionProperties = runtimeExecutionProperties;
        this.objectMapper = objectMapper
                .copy()
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.globalToolRegistry = globalToolRegistry;
        this.skillKit = skillKit;
        this.skillPoolManager = skillPoolManager;
        this.skillBox = skillBox;
        this.skillCatalogService = skillCatalogService;
        this.skillPublishBindingMapper = skillPublishBindingMapper;
        this.skillStudioProjectMapper = skillStudioProjectMapper;
        this.runtimeV2SkillContractBuilder = runtimeV2SkillContractBuilder;
        this.runtimeV2ContractSupport = runtimeV2ContractSupport;
    }

    public ExportedPackage exportSkillPackage(Long skillId, Long userId) throws TaskException {
        SkillCatalog catalog = skillCatalogMapper.selectById(skillId);
        if (catalog == null) {
            throw new TaskException("技能不存在", TaskException.Code.UNKNOWN);
        }
        skillCatalogService.assertCanAccessSkillDetail(userId, catalog);
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

            String packageVersion = resolveExportVersion(catalog);
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
                    sanitizeFileName(descriptor.runtimeSkillName() + "-" + packageVersion), ".zip");
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
        PreparedImport prepared = prepareImport(file);
        try {
            return buildPreview(prepared);
        } finally {
            prepared.cleanup();
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public ImportResult confirmImport(MultipartFile file, boolean confirmDowngrade, Long userId) throws TaskException {
        PreparedImport prepared = prepareImport(file);
        try {
            PreviewResult preview = buildPreview(prepared);
            if (preview.requiresDowngradeConfirmation() && !confirmDowngrade) {
                throw new TaskException("当前导入包为降级包，请确认后再执行导入", TaskException.Code.UNKNOWN);
            }

            Path skillRoot = SkillFilesystemSupport.resolveSkillRoot();
            Path targetSkillDir =
                    skillRoot.resolve(prepared.manifest.packageId()).normalize();
            Files.createDirectories(targetSkillDir);

            Path backupDir = skillRoot
                    .resolve(".backups")
                    .resolve(prepared.manifest.packageId())
                    .resolve(new SimpleDateFormat("yyyyMMddHHmmss", Locale.ROOT).format(new Date()));
            List<FileChange> fileChanges = preview.fileChanges();
            Map<String, InstalledFileSeed> importedFiles = collectImportedFiles(prepared);
            backupManagedFiles(targetSkillDir, backupDir, fileChanges);
            applyImportedFiles(targetSkillDir, importedFiles);
            removeManagedFiles(targetSkillDir, fileChanges);

            SkillCatalog catalog = upsertCatalog(prepared);
            applyToolBindingStatus(catalog, preview.toolBindingSummary(), preview.toolBindingResults());
            skillCatalogMapper.updateById(catalog);
            List<String> appliedManualBindings = syncManualBindings(catalog.getId(), preview.toolBindingResults());

            DependencyInstallResult dependencyResult = installDependencies(prepared, targetSkillDir);
            String installStatus = "SUCCESS";
            if ("FAILED".equals(dependencyResult.status())
                    && skillProperties.getInstaller().isContinueOnDependencyError()) {
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
                    "warnings",
                    preview.warnings(),
                    "backupDir",
                    Files.isDirectory(backupDir) ? backupDir.toString() : "",
                    "dependencyMessage",
                    dependencyResult.message(),
                    "appliedManualBindings",
                    appliedManualBindings,
                    RuntimeV2ContractSupport.EXTENSION_KEY,
                    prepared.config.runtimeContract(),
                    "toolBindingSummary",
                    preview.toolBindingSummary(),
                    "toolBindingResults",
                    preview.toolBindingResults())));
            skillPackageInstallMapper.insert(install);

            recordInstalledFiles(install.getId(), prepared.manifest.packageId(), fileChanges, importedFiles);

            return new ImportResult(
                    prepared.manifest.packageId(),
                    prepared.manifest.runtimeSkillName(),
                    preview.installMode(),
                    installStatus,
                    dependencyResult.status(),
                    Files.isDirectory(backupDir) ? backupDir.toString() : "",
                    preview.warnings(),
                    preview.toolBindingSummary(),
                    preview.toolBindingResults());
        } catch (IOException ex) {
            throw new TaskException("导入技能包失败：" + ex.getMessage(), TaskException.Code.UNKNOWN);
        } finally {
            prepared.cleanup();
        }
    }

    public RefreshResult refreshSkillRuntime() {
        skillRuntimeRegistry.reload(skillKit, skillPoolManager, skillBox);
        List<SkillRuntimeRegistry.FilesystemSkillDescriptor> filesystemSkills =
                skillRuntimeRegistry.listFilesystemSkills();
        return new RefreshResult(
                filesystemSkills.size(),
                skillCatalogService.listRuntimeSkills().size(),
                filesystemSkills.stream()
                        .map(SkillRuntimeRegistry.FilesystemSkillDescriptor::runtimeSkillName)
                        .toList());
    }

    @Transactional(rollbackFor = Exception.class)
    public DeleteSkillResult deleteSkill(Long skillId, Long operatorUserId) throws TaskException {
        if (skillId == null || skillId <= 0) {
            throw new TaskException("技能ID无效", TaskException.Code.UNKNOWN);
        }
        SkillCatalog catalog = skillCatalogMapper.selectById(skillId);
        if (catalog == null) {
            throw new TaskException("技能不存在", TaskException.Code.UNKNOWN);
        }
        skillCatalogService.assertCanDeleteSkill(operatorUserId, catalog);
        String runtimeSkillName = normalizeRequired(catalog.getRuntimeSkillName(), "技能运行时名称不能为空");

        Path skillRoot =
                SkillFilesystemSupport.resolveSkillRoot().toAbsolutePath().normalize();
        Path runtimeSkillDir = skillRoot.resolve(runtimeSkillName).normalize();
        Path pythonSkillsRoot = Path.of(runtimeExecutionProperties.getWorkspaceBaseDir())
                .toAbsolutePath()
                .normalize()
                .resolve("public")
                .resolve("runtime-envs")
                .resolve("python")
                .resolve("skills")
                .normalize();
        Path pythonEnvDir = pythonSkillsRoot.resolve(runtimeSkillName).normalize();

        boolean skillDirectoryDeleted = deleteDirectorySafely(runtimeSkillDir, skillRoot, "技能目录");
        boolean pythonEnvDeleted = deleteDirectorySafely(pythonEnvDir, pythonSkillsRoot, "Python 运行时目录");

        List<SkillPackageInstall> installs = skillPackageInstallMapper.selectByRuntimeSkillName(runtimeSkillName);
        List<Long> installIds = installs.stream()
                .map(SkillPackageInstall::getId)
                .filter(Objects::nonNull)
                .toList();
        if (!installIds.isEmpty()) {
            skillPackageFileMapper.deleteByInstallIds(installIds);
        }
        skillPackageInstallMapper.deleteByRuntimeSkillName(runtimeSkillName);

        skillToolBindingMapper.deleteBySkillId(skillId);
        skillPublishBindingMapper.deleteBySkillId(skillId);
        int deletedCatalogRows = skillCatalogMapper.deleteById(skillId);
        if (deletedCatalogRows <= 0) {
            throw new TaskException("删除技能数据失败", TaskException.Code.UNKNOWN);
        }

        boolean republishRequired = false;
        if (StringUtils.hasText(catalog.getSource())
                && SKILL_SOURCE_SKILLSTUDIO.equalsIgnoreCase(catalog.getSource().trim())) {
            republishRequired = skillStudioProjectMapper.updateActiveStatusByRuntimeSkillName(
                            runtimeSkillName, SKILL_STUDIO_PROJECT_STATUS_DRAFT)
                    > 0;
        }

        refreshSkillRuntime();
        return new DeleteSkillResult(
                skillId, runtimeSkillName, skillDirectoryDeleted, pythonEnvDeleted, republishRequired);
    }

    @Transactional(rollbackFor = Exception.class)
    public BindingRefreshResult refreshToolBindings(Long skillId) throws TaskException {
        if (skillId == null || skillId <= 0) {
            throw new TaskException("技能ID无效", TaskException.Code.UNKNOWN);
        }
        SkillCatalog catalog = skillCatalogMapper.selectById(skillId);
        if (catalog == null) {
            throw new TaskException("技能不存在", TaskException.Code.UNKNOWN);
        }
        if (!StringUtils.hasText(catalog.getRuntimeSkillName())) {
            throw new TaskException("技能缺少运行时标识，无法重新检测绑定", TaskException.Code.UNKNOWN);
        }
        SkillPackageInstall install = skillPackageInstallMapper.selectLatestSuccessfulByRuntimeSkillName(
                catalog.getRuntimeSkillName().trim());
        if (install == null || !StringUtils.hasText(install.getSummaryJson())) {
            throw new TaskException("未找到可用于重新检测的导入记录", TaskException.Code.UNKNOWN);
        }
        List<ToolBindingSnapshot> snapshots = parseStoredToolBindingSnapshots(install.getSummaryJson());
        if (snapshots.isEmpty()) {
            catalog.setToolBindingStatus(TOOL_BINDING_STATUS_READY);
            catalog.setToolBindingMessage(null);
            catalog.setToolBindingDetails(null);
            skillCatalogMapper.updateById(catalog);
            skillToolBindingMapper.deleteBySkillIdAndBindingType(skillId, "MANUAL");
            return new BindingRefreshResult(
                    skillId,
                    new ToolBindingRestoreSummary(0, 0, 0, 0, 0, 0),
                    List.of(),
                    List.of(),
                    TOOL_BINDING_STATUS_READY,
                    null);
        }
        ToolBindingAnalysis analysis = analyzeToolBindings(snapshots);
        List<String> appliedManualBindings = syncManualBindings(skillId, analysis.results());
        applyToolBindingStatus(catalog, analysis.summary(), analysis.results());
        skillCatalogMapper.updateById(catalog);
        return new BindingRefreshResult(
                skillId,
                analysis.summary(),
                analysis.results(),
                appliedManualBindings,
                normalizeToolBindingStatus(catalog.getToolBindingStatus()),
                catalog.getToolBindingMessage());
    }

    private PreviewResult buildPreview(PreparedImport prepared) throws TaskException {
        validateManifest(prepared.manifest);
        SkillPackageInstall currentInstall =
                skillPackageInstallMapper.selectLatestSuccessful(prepared.manifest.packageId());
        Map<String, String> previousManagedFiles = loadPreviousManagedFiles(currentInstall);
        Map<String, InstalledFileSeed> importedFiles = collectImportedFiles(prepared);
        List<FileChange> fileChanges = buildFileChanges(previousManagedFiles, importedFiles);
        int unmanagedFileCount =
                countUnmanagedExistingFiles(prepared.manifest.packageId(), previousManagedFiles.keySet());
        ComparisonResult comparison = compareVersions(
                currentInstall == null ? null : currentInstall.getPackageVersion(), prepared.manifest.version());
        ToolBindingAnalysis bindingAnalysis = analyzeToolBindings(prepared.config.toolBindings());

        List<String> warnings = new ArrayList<>();
        if (comparison.downgrade()) {
            warnings.add("导入包版本低于当前已安装版本，将按降级流程处理。");
        }
        if (unmanagedFileCount > 0) {
            warnings.add("检测到 " + unmanagedFileCount + " 个本地额外文件，不在技能包管理范围内，导入时将保留。");
        }
        warnings.addAll(prepared.validationWarnings);
        warnings.addAll(buildBindingWarnings(bindingAnalysis.summary()));

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
                warnings,
                bindingAnalysis.summary(),
                bindingAnalysis.results());
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
            ZipFile zipFile = new ZipFile(
                    uploadPath.toFile(),
                    skillProperties.getPackageConfig().getPassword().toCharArray());
            zipFile.extractAll(extractDir.toString());

            Manifest manifest =
                    objectMapper.readValue(extractDir.resolve("manifest.json").toFile(), Manifest.class);
            ConfigSnapshot config = Files.exists(extractDir.resolve("config.json"))
                    ? parseConfigSnapshot(extractDir.resolve("config.json"), manifest.runtimeSkillName())
                    : ConfigSnapshot.empty(manifest.runtimeSkillName());
            PreparedImport prepared = new PreparedImport(
                    uploadPath, extractDir, manifest, config, file.getOriginalFilename(), sha256(uploadPath));
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
            throw new TaskException(
                    "manifest.runtimeSkillName 与 skill/SKILL.md 中的 name 不一致", TaskException.Code.UNKNOWN);
        }
    }

    private void validateManifest(Manifest manifest) throws TaskException {
        if (manifest == null) {
            throw new TaskException("技能包缺少 manifest.json", TaskException.Code.UNKNOWN);
        }
        if (!StringUtils.hasText(manifest.packageId())
                || !SAFE_PACKAGE_ID.matcher(manifest.packageId().trim()).matches()) {
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
        List<ToolBindingSnapshot> manualBindings =
                skillToolBindingMapper.selectBySkillIdAndBindingType(catalog.getId(), "MANUAL").stream()
                        .map(binding -> buildToolBindingSnapshot(binding.getToolName(), binding.getBindingType()))
                        .filter(Objects::nonNull)
                        .sorted(Comparator.comparing(ToolBindingSnapshot::toolName))
                        .toList();
        return new ConfigSnapshot(
                new SkillCatalogSnapshot(
                        catalog.getRuntimeSkillName(),
                        catalog.getDisplayName(),
                        catalog.getDescription(),
                        catalog.getCategory(),
                        SkillCatalogMetadataDefaults.resolveVersion(catalog.getVersion()),
                        SkillCatalogMetadataDefaults.resolveAuthor(catalog.getAuthor()),
                        SkillCatalogMetadataDefaults.resolveIcon(catalog.getRuntimeSkillName(), catalog.getIcon()),
                        normalize(catalog.getIconColor(), null),
                        catalog.getVisible() == null || catalog.getVisible() == 1,
                        catalog.getSortOrder() == null ? 0 : catalog.getSortOrder()),
                manualBindings,
                buildRuntimeContractSnapshot(catalog, manualBindings));
    }

    private RuntimeV2SkillContract buildRuntimeContractSnapshot(
            SkillCatalog catalog, List<ToolBindingSnapshot> manualBindings) {
        if (catalog == null || !StringUtils.hasText(catalog.getRuntimeSkillName())) {
            return null;
        }
        Skill skill = skillKit.getSkill(catalog.getRuntimeSkillName().trim());
        if (skill == null) {
            return null;
        }
        List<String> boundToolNames = manualBindings == null
                ? List.of()
                : manualBindings.stream()
                        .map(ToolBindingSnapshot::toolName)
                        .filter(StringUtils::hasText)
                        .toList();
        List<String> toolNames =
                skillCatalogService.mergeToolCallbacksForExport(skill.getTools(), boundToolNames).stream()
                        .filter(tool -> tool != null && tool.getToolDefinition() != null)
                        .map(tool -> tool.getToolDefinition().name())
                        .filter(StringUtils::hasText)
                        .map(String::trim)
                        .distinct()
                        .toList();
        return runtimeV2ContractSupport.normalize(runtimeV2SkillContractBuilder.build(
                catalog.getRuntimeSkillName(), catalog.getDisplayName(), toolNames));
    }

    private String resolveExportVersion(SkillCatalog catalog) {
        return SkillCatalogMetadataDefaults.resolveVersion(catalog == null ? null : catalog.getVersion());
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
                files.put(
                        relativePath,
                        new InstalledFileSeed(
                                relativePath,
                                file,
                                sha256(file),
                                Files.size(file),
                                "requirements.txt".equals(relativePath) ? "DEPENDENCY" : "SKILL_CONTENT"));
            }
        } catch (IOException ex) {
            throw new TaskException("读取技能包文件失败：" + ex.getMessage(), TaskException.Code.UNKNOWN);
        }
        Path dependencyRequirements =
                prepared.extractDir.resolve("dependencies").resolve("requirements.txt");
        if (Files.isRegularFile(dependencyRequirements) && !files.containsKey("requirements.txt")) {
            try {
                files.put(
                        "requirements.txt",
                        new InstalledFileSeed(
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

    private String determineInstallMode(
            SkillPackageInstall currentInstall, ComparisonResult comparison, List<FileChange> changes) {
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

    private void backupManagedFiles(Path targetSkillDir, Path backupDir, List<FileChange> fileChanges)
            throws IOException {
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

    private void applyImportedFiles(Path targetSkillDir, Map<String, InstalledFileSeed> importedFiles)
            throws IOException {
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
        catalog.setVersion(SkillCatalogMetadataDefaults.resolveVersion(snapshot == null ? null : snapshot.version()));
        catalog.setAuthor(SkillCatalogMetadataDefaults.resolveAuthor(snapshot == null ? null : snapshot.author()));
        if (StringUtils.hasText(snapshot == null ? null : snapshot.icon())) {
            catalog.setIcon(snapshot.icon().trim());
        } else if (!StringUtils.hasText(catalog.getIcon())) {
            catalog.setIcon(SkillCatalogMetadataDefaults.resolveIcon(manifest.runtimeSkillName(), null));
        }
        if (StringUtils.hasText(snapshot == null ? null : snapshot.iconColor())) {
            catalog.setIconColor(snapshot.iconColor().trim());
        }
        catalog.setVisible(snapshot != null && !snapshot.visible() ? 0 : 1);
        catalog.setSortOrder(snapshot == null || snapshot.sortOrder() == null ? 0 : snapshot.sortOrder());
        catalog.setSource("filesystem");
        if (!StringUtils.hasText(catalog.getToolBindingStatus())) {
            catalog.setToolBindingStatus(TOOL_BINDING_STATUS_READY);
        }
    }

    private void applyToolBindingStatus(
            SkillCatalog catalog, ToolBindingRestoreSummary summary, List<ToolBindingRestoreResult> bindingResults) {
        if (catalog == null) {
            return;
        }
        if (summary == null
                || (summary.missingDependencyCount() <= 0
                        && summary.needsRebindCount() <= 0
                        && summary.unsupportedCount() <= 0)) {
            catalog.setToolBindingStatus(TOOL_BINDING_STATUS_READY);
            catalog.setToolBindingMessage(null);
            catalog.setToolBindingDetails(null);
            return;
        }
        if (summary.missingDependencyCount() > 0) {
            catalog.setToolBindingStatus(TOOL_BINDING_STATUS_MISSING_DEPENDENCY);
        } else if (summary.needsRebindCount() > 0) {
            catalog.setToolBindingStatus(TOOL_BINDING_STATUS_NEEDS_REBIND);
        } else {
            catalog.setToolBindingStatus(TOOL_BINDING_STATUS_UNSUPPORTED);
        }
        catalog.setToolBindingMessage(buildToolBindingStatusMessage(summary));
        catalog.setToolBindingDetails(toSummaryJson(buildToolBindingIssueViews(bindingResults)));
        catalog.setVisible(0);
    }

    private String buildToolBindingStatusMessage(ToolBindingRestoreSummary summary) {
        if (summary == null) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        if (summary.missingDependencyCount() > 0) {
            parts.add("缺失依赖 " + summary.missingDependencyCount() + " 项");
        }
        if (summary.needsRebindCount() > 0) {
            parts.add("需要重绑 " + summary.needsRebindCount() + " 项");
        }
        if (summary.unsupportedCount() > 0) {
            parts.add("暂不支持恢复 " + summary.unsupportedCount() + " 项");
        }
        return parts.isEmpty() ? null : String.join("，", parts);
    }

    private String normalizeToolBindingStatus(String status) {
        return StringUtils.hasText(status) ? status.trim() : TOOL_BINDING_STATUS_READY;
    }

    private List<SkillCatalogService.ToolBindingIssueView> buildToolBindingIssueViews(
            List<ToolBindingRestoreResult> bindingResults) {
        if (bindingResults == null || bindingResults.isEmpty()) {
            return List.of();
        }
        return bindingResults.stream()
                .filter(Objects::nonNull)
                .filter(result -> StringUtils.hasText(result.restoreStatus())
                        && !Objects.equals(result.restoreStatus(), "RESTORED"))
                .map(result -> new SkillCatalogService.ToolBindingIssueView(
                        result.toolName(),
                        result.resolvedToolName(),
                        result.toolSourceType(),
                        result.restoreStatus(),
                        result.message()))
                .toList();
    }

    private List<ToolBindingSnapshot> parseStoredToolBindingSnapshots(String summaryJson) throws TaskException {
        try {
            JsonNode root = objectMapper.readTree(summaryJson);
            JsonNode node = root == null ? null : root.path("toolBindingResults");
            if (node == null || node.isMissingNode() || !node.isArray()) {
                return List.of();
            }
            List<ToolBindingSnapshot> snapshots = new ArrayList<>();
            for (JsonNode item : node) {
                String toolName = textValue(item.path("toolName"));
                if (!StringUtils.hasText(toolName)) {
                    continue;
                }
                snapshots.add(new ToolBindingSnapshot(
                        toolName.trim(),
                        normalize(textValue(item.path("bindingType")), "MANUAL"),
                        normalizeToolSourceType(textValue(item.path("toolSourceType")), toolName),
                        normalize(
                                textValue(item.path("exportMode")),
                                defaultExportMode(textValue(item.path("toolSourceType")))),
                        "BOUND",
                        parseReferenceMeta(item.path("referenceMeta"))));
            }
            return List.copyOf(snapshots);
        } catch (IOException ex) {
            throw new TaskException("解析历史导入绑定记录失败：" + ex.getMessage(), TaskException.Code.UNKNOWN);
        }
    }

    private ConfigSnapshot parseConfigSnapshot(Path configPath, String runtimeSkillName) throws TaskException {
        try {
            JsonNode root = objectMapper.readTree(configPath.toFile());
            JsonNode skillCatalogNode = root == null ? null : root.path("skillCatalog");
            SkillCatalogSnapshot snapshot = parseSkillCatalogSnapshot(skillCatalogNode, runtimeSkillName);
            List<ToolBindingSnapshot> toolBindings =
                    parseToolBindingSnapshots(root == null ? null : root.path("toolBindings"));
            RuntimeV2SkillContract runtimeContract =
                    runtimeV2ContractSupport.readSkillContract(root == null ? null : root.path("runtimeContract"));
            return new ConfigSnapshot(snapshot, toolBindings, runtimeContract);
        } catch (IOException ex) {
            throw new TaskException("读取技能包配置失败：" + ex.getMessage(), TaskException.Code.UNKNOWN);
        }
    }

    private SkillCatalogSnapshot parseSkillCatalogSnapshot(JsonNode node, String runtimeSkillName) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return ConfigSnapshot.empty(runtimeSkillName).skillCatalog();
        }
        String resolvedRuntimeSkillName = textValue(node.path("runtimeSkillName"));
        return new SkillCatalogSnapshot(
                StringUtils.hasText(resolvedRuntimeSkillName) ? resolvedRuntimeSkillName : runtimeSkillName,
                textValue(node.path("displayName")),
                textValue(node.path("description")),
                textValue(node.path("category")),
                SkillCatalogMetadataDefaults.resolveVersion(textValue(node.path("version"))),
                SkillCatalogMetadataDefaults.resolveAuthor(textValue(node.path("author"))),
                SkillCatalogMetadataDefaults.resolveIcon(runtimeSkillName, textValue(node.path("icon"))),
                textValue(node.path("iconColor")),
                booleanValue(node.path("visible"), true),
                integerValue(node.path("sortOrder"), 0));
    }

    private List<ToolBindingSnapshot> parseToolBindingSnapshots(JsonNode node) {
        if (node == null || node.isMissingNode() || !node.isArray()) {
            return List.of();
        }
        List<ToolBindingSnapshot> snapshots = new ArrayList<>();
        for (JsonNode item : node) {
            if (item == null || item.isNull()) {
                continue;
            }
            if (item.isTextual()) {
                ToolBindingSnapshot legacySnapshot = buildToolBindingSnapshot(item.asText(), "MANUAL");
                if (legacySnapshot != null) {
                    snapshots.add(legacySnapshot);
                }
                continue;
            }
            String toolName = textValue(item.path("toolName"));
            if (!StringUtils.hasText(toolName)) {
                continue;
            }
            String bindingType = normalize(textValue(item.path("bindingType")), "MANUAL");
            String toolSourceType = normalizeToolSourceType(textValue(item.path("toolSourceType")), toolName);
            String exportMode = normalize(textValue(item.path("exportMode")), defaultExportMode(toolSourceType));
            String referenceStatus = normalize(textValue(item.path("referenceStatus")), "BOUND");
            Map<String, Object> referenceMeta = parseReferenceMeta(item.path("referenceMeta"));
            if (referenceMeta.isEmpty()) {
                referenceMeta = buildReferenceMeta(toolName, toolSourceType);
            }
            snapshots.add(new ToolBindingSnapshot(
                    toolName.trim(), bindingType, toolSourceType, exportMode, referenceStatus, referenceMeta));
        }
        return List.copyOf(snapshots);
    }

    private Map<String, Object> parseReferenceMeta(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull() || !node.isObject()) {
            return Map.of();
        }
        return objectMapper.convertValue(node, new TypeReference<Map<String, Object>>() {});
    }

    private ToolBindingSnapshot buildToolBindingSnapshot(String toolName, String bindingType) {
        if (!StringUtils.hasText(toolName)) {
            return null;
        }
        String normalizedToolName = toolName.trim();
        String toolSourceType = normalizeToolSourceType("", normalizedToolName);
        return new ToolBindingSnapshot(
                normalizedToolName,
                normalize(bindingType, "MANUAL"),
                toolSourceType,
                defaultExportMode(toolSourceType),
                "BOUND",
                buildReferenceMeta(normalizedToolName, toolSourceType));
    }

    private ToolBindingAnalysis analyzeToolBindings(List<ToolBindingSnapshot> toolBindings) {
        if (toolBindings == null || toolBindings.isEmpty()) {
            return new ToolBindingAnalysis(new ToolBindingRestoreSummary(0, 0, 0, 0, 0, 0), List.of());
        }
        List<ToolBindingRestoreResult> results =
                toolBindings.stream().map(this::analyzeToolBinding).toList();
        int restoredCount = 0;
        int missingDependencyCount = 0;
        int unsupportedCount = 0;
        int needsRebindCount = 0;
        int skippedCount = 0;
        for (ToolBindingRestoreResult result : results) {
            if (result == null) {
                continue;
            }
            switch (normalize(result.restoreStatus(), "")) {
                case "RESTORED" -> restoredCount++;
                case "MISSING_DEPENDENCY" -> missingDependencyCount++;
                case "UNSUPPORTED" -> unsupportedCount++;
                case "NEEDS_REBIND" -> needsRebindCount++;
                case "SKIPPED" -> skippedCount++;
                default -> {}
            }
        }
        return new ToolBindingAnalysis(
                new ToolBindingRestoreSummary(
                        results.size(),
                        restoredCount,
                        missingDependencyCount,
                        unsupportedCount,
                        needsRebindCount,
                        skippedCount),
                results);
    }

    private ToolBindingRestoreResult analyzeToolBinding(ToolBindingSnapshot snapshot) {
        if (snapshot == null || !StringUtils.hasText(snapshot.toolName())) {
            return new ToolBindingRestoreResult(
                    "", "", "MANUAL", "UNKNOWN", "REFERENCE_ONLY", "SKIPPED", "空绑定项已跳过", Map.of());
        }
        String toolName = snapshot.toolName().trim();
        String bindingType = normalize(snapshot.bindingType(), "MANUAL");
        String toolSourceType = normalizeToolSourceType(snapshot.toolSourceType(), toolName);
        String exportMode = normalize(snapshot.exportMode(), defaultExportMode(toolSourceType));
        Map<String, Object> referenceMeta = snapshot.referenceMeta() == null ? Map.of() : snapshot.referenceMeta();
        return switch (toolSourceType) {
            case "GLOBAL" -> analyzeGlobalBinding(toolName, bindingType, exportMode, referenceMeta);
            case "LOWCODE_API" -> analyzeLowcodeBinding(snapshot, bindingType, exportMode, referenceMeta);
            case "CONNECTOR_API" -> analyzeConnectorBinding(snapshot, bindingType, exportMode, referenceMeta);
            case "DATASET_TOOL" -> analyzeDatasetBinding(snapshot, bindingType, exportMode, referenceMeta);
            case "KNOWLEDGE_BASE_TOOL" -> analyzeKnowledgeBaseBinding(snapshot, bindingType, exportMode, referenceMeta);
            case "MCP_REMOTE" -> analyzeMcpBinding(snapshot, bindingType, exportMode, referenceMeta);
            default -> new ToolBindingRestoreResult(
                    toolName,
                    "",
                    bindingType,
                    toolSourceType,
                    exportMode,
                    "UNSUPPORTED",
                    "当前版本暂不支持自动恢复该类绑定",
                    referenceMeta);
        };
    }

    private ToolBindingRestoreResult analyzeGlobalBinding(
            String toolName, String bindingType, String exportMode, Map<String, Object> referenceMeta) {
        if (!globalToolRegistry.containsBindable(toolName)) {
            return new ToolBindingRestoreResult(
                    toolName,
                    "",
                    bindingType,
                    "GLOBAL",
                    exportMode,
                    "MISSING_DEPENDENCY",
                    "目标环境未找到可绑定公共工具",
                    referenceMeta);
        }
        return new ToolBindingRestoreResult(
                toolName, toolName, bindingType, "GLOBAL", exportMode, "RESTORED", "已恢复公共工具绑定", referenceMeta);
    }

    private ToolBindingRestoreResult analyzeLowcodeBinding(
            ToolBindingSnapshot snapshot, String bindingType, String exportMode, Map<String, Object> referenceMeta) {
        String requestedToolName = snapshot.toolName().trim();
        ToolCatalog directCatalog = toolCatalogMapper.selectBindableByToolName(requestedToolName);
        if (directCatalog != null && Objects.equals(directCatalog.getToolType(), "LOWCODE_API")) {
            return new ToolBindingRestoreResult(
                    requestedToolName,
                    requestedToolName,
                    bindingType,
                    "LOWCODE_API",
                    exportMode,
                    "RESTORED",
                    "已恢复低代码 API 工具绑定",
                    referenceMeta);
        }
        String platformKey = stringMeta(referenceMeta, "platformKey");
        String apiCode = stringMeta(referenceMeta, "apiCode");
        if (!StringUtils.hasText(platformKey) || !StringUtils.hasText(apiCode)) {
            String[] parts = requestedToolName.split("\\.", 3);
            if (parts.length >= 3 && "lowcode".equals(parts[0])) {
                platformKey = parts[1];
                apiCode = parts[2];
            }
        }
        if (!StringUtils.hasText(platformKey) || !StringUtils.hasText(apiCode)) {
            return new ToolBindingRestoreResult(
                    requestedToolName,
                    "",
                    bindingType,
                    "LOWCODE_API",
                    exportMode,
                    "UNSUPPORTED",
                    "低代码 API 绑定缺少 platformKey/apiCode，暂无法自动恢复",
                    referenceMeta);
        }
        LowcodeApiCatalog catalog = lowcodeApiCatalogMapper.selectByPlatformKeyAndApiCode(platformKey, apiCode);
        if (catalog == null) {
            return new ToolBindingRestoreResult(
                    requestedToolName,
                    "",
                    bindingType,
                    "LOWCODE_API",
                    exportMode,
                    "MISSING_DEPENDENCY",
                    "目标环境未找到对应低代码 API 资源",
                    referenceMeta);
        }
        if (catalog.getEnabled() == null || catalog.getEnabled() != 1 || !StringUtils.hasText(catalog.getToolName())) {
            return new ToolBindingRestoreResult(
                    requestedToolName,
                    "",
                    bindingType,
                    "LOWCODE_API",
                    exportMode,
                    "NEEDS_REBIND",
                    "已找到低代码 API 资源，但当前未发布为可绑定工具",
                    referenceMeta);
        }
        return new ToolBindingRestoreResult(
                requestedToolName,
                catalog.getToolName().trim(),
                bindingType,
                "LOWCODE_API",
                exportMode,
                "RESTORED",
                "已根据目标环境低代码 API 资源恢复绑定",
                referenceMeta);
    }

    private ToolBindingRestoreResult analyzeConnectorBinding(
            ToolBindingSnapshot snapshot, String bindingType, String exportMode, Map<String, Object> referenceMeta) {
        String requestedToolName = snapshot.toolName().trim();
        ToolCatalog directCatalog = toolCatalogMapper.selectBindableByToolName(requestedToolName);
        if (directCatalog != null && Objects.equals(directCatalog.getToolType(), "CONNECTOR_API")) {
            return new ToolBindingRestoreResult(
                    requestedToolName,
                    requestedToolName,
                    bindingType,
                    "CONNECTOR_API",
                    exportMode,
                    "RESTORED",
                    "已恢复连接器 API 工具绑定",
                    referenceMeta);
        }
        String connectorId = stringMeta(referenceMeta, "connectorId");
        String apiCode = stringMeta(referenceMeta, "apiCode");
        if ((!StringUtils.hasText(connectorId) || !StringUtils.hasText(apiCode)) && requestedToolName.startsWith("connector.")) {
            String[] parts = requestedToolName.split("\\.", 3);
            if (parts.length >= 3) {
                connectorId = parts[1];
                apiCode = parts[2];
            }
        }
        if (!StringUtils.hasText(connectorId) || !StringUtils.hasText(apiCode)) {
            return new ToolBindingRestoreResult(
                    requestedToolName,
                    "",
                    bindingType,
                    "CONNECTOR_API",
                    exportMode,
                    "UNSUPPORTED",
                    "连接器 API 绑定缺少 connectorId/apiCode，暂无法自动恢复",
                    referenceMeta);
        }
        lingzhou.agent.backend.business.integration.domain.IntegrationConnectorApi catalog =
                integrationConnectorApiMapper.selectByConnectorIdAndApiCode(Long.valueOf(connectorId), apiCode);
        if (catalog == null) {
            return new ToolBindingRestoreResult(
                    requestedToolName,
                    "",
                    bindingType,
                    "CONNECTOR_API",
                    exportMode,
                    "MISSING_DEPENDENCY",
                    "目标环境未找到对应连接器 API 资源",
                    referenceMeta);
        }
        if (catalog.getEnabled() == null || catalog.getEnabled() != 1 || !StringUtils.hasText(catalog.getToolName())) {
            return new ToolBindingRestoreResult(
                    requestedToolName,
                    "",
                    bindingType,
                    "CONNECTOR_API",
                    exportMode,
                    "NEEDS_REBIND",
                    "已找到连接器 API 资源，但当前未发布为可绑定工具",
                    referenceMeta);
        }
        return new ToolBindingRestoreResult(
                requestedToolName,
                catalog.getToolName().trim(),
                bindingType,
                "CONNECTOR_API",
                exportMode,
                "RESTORED",
                "已根据目标环境连接器 API 资源恢复绑定",
                referenceMeta);
    }

    private ToolBindingRestoreResult analyzeDatasetBinding(
            ToolBindingSnapshot snapshot, String bindingType, String exportMode, Map<String, Object> referenceMeta) {
        String datasetCode = stringMeta(referenceMeta, "datasetCode");
        if (!StringUtils.hasText(datasetCode)
                && StringUtils.hasText(snapshot.toolName())
                && snapshot.toolName().startsWith("dataset.")) {
            String[] parts = snapshot.toolName().split("\\.", 3);
            if (parts.length >= 3) {
                datasetCode = parts[1];
            }
        }
        return analyzeSourceBackedBinding(
                snapshot.toolName(),
                bindingType,
                "DATASET_TOOL",
                exportMode,
                referenceMeta,
                datasetSource(referenceMeta, snapshot.toolName()),
                datasetSuffix(snapshot.toolName()),
                integrationDatasetMapper.selectByDatasetCode(datasetCode),
                "目标环境未找到对应数据集",
                "已找到数据集，但数据集工具尚未发布");
    }

    private ToolBindingRestoreResult analyzeKnowledgeBaseBinding(
            ToolBindingSnapshot snapshot, String bindingType, String exportMode, Map<String, Object> referenceMeta) {
        String kbCode = stringMeta(referenceMeta, "knowledgeBaseCode");
        if (!StringUtils.hasText(kbCode)
                && StringUtils.hasText(snapshot.toolName())
                && snapshot.toolName().startsWith("knowledge_base.")) {
            String[] parts = snapshot.toolName().split("\\.", 3);
            if (parts.length >= 3) {
                kbCode = parts[1];
            }
        }
        return analyzeSourceBackedBinding(
                snapshot.toolName(),
                bindingType,
                "KNOWLEDGE_BASE_TOOL",
                exportMode,
                referenceMeta,
                knowledgeBaseSource(referenceMeta, snapshot.toolName()),
                datasetSuffix(snapshot.toolName()),
                knowledgeBaseMapper.selectKnowledgeBaseByKbCode(kbCode),
                "目标环境未找到对应知识库",
                "已找到知识库，但知识库工具尚未发布");
    }

    private ToolBindingRestoreResult analyzeMcpBinding(
            ToolBindingSnapshot snapshot, String bindingType, String exportMode, Map<String, Object> referenceMeta) {
        String requestedToolName = snapshot.toolName().trim();
        ToolCatalog directCatalog = toolCatalogMapper.selectBindableByToolName(requestedToolName);
        if (directCatalog != null && Objects.equals(directCatalog.getToolType(), "MCP_REMOTE")) {
            return new ToolBindingRestoreResult(
                    requestedToolName,
                    requestedToolName,
                    bindingType,
                    "MCP_REMOTE",
                    exportMode,
                    "RESTORED",
                    "已恢复 MCP 工具绑定",
                    referenceMeta);
        }
        String serverKey = stringMeta(referenceMeta, "serverKey");
        if (!StringUtils.hasText(serverKey)) {
            serverKey = McpToolNaming.extractServerKey(requestedToolName);
        }
        if (!StringUtils.hasText(serverKey)) {
            return new ToolBindingRestoreResult(
                    requestedToolName,
                    "",
                    bindingType,
                    "MCP_REMOTE",
                    exportMode,
                    "UNSUPPORTED",
                    "MCP 绑定缺少 serverKey，暂无法自动恢复",
                    referenceMeta);
        }
        String remoteToolName = stringMeta(referenceMeta, "remoteToolName");
        if (!StringUtils.hasText(remoteToolName)) {
            remoteToolName = McpToolNaming.extractRemoteToolName(requestedToolName);
        }
        String source = McpToolNaming.source(serverKey);
        String resolvedToolName = resolveToolNameBySourceAndSuffix(source, remoteToolName, requestedToolName);
        if (StringUtils.hasText(resolvedToolName)) {
            return new ToolBindingRestoreResult(
                    requestedToolName,
                    resolvedToolName,
                    bindingType,
                    "MCP_REMOTE",
                    exportMode,
                    "RESTORED",
                    "已根据目标环境 MCP server 恢复工具绑定",
                    referenceMeta);
        }
        McpServer server = mcpServerMapper.selectByServerKey(serverKey);
        if (server == null) {
            return new ToolBindingRestoreResult(
                    requestedToolName,
                    "",
                    bindingType,
                    "MCP_REMOTE",
                    exportMode,
                    "MISSING_DEPENDENCY",
                    "目标环境未找到对应 MCP server",
                    referenceMeta);
        }
        return new ToolBindingRestoreResult(
                requestedToolName,
                "",
                bindingType,
                "MCP_REMOTE",
                exportMode,
                "NEEDS_REBIND",
                "已找到 MCP server，但当前未同步到对应远程工具",
                referenceMeta);
    }

    private ToolBindingRestoreResult analyzeSourceBackedBinding(
            String requestedToolName,
            String bindingType,
            String toolSourceType,
            String exportMode,
            Map<String, Object> referenceMeta,
            String source,
            String suffix,
            Object resource,
            String missingMessage,
            String needsRebindMessage) {
        ToolCatalog directCatalog = toolCatalogMapper.selectBindableByToolName(requestedToolName.trim());
        if (directCatalog != null && Objects.equals(directCatalog.getToolType(), toolSourceType)) {
            return new ToolBindingRestoreResult(
                    requestedToolName.trim(),
                    requestedToolName.trim(),
                    bindingType,
                    toolSourceType,
                    exportMode,
                    "RESTORED",
                    "已恢复工具绑定",
                    referenceMeta);
        }
        String resolvedToolName = resolveToolNameBySourceAndSuffix(source, suffix, requestedToolName);
        if (StringUtils.hasText(resolvedToolName)) {
            return new ToolBindingRestoreResult(
                    requestedToolName.trim(),
                    resolvedToolName,
                    bindingType,
                    toolSourceType,
                    exportMode,
                    "RESTORED",
                    "已根据目标环境资源恢复工具绑定",
                    referenceMeta);
        }
        if (resource == null) {
            return new ToolBindingRestoreResult(
                    requestedToolName.trim(),
                    "",
                    bindingType,
                    toolSourceType,
                    exportMode,
                    "MISSING_DEPENDENCY",
                    missingMessage,
                    referenceMeta);
        }
        return new ToolBindingRestoreResult(
                requestedToolName.trim(),
                "",
                bindingType,
                toolSourceType,
                exportMode,
                "NEEDS_REBIND",
                needsRebindMessage,
                referenceMeta);
    }

    private String resolveToolNameBySourceAndSuffix(String source, String suffix, String requestedToolName) {
        if (!StringUtils.hasText(source)) {
            return "";
        }
        List<ToolCatalog> candidates = toolCatalogMapper.selectBySource(source).stream()
                .filter(item -> item.getBindable() != null && item.getBindable() == 1)
                .toList();
        if (candidates.isEmpty()) {
            return "";
        }
        for (ToolCatalog candidate : candidates) {
            if (Objects.equals(candidate.getToolName(), requestedToolName)) {
                return requestedToolName;
            }
        }
        if (StringUtils.hasText(suffix)) {
            for (ToolCatalog candidate : candidates) {
                if (candidate.getToolName() != null && candidate.getToolName().endsWith(suffix)) {
                    return candidate.getToolName().trim();
                }
            }
        }
        return candidates.size() == 1 ? normalize(candidates.get(0).getToolName(), "") : "";
    }

    private List<String> buildBindingWarnings(ToolBindingRestoreSummary summary) {
        if (summary == null || summary.totalCount() <= 0) {
            return List.of();
        }
        List<String> warnings = new ArrayList<>();
        if (summary.missingDependencyCount() > 0 || summary.needsRebindCount() > 0 || summary.unsupportedCount() > 0) {
            warnings.add("工具绑定恢复分析：共 " + summary.totalCount()
                    + " 项，已恢复 " + summary.restoredCount()
                    + " 项，缺失依赖 " + summary.missingDependencyCount()
                    + " 项，需要重绑 " + summary.needsRebindCount()
                    + " 项，不支持 " + summary.unsupportedCount() + " 项。");
        }
        return warnings;
    }

    private String normalizeToolSourceType(String declaredType, String toolName) {
        if (StringUtils.hasText(declaredType)) {
            return declaredType.trim();
        }
        if (globalToolRegistry.containsBindable(toolName)) {
            return "GLOBAL";
        }
        ToolCatalog catalog = toolCatalogMapper.selectByToolName(toolName);
        if (catalog != null && StringUtils.hasText(catalog.getToolType())) {
            return catalog.getToolType().trim();
        }
        if (McpToolNaming.isMcpToolName(toolName)) {
            return "MCP_REMOTE";
        }
        if (toolName != null && toolName.startsWith("lowcode.")) {
            return "LOWCODE_API";
        }
        if (toolName != null && toolName.startsWith("connector.")) {
            return "CONNECTOR_API";
        }
        if (toolName != null && toolName.startsWith("dataset.")) {
            return "DATASET_TOOL";
        }
        if (toolName != null && toolName.startsWith("knowledge_base.")) {
            return "KNOWLEDGE_BASE_TOOL";
        }
        return "UNKNOWN";
    }

    private String defaultExportMode(String toolSourceType) {
        return Objects.equals(toolSourceType, "GLOBAL") ? "INLINE" : "REFERENCE_ONLY";
    }

    private Map<String, Object> buildReferenceMeta(String toolName, String toolSourceType) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("toolName", toolName);
        switch (toolSourceType) {
            case "CONNECTOR_API" -> {
                lingzhou.agent.backend.business.integration.domain.IntegrationConnectorApi connectorApi =
                        integrationConnectorApiMapper.selectByToolName(toolName);
                if (connectorApi != null) {
                    meta.put("connectorId", connectorApi.getConnectorId());
                    putIfText(meta, "apiCode", connectorApi.getApiCode());
                    putIfText(meta, "apiName", connectorApi.getApiName());
                } else if (toolName.startsWith("connector.")) {
                    String[] parts = toolName.split("\\.", 3);
                    if (parts.length >= 3) {
                        putIfText(meta, "connectorId", parts[1]);
                        putIfText(meta, "apiCode", parts[2]);
                    }
                }
            }
            case "LOWCODE_API" -> {
                LowcodeApiCatalog catalog = lowcodeApiCatalogMapper.selectByToolName(toolName);
                if (catalog != null) {
                    putIfText(meta, "platformKey", catalog.getPlatformKey());
                    putIfText(meta, "appId", catalog.getAppId());
                    putIfText(meta, "apiId", catalog.getApiId());
                    putIfText(meta, "apiCode", catalog.getApiCode());
                } else if (toolName.startsWith("lowcode.")) {
                    String[] parts = toolName.split("\\.", 3);
                    if (parts.length >= 3) {
                        putIfText(meta, "platformKey", parts[1]);
                        putIfText(meta, "apiCode", parts[2]);
                    }
                }
            }
            case "DATASET_TOOL" -> {
                String datasetCode = extractSourceSuffix(toolCatalogMapper.selectByToolName(toolName), "dataset:");
                if (!StringUtils.hasText(datasetCode) && toolName.startsWith("dataset.")) {
                    String[] parts = toolName.split("\\.", 3);
                    if (parts.length >= 3) {
                        datasetCode = parts[1];
                    }
                }
                putIfText(meta, "datasetCode", datasetCode);
                if (StringUtils.hasText(datasetCode)) {
                    IntegrationDataset dataset = integrationDatasetMapper.selectByDatasetCode(datasetCode);
                    if (dataset != null) {
                        meta.put("datasetId", dataset.getId());
                        putIfText(meta, "datasetName", dataset.getName());
                    }
                }
            }
            case "KNOWLEDGE_BASE_TOOL" -> {
                String kbCode = extractSourceSuffix(toolCatalogMapper.selectByToolName(toolName), "knowledge_base:");
                if (!StringUtils.hasText(kbCode) && toolName.startsWith("knowledge_base.")) {
                    String[] parts = toolName.split("\\.", 3);
                    if (parts.length >= 3) {
                        kbCode = parts[1];
                    }
                }
                putIfText(meta, "knowledgeBaseCode", kbCode);
                if (StringUtils.hasText(kbCode)) {
                    KnowledgeBase knowledgeBase = knowledgeBaseMapper.selectKnowledgeBaseByKbCode(kbCode);
                    if (knowledgeBase != null) {
                        meta.put("knowledgeBaseId", knowledgeBase.getKbId());
                        putIfText(meta, "knowledgeBaseName", knowledgeBase.getKbName());
                    }
                }
            }
            case "MCP_REMOTE" -> {
                ToolCatalog catalog = toolCatalogMapper.selectByToolName(toolName);
                String serverKey = extractSourceSuffix(catalog, "mcp:");
                if (!StringUtils.hasText(serverKey)) {
                    serverKey = McpToolNaming.extractServerKey(toolName);
                }
                putIfText(meta, "serverKey", serverKey);
                putIfText(meta, "remoteToolName", McpToolNaming.extractRemoteToolName(toolName));
                if (StringUtils.hasText(serverKey)) {
                    McpServer server = mcpServerMapper.selectByServerKey(serverKey);
                    if (server != null) {
                        meta.put("serverId", server.getId());
                        putIfText(meta, "serverName", server.getDisplayName());
                    }
                }
            }
            default -> {}
        }
        return Map.copyOf(meta);
    }

    private String extractSourceSuffix(ToolCatalog catalog, String prefix) {
        if (catalog == null
                || !StringUtils.hasText(catalog.getSource())
                || !catalog.getSource().startsWith(prefix)) {
            return "";
        }
        return catalog.getSource().substring(prefix.length()).trim();
    }

    private String datasetSource(Map<String, Object> referenceMeta, String toolName) {
        String datasetCode = stringMeta(referenceMeta, "datasetCode");
        if (!StringUtils.hasText(datasetCode) && toolName.startsWith("dataset.")) {
            String[] parts = toolName.split("\\.", 3);
            if (parts.length >= 3) {
                datasetCode = parts[1];
            }
        }
        return StringUtils.hasText(datasetCode) ? "dataset:" + datasetCode.trim() : "";
    }

    private String knowledgeBaseSource(Map<String, Object> referenceMeta, String toolName) {
        String kbCode = stringMeta(referenceMeta, "knowledgeBaseCode");
        if (!StringUtils.hasText(kbCode) && toolName.startsWith("knowledge_base.")) {
            String[] parts = toolName.split("\\.", 3);
            if (parts.length >= 3) {
                kbCode = parts[1];
            }
        }
        return StringUtils.hasText(kbCode) ? "knowledge_base:" + kbCode.trim() : "";
    }

    private String datasetSuffix(String toolName) {
        if (!StringUtils.hasText(toolName)) {
            return "";
        }
        int lastDot = toolName.lastIndexOf('.');
        return lastDot >= 0 ? toolName.substring(lastDot) : toolName;
    }

    private void putIfText(Map<String, Object> target, String key, String value) {
        if (target != null && StringUtils.hasText(key) && StringUtils.hasText(value)) {
            target.put(key, value.trim());
        }
    }

    private String stringMeta(Map<String, Object> meta, String key) {
        if (meta == null || !StringUtils.hasText(key)) {
            return "";
        }
        Object value = meta.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String textValue(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        return node.isTextual() ? node.asText("").trim() : node.asText("").trim();
    }

    private boolean booleanValue(JsonNode node, boolean defaultValue) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return defaultValue;
        }
        return node.asBoolean(defaultValue);
    }

    private Integer integerValue(JsonNode node, Integer defaultValue) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return defaultValue;
        }
        return node.canConvertToInt() ? node.intValue() : defaultValue;
    }

    private List<String> syncManualBindings(Long skillId, List<ToolBindingRestoreResult> bindingResults) {
        List<String> applied = new ArrayList<>();
        skillToolBindingMapper.deleteBySkillIdAndBindingType(skillId, "MANUAL");
        if (bindingResults == null) {
            return applied;
        }
        for (String toolName : bindingResults.stream()
                .filter(Objects::nonNull)
                .filter(result -> Objects.equals(result.restoreStatus(), "RESTORED"))
                .map(ToolBindingRestoreResult::resolvedToolName)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .sorted()
                .toList()) {
            if (!globalToolRegistry.containsBindable(toolName)) {
                ToolCatalog catalog = toolCatalogMapper.selectBindableByToolName(toolName);
                if (catalog == null) {
                    logger.warn("跳过未知可绑定工具：toolName={}", toolName);
                    continue;
                }
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
        if (StringUtils.hasText(installer.getPipIndexUrl())) {
            builder.environment()
                    .put("PIP_INDEX_URL", installer.getPipIndexUrl().trim());
        }
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
            logger.warn(
                    "技能依赖安装失败：packageId={}, exitCode={}, output={}", prepared.manifest.packageId(), exitCode, output);
            return new DependencyInstallResult("FAILED", output.isEmpty() ? "依赖安装失败，exitCode=" + exitCode : output);
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

    private static String normalizeRequired(String value, String message) throws TaskException {
        if (!StringUtils.hasText(value)) {
            throw new TaskException(message, TaskException.Code.UNKNOWN);
        }
        return value.trim();
    }

    private boolean deleteDirectorySafely(Path targetDir, Path allowedRoot, String targetLabel) throws TaskException {
        if (targetDir == null || allowedRoot == null) {
            return false;
        }
        Path normalizedRoot = allowedRoot.toAbsolutePath().normalize();
        Path normalizedTarget = targetDir.toAbsolutePath().normalize();
        if (!normalizedTarget.startsWith(normalizedRoot)) {
            throw new TaskException(targetLabel + "路径非法，无法删除", TaskException.Code.UNKNOWN);
        }
        if (!Files.exists(normalizedTarget)) {
            return false;
        }
        if (!Files.isDirectory(normalizedTarget)) {
            throw new TaskException(targetLabel + "结构异常，无法删除", TaskException.Code.UNKNOWN);
        }
        try {
            try (Stream<Path> stream = Files.walk(normalizedTarget)) {
                for (Path path : stream.sorted(
                                Comparator.comparingInt(Path::getNameCount).reversed())
                        .toList()) {
                    Files.deleteIfExists(path);
                }
            }
            return true;
        } catch (IOException ex) {
            throw new TaskException(targetLabel + "删除失败: " + ex.getMessage(), TaskException.Code.UNKNOWN);
        }
    }

    public record ExportedPackage(String filename, byte[] content) {}

    public record DeleteSkillResult(
            Long skillId,
            String runtimeSkillName,
            boolean skillDirectoryDeleted,
            boolean pythonEnvDeleted,
            boolean skillStudioRepublishRequired) {}

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
            List<String> warnings,
            ToolBindingRestoreSummary toolBindingSummary,
            List<ToolBindingRestoreResult> toolBindingResults) {}

    public record ImportResult(
            String packageId,
            String runtimeSkillName,
            String installMode,
            String installStatus,
            String dependencyStatus,
            String backupDir,
            List<String> warnings,
            ToolBindingRestoreSummary toolBindingSummary,
            List<ToolBindingRestoreResult> toolBindingResults) {}

    public record BindingRefreshResult(
            Long skillId,
            ToolBindingRestoreSummary toolBindingSummary,
            List<ToolBindingRestoreResult> toolBindingResults,
            List<String> appliedManualBindings,
            String toolBindingStatus,
            String toolBindingMessage) {}

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

    public record ConfigSnapshot(
            SkillCatalogSnapshot skillCatalog,
            List<ToolBindingSnapshot> toolBindings,
            RuntimeV2SkillContract runtimeContract) {
        static ConfigSnapshot empty(String runtimeSkillName) {
            return new ConfigSnapshot(
                    new SkillCatalogSnapshot(
                            runtimeSkillName,
                            runtimeSkillName,
                            "",
                            "通用能力",
                            SkillCatalogMetadataDefaults.DEFAULT_SKILL_VERSION,
                            SkillCatalogMetadataDefaults.DEFAULT_SKILL_AUTHOR,
                            SkillCatalogMetadataDefaults.defaultIcon(runtimeSkillName),
                            null,
                            true,
                            0),
                    List.of(),
                    null);
        }
    }

    public record ToolBindingSnapshot(
            String toolName,
            String bindingType,
            String toolSourceType,
            String exportMode,
            String referenceStatus,
            Map<String, Object> referenceMeta) {}

    public record ToolBindingRestoreSummary(
            int totalCount,
            int restoredCount,
            int missingDependencyCount,
            int unsupportedCount,
            int needsRebindCount,
            int skippedCount) {}

    public record ToolBindingRestoreResult(
            String toolName,
            String resolvedToolName,
            String bindingType,
            String toolSourceType,
            String exportMode,
            String restoreStatus,
            String message,
            Map<String, Object> referenceMeta) {}

    public record SkillCatalogSnapshot(
            String runtimeSkillName,
            String displayName,
            String description,
            String category,
            String version,
            String author,
            String icon,
            String iconColor,
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

    private record ToolBindingAnalysis(ToolBindingRestoreSummary summary, List<ToolBindingRestoreResult> results) {}
}
