package lingzhou.agent.backend.capability.agentruntime.v2.code;

import java.util.List;
import java.util.Locale;
import org.springframework.util.StringUtils;

final class RuntimeV2CodeOutputHeuristics {

    private RuntimeV2CodeOutputHeuristics() {}

    static OutputPreference resolveOutputPreference(List<String> inputPaths, String goal, String fallbackGoal) {
        String combined = (normalize(goal) + "\n" + normalize(fallbackGoal)).trim();
        if (isExplicitHtmlTask(combined)) {
            return new OutputPreference(".html", "text/html", true);
        }
        String explicitExtension = resolveExplicitRequestedExtension(combined);
        if (StringUtils.hasText(explicitExtension)) {
            return new OutputPreference(explicitExtension, inferMimeTypeByExtension(explicitExtension), true);
        }
        return new OutputPreference(".bin", "application/octet-stream", false);
    }

    static boolean isExplicitHtmlTask(String text) {
        String normalized = normalize(text);
        if (!StringUtils.hasText(normalized)) {
            return false;
        }
        return containsAny(
                normalized, "html", "报告", "报表", "页面", "网页", "可视化", "图表", "dashboard", "report", "chart", "visual");
    }

    static String resolveExplicitRequestedExtension(String text) {
        String normalized = normalize(text);
        if (!StringUtils.hasText(normalized)) {
            return "";
        }
        if (containsAny(
                normalized, ".zip", "输出zip", "输出 zip", "导出zip", "导出 zip", "压缩一下", "重新压缩", "重新打包", "打包给我", "压缩给我")) {
            return ".zip";
        }
        if (containsAny(normalized, ".xlsx", "excel", "xlsx", "输出excel", "输出 excel", "导出excel", "导出 excel")) {
            return ".xlsx";
        }
        if (containsAny(normalized, ".csv", "csv")) {
            return ".csv";
        }
        if (containsAny(normalized, ".tsv", "tsv")) {
            return ".tsv";
        }
        if (containsAny(normalized, ".json", "json")) {
            return ".json";
        }
        if (containsAny(normalized, ".txt", "txt", "文本")) {
            return ".txt";
        }
        return "";
    }

    static String ensureFileNameExtension(String outputFileName, String preferredExtension, String fallbackBaseName) {
        String extension = normalizeExtension(preferredExtension);
        String normalized = trimToEmpty(outputFileName);
        if (!StringUtils.hasText(normalized)) {
            return trimToEmpty(fallbackBaseName) + extension;
        }
        if (normalized.toLowerCase(Locale.ROOT).endsWith(extension)) {
            return normalized;
        }
        int dotIndex = normalized.lastIndexOf('.');
        if (dotIndex > 0) {
            normalized = normalized.substring(0, dotIndex);
        }
        return normalized + extension;
    }

    static String ensureOutputPathExtension(String outputPath, String outputFileName, String preferredExtension) {
        String normalizedPath = trimToEmpty(outputPath);
        String normalizedFileName = ensureFileNameExtension(outputFileName, preferredExtension, "runtime_v2_output");
        if (!StringUtils.hasText(normalizedPath) || !normalizedPath.startsWith("/outputs/")) {
            return "/outputs/" + normalizedFileName;
        }
        if (normalizedPath.toLowerCase(Locale.ROOT).endsWith(normalizeExtension(preferredExtension))) {
            return normalizedPath;
        }
        int slashIndex = normalizedPath.lastIndexOf('/');
        if (slashIndex < 0) {
            return "/outputs/" + normalizedFileName;
        }
        return normalizedPath.substring(0, slashIndex + 1) + normalizedFileName;
    }

    static String inferMimeTypeByExtension(String extension) {
        String normalized = normalizeExtension(extension);
        return switch (normalized) {
            case ".html", ".htm" -> "text/html";
            case ".xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case ".csv" -> "text/csv";
            case ".tsv" -> "text/tab-separated-values";
            case ".json" -> "application/json";
            case ".zip" -> "application/zip";
            case ".txt" -> "text/plain";
            default -> "application/octet-stream";
        };
    }

    private static boolean containsAny(String source, String... candidates) {
        if (!StringUtils.hasText(source) || candidates == null || candidates.length == 0) {
            return false;
        }
        for (String candidate : candidates) {
            if (StringUtils.hasText(candidate)
                    && source.contains(candidate.trim().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeExtension(String extension) {
        String normalized = trimToEmpty(extension).toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(normalized)) {
            return "";
        }
        return normalized.startsWith(".") ? normalized : "." + normalized;
    }

    private static String trimToEmpty(String text) {
        return StringUtils.hasText(text) ? text.trim() : "";
    }

    private static String normalize(String text) {
        return StringUtils.hasText(text) ? text.trim().toLowerCase(Locale.ROOT) : "";
    }

    record OutputPreference(String extension, String mimeType, boolean explicitRequest) {}
}
