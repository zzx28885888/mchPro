package com.excelai.excel;

import com.excelai.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Service
public class CsvService {
    private static final long MAX_CSV_BYTES = 210L * 1024 * 1024;
    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final List<Character> DELIMITERS = List.of(',', '\t', ';');
    private static final String SHEET_NAME = "csv_data";

    public ExcelWorkbook.Sheet read(Path path) {
        try {
            return read(readBytes(path));
        } catch (ApiException e) {
            throw e;
        } catch (IOException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "cannot read csv file");
        }
    }

    public ExcelWorkbook.SheetSchema inspect(Path path) {
        try {
            String text = decode(readBytes(path));
            char delimiter = detectDelimiter(text);
            List<List<String>> records = parseRecords(detectionSample(text), delimiter, 1);
            if (records.isEmpty() || records.get(0).isEmpty()) throw invalid("csv header is missing");
            List<String> headers = records.get(0);
            if (headers.stream().anyMatch(String::isBlank)) throw invalid("csv header contains an empty column name");
            return new ExcelWorkbook.SheetSchema(SHEET_NAME, headers);
        } catch (ApiException e) {
            throw e;
        } catch (IOException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "cannot read csv file");
        }
    }

    public ExcelWorkbook.Sheet read(byte[] content) {
        if (content == null || content.length == 0) throw invalid("csv file is empty");
        if (content.length > MAX_CSV_BYTES) throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "csv file exceeds upload limit");
        String text = decode(content);
        char delimiter = detectDelimiter(text);
        List<List<String>> records = parseRecords(text, delimiter);
        if (records.isEmpty() || records.get(0).isEmpty()) throw invalid("csv header is missing");

        List<String> headers = records.get(0);
        if (headers.stream().anyMatch(String::isBlank)) throw invalid("csv header contains an empty column name");
        List<List<String>> rows = new ArrayList<>();
        for (int index = 1; index < records.size(); index++) {
            List<String> row = records.get(index);
            if (row.size() != headers.size()) throw invalid("csv rows must have the same number of columns as the header");
            rows.add(row);
        }
        return new ExcelWorkbook.Sheet(SHEET_NAME, headers, rows);
    }

    public void validate(byte[] content) {
        read(content);
    }

    public void write(Path path, OutputSheet sheet) {
        try (var output = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            output.write('\uFEFF');
            writeRecord(output, sheet.headers());
            for (List<String> row : sheet.rows()) {
                if (row.size() != sheet.headers().size()) throw new IllegalArgumentException("output row does not match header column count");
                writeRecord(output, row);
            }
        } catch (IOException e) {
            throw new IllegalStateException("cannot write csv result", e);
        }
    }

    private String decode(byte[] content) {
        boolean bom = hasUtf8Bom(content);
        int start = bom ? UTF8_BOM.length : 0;
        String utf8;
        try {
            utf8 = decodeStrict(content, start, StandardCharsets.UTF_8);
        } catch (CharacterCodingException utf8Failure) {
            return decodeGb18030(content);
        }
        return utf8;
    }

    private byte[] readBytes(Path path) throws IOException {
        if (Files.size(path) > MAX_CSV_BYTES) throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "csv file exceeds upload limit");
        return Files.readAllBytes(path);
    }

    private String decodeGb18030(byte[] content) {
        try {
            return decodeStrict(content, 0, Charset.forName("GB18030"));
        } catch (CharacterCodingException gbFailure) {
            throw invalid("csv encoding is not valid UTF-8 or GB18030");
        }
    }

    private String decodeStrict(byte[] content, int start, Charset charset) throws CharacterCodingException {
        return charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(content, start, content.length - start)).toString();
    }

    private boolean hasUtf8Bom(byte[] content) {
        return content.length >= UTF8_BOM.length
                && content[0] == UTF8_BOM[0] && content[1] == UTF8_BOM[1] && content[2] == UTF8_BOM[2];
    }

    private char detectDelimiter(String text) {
        char selected = ',';
        double bestScore = -1;
        int bestWidth = 1;
        String sample = detectionSample(text);
        for (char candidate : DELIMITERS) {
            List<List<String>> records;
            try {
                records = parseRecords(sample, candidate);
            } catch (ApiException e) {
                continue;
            }
            if (records.isEmpty()) continue;
            java.util.Map<Integer, Integer> counts = new java.util.HashMap<>();
            for (List<String> record : records) {
                counts.merge(record.size(), 1, Integer::sum);
            }
            int modeWidth = 1;
            int modeCount = 0;
            for (var count : counts.entrySet()) {
                int width = count.getKey();
                int occurrences = count.getValue();
                if (occurrences > modeCount || (occurrences == modeCount && width > modeWidth)) {
                    modeCount = occurrences;
                    modeWidth = width;
                }
            }
            double score = (double) modeCount * modeWidth / records.size();
            if (score > bestScore || (score == bestScore && modeWidth > bestWidth)) {
                bestScore = score;
                bestWidth = modeWidth;
                selected = candidate;
            }
        }
        if (bestScore < 0) throw invalid("csv quoting or delimiters are malformed");
        return selected;
    }

    private String detectionSample(String text) {
        int limit = Math.min(text.length(), 64 * 1024);
        if (limit == text.length()) return text;
        boolean quoted = false;
        boolean fieldStart = true;
        int lastRecordEnd = 0;
        for (int index = 0; index < limit; index++) {
            char current = text.charAt(index);
            if (quoted) {
                if (current == '"') {
                    if (index + 1 < limit && text.charAt(index + 1) == '"') index++;
                    else quoted = false;
                }
            } else if (current == '"' && fieldStart) {
                quoted = true;
                fieldStart = false;
            } else if (current == ',' || current == '\t' || current == ';') {
                fieldStart = true;
            } else if (current == '\r' || current == '\n') {
                lastRecordEnd = index + 1;
                if (current == '\r' && index + 1 < limit && text.charAt(index + 1) == '\n') {
                    lastRecordEnd = ++index + 1;
                }
                fieldStart = true;
            } else {
                fieldStart = false;
            }
        }
        if (lastRecordEnd == 0) return text;
        return text.substring(0, lastRecordEnd);
    }

    private List<List<String>> parseRecords(String text, char delimiter) {
        return parseRecords(text, delimiter, Integer.MAX_VALUE);
    }

    private List<List<String>> parseRecords(String text, char delimiter, int maxRecords) {
        List<List<String>> records = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        boolean afterQuote = false;
        boolean atFieldStart = true;
        boolean recordStarted = false;

        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            if (quoted) {
                if (current == '"') {
                    if (index + 1 < text.length() && text.charAt(index + 1) == '"') {
                        field.append('"');
                        index++;
                    } else {
                        quoted = false;
                        afterQuote = true;
                    }
                } else {
                    field.append(current);
                }
                continue;
            }

            if (afterQuote) {
                if (current == delimiter) {
                    row.add(field.toString());
                    field.setLength(0);
                    atFieldStart = true;
                    afterQuote = false;
                    recordStarted = true;
                } else if (current == '\r' || current == '\n') {
                    row.add(field.toString());
                    records.add(List.copyOf(row));
                    if (records.size() >= maxRecords) return records;
                    row.clear();
                    field.setLength(0);
                    atFieldStart = true;
                    afterQuote = false;
                    recordStarted = false;
                    if (current == '\r' && index + 1 < text.length() && text.charAt(index + 1) == '\n') index++;
                } else {
                    throw invalid("csv contains characters after a closing quote");
                }
                continue;
            }

            if (current == '"') {
                if (!atFieldStart) throw invalid("csv contains an unexpected quote");
                quoted = true;
                atFieldStart = false;
                recordStarted = true;
            } else if (current == delimiter) {
                row.add(field.toString());
                field.setLength(0);
                atFieldStart = true;
                recordStarted = true;
            } else if (current == '\r' || current == '\n') {
                if (recordStarted || !row.isEmpty() || field.length() > 0) {
                    row.add(field.toString());
                    records.add(List.copyOf(row));
                    if (records.size() >= maxRecords) return records;
                }
                row.clear();
                field.setLength(0);
                atFieldStart = true;
                recordStarted = false;
                if (current == '\r' && index + 1 < text.length() && text.charAt(index + 1) == '\n') index++;
            } else {
                field.append(current);
                atFieldStart = false;
                recordStarted = true;
            }
        }

        if (quoted) throw invalid("csv contains an unterminated quoted field");
        if (recordStarted || !row.isEmpty() || field.length() > 0 || afterQuote) {
            row.add(field.toString());
            records.add(List.copyOf(row));
        }
        return records;
    }

    private void writeRecord(java.io.Writer output, List<String> values) throws IOException {
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) output.write(',');
            String value = values.get(index) == null ? "" : values.get(index);
            boolean quote = value.indexOf(',') >= 0 || value.indexOf('"') >= 0 || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0;
            if (quote) output.write('"');
            for (int character = 0; character < value.length(); character++) {
                char current = value.charAt(character);
                if (current == '"') output.write('"');
                output.write(current);
            }
            if (quote) output.write('"');
        }
        output.write("\r\n");
    }

    private ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }
}
