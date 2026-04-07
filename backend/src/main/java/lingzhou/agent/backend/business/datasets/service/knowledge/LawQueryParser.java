package lingzhou.agent.backend.business.datasets.service.knowledge;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;

public class LawQueryParser {

    private static final Pattern ARTICLE_PATTERN = Pattern.compile(
            "(?:(?<law>[\\p{IsHan}A-Za-z0-9《》\\-（）()]{2,40}?)(?:中|里|内|的)?\\s*)?第?(?<article>[一二三四五六七八九十百千万零两〇0-9]+)条");

    public ParsedLawQuery parse(String query) {
        if (StringUtils.isBlank(query)) {
            return ParsedLawQuery.notMatched();
        }
        Matcher matcher = ARTICLE_PATTERN.matcher(query.trim());
        if (!matcher.find()) {
            return ParsedLawQuery.notMatched();
        }

        Integer articleNo = toNumber(matcher.group("article"));
        if (articleNo == null || articleNo <= 0) {
            return ParsedLawQuery.notMatched();
        }

        String lawTitleCandidate = normalizeLawTitleCandidate(matcher.group("law"));
        return new ParsedLawQuery(true, lawTitleCandidate, articleNo);
    }

    private String normalizeLawTitleCandidate(String raw) {
        if (StringUtils.isBlank(raw)) {
            return null;
        }
        String value = raw.trim()
                .replace("《", "")
                .replace("》", "")
                .replaceAll("(中|里|内|的)$", "")
                .trim();
        return StringUtils.trimToNull(value);
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

    public record ParsedLawQuery(boolean matched, String lawTitleCandidate, Integer articleNo) {
        private static ParsedLawQuery notMatched() {
            return new ParsedLawQuery(false, null, null);
        }
    }
}
