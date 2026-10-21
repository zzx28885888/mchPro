package com.excelai.excel;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CsvServiceTest {
    @TempDir
    Path tempDir;

    private final CsvService csv = new CsvService();

    @Test
    void readsUtf8BomAndGb18030() {
        byte[] utf8Bom = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] utf8 = "名称,金额\n苹果,12".getBytes(StandardCharsets.UTF_8);
        byte[] withBom = new byte[utf8Bom.length + utf8.length];
        System.arraycopy(utf8Bom, 0, withBom, 0, utf8Bom.length);
        System.arraycopy(utf8, 0, withBom, utf8Bom.length, utf8.length);

        assertEquals(List.of("名称", "金额"), csv.read(utf8).headers());
        assertEquals(List.of(List.of("苹果", "12")), csv.read(utf8).rows());
        assertEquals(List.of("名称", "金额"), csv.read(withBom).headers());
        assertEquals(List.of(List.of("苹果", "12")), csv.read(withBom).rows());

        byte[] gb18030 = "名称,金额\n苹果,12".getBytes(Charset.forName("GB18030"));
        assertEquals(List.of("名称", "金额"), csv.read(gb18030).headers());
        assertEquals(List.of(List.of("苹果", "12")), csv.read(gb18030).rows());
    }

    @Test
    void detectsCommaTabAndSemicolon() {
        for (String content : List.of("a,b\n1,2", "a\tb\n1\t2", "a;b\n1;2")) {
            ExcelWorkbook.Sheet sheet = csv.read(content.getBytes(StandardCharsets.UTF_8));
            assertEquals(List.of("a", "b"), sheet.headers());
            assertEquals(List.of(List.of("1", "2")), sheet.rows());
        }
    }

    @Test
    void parsesQuotedDelimitersEscapesAndNewlines() {
        String content = "名称,备注\r\n苹果,\"a,b\"\r\n梨,\"他说\"\"你好\"\"\n第二行\"";
        ExcelWorkbook.Sheet sheet = csv.read(content.getBytes(StandardCharsets.UTF_8));
        assertEquals("csv_data", sheet.name());
        assertEquals(List.of("名称", "备注"), sheet.headers());
        assertEquals(List.of(List.of("苹果", "a,b"), List.of("梨", "他说\"你好\"\n第二行")), sheet.rows());
    }

    @Test
    void rejectsEmptyHeaderMalformedRowsAndQuotes() {
        assertThrows(RuntimeException.class, () -> csv.read(new byte[0]));
        assertThrows(RuntimeException.class, () -> csv.read("a,b\n1".getBytes(StandardCharsets.UTF_8)));
        assertThrows(RuntimeException.class, () -> csv.read("a;b\n1".getBytes(StandardCharsets.UTF_8)));
        assertThrows(RuntimeException.class, () -> csv.read("a,b\n1,\"unterminated".getBytes(StandardCharsets.UTF_8)));
        assertThrows(RuntimeException.class, () -> csv.read(",b\n1,2".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void writesUtf8BomCsvWithEscapedValues() throws Exception {
        Path path = tempDir.resolve("result.csv");
        csv.write(path, new OutputSheet("summary", List.of("name", "detail"),
                List.of(List.of("A", "comma, quote \"ok\""))));

        byte[] bytes = Files.readAllBytes(path);
        assertArrayEquals(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF},
                new byte[]{bytes[0], bytes[1], bytes[2]});
        assertEquals("name,detail\r\nA,\"comma, quote \"\"ok\"\"\"\r\n",
                new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8));
        assertEquals(List.of(List.of("A", "comma, quote \"ok\"")), csv.read(path).rows());
    }
}
