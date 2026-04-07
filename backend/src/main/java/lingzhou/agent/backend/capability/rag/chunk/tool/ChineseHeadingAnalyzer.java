package lingzhou.agent.backend.capability.rag.chunk.tool;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFStyle;
import org.apache.poi.xwpf.usermodel.XWPFStyles;

public class ChineseHeadingAnalyzer {

    // 上下文状态
    private int lastHeadingLevel = 0;
    private int currentSectionLevel = 0;
    private final Deque<Integer> headingStack = new ArrayDeque<>();

    private boolean startBool = false;

    // 关键词配置
    private static final String[] LEVEL1_KEYWORDS = {"摘要", "目录", "目次", "结论", "参考文献", "附录", "致谢", "前言", "引言", "总结"};
    private static final String[] LEVEL2_KEYWORDS = {"概述", "背景", "方法", "结果", "讨论", "建议", "分析", "设计", "实现"};
    private static final String[] LEVEL3_KEYWORDS = {"步骤", "流程", "示例", "说明", "定义", "注意", "要点", "建议", "问题"};

    // 主入口：获取标题级别
    public int getHeadingLevel(XWPFParagraph paragraph) {
        // 跳过空段落
        if (paragraph == null || paragraph.getText().trim().isEmpty()) {
            return 0;
        }

        // 获取基础级别（样式+内容）
        int baseLevel = getBaseHeadingLevel(paragraph);
        return finalizeHeadingLevel(baseLevel, paragraph.getText());
    }

    public int getHeadingLevel(String text) {
        int baseLevel = getContentHeadingLevel(text);
        return finalizeHeadingLevel(baseLevel, text);
    }

    // 获取基础标题级别
    private int getBaseHeadingLevel(XWPFParagraph paragraph) {
        // 1. 基于样式的级别判定
        int styleLevel = getStyleHeadingLevel(paragraph);
        if (styleLevel > 0) {
            return styleLevel;
        }

        // 2. 基于内容的级别判定
        return getContentHeadingLevel(paragraph);
    }

    // 基于样式的标题判定
    private int getStyleHeadingLevel(XWPFParagraph paragraph) {
        String styleId = paragraph.getStyle();
        if (styleId == null) return 0;

        XWPFDocument doc = paragraph.getDocument();
        XWPFStyles styles = doc.getStyles();
        if (styles == null) return 0;

        XWPFStyle style = styles.getStyle(styleId);
        if (style == null) return 0;

        String styleName = style.getName();
        if (styleName != null) {
            // 支持“Heading1”、“标题1”、“样式1”、“heading 1”、“标题 1”、“样式 1”等
            String s = styleName.replaceAll("\\s+", "").toLowerCase();
            // 英文或中文“标题”或“样式”或“heading”开头，后面跟数字
            if (s.matches("^(heading|标题|样式)[0-9]+$")) {
                try {
                    return Integer.parseInt(s.replaceAll("^(heading|标题|样式)", ""));
                } catch (NumberFormatException e) {
                    return 0;
                }
            }
        }
        return 0;
    }

    // 基于内容的标题判定
    private int getContentHeadingLevel(XWPFParagraph paragraph) {
        return getContentHeadingLevel(paragraph.getText());
    }

    private int getContentHeadingLevel(String rawText) {
        String text = rawText == null ? "" : rawText.trim();
        if (text.isEmpty()) return 0;

        // 1. 长度检测（短文本更可能是标题）
        boolean isShortText = text.length() <= 60;

        // 2. 编号模式检测
        int numberingLevel = detectNumberingPattern(text);
        if (numberingLevel > 0 && isShortText) {
            return numberingLevel;
        }

        // 3. 关键词检测
        int keywordLevel = detectHeadingKeywords(text);
        if (keywordLevel > 0 && isShortText) return keywordLevel;

        // 4. 章节特征检测
        int chapterLevel = detectChapterFeature(text);
        if (chapterLevel > 0 && isShortText) return chapterLevel;

        // 5. 短文本默认作为标题
        return 0;
    }

    // 检测编号模式
    private int detectNumberingPattern(String text) {
        String t = text.trim();

        // 六级标题：1.1.1.1.1.1 或 1-1-1-1-1-1
        if (t.matches("^\\d+([\\.\\-]\\d+){5}\\s+.*") && hasReasonableHeadingTail(t, "^\\d+([\\.\\-]\\d+){5}\\s+")) {
            return 6;
        }
        // 五级标题：1.1.1.1.1 或 1-1-1-1-1
        if (t.matches("^\\d+([\\.\\-]\\d+){4}\\s+.*") && hasReasonableHeadingTail(t, "^\\d+([\\.\\-]\\d+){4}\\s+")) {
            return 5;
        }
        // 四级标题：1.1.1.1 或 1-1-1-1
        if (t.matches("^\\d+([\\.\\-]\\d+){3}\\s+.*") && hasReasonableHeadingTail(t, "^\\d+([\\.\\-]\\d+){3}\\s+")) {
            return 4;
        }
        // 三级标题：1.1.1 或 1-1-1
        if (t.matches("^\\d+([\\.\\-]\\d+){2}\\s+.*") && hasReasonableHeadingTail(t, "^\\d+([\\.\\-]\\d+){2}\\s+")) {
            return 3;
        }
        // 二级标题：1.1 或 1-1 或 （1）
        if ((t.matches("^\\d+[\\.\\-]\\d+\\s+.*") && hasReasonableHeadingTail(t, "^\\d+[\\.\\-]\\d+\\s+"))
                || (t.matches("^（\\d+）\\s+.*") && hasReasonableHeadingTail(t, "^（\\d+）\\s+"))) {
            return 2;
        }
        // 一级标题：1 标题、一、标题、（一）标题
        if ((t.matches("^\\d+\\s+.*") && hasReasonableHeadingTail(t, "^\\d+\\s+"))
                || (t.matches("^[一二三四五六七八九十]+[、.]\\s*.*")
                        && hasReasonableHeadingTail(t, "^[一二三四五六七八九十]+[、.]\\s*"))
                || (t.matches("^（[一二三四五六七八九十]+）\\s+.*")
                        && hasReasonableHeadingTail(t, "^（[一二三四五六七八九十]+）\\s+"))) {
            return 1;
        }
        if (t.matches("^第[一二三四五六七八九十百千万零0-9]+章\\s*.*")) {
            return 1;
        }
        if (t.matches("^第[一二三四五六七八九十百千万零0-9]+节\\s*.*")) {
            return 2;
        }
        if (t.matches("^第[一二三四五六七八九十百千万零0-9]+条\\s*.*")) {
            return 3;
        }
        if (t.matches("^第[一二三四五六七八九十百千万零0-9]+款\\s*.*")) {
            return 4;
        }
        return 0;
    }

    // 检测标题关键词
    private int detectHeadingKeywords(String text) {
        String normalized = normalizeKeywordText(text);
        if (normalized.isEmpty()) {
            return 0;
        }

        // 一级标题关键词
        for (String kw : LEVEL1_KEYWORDS) {
            if (normalized.equals(kw)) {
                return 1;
            }
        }

        // 二级标题关键词
        for (String kw : LEVEL2_KEYWORDS) {
            if (normalized.equals(kw)) {
                return 2;
            }
        }

        // 三级标题关键词
        for (String kw : LEVEL3_KEYWORDS) {
            if (normalized.equals(kw)) {
                return 3;
            }
        }

        return 0;
    }

    // 检测章节特征
    private int detectChapterFeature(String text) {
        if (text.matches("^第?[一二三四五六七八九十百千万零0-9]+章\\s*.*")) {
            return 1;
        }

        if (text.matches("^第?[一二三四五六七八九十百千万零0-9]+节\\s*.*")) {
            return 2;
        }

        if (text.matches("^第?[一二三四五六七八九十百千万零0-9]+条\\s*.*")
                || text.matches("^第?[一二三四五六七八九十百千万零0-9]+款\\s*.*")) {
            return 3;
        }

        return 0;
    }

    // 上下文感知的级别调整
    private int adjustLevelWithContext(int baseLevel, XWPFParagraph paragraph) {
        String text = paragraph == null ? "" : paragraph.getText().trim();
        return adjustLevelWithContext(baseLevel, text);
    }

    private int adjustLevelWithContext(int baseLevel, String text) {
        String normalizedText = text == null ? "" : text.trim();

        // 1. 文档起始标题特殊处理
        if (lastHeadingLevel == 0) {
            // 确保文档第一个标题至少是1级
            return Math.max(baseLevel, 1);
        }

        // 2. 新章节检测（重置为1级）
        if (isNewChapter(normalizedText)) {
            return 1;
        }

        // 3. 编号连续性检测
        int numberingLevel = detectNumberingContinuity(normalizedText);
        if (numberingLevel > 0) {
            return numberingLevel;
        }

        // 4. 层级跳跃限制
        if (baseLevel > lastHeadingLevel + 1) {
            // 禁止跨级跳跃（如从2级直接到4级）
            return lastHeadingLevel + 1;
        }

        // 5. 同级内容延续
        if (currentSectionLevel > 0 && baseLevel == lastHeadingLevel) {
            return currentSectionLevel;
        }

        return baseLevel;
    }

    // 检测是否为新章节
    private boolean isNewChapter(String text) {
        // 中文章节模式：第X章
        return Pattern.compile("^第[一二三四五六七八九十零百千]+章").matcher(text).find();
    }

    // 检测编号连续性
    private int detectNumberingContinuity(String text) {
        // 中文编号：第一章、第二章
        Matcher chineseMatcher = Pattern.compile("第([一二三四五六七八九十零百千]+)章").matcher(text);
        if (chineseMatcher.find()) {
            int chapterNum = chineseToNumber(chineseMatcher.group(1));
            return (chapterNum == 1) ? 1 : lastHeadingLevel;
        }

        // 数字编号：1.、1.1、1.1.1
        Matcher numericMatcher =
                Pattern.compile("^(\\d+)(?:\\.(\\d+))?(?:\\.(\\d+))?").matcher(text);
        if (numericMatcher.find()) {
            int level = 0;
            if (numericMatcher.group(1) != null) level++;
            if (numericMatcher.group(2) != null) level++;
            if (numericMatcher.group(3) != null) level++;

            // 检查是否连续
            if (level == lastHeadingLevel || level == lastHeadingLevel + 1) {
                return level;
            }
        }

        // 中文数字：一、二、三
        Matcher chineseNumMatcher = Pattern.compile("^[一二三四五六七八九十]+、").matcher(text);
        if (chineseNumMatcher.find()) {
            return lastHeadingLevel; // 保持同级
        }

        return 0;
    }

    // 更新上下文状态
    private void updateContextState(int currentLevel) {
        // 1. 更新上一个标题级别
        lastHeadingLevel = currentLevel;

        // 2. 维护标题栈（用于层级跟踪）
        if (!headingStack.isEmpty() && currentLevel <= headingStack.peek()) {
            // 遇到同级或更高级标题，弹出低级标题
            while (!headingStack.isEmpty() && currentLevel <= headingStack.peek()) {
                headingStack.pop();
            }
        }
        headingStack.push(currentLevel);

        // 3. 更新当前章节级别
        currentSectionLevel = currentLevel;
    }

    private int finalizeHeadingLevel(int baseLevel, String text) {
        if (baseLevel == 0) {
            return 0;
        }

        int finalLevel = adjustLevelWithContext(baseLevel, text);
        updateContextState(finalLevel);
        return finalLevel;
    }

    private String normalizeKeywordText(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("\\s+", "").replaceAll("[：:;；,.，。]+$", "");
    }

    private boolean hasReasonableHeadingTail(String text, String prefixRegex) {
        String tail = text.replaceFirst(prefixRegex, "").trim();
        if (tail.isEmpty()) {
            return false;
        }
        if (tail.length() > 30) {
            return false;
        }
        return !tail.matches(".*[；;。.!！？]$");
    }

    // 中文数字转阿拉伯数字
    private int chineseToNumber(String chinese) {
        Map<Character, Integer> map = new HashMap<>();
        map.put('零', 0);
        map.put('一', 1);
        map.put('二', 2);
        map.put('三', 3);
        map.put('四', 4);
        map.put('五', 5);
        map.put('六', 6);
        map.put('七', 7);
        map.put('八', 8);
        map.put('九', 9);
        map.put('十', 10);
        map.put('百', 100);
        map.put('千', 1000);

        int result = 0;
        int temp = 0;
        int total = 0;

        for (char c : chinese.toCharArray()) {
            int num = map.getOrDefault(c, -1);
            if (num < 0) continue;

            if (num < 10) {
                temp = num;
            } else if (num == 10) {
                total += (temp == 0 ? 1 : temp) * 10;
                temp = 0;
            } else {
                total += temp * num;
                temp = 0;
            }
        }
        return total + temp;
    }

    // 重置分析器状态（处理新文档时调用）
    public void reset() {
        lastHeadingLevel = 0;
        currentSectionLevel = 0;
        headingStack.clear();
    }
}
