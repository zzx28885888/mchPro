package com.excelai.excel;

import com.example.agent.tool.service.AgentToolContext;
import com.excelai.file.FileRepository;
import com.excelai.file.FileStorageService;
import com.excelai.task.TaskDataService;
import com.excelai.task.TaskRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ExcelWorkflowToolsTest {
    @TempDir
    Path tempDir;

    @AfterEach
    void clearAgentContext() {
        AgentToolContext.clear();
    }

    @Test
    void exportsSummaryAndMergeAsCsv() throws Exception {
        for (String workflow : List.of("SUMMARY", "MERGE_CLEAN")) {
            TaskDataService data = mock(TaskDataService.class);
            TaskRepository tasks = mock(TaskRepository.class);
            FileRepository files = mock(FileRepository.class);
            FileStorageService storage = mock(FileStorageService.class);
            ExcelService excel = mock(ExcelService.class);
            CsvService csv = new CsvService();
            WorkflowResult result = result(new OutputSheet("summary", List.of("name", "value"), List.of(List.of("A", "1"))));
            when(data.result(31L)).thenReturn(null);
            when(data.workflowResult(31L)).thenReturn(result);
            when(storage.savePathName(anyString())).thenAnswer(call -> tempDir.resolve((String) call.getArgument(0)));
            when(files.insert(anyLong(), anyString(), anyString(), anyString(), anyLong(), anyString(), anyString())).thenReturn(55L);
            ExcelWorkflowTools tools = new ExcelWorkflowTools(data, tasks, files, storage, excel, csv, null, new ObjectMapper());
            AgentToolContext.set(Map.of("taskId", 31L, "userId", 7L, "workflowType", workflow,
                    "options", Map.of("outputFormat", "CSV")));

            assertEquals(55L, tools.export().get("fileId"));
            String name = "ai-" + workflow.toLowerCase(java.util.Locale.ROOT) + "-result-31.csv";
            Path output = tempDir.resolve(name);
            byte[] bytes = Files.readAllBytes(output);
            assertArrayEquals(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF}, new byte[]{bytes[0], bytes[1], bytes[2]});
            assertEquals("name,value\r\nA,1\r\n", new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8));
            verify(files).insert(7L, name, name, output.toString(), bytes.length, "text/csv; charset=UTF-8", "OUTPUT");
            verify(excel, never()).writeWorkbook(any(), anyList());
        }
    }

    @Test
    void rejectsCsvForMultipleOutputSheets() {
        TaskDataService data = mock(TaskDataService.class);
        TaskRepository tasks = mock(TaskRepository.class);
        FileRepository files = mock(FileRepository.class);
        FileStorageService storage = mock(FileStorageService.class);
        CsvService csv = new CsvService();
        when(data.result(31L)).thenReturn(null);
        when(data.workflowResult(31L)).thenReturn(result(
                new OutputSheet("one", List.of("a"), List.of(List.of("1"))),
                new OutputSheet("two", List.of("b"), List.of(List.of("2")))));
        ExcelWorkflowTools tools = new ExcelWorkflowTools(data, tasks, files, storage, mock(ExcelService.class), csv, null, new ObjectMapper());
        AgentToolContext.set(Map.of("taskId", 31L, "userId", 7L, "workflowType", "SUMMARY",
                "options", Map.of("outputFormat", "CSV")));

        assertThrows(IllegalArgumentException.class, tools::export);
        verifyNoInteractions(files, storage);
    }

    @Test
    void keepsXlsxDefaultExport() throws Exception {
        TaskDataService data = mock(TaskDataService.class);
        TaskRepository tasks = mock(TaskRepository.class);
        FileRepository files = mock(FileRepository.class);
        FileStorageService storage = mock(FileStorageService.class);
        when(data.result(31L)).thenReturn(null);
        when(data.workflowResult(31L)).thenReturn(result(
                new OutputSheet("summary", List.of("value"), List.of(List.of("1")))));
        when(storage.savePathName(anyString())).thenAnswer(call -> tempDir.resolve((String) call.getArgument(0)));
        when(files.insert(anyLong(), anyString(), anyString(), anyString(), anyLong(), anyString(), anyString())).thenReturn(55L);
        ExcelWorkflowTools tools = new ExcelWorkflowTools(data, tasks, files, storage, new ExcelService(),
                new CsvService(), null, new ObjectMapper());
        AgentToolContext.set(Map.of("taskId", 31L, "userId", 7L, "workflowType", "SUMMARY", "options", Map.of()));

        assertEquals(55L, tools.export().get("fileId"));
        String name = "ai-summary-result-31.xlsx";
        Path output = tempDir.resolve(name);
        assertTrue(Files.size(output) > 0);
        verify(files).insert(7L, name, name, output.toString(), Files.size(output),
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "OUTPUT");
    }

    private WorkflowResult result(OutputSheet... sheets) {
        return new WorkflowResult(List.of(sheets), Map.of("rows", 1L), List.of(), List.of());
    }
}
