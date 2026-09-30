package com.excelai.file;

import com.excelai.common.ApiException;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.util.Map;

@RestController
@RequestMapping("/api/files")
/**
 * 中文：处理当前登录用户的 Excel 上传与下载；响应和数据库记录只暴露文件 ID，不暴露服务器路径。
 * English: Handles Excel upload/download for the authenticated user; APIs expose file IDs, never server paths.
 */
public class FileController {
    private final FileStorageService storage;
    private final FileRepository repo;

    public FileController(FileStorageService s, FileRepository r) {
        storage = s;
        repo = r;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    Map<String, Object> upload(@RequestParam MultipartFile file, Authentication a) throws Exception {
        // 中文：用户 ID 从已验签的 Authentication 读取，不能由表单参数指定。
        // English: The owner ID comes from verified Authentication, not from a client-supplied form field.
        if (file.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "file is empty");
        Long uid = (Long) a.getPrincipal();
        var x = storage.save(file);
        Long id = repo.insert(uid, file.getOriginalFilename(), x.name(), x.path(), x.size(), x.type(), "INPUT");
        return Map.of("fileId", id, "name", file.getOriginalFilename(), "size", file.getSize());
    }

    @GetMapping("/{id}/download")
    org.springframework.core.io.Resource download(@PathVariable Long id, Authentication a) throws Exception {
        var f = repo.findOwned(id, (Long) a.getPrincipal());
        if (f == null) throw new ApiException(HttpStatus.NOT_FOUND, "file not found");
        var p = storage.path(f.storagePath());
        if (!Files.exists(p)) throw new ApiException(HttpStatus.NOT_FOUND, "physical file not found");
        return new org.springframework.core.io.FileSystemResource(p);
    }
}
