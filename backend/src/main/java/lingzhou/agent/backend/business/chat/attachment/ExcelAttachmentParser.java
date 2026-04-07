package lingzhou.agent.backend.business.chat.attachment;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lingzhou.agent.backend.capability.rag.chunk.model.FileType;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;

@Component
public class ExcelAttachmentParser implements AttachmentParser {

    private static final int MAX_SAMPLE_ROWS = 3;
    private static final int MAX_SAMPLE_VALUES = 5;
    private static final int MAX_SCAN_ROWS = 5;
    private static final int MAX_ENTITY_COUNT = 40;
    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("yyyy/M/d"),
            DateTimeFormatter.ofPattern("yyyy-M-d"),
            DateTimeFormatter.ofPattern("yyyy.MM.dd"),
            DateTimeFormatter.ofPattern("M/d/yyyy"),
            DateTimeFormatter.ofPattern("M-d-yyyy"));

    @Override
    public boolean supports(String fileName) {
        return extension(fileName).equalsIgnoreCase(FileType.XLSX);
    }

    @Override
    public AttachmentParseResult parse(InputStream inputStream, String fileName) throws Exception {
        List<AttachmentParseResult.Section> sections = new ArrayList<>();
        List<AttachmentParseResult.EntityTable> tables = new ArrayList<>();
        Set<String> labels = new LinkedHashSet<>();
        List<String> sheetNames = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            DataFormatter formatter = new DataFormatter(Locale.SIMPLIFIED_CHINESE);
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            for (int sheetIndex = 0; sheetIndex < workbook.getNumberOfSheets(); sheetIndex++) {
                Sheet sheet = workbook.getSheetAt(sheetIndex);
                if (sheet == null) {
                    continue;
                }
                String sheetName = sheet.getSheetName();
                sheetNames.add(sheetName);
                int headerRowIndex = detectHeaderRow(sheet, formatter, evaluator);
                Row headerRow = headerRowIndex >= 0 ? sheet.getRow(headerRowIndex) : null;
                List<String> header = readHeader(headerRow, formatter, evaluator);
                List<Integer> activeColumns = activeColumns(header);
                List<List<String>> sampleRows = readSampleRows(sheet, headerRowIndex + 1, activeColumns, formatter, evaluator);
                List<AttachmentParseResult.Column> columns =
                        analyzeColumns(sheet, headerRowIndex + 1, activeColumns, header, formatter, evaluator);
                sections.add(new AttachmentParseResult.Section(
                        "sheet",
                        sections.size(),
                        sheetName,
                        "",
                        effectiveRowCount(sheet, headerRowIndex),
                        activeColumns.size(),
                        headerRowIndex >= 0 ? headerRowIndex + 1 : null,
                        header,
                        sampleRows,
                        columns));
                tables.add(new AttachmentParseResult.EntityTable(sheetName, header, sampleRows));
                for (String headerName : header) {
                    if (!headerName.isBlank() && labels.size() < MAX_ENTITY_COUNT) {
                        labels.add(headerName);
                    }
                }
                if (header.isEmpty()) {
                    warnings.add("Sheet " + sheetName + " 未识别到清晰表头，已按原始顺序提取样本");
                }
            }
        }

        return new AttachmentParseResult(
                true,
                fileName,
                FileType.XLSX,
                new AttachmentParseResult.Summary(0, 0, sections.size(), sections.size()),
                sections,
                new AttachmentParseResult.Entities(List.of(), List.copyOf(labels), tables, sheetNames),
                warnings,
                "");
    }

    private int detectHeaderRow(Sheet sheet, DataFormatter formatter, FormulaEvaluator evaluator) {
        int maxRow = Math.min(sheet.getLastRowNum(), MAX_SCAN_ROWS - 1);
        int bestRowIndex = -1;
        int bestScore = -1;
        for (int rowIndex = 0; rowIndex <= maxRow; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }
            int nonBlank = 0;
            int textLike = 0;
            short lastCellNum = row.getLastCellNum();
            for (int cellIndex = 0; cellIndex < Math.max(lastCellNum, 0); cellIndex++) {
                Cell cell = row.getCell(cellIndex);
                String value = normalizeText(formatCell(cell, formatter, evaluator));
                if (value.isEmpty()) {
                    continue;
                }
                nonBlank++;
                if (!looksNumeric(value)) {
                    textLike++;
                }
            }
            int score = nonBlank * 2 + textLike;
            if (score > bestScore) {
                bestScore = score;
                bestRowIndex = rowIndex;
            }
        }
        return Math.max(bestRowIndex, 0);
    }

    private List<String> readHeader(Row headerRow, DataFormatter formatter, FormulaEvaluator evaluator) {
        if (headerRow == null) {
            return List.of();
        }
        List<String> header = new ArrayList<>();
        for (int cellIndex = 0; cellIndex < Math.max(headerRow.getLastCellNum(), 0); cellIndex++) {
            String value = normalizeText(formatCell(headerRow.getCell(cellIndex), formatter, evaluator));
            header.add(value);
        }
        return trimTrailingBlanks(header);
    }

    private List<Integer> activeColumns(List<String> header) {
        List<Integer> active = new ArrayList<>();
        for (int index = 0; index < header.size(); index++) {
            String name = header.get(index);
            if (!name.isBlank()) {
                active.add(index);
            }
        }
        if (active.isEmpty()) {
            for (int index = 0; index < header.size(); index++) {
                active.add(index);
            }
        }
        return active;
    }

    private List<List<String>> readSampleRows(
            Sheet sheet,
            int dataStartRow,
            List<Integer> activeColumns,
            DataFormatter formatter,
            FormulaEvaluator evaluator) {
        List<List<String>> sampleRows = new ArrayList<>();
        for (int rowIndex = Math.max(dataStartRow, 0); rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }
            List<String> values = new ArrayList<>();
            boolean hasValue = false;
            for (Integer columnIndex : activeColumns) {
                String value = normalizeText(formatCell(row.getCell(columnIndex), formatter, evaluator));
                values.add(truncate(value, 120));
                hasValue = hasValue || !value.isEmpty();
            }
            if (hasValue) {
                sampleRows.add(values);
            }
            if (sampleRows.size() >= MAX_SAMPLE_ROWS) {
                break;
            }
        }
        return sampleRows;
    }

    private List<AttachmentParseResult.Column> analyzeColumns(
            Sheet sheet,
            int dataStartRow,
            List<Integer> activeColumns,
            List<String> header,
            DataFormatter formatter,
            FormulaEvaluator evaluator) {
        List<AttachmentParseResult.Column> columns = new ArrayList<>();
        for (Integer columnIndex : activeColumns) {
            int nullCount = 0;
            int totalCount = 0;
            int dateScore = 0;
            int numberScore = 0;
            List<String> sampleValues = new ArrayList<>();
            Set<String> distinctValues = new LinkedHashSet<>();

            for (int rowIndex = Math.max(dataStartRow, 0); rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) {
                    continue;
                }
                Cell cell = row.getCell(columnIndex);
                String display = normalizeText(formatCell(cell, formatter, evaluator));
                if (display.isEmpty()) {
                    nullCount++;
                    continue;
                }
                totalCount++;
                if (sampleValues.size() < MAX_SAMPLE_VALUES) {
                    sampleValues.add(truncate(display, 80));
                }
                if (distinctValues.size() <= 12) {
                    distinctValues.add(display);
                }
                if (isDateCell(cell) || looksLikeDate(display)) {
                    dateScore++;
                } else if (looksNumeric(display)) {
                    numberScore++;
                }
            }

            String inferredType = inferType(totalCount, dateScore, numberScore, distinctValues.size());
            String name = columnIndex < header.size() && !header.get(columnIndex).isBlank()
                    ? header.get(columnIndex)
                    : "Column" + (columnIndex + 1);
            columns.add(new AttachmentParseResult.Column(
                    columnIndex,
                    name,
                    inferredType,
                    nullCount,
                    totalCount,
                    sampleValues));
        }
        return columns;
    }

    private String inferType(int totalCount, int dateScore, int numberScore, int distinctCount) {
        if (totalCount <= 0) {
            return "text";
        }
        if (dateScore > 0 && dateScore * 2 >= totalCount) {
            return "date";
        }
        if (numberScore > 0 && numberScore * 2 >= totalCount) {
            return "number";
        }
        if (distinctCount > 0 && distinctCount <= 10 && totalCount >= distinctCount * 2) {
            return "select_like";
        }
        return "text";
    }

    private int effectiveRowCount(Sheet sheet, int headerRowIndex) {
        int count = 0;
        for (int rowIndex = Math.max(headerRowIndex + 1, 0); rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row != null && row.getPhysicalNumberOfCells() > 0) {
                count++;
            }
        }
        return count;
    }

    private List<String> trimTrailingBlanks(List<String> values) {
        int last = values.size() - 1;
        while (last >= 0 && values.get(last).isBlank()) {
            last--;
        }
        if (last < 0) {
            return List.of();
        }
        return new ArrayList<>(values.subList(0, last + 1));
    }

    private boolean isDateCell(Cell cell) {
        if (cell == null) {
            return false;
        }
        CellType cellType = cell.getCellType();
        if (cellType == CellType.FORMULA) {
            cellType = cell.getCachedFormulaResultType();
        }
        return cellType == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell);
    }

    private boolean looksLikeDate(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                LocalDate.parse(value, formatter);
                return true;
            } catch (DateTimeParseException ignored) {
                // ignore
            }
        }
        return false;
    }

    private boolean looksNumeric(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        return value.matches("[-+]?\\d+(\\.\\d+)?");
    }

    private String formatCell(Cell cell, DataFormatter formatter, FormulaEvaluator evaluator) {
        if (cell == null) {
            return "";
        }
        return formatter.formatCellValue(cell, evaluator);
    }

    private String extension(String fileName) {
        int dotIndex = fileName == null ? -1 : fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex >= fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dotIndex + 1).trim().toLowerCase();
    }

    private String normalizeText(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("\\s+", " ").trim();
    }

    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text == null ? "" : text;
        }
        return text.substring(0, maxLength) + "...";
    }
}
