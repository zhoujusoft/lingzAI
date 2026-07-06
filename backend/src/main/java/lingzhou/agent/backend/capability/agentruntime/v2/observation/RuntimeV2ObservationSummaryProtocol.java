package lingzhou.agent.backend.capability.agentruntime.v2.observation;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class RuntimeV2ObservationSummaryProtocol {

    private static final Pattern HTML_TITLE_PATTERN =
            Pattern.compile("<title>(.*?)</title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern HTML_HEADING_PATTERN =
            Pattern.compile("<h([1-3])[^>]*>(.*?)</h\\1>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern HTML_CONCLUSION_PATTERN = Pattern.compile(
            "<div[^>]*class=\"[^\"]*conclusion[^\"]*\"[^>]*>.*?<p>(.*?)</p>.*?</div>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final String SKILL_INSTRUCTION_HEADER = "## 技能使用说明";
    private static final String SKILL_RAW_CONTENT_MARKER =
            "Follow the skill instructions below. Use available tools only when needed.";
    private static final int MAX_SKILL_RULES = 5;
    private static final int MAX_SKILL_TOOLS = 8;
    private static final Set<String> SKILL_RULE_KEYWORDS = Set.of("必须", "优先", "不要", "禁止", "只能", "应", "需", "不得");

    public String summarize(String toolName, String toolResult, ObservationSummaryOptions options) {
        if (!StringUtils.hasText(toolResult)) {
            return "";
        }
        String normalizedToolName = normalizeText(toolName);
        if ("loadSkillContent".equalsIgnoreCase(normalizedToolName)) {
            return summarizeLoadSkillContentObservation(toolResult, options.maxPromptLength());
        }
        Map<String, Object> payload = tryParseJsonObject(toolResult);
        if (payload.isEmpty()) {
            return trimForPrompt(toolResult, options.maxPromptLength());
        }
        if ("parse_file".equalsIgnoreCase(normalizedToolName)) {
            return summarizeParseFileObservation(payload, options);
        }
        if ("file_read".equalsIgnoreCase(normalizedToolName)) {
            return summarizeFileReadObservation(payload, options.maxPromptLength());
        }
        if ("file_write".equalsIgnoreCase(normalizedToolName)) {
            return summarizeFileWriteObservation(payload, options.maxPromptLength());
        }
        if ("run_python".equalsIgnoreCase(normalizedToolName)) {
            return summarizeRunPythonObservation(payload, options.maxPromptLength());
        }
        if (normalizedToolName.startsWith("knowledge_base.") && normalizedToolName.endsWith(".search")) {
            return summarizeKnowledgeBaseObservation(payload, options.maxPromptLength());
        }
        if (normalizedToolName.endsWith(".search_dataset_summary")
                || "search_dataset_summary".equalsIgnoreCase(normalizedToolName)) {
            return summarizeDatasetSummaryObservation(payload, options.maxPromptLength());
        }
        if (normalizedToolName.endsWith(".get_dataset_schema")
                || "get_dataset_schema".equalsIgnoreCase(normalizedToolName)) {
            return summarizeDatasetSchemaObservation(payload, options.maxPromptLength());
        }
        if (normalizedToolName.endsWith(".execute_dataset_sql")
                || "execute_dataset_sql".equalsIgnoreCase(normalizedToolName)) {
            return summarizeDatasetSqlObservation(payload, options.maxPromptLength());
        }
        return summarizeGenericJsonObservation(payload, options.maxPromptLength());
    }

    public Map<String, Object> tryParseJsonObject(String text) {
        if (!StringUtils.hasText(text)) {
            return Map.of();
        }
        try {
            Map<String, Object> parsed = JSON.parseObject(text, new TypeReference<Map<String, Object>>() {});
            return parsed == null ? Map.of() : parsed;
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private String summarizeFileReadObservation(Map<String, Object> payload, int maxPromptLength) {
        StringBuilder builder = new StringBuilder();
        appendObservationLine(builder, "success", normalizeText(payload.get("success")));
        appendObservationLine(builder, "action", normalizeText(payload.get("action")));
        Map<String, Object> data = asObject(payload.get("data"));
        String path = normalizeText(data.get("path"));
        String content = normalizeText(data.get("content"));
        if (!StringUtils.hasText(content)) {
            content = normalizeText(payload.get("textOutput"));
        }
        appendObservationLine(builder, "path", path);
        if (StringUtils.hasText(content)) {
            appendObservationLine(builder, "contentLength", String.valueOf(content.length()));
        }
        if (looksLikeHtml(content)) {
            appendObservationLine(builder, "fileKind", "html");
            appendObservationLine(builder, "contentReady", "true");
            appendObservationLine(builder, "title", extractHtmlTagContent(content, HTML_TITLE_PATTERN));
            appendObservationLine(builder, "headings", JSON.toJSONString(extractHtmlHeadings(content)));
            appendObservationLine(
                    builder, "conclusionPreview", shrinkForObservation(extractHtmlConclusion(content), 320));
            appendObservationLine(builder, "chartIds", JSON.toJSONString(extractHtmlElementIds(content)));
            return trimForPrompt(builder.toString(), maxPromptLength);
        }
        appendObservationLine(builder, "fileKind", "text");
        appendObservationLine(builder, "contentReady", "true");
        appendObservationLine(builder, "contentPreview", shrinkForObservation(content, 320));
        return trimForPrompt(builder.toString(), maxPromptLength);
    }

    private String summarizeParseFileObservation(Map<String, Object> payload, ObservationSummaryOptions options) {
        StringBuilder builder = new StringBuilder();
        appendObservationLine(builder, "success", normalizeText(payload.get("success")));
        appendObservationLine(builder, "status", normalizeText(payload.get("status")));
        appendObservationLine(builder, "fileName", normalizeText(payload.get("fileName")));
        appendObservationLine(builder, "fileType", normalizeText(payload.get("fileType")));
        appendObservationLine(builder, "mode", normalizeText(payload.get("mode")));
        appendObservationLine(builder, "qualityHint", normalizeText(payload.get("qualityHint")));

        Map<String, Object> summary = asObject(payload.get("summary"));
        if (!summary.isEmpty()) {
            appendObservationLine(
                    builder,
                    "summary",
                    JSON.toJSONString(Map.of(
                            "sheetCount", summary.getOrDefault("sheetCount", 0),
                            "tableCount", summary.getOrDefault("tableCount", 0),
                            "sectionCount", summary.getOrDefault("sectionCount", 0),
                            "paragraphCount", summary.getOrDefault("paragraphCount", 0))));
        }

        Map<String, Object> contentView = asObject(payload.get("contentView"));
        String rawText = normalizeText(contentView.get("text"));
        String rawMarkdown = normalizeText(contentView.get("markdown"));
        appendObservationLine(builder, "contentScope", normalizeText(contentView.get("contentScope")));
        appendObservationLine(builder, "schemaOnly", normalizeText(contentView.get("schemaOnly")));
        appendObservationLine(
                builder,
                "readableContentAvailable",
                String.valueOf(StringUtils.hasText(rawText) || StringUtils.hasText(rawMarkdown)));
        Map<String, Object> entities = asObject(contentView.get("entities"));
        List<Object> sheetNames = asList(entities.get("sheetNames"));
        if (!sheetNames.isEmpty()) {
            appendObservationLine(builder, "sheetNames", JSON.toJSONString(limitList(sheetNames, 5)));
        }
        if (StringUtils.hasText(rawText)) {
            appendObservationLine(builder, "content", rawText);
        } else if (StringUtils.hasText(rawMarkdown)) {
            appendObservationLine(builder, "markdown", rawMarkdown);
        }

        List<Object> sections = asList(contentView.get("sections"));
        if (!sections.isEmpty()) {
            List<Map<String, Object>> sectionSummaries = new ArrayList<>();
            boolean archiveContainsPdf = false;
            boolean archiveContainsNestedZip = false;
            boolean hasHeader = false;
            boolean hasSampleRows = false;
            for (Object item : limitList(sections, 2)) {
                Map<String, Object> section = asObject(item);
                if (section.isEmpty()) {
                    continue;
                }
                Map<String, Object> sectionSummary = new LinkedHashMap<>();
                copyIfPresent(section, sectionSummary, "type");
                copyIfPresent(section, sectionSummary, "index");
                copyIfPresent(section, sectionSummary, "name");
                copyIfPresent(section, sectionSummary, "rowCount");
                copyIfPresent(section, sectionSummary, "columnCount");
                Object header = section.get("header");
                if (header instanceof List<?> headerList && !headerList.isEmpty()) {
                    sectionSummary.put("header", limitList(new ArrayList<>(headerList), 12));
                    hasHeader = true;
                }
                Object sampleRows = section.get("sampleRows");
                if (sampleRows instanceof List<?> sampleRowList && !sampleRowList.isEmpty()) {
                    sectionSummary.put("sampleRows", limitList(new ArrayList<>(sampleRowList), 2));
                    hasSampleRows = true;
                }
                String text = normalizeText(section.get("textPreview"));
                if (!StringUtils.hasText(text)) {
                    text = normalizeText(section.get("text"));
                }
                if (StringUtils.hasText(text)) {
                    sectionSummary.put("textPreview", shrinkForObservation(text, 240));
                    String lowered = text.toLowerCase();
                    archiveContainsPdf = archiveContainsPdf || lowered.contains(".pdf");
                    archiveContainsNestedZip = archiveContainsNestedZip || lowered.contains(".zip");
                }
                String type = normalizeText(section.get("type")).toLowerCase();
                archiveContainsPdf = archiveContainsPdf || type.contains("pdf");
                archiveContainsNestedZip = archiveContainsNestedZip || type.contains("zip");
                if (!sectionSummary.isEmpty()) {
                    sectionSummaries.add(sectionSummary);
                }
            }
            if (!sectionSummaries.isEmpty()) {
                appendObservationLine(builder, "sections", JSON.toJSONString(sectionSummaries));
            }
            appendObservationLine(builder, "hasHeader", String.valueOf(hasHeader));
            appendObservationLine(builder, "hasSampleRows", String.valueOf(hasSampleRows));
            appendObservationLine(builder, "archiveContainsPdf", String.valueOf(archiveContainsPdf));
            appendObservationLine(builder, "archiveContainsNestedZip", String.valueOf(archiveContainsNestedZip));
        }

        List<Object> warnings = asList(payload.get("warnings"));
        if (!warnings.isEmpty()) {
            appendObservationLine(builder, "warnings", JSON.toJSONString(limitList(warnings, 5)));
        }
        String error = normalizeText(payload.get("error"));
        if (StringUtils.hasText(error)) {
            appendObservationLine(builder, "error", shrinkForObservation(error, 240));
        }
        appendObservationLine(
                builder, "observationClass", resolveParseFileObservationClass(payload, contentView, sections, options));
        return trimForPrompt(builder.toString(), options.maxPromptLength());
    }

    private String resolveParseFileObservationClass(
            Map<String, Object> payload,
            Map<String, Object> contentView,
            List<Object> sections,
            ObservationSummaryOptions options) {
        String fileType = normalizeText(payload.get("fileType")).toLowerCase();
        String mode = normalizeText(payload.get("mode")).toLowerCase();
        if ("zip".equals(fileType)) {
            return "archive-schema";
        }
        String rawText = normalizeText(contentView.get("text"));
        String rawMarkdown = normalizeText(contentView.get("markdown"));
        boolean hasFullReadableContent = ("text".equals(mode) && StringUtils.hasText(rawText))
                || ("markdown".equals(mode) && StringUtils.hasText(rawMarkdown));
        if (hasFullReadableContent) {
            return "readable-content";
        }
        boolean tabularFile =
                "xlsx".equals(fileType) || "xls".equals(fileType) || "csv".equals(fileType) || "tsv".equals(fileType);
        if (!tabularFile) {
            return "";
        }
        if (!options.allowCodeExecution()) {
            return "tabular-schema";
        }
        String text = normalizeText(contentView.get("text"));
        String markdown = normalizeText(contentView.get("markdown"));
        boolean hasRichText = StringUtils.hasText(text) || StringUtils.hasText(markdown);
        boolean hasSampleRows = false;
        boolean hasHeader = false;
        for (Object item : sections) {
            Map<String, Object> section = asObject(item);
            List<Object> header = asList(section.get("header"));
            if (!header.isEmpty()) {
                hasHeader = true;
            }
            List<Object> sampleRows = asList(section.get("sampleRows"));
            if (!sampleRows.isEmpty()) {
                hasSampleRows = true;
            }
            if (hasHeader && hasSampleRows) {
                break;
            }
        }
        if (hasHeader || hasSampleRows) {
            return "tabular-schema-with-samples";
        }
        if (hasRichText || hasSampleRows) {
            return "";
        }
        return "tabular-schema";
    }

    private String summarizeGenericJsonObservation(Map<String, Object> payload, int maxPromptLength) {
        return trimForPrompt(JSON.toJSONString(payload), maxPromptLength);
    }

    private String summarizeLoadSkillContentObservation(String toolResult, int maxPromptLength) {
        if (!StringUtils.hasText(toolResult)) {
            return "";
        }
        String normalized = toolResult.replace("\r\n", "\n").trim();
        if (normalized.startsWith("Error:")) {
            return trimForPrompt(normalized, maxPromptLength);
        }
        StringBuilder builder = new StringBuilder();
        String sanitizedSkillBody = sanitizeSkillContent(normalized);
        appendObservationLine(builder, "observationClass", "skill-doc");
        appendObservationLine(builder, "skillRuntimeName", extractSkillRuntimeName(normalized));
        appendObservationLine(builder, "skillDisplayName", extractSkillDisplayName(normalized));
        appendObservationLine(
                builder, "skillDescription", shrinkForObservation(extractSkillDescription(normalized), 180));
        List<String> keyRules = extractSkillRuleHighlights(sanitizedSkillBody);
        if (!keyRules.isEmpty()) {
            appendObservationLine(builder, "keyRules", JSON.toJSONString(keyRules));
        }
        return trimForPrompt(builder.toString(), maxPromptLength);
    }

    private String summarizeKnowledgeBaseObservation(Map<String, Object> payload, int maxPromptLength) {
        StringBuilder builder = new StringBuilder();
        appendObservationLine(builder, "observationClass", "knowledge-base-hit");
        appendObservationLine(builder, "kbName", normalizeText(payload.get("kbName")));
        appendObservationLine(builder, "query", shrinkForObservation(normalizeText(payload.get("query")), 180));
        List<Object> hits = asList(payload.get("hits"));
        appendObservationLine(builder, "hitCount", String.valueOf(hits.size()));
        if (!hits.isEmpty()) {
            List<Object> evidence = new ArrayList<>();
            for (Object item : limitList(hits, 3)) {
                Map<String, Object> hit = asObject(item);
                if (hit.isEmpty()) {
                    continue;
                }
                Map<String, Object> summary = new LinkedHashMap<>();
                copyIfPresent(hit, summary, "documentName");
                copyIfPresent(hit, summary, "chunkLabel");
                copyIfPresent(hit, summary, "score");
                String content = normalizeText(hit.get("content"));
                if (StringUtils.hasText(content)) {
                    summary.put("snippet", shrinkForObservation(content, 120));
                }
                if (!summary.isEmpty()) {
                    evidence.add(summary);
                }
            }
            if (!evidence.isEmpty()) {
                appendObservationLine(builder, "evidence", JSON.toJSONString(evidence));
            }
        }
        return trimForPrompt(builder.toString(), maxPromptLength);
    }

    private String summarizeDatasetSummaryObservation(Map<String, Object> payload, int maxPromptLength) {
        StringBuilder builder = new StringBuilder();
        appendObservationLine(builder, "observationClass", "dataset-summary");
        appendObservationLine(builder, "datasetName", normalizeText(payload.get("datasetName")));
        appendObservationLine(builder, "sourceKind", normalizeText(payload.get("sourceKind")));
        appendObservationLine(builder, "summary", shrinkForObservation(normalizeText(payload.get("summary")), 240));
        appendObservationLine(
                builder,
                "relationDescription",
                shrinkForObservation(normalizeText(payload.get("relationDescription")), 240));
        List<Object> objects = asList(payload.get("candidateObjects"));
        if (!objects.isEmpty()) {
            List<Object> candidates = new ArrayList<>();
            for (Object item : limitList(objects, 4)) {
                Map<String, Object> object = asObject(item);
                if (object.isEmpty()) {
                    continue;
                }
                Map<String, Object> summary = new LinkedHashMap<>();
                copyIfPresent(object, summary, "objectCode");
                copyIfPresent(object, summary, "objectName");
                copyIfPresent(object, summary, "objectSource");
                List<Object> fields = asList(object.get("fields"));
                if (!fields.isEmpty()) {
                    summary.put("fields", limitList(fields, 8));
                }
                if (!summary.isEmpty()) {
                    candidates.add(summary);
                }
            }
            if (!candidates.isEmpty()) {
                appendObservationLine(builder, "candidateObjects", JSON.toJSONString(candidates));
            }
        }
        return trimForPrompt(builder.toString(), maxPromptLength);
    }

    private String summarizeDatasetSchemaObservation(Map<String, Object> payload, int maxPromptLength) {
        StringBuilder builder = new StringBuilder();
        appendObservationLine(builder, "observationClass", "dataset-schema");
        appendObservationLine(builder, "datasetName", normalizeText(payload.get("datasetName")));
        appendObservationLine(builder, "sourceKind", normalizeText(payload.get("sourceKind")));
        List<Object> objects = asList(payload.get("objects"));
        appendObservationLine(builder, "objectCount", String.valueOf(objects.size()));
        if (!objects.isEmpty()) {
            List<Object> schemaObjects = new ArrayList<>();
            for (Object item : limitList(objects, 3)) {
                Map<String, Object> object = asObject(item);
                if (object.isEmpty()) {
                    continue;
                }
                Map<String, Object> summary = new LinkedHashMap<>();
                copyIfPresent(object, summary, "objectCode");
                copyIfPresent(object, summary, "objectName");
                copyIfPresent(object, summary, "objectSource");
                List<Object> fields = asList(object.get("fields"));
                if (!fields.isEmpty()) {
                    summary.put("fieldNames", extractFieldNames(fields, 10));
                }
                List<Object> subObjects = asList(object.get("subObjects"));
                if (!subObjects.isEmpty()) {
                    summary.put("subObjects", extractSubObjectSummaries(subObjects, 3));
                }
                if (!summary.isEmpty()) {
                    schemaObjects.add(summary);
                }
            }
            if (!schemaObjects.isEmpty()) {
                appendObservationLine(builder, "objects", JSON.toJSONString(schemaObjects));
            }
        }
        return trimForPrompt(builder.toString(), maxPromptLength);
    }

    private String summarizeFileWriteObservation(Map<String, Object> payload, int maxPromptLength) {
        StringBuilder builder = new StringBuilder();
        appendObservationLine(builder, "success", normalizeText(payload.get("success")));
        appendObservationLine(builder, "action", normalizeText(payload.get("action")));
        appendObservationLine(builder, "errorCode", normalizeText(payload.get("errorCode")));
        appendObservationLine(
                builder, "errorMessage", shrinkForObservation(normalizeText(payload.get("errorMessage")), 320));

        Map<String, Object> data = asObject(payload.get("data"));
        appendObservationLine(builder, "path", normalizeText(data.get("path")));

        String errorCode = normalizeText(payload.get("errorCode"));
        if ("FILE_WRITE_PYTHON_BLOCKED".equalsIgnoreCase(errorCode)) {
            appendObservationLine(builder, "failureKind", "python-blocked");
        } else if ("false".equalsIgnoreCase(normalizeText(payload.get("success")))) {
            appendObservationLine(builder, "failureKind", "file-write-failed");
        }
        return trimForPrompt(builder.toString(), maxPromptLength);
    }

    private String summarizeRunPythonObservation(Map<String, Object> payload, int maxPromptLength) {
        StringBuilder builder = new StringBuilder();
        appendObservationLine(builder, "success", normalizeText(payload.get("success")));
        appendObservationLine(builder, "action", normalizeText(payload.get("action")));
        appendObservationLine(builder, "errorCode", normalizeText(payload.get("errorCode")));
        appendObservationLine(
                builder, "errorMessage", shrinkForObservation(normalizeText(payload.get("errorMessage")), 320));

        Map<String, Object> data = asObject(payload.get("data"));
        appendObservationLine(builder, "scriptPath", normalizeText(data.get("scriptPath")));
        appendObservationLine(builder, "args", JSON.toJSONString(limitList(asList(data.get("args")), 6)));
        appendObservationLine(builder, "exitCode", normalizeText(data.get("exitCode")));
        String textOutput = normalizeText(payload.get("textOutput"));
        if (!StringUtils.hasText(textOutput)) {
            textOutput = normalizeText(data.get("output"));
        }
        if (StringUtils.hasText(textOutput)) {
            appendObservationLine(builder, "textOutput", shrinkForObservation(textOutput, 480));
        }

        String errorCode = normalizeText(payload.get("errorCode"));
        if ("RUNTIME_TOOL_RUN_PYTHON_FORBIDDEN".equalsIgnoreCase(errorCode)) {
            appendObservationLine(builder, "failureKind", "runtime-forbidden");
        } else {
            String failureKind = resolveRunPythonFailureKind(payload, textOutput);
            if (StringUtils.hasText(failureKind)) {
                appendObservationLine(builder, "failureKind", failureKind);
            }
        }
        return trimForPrompt(builder.toString(), maxPromptLength);
    }

    private String resolveRunPythonFailureKind(Map<String, Object> payload, String textOutput) {
        if (payload == null || payload.isEmpty()) {
            return "";
        }
        if (!"false".equalsIgnoreCase(normalizeText(payload.get("success")))) {
            return "";
        }
        String errorCode = normalizeText(payload.get("errorCode"));
        String normalizedOutput = normalizeText(textOutput).toLowerCase();
        if ("RUN_PYTHON_EXIT_NON_ZERO".equalsIgnoreCase(errorCode)
                && (normalizedOutput.contains("no invoice pdf files found")
                        || normalizedOutput.contains("no pdf files found in the archive")
                        || normalizedOutput.contains("no pdf files found"))) {
            return "no-matching-pdf-found";
        }
        if ("RUN_PYTHON_EXIT_NON_ZERO".equalsIgnoreCase(errorCode)) {
            return "run-python-failed";
        }
        return "";
    }

    private String summarizeDatasetSqlObservation(Map<String, Object> payload, int maxPromptLength) {
        StringBuilder builder = new StringBuilder();
        appendObservationLine(builder, "success", normalizeText(payload.get("success")));
        appendObservationLine(builder, "action", normalizeText(payload.get("action")));
        appendObservationLine(builder, "datasetName", normalizeText(payload.get("datasetName")));
        appendObservationLine(builder, "sql", shrinkForObservation(normalizeText(payload.get("sql")), 320));
        if ("false".equalsIgnoreCase(normalizeText(payload.get("success")))) {
            appendObservationLine(builder, "errorType", normalizeText(payload.get("errorType")));
            appendObservationLine(
                    builder, "errorMessage", shrinkForObservation(normalizeText(payload.get("errorMessage")), 240));
            appendObservationLine(builder, "unknownColumnName", normalizeText(payload.get("unknownColumnName")));
            appendObservationLine(
                    builder,
                    "referencedObjectCodes",
                    JSON.toJSONString(limitList(asList(payload.get("referencedObjectCodes")), 5)));
            appendObservationLine(
                    builder,
                    "candidateObjects",
                    JSON.toJSONString(limitList(asList(payload.get("candidateObjects")), 3)));
            appendObservationLine(
                    builder, "schemaHints", JSON.toJSONString(limitList(asList(payload.get("schemaHints")), 2)));
            appendObservationLine(
                    builder,
                    "fieldCandidates",
                    JSON.toJSONString(limitList(asList(payload.get("fieldCandidates")), 6)));
            Map<String, Object> suggestedSchemaRequest = asObject(payload.get("suggestedSchemaRequest"));
            if (!suggestedSchemaRequest.isEmpty()) {
                appendObservationLine(builder, "suggestedSchemaRequest", JSON.toJSONString(suggestedSchemaRequest));
            }
            appendObservationLine(
                    builder,
                    "nextActionHint",
                    firstNonBlank(
                            normalizeText(payload.get("nextActionHint")),
                            "当前 SQL 可修复。请先读取 suggestedSchemaRequest/schemaHints/fieldCandidates，再继续 get_dataset_schema 或重写 SQL。"));
            return trimForPrompt(builder.toString(), maxPromptLength);
        }
        appendObservationLine(builder, "rowCount", normalizeText(payload.get("rowCount")));
        appendObservationLine(builder, "columns", JSON.toJSONString(limitList(asList(payload.get("columns")), 12)));
        appendObservationLine(builder, "rowsPreview", JSON.toJSONString(limitList(asList(payload.get("rows")), 3)));
        return trimForPrompt(builder.toString(), maxPromptLength);
    }

    private String extractHtmlTagContent(String content, Pattern pattern) {
        if (!StringUtils.hasText(content) || pattern == null) {
            return "";
        }
        Matcher matcher = pattern.matcher(content);
        if (!matcher.find()) {
            return "";
        }
        return shrinkForObservation(stripHtmlTags(matcher.group(1)), 240);
    }

    private List<String> extractHtmlHeadings(String content) {
        if (!StringUtils.hasText(content)) {
            return List.of();
        }
        Matcher matcher = HTML_HEADING_PATTERN.matcher(content);
        List<String> headings = new ArrayList<>();
        while (matcher.find() && headings.size() < 6) {
            String heading = shrinkForObservation(stripHtmlTags(matcher.group(2)), 120);
            if (StringUtils.hasText(heading)) {
                headings.add(heading);
            }
        }
        return List.copyOf(headings);
    }

    private String extractHtmlConclusion(String content) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        Matcher matcher = HTML_CONCLUSION_PATTERN.matcher(content);
        if (matcher.find()) {
            return stripHtmlTags(matcher.group(1));
        }
        return "";
    }

    private List<String> extractHtmlElementIds(String content) {
        if (!StringUtils.hasText(content)) {
            return List.of();
        }
        Matcher matcher =
                Pattern.compile("id=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE).matcher(content);
        List<String> ids = new ArrayList<>();
        Set<String> seen = new java.util.LinkedHashSet<>();
        while (matcher.find() && ids.size() < 10) {
            String id = normalizeText(matcher.group(1));
            if (StringUtils.hasText(id) && seen.add(id)) {
                ids.add(id);
            }
        }
        return List.copyOf(ids);
    }

    private String stripHtmlTags(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.replaceAll("(?is)<[^>]+>", " ").replaceAll("\\s+", " ").trim();
    }

    private boolean looksLikeHtml(String content) {
        if (!StringUtils.hasText(content)) {
            return false;
        }
        String normalized = content.trim().toLowerCase();
        return normalized.contains("<html") || normalized.contains("<body") || normalized.contains("<!doctype html");
    }

    private void appendObservationLine(StringBuilder builder, String key, String value) {
        if (builder == null || !StringUtils.hasText(key) || !StringUtils.hasText(value)) {
            return;
        }
        if (!builder.isEmpty()) {
            builder.append('\n');
        }
        builder.append(key).append(": ").append(value.trim());
    }

    private Map<String, Object> asObject(Object value) {
        if (value instanceof Map<?, ?> rawMap) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            rawMap.forEach((key, item) -> {
                if (key != null) {
                    normalized.put(String.valueOf(key), item);
                }
            });
            return normalized;
        }
        return Map.of();
    }

    private List<Object> asList(Object value) {
        if (value instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        return List.of();
    }

    private List<Object> limitList(List<Object> list, int limit) {
        if (list == null || list.isEmpty() || limit <= 0) {
            return List.of();
        }
        return list.size() <= limit ? List.copyOf(list) : List.copyOf(list.subList(0, limit));
    }

    private void copyIfPresent(Map<String, Object> source, Map<String, Object> target, String key) {
        if (source == null || target == null || !StringUtils.hasText(key) || !source.containsKey(key)) {
            return;
        }
        Object value = source.get(key);
        if (value != null && StringUtils.hasText(String.valueOf(value))) {
            target.put(key, value);
        }
    }

    private String shrinkForObservation(String text, int maxLength) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength) + "...";
    }

    private String trimForPrompt(String text, int maxPromptLength) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String normalized = text.trim();
        if (maxPromptLength <= 0 || normalized.length() <= maxPromptLength) {
            return normalized;
        }
        return normalized.substring(0, maxPromptLength) + "\n...[truncated]";
    }

    private String normalizeText(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String normalizeSingleLine(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.replaceAll("\\s+", " ").trim();
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private String firstMatchedGroup(String text, Pattern pattern) {
        if (!StringUtils.hasText(text) || pattern == null) {
            return "";
        }
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) {
            return "";
        }
        for (int index = 1; index <= matcher.groupCount(); index += 1) {
            String value = normalizeText(matcher.group(index));
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "";
    }

    private String sanitizeSkillContent(String content) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        String normalized = content.replace("\r\n", "\n").trim();
        int instructionHeaderIndex = normalized.indexOf(SKILL_INSTRUCTION_HEADER);
        if (instructionHeaderIndex >= 0) {
            normalized = normalized
                    .substring(instructionHeaderIndex + SKILL_INSTRUCTION_HEADER.length())
                    .trim();
        }
        int rawContentMarkerIndex = normalized.indexOf(SKILL_RAW_CONTENT_MARKER);
        if (rawContentMarkerIndex >= 0) {
            normalized = normalized
                    .substring(rawContentMarkerIndex + SKILL_RAW_CONTENT_MARKER.length())
                    .trim();
        }
        return normalized;
    }

    private String extractSkillRuntimeName(String content) {
        return firstMatchingLineValue(content, "- 运行时技能名：`", "`", "Current skill runtime name:");
    }

    private String extractSkillDisplayName(String content) {
        return firstMatchingLineValue(content, "# 技能：", "", "Current skill display name:");
    }

    private String extractSkillDescription(String content) {
        return firstMatchingLineValue(content, "- 描述：", "", "Skill description:");
    }

    private String firstMatchingLineValue(
            String content, String primaryPrefix, String primarySuffix, String secondaryPrefix) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        for (String rawLine : content.replace("\r\n", "\n").replace('\r', '\n').split("\\n")) {
            String line = rawLine == null ? "" : rawLine.trim();
            if (!StringUtils.hasText(line)) {
                continue;
            }
            if (StringUtils.hasText(primaryPrefix) && line.startsWith(primaryPrefix)) {
                String value = line.substring(primaryPrefix.length()).trim();
                if (StringUtils.hasText(primarySuffix) && value.endsWith(primarySuffix)) {
                    value = value.substring(0, value.length() - primarySuffix.length())
                            .trim();
                }
                return normalizeSingleLine(value);
            }
            if (StringUtils.hasText(secondaryPrefix) && line.startsWith(secondaryPrefix)) {
                return normalizeSingleLine(
                        line.substring(secondaryPrefix.length()).trim());
            }
        }
        return "";
    }

    private List<String> extractSkillToolNames(String content) {
        if (!StringUtils.hasText(content)) {
            return List.of();
        }
        List<String> toolNames = new ArrayList<>();
        boolean inToolSection = false;
        for (String rawLine : content.split("\\R")) {
            String line = normalizeText(rawLine);
            if (!StringUtils.hasText(line)) {
                if (inToolSection) {
                    break;
                }
                continue;
            }
            if (line.startsWith("Current skill available tools:")
                    || line.startsWith("当前技能会动态绑定以下类型的工具")
                    || line.startsWith("当前技能可用工具")) {
                inToolSection = true;
                continue;
            }
            if (!inToolSection || !line.startsWith("- ")) {
                continue;
            }
            String normalized = line.substring(2).trim();
            int colonIndex = normalized.indexOf(':');
            if (colonIndex >= 0) {
                normalized = normalized.substring(0, colonIndex).trim();
            }
            int bracketIndex = normalized.indexOf(" (");
            if (bracketIndex >= 0) {
                normalized = normalized.substring(0, bracketIndex).trim();
            }
            if (StringUtils.hasText(normalized) && !toolNames.contains(normalized)) {
                toolNames.add(normalized);
            }
            if (toolNames.size() >= MAX_SKILL_TOOLS) {
                break;
            }
        }
        return List.copyOf(toolNames);
    }

    private List<String> extractSkillRuleHighlights(String content) {
        if (!StringUtils.hasText(content)) {
            return List.of();
        }
        List<String> rules = new ArrayList<>();
        for (String rawLine : content.split("\\R")) {
            String line = normalizeText(rawLine);
            if (!StringUtils.hasText(line)) {
                continue;
            }
            if ((line.startsWith("-") || line.matches("^\\d+\\..*"))
                    && SKILL_RULE_KEYWORDS.stream().anyMatch(line::contains)) {
                String normalized = line.startsWith("-")
                        ? line.substring(1).trim()
                        : line.replaceFirst("^\\d+\\.", "").trim();
                if (StringUtils.hasText(normalized) && !rules.contains(normalized)) {
                    rules.add(shrinkForObservation(normalized, 120));
                }
            }
            if (rules.size() >= MAX_SKILL_RULES) {
                break;
            }
        }
        return List.copyOf(rules);
    }

    private List<String> extractFieldNames(List<Object> fields, int limit) {
        List<String> names = new ArrayList<>();
        for (Object item : limitList(fields, limit)) {
            Map<String, Object> field = asObject(item);
            String fieldName =
                    firstNonBlank(normalizeText(field.get("fieldName")), normalizeText(field.get("fieldLabel")));
            if (StringUtils.hasText(fieldName)) {
                names.add(fieldName);
            }
        }
        return List.copyOf(names);
    }

    private List<Object> extractSubObjectSummaries(List<Object> subObjects, int limit) {
        List<Object> results = new ArrayList<>();
        for (Object item : limitList(subObjects, limit)) {
            Map<String, Object> subObject = asObject(item);
            if (subObject.isEmpty()) {
                continue;
            }
            Map<String, Object> summary = new LinkedHashMap<>();
            copyIfPresent(subObject, summary, "objectCode");
            copyIfPresent(subObject, summary, "objectName");
            List<Object> fields = asList(subObject.get("fields"));
            if (!fields.isEmpty()) {
                summary.put("fieldNames", extractFieldNames(fields, 8));
            }
            if (!summary.isEmpty()) {
                results.add(summary);
            }
        }
        return List.copyOf(results);
    }

    public record ObservationSummaryOptions(boolean allowCodeExecution, int maxPromptLength, String userRequest) {}
}
