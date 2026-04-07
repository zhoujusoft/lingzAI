package lingzhou.agent.backend.capability.rag.chunk.tool;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;

public class LawDocumentStructureAnalyzer {

    private static final Pattern LAW_TITLE_PATTERN = Pattern.compile("^.*?(条例|规定|办法|法|解释|决定)$");
    private static final Pattern CHAPTER_PATTERN =
            Pattern.compile("^第([一二三四五六七八九十百千万零两〇0-9]+)章\\s*(.*)$");
    private static final Pattern ARTICLE_PATTERN =
            Pattern.compile("^第([一二三四五六七八九十百千万零两〇0-9]+)条\\s*(.*)$");

    public LawMetadata analyze(String documentName, List<String> headings, String content) {
        String lawTitle = resolveLawTitle(documentName, headings);
        String chapterHeading = findHeading(headings, CHAPTER_PATTERN);
        String articleHeading = findHeading(headings, ARTICLE_PATTERN);
        if (!StringUtils.isNotBlank(articleHeading)) {
            articleHeading = extractArticleHeadingFromContent(content);
        }

        Integer chapterNo = extractNumber(chapterHeading, CHAPTER_PATTERN);
        String chapterTitle = extractTail(chapterHeading, CHAPTER_PATTERN);
        Integer articleNo = extractNumber(articleHeading, ARTICLE_PATTERN);
        String articleCn = normalizeHeading(articleHeading);
        String articleKey = StringUtils.isAnyBlank(lawTitle, articleCn) ? null : lawTitle + "#" + articleCn;

        return new LawMetadata(
                lawTitle,
                buildLawAlias(lawTitle),
                chapterNo,
                normalizeHeading(chapterHeading),
                StringUtils.defaultIfBlank(chapterTitle, normalizeHeading(chapterHeading)),
                articleNo,
                articleCn,
                articleKey,
                articleNo == null ? null : "FULL_ARTICLE");
    }

    public boolean isLikelyLawDocument(String documentName, List<String> headings) {
        if (isLawTitle(documentName)) {
            return true;
        }
        if (headings == null || headings.isEmpty()) {
            return false;
        }
        return headings.stream().anyMatch(heading -> matches(heading, ARTICLE_PATTERN) || matches(heading, CHAPTER_PATTERN));
    }

    private String resolveLawTitle(String documentName, List<String> headings) {
        if (headings != null) {
            for (String heading : headings) {
                if (isLawTitle(heading)) {
                    return normalizeDocumentName(heading);
                }
            }
        }
        return isLawTitle(documentName) ? normalizeDocumentName(documentName) : normalizeDocumentName(documentName);
    }

    private boolean isLawTitle(String text) {
        String normalized = normalizeDocumentName(text);
        return StringUtils.isNotBlank(normalized) && LAW_TITLE_PATTERN.matcher(normalized).matches();
    }

    private String buildLawAlias(String lawTitle) {
        if (StringUtils.isBlank(lawTitle)) {
            return null;
        }
        if (lawTitle.startsWith("中华人民共和国") && lawTitle.length() > "中华人民共和国".length()) {
            return lawTitle.substring("中华人民共和国".length());
        }
        return lawTitle;
    }

    private String findHeading(List<String> headings, Pattern pattern) {
        if (headings == null) {
            return null;
        }
        for (int i = headings.size() - 1; i >= 0; i--) {
            String heading = headings.get(i);
            if (matches(heading, pattern)) {
                return heading;
            }
        }
        return null;
    }

    private boolean matches(String text, Pattern pattern) {
        return StringUtils.isNotBlank(text) && pattern.matcher(text.trim()).matches();
    }

    private String extractArticleHeadingFromContent(String content) {
        if (StringUtils.isBlank(content)) {
            return null;
        }
        for (String line : content.split("\\R")) {
            String trimmed = line == null ? "" : line.trim();
            if (matches(trimmed, ARTICLE_PATTERN)) {
                return trimmed;
            }
        }
        return null;
    }

    private Integer extractNumber(String text, Pattern pattern) {
        if (StringUtils.isBlank(text)) {
            return null;
        }
        Matcher matcher = pattern.matcher(text.trim());
        if (!matcher.matches()) {
            return null;
        }
        return toNumber(matcher.group(1));
    }

    private String extractTail(String text, Pattern pattern) {
        if (StringUtils.isBlank(text)) {
            return null;
        }
        Matcher matcher = pattern.matcher(text.trim());
        if (!matcher.matches()) {
            return null;
        }
        return StringUtils.trimToNull(matcher.group(2));
    }

    private String normalizeHeading(String text) {
        return StringUtils.trimToNull(text);
    }

    private String normalizeDocumentName(String text) {
        if (StringUtils.isBlank(text)) {
            return null;
        }
        String normalized = text.trim();
        int extensionIndex = normalized.lastIndexOf('.');
        if (extensionIndex > 0) {
            normalized = normalized.substring(0, extensionIndex);
        }
        return StringUtils.trimToNull(normalized);
    }

    private Integer toNumber(String rawValue) {
        if (StringUtils.isBlank(rawValue)) {
            return null;
        }
        String value = rawValue.trim();
        if (value.matches("\\d+")) {
            return Integer.parseInt(value);
        }

        int result = 0;
        int section = 0;
        int number = 0;
        for (char ch : value.toCharArray()) {
            int digit = chineseDigit(ch);
            if (digit >= 0) {
                number = digit;
                continue;
            }
            int unit = chineseUnit(ch);
            if (unit == 0) {
                continue;
            }
            if (unit == 10000) {
                section = (section + Math.max(number, 1)) * unit;
                result += section;
                section = 0;
            } else {
                section += Math.max(number, 1) * unit;
            }
            number = 0;
        }
        return result + section + number;
    }

    private int chineseDigit(char ch) {
        return switch (ch) {
            case '零', '〇' -> 0;
            case '一' -> 1;
            case '二', '两' -> 2;
            case '三' -> 3;
            case '四' -> 4;
            case '五' -> 5;
            case '六' -> 6;
            case '七' -> 7;
            case '八' -> 8;
            case '九' -> 9;
            default -> -1;
        };
    }

    private int chineseUnit(char ch) {
        return switch (ch) {
            case '十' -> 10;
            case '百' -> 100;
            case '千' -> 1000;
            case '万' -> 10000;
            default -> 0;
        };
    }

    public record LawMetadata(
            String lawTitle,
            String lawAlias,
            Integer chapterNo,
            String chapterHeading,
            String chapterTitle,
            Integer articleNo,
            String articleCn,
            String articleKey,
            String articleTextType) {}
}
