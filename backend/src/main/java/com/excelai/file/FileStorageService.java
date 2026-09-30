package com.excelai.file;

import com.excelai.config.AppProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.*;
import java.util.UUID;

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
        f.transferTo(p);
        return new Stored(n, p.toString(), f.getSize(), f.getContentType());
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
