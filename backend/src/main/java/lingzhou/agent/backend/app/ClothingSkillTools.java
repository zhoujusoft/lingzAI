/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package lingzhou.agent.backend.app;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import lingzhou.agent.backend.business.datasets.service.MinioService;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ClothingSkillTools {

    private static final Logger logger = LoggerFactory.getLogger(ClothingSkillTools.class);
    private static volatile ChatUploadReader chatUploadReader;
    private static volatile ChatUploadMaterializer chatUploadMaterializer;
    private static volatile ArtifactWriter artifactWriter;

    private ClothingSkillTools() {}

    @FunctionalInterface
    interface ChatUploadReader {
        String readFile(String path);
    }

    @FunctionalInterface
    interface ChatUploadMaterializer {
        Path materialize(String path) throws IOException;
    }

    @FunctionalInterface
    interface ArtifactWriter {
        MinioService.ArtifactUploadResult write(
                String folder, String fileName, String content, String sourcePath, String contentType) throws Exception;
    }

    static void setChatUploadReader(ChatUploadReader reader) {
        chatUploadReader = reader;
    }

    static void setChatUploadMaterializer(ChatUploadMaterializer materializer) {
        chatUploadMaterializer = materializer;
    }

    static void setArtifactWriter(ArtifactWriter writer) {
        artifactWriter = writer;
    }

    static String readFileAsString(String pathValue) {
        if (pathValue == null || pathValue.trim().isEmpty()) {
            return errorJson("Missing file path");
        }
        if (pathValue.startsWith("chat-upload://")) {
            ChatUploadReader reader = chatUploadReader;
            if (reader == null) {
                return errorJson("Chat upload reader is not available");
            }
            return reader.readFile(pathValue);
        }

        Path path = resolveToolPath(pathValue, false);
        if (path == null) {
            return errorJson("File not found: " + pathValue);
        }
        if (!Files.exists(path)) {
            return errorJson("File not found: " + path);
        }
        try {
            if (path.toString().toLowerCase().endsWith(".docx")) {
                return readDocxAsString(path);
            }
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return errorJson("Read failed: " + e.getMessage());
        }
    }

    private static String readDocxAsString(Path path) throws IOException {
        try (InputStream input = Files.newInputStream(path);
                XWPFDocument document = new XWPFDocument(input);
                XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        }
    }

    static String writeFileAsString(String pathValue, String content) {
        if (pathValue == null || pathValue.trim().isEmpty()) {
            return errorJson("Missing file path");
        }
        Path path = resolveToolPath(pathValue, true);
        if (path == null) {
            if (SkillExecutionScope.hasActiveSkillDir()) {
                return errorJson("写入路径必须位于当前技能目录下");
            }
            return errorJson("Invalid file path");
        }
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(path, content == null ? "" : content, StandardCharsets.UTF_8);
            return "{\"success\": true, \"path\": \"" + escapeJson(path.toString()) + "\"}";
        } catch (IOException e) {
            return errorJson("Write failed: " + e.getMessage());
        }
    }

    static String writeArtifact(String folder, String fileName, String content, String sourcePath, String contentType) {
        ArtifactWriter writer = artifactWriter;
        if (writer == null) {
            return errorJson("Artifact writer is not available");
        }
        try {
            MinioService.ArtifactUploadResult result = writer.write(folder, fileName, content, sourcePath, contentType);
            String artifactId = MinioService.toArtifactId(result.objectName());
            String artifactShortId = MinioService.toArtifactShortId(result.objectName());
            MinioService.StoredFileDescriptor file = new MinioService.StoredFileDescriptor(
                    artifactShortId,
                    result.fileName(),
                    null,
                    result.bucket(),
                    result.objectName(),
                    result.path(),
                    "/api/files/artifacts/"
                            + artifactId
                            + "/download?fileName="
                            + java.net.URLEncoder.encode(result.fileName(), java.nio.charset.StandardCharsets.UTF_8),
                    result.contentType());
            return """
                    {
                      "success": true,
                      "file": {
                        "id": "%s",
                        "fileName": "%s",
                        "size": null,
                        "bucket": "%s",
                        "objectName": "%s",
                        "path": "%s",
                        "downloadUrl": "%s",
                        "contentType": "%s"
                      },
                      "bucket": "%s",
                      "objectName": "%s",
                      "fileName": "%s",
                      "path": "%s"
                    }
                    """
                    .formatted(
                            escapeJson(file.id()),
                            escapeJson(file.fileName()),
                            escapeJson(file.bucket()),
                            escapeJson(file.objectName()),
                            escapeJson(file.path()),
                            escapeJson(file.downloadUrl()),
                            escapeJson(file.contentType()),
                            escapeJson(result.bucket()),
                            escapeJson(result.objectName()),
                            escapeJson(result.fileName()),
                            escapeJson(result.path()));
        } catch (Exception ex) {
            logger.warn(
                    "Write artifact failed: folder={}, fileName={}, sourcePath={}, error={}",
                    folder,
                    fileName,
                    sourcePath,
                    ex.getMessage(),
                    ex);
            return errorJson("Write artifact failed: " + ex.getMessage());
        }
    }

    static String runPythonScript(String scriptPathValue, String args) {
        if (scriptPathValue == null || scriptPathValue.trim().isEmpty()) {
            return errorJson("Missing script path");
        }

        Path scriptPath = resolveScriptPath(scriptPathValue);
        if (scriptPath == null) {
            return errorJson("Script not found: " + scriptPathValue);
        }

        Path readyFile = resolvePythonReadyFile();
        if (!Files.exists(readyFile)) {
            String status = readPythonBootstrapStatus();
            if (status == null || status.isBlank()) {
                status = "UNKNOWN";
            }
            return errorJson(
                    "Python runtime is not ready (status: " + status + "). See container logs for [python-bootstrap].");
        }

        Path workingDir = resolveSkillWorkingDir(scriptPath);
        String pythonCommand = resolvePreferredPythonCommand();
        List<String> command = buildPythonCommand(pythonCommand, scriptPath, args);
        if (command == null) {
            return errorJson("Failed to prepare python arguments");
        }
        String output = runCommand(command, workingDir);
        if (output == null) {
            command = buildPythonCommand("python3.11", scriptPath, args);
            if (command == null) {
                return errorJson("Failed to prepare python arguments");
            }
            output = runCommand(command, workingDir);
        }

        if (output == null) {
            return errorJson("Failed to execute python or python3.11");
        }

        return output;
    }

    private static List<String> buildPythonCommand(String pythonCmd, Path scriptPath, String args) {
        List<String> command = new ArrayList<>();
        command.add(pythonCmd);
        command.add(scriptPath.toString());
        if (args != null && !args.trim().isEmpty()) {
            for (String part : args.trim().split("\\s+")) {
                String normalizedPart = normalizePythonArg(part);
                if (normalizedPart == null) {
                    return null;
                }
                command.add(normalizedPart);
            }
        }
        return command;
    }

    private static String normalizePythonArg(String arg) {
        if (arg == null || arg.isBlank()) {
            return arg;
        }
        if (!arg.startsWith("chat-upload://")) {
            return arg;
        }
        ChatUploadMaterializer materializer = chatUploadMaterializer;
        if (materializer == null) {
            logger.warn("Chat upload materializer is not available for arg={}", arg);
            return null;
        }
        try {
            Path path = materializer.materialize(arg);
            return path == null ? null : path.toAbsolutePath().normalize().toString();
        } catch (IOException ex) {
            logger.warn("Failed to materialize chat upload for python arg={}", arg, ex);
            return null;
        }
    }

    private static String runCommand(List<String> command, Path workingDir) {
        ProcessBuilder builder = new ProcessBuilder(command);
        if (workingDir != null) {
            builder.directory(workingDir.toFile());
        }
        if (workingDir != null) {
            builder.environment().put("PYTHONPATH", workingDir.toString());
        }
        builder.redirectErrorStream(true);
        try {
            Process process = builder.start();
            String output;
            try (InputStream input = process.getInputStream()) {
                output = new String(input.readAllBytes(), StandardCharsets.UTF_8).trim();
            }
            int exit = process.waitFor();
            if (exit != 0) {
                logger.warn("Command failed (exit {}): {}", exit, String.join(" ", command));
                if (output.isEmpty()) {
                    return errorJson("Command failed with exit code " + exit);
                }
                return errorJson(output);
            }
            return output.isEmpty() ? "{}" : output;
        } catch (IOException e) {
            logger.warn("Command failed: {}", String.join(" ", command), e);
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return errorJson("Command interrupted");
        }
    }

    private static Path resolveScriptPath(String scriptPathValue) {
        Path scopedPath = resolveScopedPath(scriptPathValue, false);
        if (scopedPath != null && Files.exists(scopedPath)) {
            return scopedPath;
        }
        Path rawPath = Path.of(scriptPathValue);
        Path scriptPath = rawPath.toAbsolutePath().normalize();
        if (Files.exists(scriptPath)) {
            return scriptPath;
        }

        String normalized = scriptPathValue.startsWith("/") ? scriptPathValue.substring(1) : scriptPathValue;
        String normalizedAlt = normalized.contains("/scripts/") ? normalized.replace("/scripts/", "/") : null;

        if (!rawPath.isAbsolute()) {
            Path rootPath = SkillFilesystemSupport.resolveSkillPath(normalized);
            if (Files.exists(rootPath)) {
                return rootPath;
            }
            if (normalizedAlt != null) {
                Path rootAltPath = SkillFilesystemSupport.resolveSkillPath(normalizedAlt);
                if (Files.exists(rootAltPath)) {
                    return rootAltPath;
                }
            }

            Path cwd = Path.of(System.getProperty("user.dir"));
            Path cwdPath = cwd.resolve(rawPath).toAbsolutePath().normalize();
            if (Files.exists(cwdPath)) {
                return cwdPath;
            }
            if (normalizedAlt != null) {
                Path cwdAltPath = cwd.resolve(normalizedAlt).toAbsolutePath().normalize();
                if (Files.exists(cwdAltPath)) {
                    return cwdAltPath;
                }
            }
        }
        return null;
    }

    private static Path resolveToolPath(String pathValue, boolean requireInsideSkillDir) {
        Path scopedPath = resolveScopedPath(pathValue, requireInsideSkillDir);
        if (scopedPath != null) {
            return scopedPath;
        }
        Path rawPath = Path.of(pathValue);
        return rawPath.toAbsolutePath().normalize();
    }

    private static Path resolveScopedPath(String pathValue, boolean requireInsideSkillDir) {
        if (pathValue == null || pathValue.isBlank()) {
            return null;
        }
        Path skillDir = SkillExecutionScope.currentSkillDir();
        if (skillDir == null) {
            return null;
        }
        Path rawPath = Path.of(pathValue);
        Path resolved = rawPath.isAbsolute()
                ? rawPath.toAbsolutePath().normalize()
                : skillDir.resolve(rawPath).toAbsolutePath().normalize();
        if (requireInsideSkillDir && !resolved.startsWith(skillDir)) {
            return null;
        }
        return resolved;
    }

    private static String resolvePreferredPythonCommand() {
        String runtimeDir = System.getenv().getOrDefault("PYTHON_RUNTIME_DIR", "/app/runtime/python");
        Path venvPython = Path.of(runtimeDir).resolve("venv").resolve("bin").resolve("python");
        if (Files.isRegularFile(venvPython)) {
            return venvPython.toAbsolutePath().normalize().toString();
        }
        return "python";
    }

    private static Path resolvePythonReadyFile() {
        String configured = System.getenv("PYTHON_BOOTSTRAP_READY_FILE");
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured).toAbsolutePath().normalize();
        }
        String runtimeDir = System.getenv().getOrDefault("PYTHON_RUNTIME_DIR", "/app/runtime/python");
        return Path.of(runtimeDir).resolve(".ready").toAbsolutePath().normalize();
    }

    private static String readPythonBootstrapStatus() {
        try {
            String configured = System.getenv("PYTHON_BOOTSTRAP_STATUS_FILE");
            Path statusPath;
            if (configured != null && !configured.isBlank()) {
                statusPath = Path.of(configured).toAbsolutePath().normalize();
            } else {
                String runtimeDir = System.getenv().getOrDefault("PYTHON_RUNTIME_DIR", "/app/runtime/python");
                statusPath = Path.of(runtimeDir)
                        .resolve("bootstrap.status")
                        .toAbsolutePath()
                        .normalize();
            }
            if (!Files.exists(statusPath)) {
                return "INSTALLING";
            }
            return Files.readString(statusPath, StandardCharsets.UTF_8).trim();
        } catch (Exception e) {
            logger.warn("Failed to read python bootstrap status", e);
            return "UNKNOWN";
        }
    }

    private static Path resolveSkillWorkingDir(Path scriptPath) {
        if (scriptPath == null) {
            return null;
        }
        Path parent = scriptPath.getParent();
        if (parent == null) {
            return null;
        }
        Path scriptDir = parent.getFileName();
        if (scriptDir != null && "scripts".equals(scriptDir.toString())) {
            Path skillDir = parent.getParent();
            if (skillDir != null) {
                return skillDir;
            }
        }
        return parent;
    }

    private static String errorJson(String message) {
        return """
                {
                  "success": false,
                  "error": "%s"
                }
                """
                .formatted(escapeJson(message));
    }

    private static String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
