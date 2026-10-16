package com.excelai.config;

import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.springframework.context.annotation.Configuration;

@Configuration
/**
 * 中文：为不可信 XLSX 输入设置全局 POI 解压边界。
 * English: Sets global POI decompression limits for untrusted XLSX input.
 */
public class ExcelSecurityConfiguration {
    public static final int MAX_ARCHIVE_ENTRIES = 10_000;
    public static final long MAX_ENTRY_UNCOMPRESSED_BYTES = 64L * 1024 * 1024;
    public static final long MAX_ARCHIVE_UNCOMPRESSED_BYTES = 256L * 1024 * 1024;
    private static final double MIN_INFLATE_RATIO = 0.01;

    public ExcelSecurityConfiguration() {
        ZipSecureFile.setMinInflateRatio(MIN_INFLATE_RATIO);
        ZipSecureFile.setMaxEntrySize(MAX_ENTRY_UNCOMPRESSED_BYTES);
    }
}
