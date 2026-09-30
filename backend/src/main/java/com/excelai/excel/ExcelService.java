package com.excelai.excel;

import com.alibaba.excel.EasyExcel;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.*;

@Service
/**
 * 中文：封装 EasyExcel 的首个工作表读写，把数据统一转换为“列名 -> 单元格值”的行列表。
 * English: Wraps EasyExcel's first-sheet import/export and represents each row as a column-name-to-cell-value map.
 */
public class ExcelService {
    public List<Map<String, Object>> read(Path path) {
        // 中文：表头映射用于把 Excel 的列序号转换成稳定列名；空表头回退到 COL_n。
        // English: The header map converts Excel column indexes into names; blank headers fall back to COL_n.
        List<Map<String, Object>> rows = new ArrayList<>();
        EasyExcel.read(path.toFile(), new com.alibaba.excel.event.AnalysisEventListener<Map<Integer, String>>() {
            List<String> h = new ArrayList<>();

            public void invokeHeadMap(Map<Integer, String> m, com.alibaba.excel.context.AnalysisContext c) {
                h.clear();
                int max = m.keySet().stream().mapToInt(Integer::intValue).max().orElse(-1);
                for (int i = 0; i <= max; i++) h.add(Objects.toString(m.get(i), "COL_" + i));
            }

            public void invoke(Map<Integer, String> d, com.alibaba.excel.context.AnalysisContext c) {
                Map<String, Object> r = new LinkedHashMap<>();
                for (int i = 0; i < h.size(); i++) r.put(h.get(i), d.get(i));
                rows.add(r);
            }

            public void doAfterAllAnalysed(com.alibaba.excel.context.AnalysisContext c) {
            }
        }).sheet().doRead();
        return rows;
    }

    public void write(Path path, List<Map<String, Object>> rows) {
        // 中文：导出列顺序沿用第一行 Map 的插入顺序，确保表头和每行数据对齐。
        // English: Export columns follow the first row's insertion order so headers and cell values stay aligned.
        if (rows.isEmpty()) {
            EasyExcel.write(path.toFile()).head(List.of(List.of("result"))).sheet().doWrite(List.of());
            return;
        }
        List<String> hs = new ArrayList<>(rows.get(0).keySet());
        List<List<String>> head = hs.stream().map(List::of).toList();
        List<List<String>> data = rows.stream().map(r -> hs.stream().map(h -> Objects.toString(r.get(h), "")).toList()).toList();
        EasyExcel.write(path.toFile()).head(head).sheet("result").doWrite(data);
    }
}
