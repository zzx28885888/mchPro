package com.excelai.file;

import com.excelai.config.AppProperties;
import com.excelai.config.ExcelSecurityConfiguration;
import com.excelai.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
/**
 * 中文：将上传文件写入配置的私有根目录，并在读写时校验规范化路径仍位于该目录内。
 * English: Stores uploads beneath a private configured root and verifies normalized paths remain inside that root.
 */
public class FileStorageService {
    private final Path root;

    public FileStorageService(AppProperties p) throws Exception {
        root = Path.of(p.storage().root()).toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    public record Stored(String name, String path, long size, String type) {
    }

    public Stored save(MultipartFile f) throws Exception {
        // 中文：UUID 避免不同用户上传同名文件时覆盖彼此内容；safe() 清理原始文件名。
        // English: A UUID prevents same-name uploads from overwriting each other; safe() sanitizes the original filename.
        String n = UUID.randomUUID() + "_" + safe(f.getOriginalFilename());
        Path p = root.resolve(n).normalize();
        if (!p.startsWith(root)) throw new SecurityException();
        try {
            f.transferTo(p);
        } catch (Exception e) {
            try {
                Files.deleteIfExists(p);
            } catch (Exception cleanupFailure) {
                e.addSuppressed(cleanupFailure);
            }
            throw e;
        }
        return new Stored(n, p.toString(), f.getSize(), f.getContentType());
    }

    /**
     * 中文：流式校验 XLSX 的 OOXML 结构和解压预算，不将上传内容整体读入内存。
     * English: Streams through the XLSX package to validate OOXML structure and expansion budgets without loading it all into memory.
     */
    public void validateXlsx(MultipartFile file) {
        Set<String> entries = new HashSet<>();
        boolean contentTypes = false;
        boolean workbook = false;
        boolean worksheet = false;
        long expandedBytes = 0;
        byte[] buffer = new byte[8192];

        try (ZipInputStream zip = new ZipInputStream(file.getInputStream())) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entries.size() >= ExcelSecurityConfiguration.MAX_ARCHIVE_ENTRIES) {
                    throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "xlsx archive has too many entries");
                }
                String name = entry.getName();
                if (!entries.add(name)) throw new ApiException(HttpStatus.BAD_REQUEST, "xlsx archive contains duplicate entries");
                contentTypes |= "[Content_Types].xml".equals(name);
                workbook |= "xl/workbook.xml".equals(name);
                worksheet |= name.startsWith("xl/worksheets/") && name.endsWith(".xml");

                long entryBytes = 0;
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    entryBytes += read;
                    expandedBytes += read;
                    if (entryBytes > ExcelSecurityConfiguration.MAX_ENTRY_UNCOMPRESSED_BYTES
                            || expandedBytes > ExcelSecurityConfiguration.MAX_ARCHIVE_UNCOMPRESSED_BYTES) {
                        throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "xlsx archive exceeds decompression limits");
                    }
                }
                zip.closeEntry();
            }
        } catch (ApiException e) {
            throw e;
        } catch (IOException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid xlsx archive");
        }

        if (!contentTypes || !workbook || !worksheet) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "file is not a valid xlsx workbook");
        }
    }

    public void delete(String storagePath) throws IOException {
        Files.deleteIfExists(path(storagePath));
    }

    public Path path(String s) {
        // 中文：数据库里的路径也视为不可信输入，读取前再次执行目录边界检查。
        // English: Database paths are also treated as untrusted and checked against the storage boundary before reading.
        Path p = Path.of(s).toAbsolutePath().normalize();
        if (!p.startsWith(root)) throw new SecurityException();
        return p;
    }

    public Path savePathName(String name) {
        Path p = root.resolve(safe(name)).normalize();
        if (!p.startsWith(root)) throw new SecurityException();
        return p;
    }

    private String safe(String n) {
        if (n == null || n.isBlank()) return "upload.xlsx";
        return n.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
