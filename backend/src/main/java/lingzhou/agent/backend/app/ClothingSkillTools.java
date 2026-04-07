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
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ClothingSkillTools {

    private static final Logger logger = LoggerFactory.getLogger(ClothingSkillTools.class);
    private static volatile ChatUploadReader chatUploadReader;
    private static volatile ChatUploadMaterializer chatUploadMaterializer;

    private ClothingSkillTools() {}

    @FunctionalInterface
    interface ChatUploadReader {
        String readFile(String path);
    }

    @FunctionalInterface
    interface ChatUploadMaterializer {
        Path materialize(String path) throws IOException;
    }

    static void setChatUploadReader(ChatUploadReader reader) {
        chatUploadReader = reader;
    }

    static void setChatUploadMaterializer(ChatUploadMaterializer materializer) {
        chatUploadMaterializer = materializer;
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

        Path path = resolveScriptPath(pathValue);
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
        Path path = Path.of(pathValue).toAbsolutePath().normalize();
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
            return errorJson("Python runtime is not ready (status: " + status + "). See container logs for [python-bootstrap].");
        }

        Path workingDir = resolveSkillWorkingDir(scriptPath);
        String pythonCommand = resolvePreferredPythonCommand();
        List<String> command = buildPythonCommand(pythonCommand, scriptPath, args);
        if (command == null) {
            return errorJson("Failed to prepare python arguments");
        }
        String output = runCommand(command, workingDir);
        if (output == null) {
            command = buildPythonCommand("python3", scriptPath, args);
            if (command == null) {
                return errorJson("Failed to prepare python arguments");
            }
            output = runCommand(command, workingDir);
        }

        if (output == null) {
            return errorJson("Failed to execute python or python3");
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
                statusPath = Path.of(runtimeDir).resolve("bootstrap.status").toAbsolutePath().normalize();
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
