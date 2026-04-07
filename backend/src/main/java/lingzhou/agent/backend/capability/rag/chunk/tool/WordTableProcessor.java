package lingzhou.agent.backend.capability.rag.chunk.tool;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTc;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STMerge;

@Slf4j
public class WordTableProcessor {

    // 中文数字映射
    private static final Map<String, Integer> CHINESE_NUMBER_MAP = new HashMap<>();

    static {
        CHINESE_NUMBER_MAP.put("零", 0);
        CHINESE_NUMBER_MAP.put("一", 1);
        CHINESE_NUMBER_MAP.put("二", 2);
        CHINESE_NUMBER_MAP.put("三", 3);
        CHINESE_NUMBER_MAP.put("四", 4);
        CHINESE_NUMBER_MAP.put("五", 5);
        CHINESE_NUMBER_MAP.put("六", 6);
        CHINESE_NUMBER_MAP.put("七", 7);
        CHINESE_NUMBER_MAP.put("八", 8);
        CHINESE_NUMBER_MAP.put("九", 9);
        CHINESE_NUMBER_MAP.put("十", 10);
        CHINESE_NUMBER_MAP.put("百", 100);
        CHINESE_NUMBER_MAP.put("千", 1000);
        CHINESE_NUMBER_MAP.put("万", 10000);
    }

    // 中文数字正则表达式
    private static final Pattern CHINESE_NUMBER_PATTERN = Pattern.compile("^[零一二三四五六七八九十百千万]+(\\s*[点半][零一二三四五六七八九])?$");

    private static final List<PatternType> PATTERNS = Arrays.asList(
            new PatternType(Pattern.compile("^(20|19)[0-9]{2}[年/-][0-9]{1,2}[月/-][0-9]{1,2}日*$"), "Dt"),
            new PatternType(Pattern.compile("^(20|19)[0-9]{2}年$"), "Dt"),
            new PatternType(Pattern.compile("^(20|19)[0-9]{2}[年/-][0-9]{1,2}月*$"), "Dt"),
            new PatternType(Pattern.compile("^[0-9]{1,2}[月/-][0-9]{1,2}日*$"), "Dt"),
            new PatternType(Pattern.compile("^第*[一二三四1-4]季度$"), "Dt"),
            new PatternType(Pattern.compile("^(20|19)[0-9]{2}年*[一二三四1-4]季度$"), "Dt"),
            new PatternType(Pattern.compile("^(20|19)[0-9]{2}[ABCDE]$"), "Dt"),
            new PatternType(Pattern.compile("^[0-9.,+%/ -]+$"), "Nu"),
            new PatternType(Pattern.compile("^[0-9A-Z/\\._~-]+$"), "Ca"),
            new PatternType(Pattern.compile("^[A-Z]*[a-z' -]+$"), "En"),
            new PatternType(Pattern.compile("^[0-9.,+-]+[0-9A-Za-z/\\$￥%<>（）()' -]+$"), "NE"),
            new PatternType(Pattern.compile("^.{1}$"), "Sg"));

    static class PatternType {
        Pattern pattern;
        String type;

        PatternType(Pattern pattern, String type) {
            this.pattern = pattern;
            this.type = type;
        }
    }

    public static String getBlockType(String text) {
        if (text == null || text.trim().isEmpty()) {
            return "Ot";
        }
        text = text.trim();

        for (PatternType pt : PATTERNS) {
            if (pt.pattern.matcher(text).matches()) {
                return pt.type;
            }
        }

        if (text.length() > 3) {
            return text.length() < 12 ? "Tx" : "Lx";
        }
        return "Ot";
    }

    // 生成块数据

    public static List<String> extractTableContent(XWPFTable table) {
        List<List<String>> df = new ArrayList<>();

        // 确定最大列数9
        int maxColumns = 0;

        // 确定最大列数
        for (XWPFTableRow row : table.getRows()) {
            int cellCount = row.getTableCells().size();
            if (cellCount > maxColumns) {
                maxColumns = cellCount;
            }
        }

        // 解析每一行数据数据
        for (int rowIndex = 0; rowIndex < table.getRows().size(); rowIndex++) {
            XWPFTableRow row = table.getRows().get(rowIndex);
            List<String> rowData = new ArrayList<>();
            int colIndex = 0;
            // 记录每列是否为跨行单元格（restart 或 continue）
            List<Boolean> isRowSpannedColumn = new ArrayList<>(Collections.nCopies(maxColumns, false));

            for (XWPFTableCell cell : row.getTableCells()) {
                // 获取包含换行符的文本
                StringBuilder textBuilder = new StringBuilder();
                for (XWPFParagraph paragraph : cell.getParagraphs()) {
                    String paragraphText = paragraph.getText();
                    if (paragraphText != null && !paragraphText.trim().isEmpty()) {
                        textBuilder.append(paragraphText).append("\n");
                    }
                }
                String text = textBuilder.toString().trim();

                // 获取跨列数
                int span = 1;
                if (cell.getCTTc().getTcPr() != null && cell.getCTTc().getTcPr().getGridSpan() != null) {
                    span = cell.getCTTc().getTcPr().getGridSpan().getVal().intValue();
                }

                // 检查是否跨行
                boolean isRowSpanned = cell.getCTTc().getTcPr() != null
                        && cell.getCTTc().getTcPr().getVMerge() != null;

                // 处理跨列
                for (int i = 0; i < span && colIndex + i < maxColumns; i++) {
                    if (isRowSpanned) {
                        isRowSpannedColumn.set(colIndex + i, true); // 标记为跨行列
                    }
                    if (colIndex + i < rowData.size()) {
                        rowData.set(colIndex + i, text);
                    } else {
                        rowData.add(text);
                    }
                }
                colIndex += span;
            }
            // 补齐列数
            while (rowData.size() < maxColumns) {
                rowData.add("");
                isRowSpannedColumn.add(false); // 补齐的列不涉及跨行
            }

            // 格式化表头行，添加单位间的空格
            if (rowIndex == 0 || rowIndex == 1) {
                for (int j = 0; j < rowData.size(); j++) {
                    rowData.set(
                            j,
                            rowData.get(j)
                                    .replaceAll("kV(?= |$)", "kV ")
                                    .replaceAll("kW(?= |$)", "kW ")
                                    .replaceAll("%(?= |$)", " %")
                                    .replaceAll("·", " ")
                                    .trim());
                }
            }

            // 填充空值，仅对非跨行单元格使用上一行值
            if (rowIndex > 0) {
                List<String> prevRow = df.get(rowIndex - 1);
                for (int j = 0; j < maxColumns; j++) {
                    // 仅对非跨行单元格填充上一行的值
                    if (rowData.get(j).isEmpty()
                            && isRowSpannedColumn.get(j)
                            && !prevRow.get(j).isEmpty()) {
                        rowData.set(j, prevRow.get(j));
                    }
                }
            }

            df.add(rowData);
        }

        if (df.size() < 2) {
            return new ArrayList<>();
        }

        // 分析每列单元格的数据类型
        List<List<String>> dataTypes = new ArrayList<>();
        for (int i = 1; i < df.size(); i++) {
            List<String> rowTypes = new ArrayList<>();
            for (String cell : df.get(i)) {
                rowTypes.add(getBlockType(cell));
            }
            dataTypes.add(rowTypes);
        }
        //        System.out.println("dataTypes:" + dataTypes);

        // 统计每列的主要类型
        List<String> columnTypes = new ArrayList<>();
        for (int j = 0; j < maxColumns; j++) {
            Map<String, Integer> typeCounter = new HashMap<>();
            for (List<String> rowTypes : dataTypes) {
                if (j < rowTypes.size()) {
                    typeCounter.merge(rowTypes.get(j), 1, Integer::sum);
                }
            }
            String mostCommon = typeCounter.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse("Ot");
            columnTypes.add(mostCommon);
        }
        //        System.out.println("columnTypes:" + columnTypes);

        // 表头行识别
        List<Integer> headerRows = new ArrayList<>();
        headerRows.add(0);

        if (columnTypes.contains("Nu")) {
            for (int r = 1; r < df.size(); r++) {
                List<String> row = df.get(r);
                // 检查第一列是否为数字
                boolean isFirstColumnNumeric =
                        !row.isEmpty() && getBlockType(row.get(0)).equals("Nu");
                if (isFirstColumnNumeric) {
                    continue; // 第一列为数字，跳过该行，不视为表头
                }

                // 继续检查行的主要类型
                List<String> rowTypes =
                        row.stream().map(WordTableProcessor::getBlockType).collect(Collectors.toList());
                Map<String, Long> typeCounter =
                        rowTypes.stream().collect(Collectors.groupingBy(t -> t, Collectors.counting()));
                String mainType = typeCounter.entrySet().stream()
                        .max(Map.Entry.comparingByValue())
                        .map(Map.Entry::getKey)
                        .orElse("Ot");
                if (!mainType.equals("Nu")) {
                    headerRows.add(r);
                }
            }
        }
        //        System.out.println("headerRows:" + headerRows);

        // Generate table content
        List<String> lines = new ArrayList<>();
        for (int i = 1; i < df.size(); i++) {
            if (headerRows.contains(i)) {
                continue;
            }

            List<String> headers = new ArrayList<>();
            for (int j = 0; j < maxColumns; j++) {
                List<String> headerParts = new ArrayList<>();
                for (int h : headerRows) {
                    if (h < i) {
                        String headerText =
                                j < df.get(h).size() ? df.get(h).get(j).trim() : "";
                        if (!headerText.isEmpty() && !headerParts.contains(headerText)) {
                            headerParts.add(headerText);
                        }
                    }
                }
                String headerText = String.join(",", headerParts);
                headers.add(headerText.isEmpty() ? "" : headerText + ": ");
            }

            List<String> cells = new ArrayList<>();
            for (int j = 0; j < maxColumns; j++) { // 使用 maxColumns
                String cellText = df.get(i).get(j).trim();
                if (!cellText.isEmpty()) {
                    cells.add(headers.get(j) + cellText);
                }
            }

            if (!cells.isEmpty()) {
                lines.add(String.join("; ", cells));
            }
        }
        //        System.out.println("lines:" + lines);

        // Chunking strategy
        int colCount = maxColumns;
        if (colCount > 3) {
            return lines;
        } else {
            return lines.isEmpty() ? new ArrayList<>() : Collections.singletonList(String.join("\n", lines));
        }
    }

    // 块转json
    public static JSONArray convertToJsonList(List<String> tableContents) {
        if (tableContents == null || tableContents.isEmpty()) {
            return new JSONArray();
        }

        JSONArray jsonList = new JSONArray();

        for (String content : tableContents) {
            if (content == null || content.trim().isEmpty()) {
                continue;
            }

            // 使用 LinkedHashMap 保持插入顺序
            JSONObject jsonObject = new JSONObject(new LinkedHashMap<>());

            // 分割成键值对
            String[] pairs = content.split("; ");
            for (String pair : pairs) {
                if (pair.isEmpty() || !pair.contains(": ")) {
                    continue;
                }
                String[] keyValue = pair.split(": ", 2);
                if (keyValue.length == 2) {
                    String key = keyValue[0].trim().replaceAll("[\\n\\r]+", ""); // 移除换行符
                    String value = keyValue[1].trim().replaceAll("[\\n\\r]+", " "); // 换行符变成空格

                    try {
                        jsonObject.put(key, value);
                    } catch (Exception e) {
                        continue; // 跳过异常
                    }
                }
            }

            jsonList.add(jsonObject); // 添加紧凑 JSON 字符串
        }

        return jsonList;
    }

    // 生成HTML表格，根据HTML页面标签后的总字符数分割并为每个子表格添加表头（一行）
    public static List<String> generateHtmlTables(XWPFTable table, int maxCharsPerTable) {
        List<String> htmlTables = new ArrayList<>();
        if (table == null || table.getRows().isEmpty()) {
            htmlTables.add("<table></table>");
            return htmlTables;
        }

        // 获取表格行数和列数
        int rowCount = table.getNumberOfRows();
        int maxCols = getMaxColumns(table);

        // 动态确定表头行
        List<Integer> headerRows = determineHeaderRows(table, maxCols);

        // 计算表头行的总HTML字符数
        int headerCharCount = headerRows.stream()
                .mapToInt(rowIdx -> calculateRowHtmlCharCount(table, rowIdx, maxCols))
                .sum();

        // 分割表格
        int startRow = 0;
        int currentCharCount = headerCharCount + "<table>".length() + "</table>".length();
        List<Integer> subTableRows = new ArrayList<>(headerRows); // 包含所有表头行

        for (int rowIdx = headerRows.isEmpty() ? 1 : headerRows.get(headerRows.size() - 1) + 1;
                rowIdx < rowCount;
                rowIdx++) {
            int rowCharCount = calculateRowHtmlCharCount(table, rowIdx, maxCols);
            if (currentCharCount + rowCharCount > maxCharsPerTable && !subTableRows.isEmpty()) {
                // 当前子表格结束，生成 HTML
                htmlTables.add(generateHtmlForRows(table, startRow, rowIdx, maxCols, headerRows));
                // 开始新的子表格
                startRow = rowIdx;
                currentCharCount = headerCharCount + "<table>".length() + "</table>".length();
                subTableRows.clear();
                subTableRows.addAll(headerRows); // 添加所有表头行
            }
            currentCharCount += rowCharCount;
            subTableRows.add(rowIdx);
        }

        // 生成最后一个子表格
        if (!subTableRows.isEmpty() && (startRow < rowCount || subTableRows.size() > headerRows.size())) {
            htmlTables.add(generateHtmlForRows(table, startRow, rowCount, maxCols, headerRows));
        }

        return htmlTables;
    }

    // 动态确定表头行
    public static List<Integer> determineHeaderRows(XWPFTable table, int maxCols) {
        List<Integer> headerRows = new ArrayList<>();
        if (table.getRows().isEmpty()) {
            return headerRows;
        }

        // 默认第一行为表头
        headerRows.add(0);

        // 从第二行开始遍历
        for (int r = 1; r < table.getNumberOfRows(); r++) {
            XWPFTableRow row = table.getRow(r);
            List<String> rowCells = row.getTableCells().stream()
                    .map(WordTableProcessor::getCellContent)
                    .collect(Collectors.toList());

            // 检查第一列是否为空或为数字
            boolean isFirstColumnNumeric = !rowCells.isEmpty() && isNumeric(rowCells.get(0));

            if (isFirstColumnNumeric) {
                // 第一列为数字，该行是数据行，之前的行都是表头
                break;
            } else {
                // 第一列非数字，检查整行是否大部分为数字
                long numericCount =
                        rowCells.stream().filter(WordTableProcessor::isNumeric).count();
                double numericRatio = rowCells.isEmpty() ? 0 : (double) numericCount / rowCells.size();

                if (numericRatio > 0.5) {
                    // 数字占比超过50%，该行是数据行，之前的行都是表头
                    break;
                }

                // 该行不是数据行，添加到表头
                headerRows.add(r);
            }
        }

        return headerRows;
    }

    // 判断字符串是否为数字
    public static boolean isNumeric(String str) {
        if (str == null || str.trim().isEmpty()) {
            return false;
        }
        try {
            Double.parseDouble(str.trim());
            return true;
        } catch (NumberFormatException e) {
            // 检查中文数字
            return CHINESE_NUMBER_PATTERN.matcher(str).matches();
        }
    }

    // 计算一行的HTML字符数
    public static int calculateRowHtmlCharCount(XWPFTable table, int rowIdx, int maxCols) {
        if (rowIdx >= table.getNumberOfRows()) return 0;

        XWPFTableRow row = table.getRow(rowIdx);
        StringBuilder html = new StringBuilder();
        html.append("<tr>");
        int charCount = "<tr>".length();
        int colIdx = 0;
        int[] rowspans = new int[maxCols];
        int[] colspans = new int[maxCols];

        for (XWPFTableCell cell : row.getTableCells()) {
            if (colIdx >= maxCols) break;

            int colspan = getColspan(cell);
            colspan = Math.min(colspan, maxCols - colIdx);
            colspans[colIdx] = colspan;

            boolean isMerged = false;
            if (cell.getCTTc() != null
                    && cell.getCTTc().getTcPr() != null
                    && cell.getCTTc().getTcPr().getVMerge() != null
                    && cell.getCTTc().getTcPr().getVMerge().getVal() != null
                    && cell.getCTTc().getTcPr().getVMerge().getVal().intValue() == 1) {
                isMerged = true;
            }

            if (!isMerged) {
                int rowspan = calculateRowspan(table, rowIdx, colIdx, colspan);
                rowspans[colIdx] = rowspan;

                String cellContent = getCellContent(cell);
                StringBuilder attrs = new StringBuilder();
                if (rowspan > 1) attrs.append(" rowspan='").append(rowspan).append("'");
                if (colspan > 1) attrs.append(" colspan='").append(colspan).append("'");

                html.append("<td")
                        .append(attrs)
                        .append(">")
                        .append(cellContent.isEmpty() ? " " : cellContent)
                        .append("</td>");
                charCount += "<td".length()
                        + attrs.length()
                        + ">".length()
                        + (cellContent.isEmpty() ? " ".length() : cellContent.length())
                        + "</td>".length();
            }

            colIdx += colspan;
        }

        html.append("</tr>");
        charCount += "</tr>".length();
        return charCount;
    }

    // 生成指定行范围的HTML表格，包含动态表头
    public static String generateHtmlForRows(
            XWPFTable table, int startRow, int endRow, int maxCols, List<Integer> headerRows) {
        int headerRowCount = headerRows.size();
        int dataRows = (startRow == 0 ? Math.max(0, endRow - headerRowCount) : endRow - startRow);
        int rowCount = headerRowCount + dataRows;
        XWPFTableCell[][] cellGrid = new XWPFTableCell[rowCount][maxCols];
        int[][] rowspans = new int[rowCount][maxCols];
        int[][] colspans = new int[rowCount][maxCols];

        // 初始化网格和跨度数组
        for (int localRowIdx = 0; localRowIdx < rowCount; localRowIdx++) {
            int sourceRowIdx;
            if (localRowIdx < headerRowCount) {
                sourceRowIdx = headerRows.get(localRowIdx);
            } else {
                sourceRowIdx = startRow + (localRowIdx - headerRowCount) + (startRow == 0 ? headerRowCount : 0);
            }

            if (sourceRowIdx >= table.getNumberOfRows()) {
                continue;
            }
            XWPFTableRow row = table.getRow(sourceRowIdx);
            int colIdx = 0;
            for (XWPFTableCell cell : row.getTableCells()) {
                if (colIdx >= maxCols) break;

                int colspan = getColspan(cell);
                colspan = Math.min(colspan, maxCols - colIdx);
                colspans[localRowIdx][colIdx] = colspan;

                for (int i = 0; i < colspan; i++) {
                    if (colIdx + i < maxCols) {
                        cellGrid[localRowIdx][colIdx + i] = cell;
                    }
                }

                if (cell.getCTTc() == null
                        || cell.getCTTc().getTcPr() == null
                        || cell.getCTTc().getTcPr().getVMerge() == null
                        || (cell.getCTTc().getTcPr().getVMerge().getVal() != null
                                && cell.getCTTc().getTcPr().getVMerge().getVal().intValue() == 2)) {
                    int rowspan = calculateRowspan(table, sourceRowIdx, colIdx, colspan);
                    rowspan = Math.min(rowspan, localRowIdx < headerRowCount ? rowspan : endRow - sourceRowIdx);
                    rowspans[localRowIdx][colIdx] = rowspan;

                    for (int r = localRowIdx + 1; r < localRowIdx + rowspan; r++) {
                        for (int c = colIdx; c < colIdx + colspan; c++) {
                            if (r < rowCount && c < maxCols) {
                                rowspans[r][c] = -1;
                            }
                        }
                    }
                } else if (cell.getCTTc().getTcPr().getVMerge() != null
                        && cell.getCTTc().getTcPr().getVMerge().getVal() != null
                        && cell.getCTTc().getTcPr().getVMerge().getVal().intValue() == 1) {
                    rowspans[localRowIdx][colIdx] = -1;
                }

                colIdx += colspan;
            }
        }

        // 生成HTML
        StringBuilder html = new StringBuilder();
        html.append("<table>");

        for (int localRowIdx = 0; localRowIdx < rowCount; localRowIdx++) {
            html.append("<tr>");
            for (int colIdx = 0; colIdx < maxCols; colIdx++) {
                if (rowspans[localRowIdx][colIdx] == -1 || colspans[localRowIdx][colIdx] == 0) {
                    continue;
                }

                XWPFTableCell cell = cellGrid[localRowIdx][colIdx];
                if (cell == null) {
                    html.append("<td> </td>");
                    continue;
                }

                int rowspan = rowspans[localRowIdx][colIdx] > 1 ? rowspans[localRowIdx][colIdx] : 1;
                int colspan = colspans[localRowIdx][colIdx] > 1 ? colspans[localRowIdx][colIdx] : 1;

                String cellContent = getCellContent(cell, localRowIdx < headerRowCount);
                StringBuilder attrs = new StringBuilder();
                if (rowspan > 1) attrs.append(" rowspan='").append(rowspan).append("'");
                if (colspan > 1) attrs.append(" colspan='").append(colspan).append("'");
                html.append(localRowIdx < headerRowCount ? "<td" : "<td")
                        .append(attrs)
                        .append(">")
                        .append(cellContent.isEmpty() ? " " : cellContent)
                        .append(localRowIdx < headerRowCount ? "</td>" : "</td>");
            }
            html.append("</tr>");
        }

        html.append("</table>");
        return html.toString();
    }

    // 获取表格最大列数
    public static int getMaxColumns(XWPFTable table) {
        int maxColumns = 0;
        for (XWPFTableRow row : table.getRows()) {
            if (row != null) {
                int rowColumns = 0;
                for (XWPFTableCell cell : row.getTableCells()) {
                    CTTc ctTc = cell.getCTTc();
                    if (ctTc.getTcPr() != null && ctTc.getTcPr().getGridSpan() != null) {
                        rowColumns += ctTc.getTcPr().getGridSpan().getVal().intValue();
                    } else {
                        rowColumns += 1;
                    }
                }
                maxColumns = Math.max(maxColumns, rowColumns);
            }
        }
        return maxColumns;
    }

    // 计算行跨度
    public static int calculateRowspan(XWPFTable table, int startRow, int startCol, int colspan) {
        int rowspan = 1;
        for (int rowIdx = startRow + 1; rowIdx < table.getNumberOfRows(); rowIdx++) {
            boolean allColumnsMerged = true;
            for (int colOffset = 0; colOffset < colspan; colOffset++) {
                int colIdx = startCol + colOffset;
                XWPFTableCell cell = getCellAt(table, rowIdx, colIdx);
                if (cell == null
                        || cell.getCTTc() == null
                        || cell.getCTTc().getTcPr() == null
                        || cell.getCTTc().getTcPr().getVMerge() == null
                        || cell.getCTTc().getTcPr().getVMerge().getVal() != STMerge.Enum.forInt(1)) {
                    allColumnsMerged = false;
                    break;
                }
            }
            if (allColumnsMerged) {
                rowspan++;
            } else {
                break;
            }
        }
        return rowspan;
    }

    // 获取指定位置的单元格
    private static XWPFTableCell getCellAt(XWPFTable table, int rowIdx, int colIdx) {
        if (rowIdx >= table.getNumberOfRows()) return null;
        XWPFTableRow row = table.getRow(rowIdx);
        int currentCol = 0;
        for (XWPFTableCell cell : row.getTableCells()) {
            int colspan = getColspan(cell);
            if (colIdx >= currentCol && colIdx < currentCol + colspan) {
                return cell;
            }
            currentCol += colspan;
        }
        return null;
    }

    // 获取列跨度
    private static int getColspan(XWPFTableCell cell) {
        CTTc cttc = cell.getCTTc();
        if (cttc.getTcPr() != null && cttc.getTcPr().getGridSpan() != null) {
            return cttc.getTcPr().getGridSpan().getVal().intValue();
        }
        return 1;
    }

    // 获取单元格内容（带格式）
    private static String getCellContent(XWPFTableCell cell, boolean isHeader) {
        StringBuilder cellContent = new StringBuilder();

        for (XWPFParagraph paragraph : cell.getParagraphs()) {
            StringBuilder paragraphContent = new StringBuilder();
            // 按 run 顺序处理段落内容
            for (XWPFRun run : paragraph.getRuns()) {
                // 处理文本
                String runText = run.getText(0); // 获取 run 的文本内容
                if (runText != null && !runText.trim().isEmpty()) {
                    String formattedText = escapeHtml(runText.trim())
                            .replaceAll("\n", "")
                            .replaceAll("\\*(.*?)\\*", "<b>$1</b>")
                            .replaceAll("_(.*?)_", "<i>$1</i>");
                    if (!isHeader) {
                        formattedText = formattedText + " ";
                    }
                    paragraphContent.append(formattedText);
                }

                //                if(minioUtils != null){
                //                    // 处理图片
                //                    List<XWPFPicture> pictures = run.getEmbeddedPictures();
                //                    for (XWPFPicture picture : pictures) {
                //                        try {
                //                            XWPFPictureData pictureData = picture.getPictureData();
                //                            if (pictureData != null) {
                //                                // 上传图片到 Minio 并获取 URL
                //                                String imageUrl = saveImageAndGetUrl(pictureData, minioUtils);
                //                                // 拼接 HTML 图片标签
                //                                cellContent.append(String.format("<img src=\"%s\" alt=\"image\"
                // style=\"max-width:100%%;\"/>", imageUrl));
                //                            }
                //                        } catch (Exception e) {
                //                            System.err.println("Error processing image: " + e.getMessage());
                //                        }
                //                    }
                //                }
            }
            String normalizedParagraph = paragraphContent.toString().trim();
            if (!normalizedParagraph.isEmpty()) {
                if (cellContent.length() > 0) {
                    cellContent.append("<br/>");
                }
                cellContent.append(normalizedParagraph);
            }
        }

        // 去除末尾多余的 <br>
        String result = cellContent.toString().trim();
        return result.isEmpty() ? "" : result;
    }

    private static String saveImageAndGetUrl(XWPFPictureData pictureData) throws IOException {
        //        String extension = pictureData.suggestFileExtension();
        //        //上传图片
        //        byte[] imageBytes = pictureData.getData();
        //        String fileName = UUID.randomUUID() + "." + extension;
        //        InputStream inputStream = new ByteArrayInputStream(imageBytes);
        //        FileInfo fileInfo = new FileInfo();
        //        fileInfo.setInputStream(inputStream);
        //        fileInfo.setFileName(fileName);
        //        String fileId = UUID.randomUUID().toString();
        //        fileInfo.setFileId(fileId);
        //        int length = imageBytes.length;
        //        fileInfo.setFileSize(String.valueOf(length));
        //        try {
        //            minioUtils.upload(fileInfo);
        //        } catch (Exception e) {
        //            throw new RuntimeException(e);
        //        }
        return "/common/fileDownload/";
    }

    // 判断第一列是否包含多个中文数字
    public static boolean hasMultipleChineseNumbersInFirstColumn(XWPFTable table) {
        if (table == null || table.getRows().isEmpty()) {
            return false;
        }

        int chineseNumberCount = 0;
        for (XWPFTableRow row : table.getRows()) {
            List<XWPFTableCell> cells = row.getTableCells();
            if (cells.isEmpty()) {
                continue;
            }
            String cellContent = getCellContent(cells.get(0));
            if (cellContent != null && !cellContent.trim().isEmpty()) {
                // 检查是否为中文数字（排除常规数字）
                if (CHINESE_NUMBER_PATTERN.matcher(cellContent.trim()).matches()) {
                    try {
                        Double.parseDouble(cellContent.trim());
                    } catch (NumberFormatException e) {
                        chineseNumberCount++;
                    }
                }
            }
            // 如果已找到至少两个中文数字，返回 true
            if (chineseNumberCount >= 3) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断表格第一列第一行内容是否为“序号”或其变体。
     *
     * @param table 表格对象
     * @return 如果第一列第一行内容是“序号”，返回 true；否则返回 false
     */
    public static boolean isFirstCellContentSequenceNumber(XWPFTable table) {
        if (table == null || table.getRows().isEmpty()) {
            return false;
        }

        List<XWPFTableRow> rows = table.getRows();

        // 检查第一行第一列
        if (rows.size() > 0) {
            XWPFTableRow firstRow = rows.get(0);
            List<XWPFTableCell> cells = firstRow.getTableCells();
            if (!cells.isEmpty()) {
                String cellContent = getCellContent(cells.get(0));
                if (cellContent != null && !cellContent.trim().isEmpty()) {
                    String sequenceNumberRegex = "^[\\s\\*\\u3000]*(序号)[\\s\\*\\u3000]*$";
                    if (Pattern.compile(sequenceNumberRegex)
                            .matcher(cellContent.trim())
                            .matches()) {
                        return true;
                    }
                }
            }
        }

        // 如果第一行不匹配，检查第二行第一列
        if (rows.size() > 1) {
            XWPFTableRow secondRow = rows.get(1);
            List<XWPFTableCell> cells = secondRow.getTableCells();
            if (!cells.isEmpty()) {
                String cellContent = getCellContent(cells.get(0));
                if (cellContent != null && !cellContent.trim().isEmpty()) {
                    String sequenceNumberRegex = "^[\\s\\*\\u3000]*(序号)[\\s\\*\\u3000]*$";
                    return Pattern.compile(sequenceNumberRegex)
                            .matcher(cellContent.trim())
                            .matches();
                }
            }
        }

        return false;
    }

    private static String getCellContent(XWPFTableCell cell) {
        StringBuilder cellContent = new StringBuilder();
        for (XWPFParagraph paragraph : cell.getParagraphs()) {
            String paragraphText = paragraph.getText();
            if (paragraphText != null && !paragraphText.trim().isEmpty()) {
                String formattedText = paragraphText
                        .trim()
                        .replaceAll("\\*(.*?)\\*", "<b>$1</b>")
                        .replaceAll("_(.*?)_", "<i>$1</i>");
                cellContent.append(formattedText).append("<br>");
            }
        }
        return cellContent.toString().trim().replaceAll("<br>$", "");
    }

    private static String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
