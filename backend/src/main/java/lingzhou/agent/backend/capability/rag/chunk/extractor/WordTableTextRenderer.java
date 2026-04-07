package lingzhou.agent.backend.capability.rag.chunk.extractor;

import java.util.ArrayList;
import java.util.List;
import lingzhou.agent.backend.capability.rag.chunk.tool.WordTableProcessor;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;

public final class WordTableTextRenderer {

    private WordTableTextRenderer() {}

    public static List<String> toLines(XWPFTable table) {
        List<String> lines = WordTableProcessor.extractTableContent(table);
        if (lines != null && !lines.isEmpty()) {
            return lines;
        }
        return fallbackToMarkdownLines(table);
    }

    public static String toText(XWPFTable table) {
        return String.join("\n", toLines(table));
    }

    private static List<String> fallbackToMarkdownLines(XWPFTable table) {
        List<String> lines = new ArrayList<>();
        List<XWPFTableRow> rows = table.getRows();
        if (rows == null || rows.isEmpty()) {
            return lines;
        }

        int columnCount = rows.get(0).getTableCells().size();
        if (columnCount <= 0) {
            return lines;
        }

        StringBuilder separator = new StringBuilder();
        for (int i = 0; i < columnCount; i++) {
            separator.append("| --- ");
        }
        separator.append("|");

        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            XWPFTableRow row = rows.get(rowIndex);
            StringBuilder line = new StringBuilder();
            line.append("| ");
            for (XWPFTableCell cell : row.getTableCells()) {
                String cellText = cell == null ? "" : cell.getText();
                line.append(StringUtils.isEmpty(cellText) ? "" : cellText.trim())
                        .append(" | ");
            }
            lines.add(line.toString().trim());
            if (rowIndex == 0) {
                lines.add(separator.toString());
            }
        }
        return lines;
    }
}
