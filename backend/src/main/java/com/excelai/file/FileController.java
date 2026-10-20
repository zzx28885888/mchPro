package com.excelai.file;

import com.excelai.common.ApiException;
import com.excelai.excel.CsvService;
import com.excelai.plan.PlanRepository;
import com.excelai.user.UserRepository;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/files")
/**
 * 中文：处理当前登录用户的 XLSX/CSV 上传与下载；响应和数据库记录只暴露文件 ID，不暴露服务器路径。
 * English: Handles XLSX/CSV uploads and downloads for the authenticated user; APIs expose file IDs, never server paths.
 */
public class FileController {
    private final FileStorageService storage;
    private final FileRepository repo;
    private final UserRepository users;
    private final PlanRepository plans;
    private final CsvService csv;

    public FileController(FileStorageService s, FileRepository r, UserRepository users, PlanRepository plans, CsvService csv) {
        storage = s;
        repo = r;
        this.users = users;
        this.plans = plans;
        this.csv = csv;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    Map<String, Object> upload(@RequestParam MultipartFile file, Authentication a) throws Exception {
        // 中文：用户 ID 从已验签的 Authentication 读取，不能由表单参数指定。
        // English: The owner ID comes from verified Authentication, not from a client-supplied form field.
        if (file.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "file is empty");
        Long uid = (Long) a.getPrincipal();
        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isBlank() || !supportedFilename(originalName)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "a valid .xlsx or .csv filename is required");
        }
        var user = users.findById(uid);
        if (user == null) throw new ApiException(HttpStatus.NOT_FOUND, "user not found");
        var plan = plans.findByCode(user.planCode());
        if (plan == null) throw new ApiException(HttpStatus.FORBIDDEN, "user plan unavailable");
        if (file.getSize() > plan.maxFileSize()) throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "file exceeds plan limit");
        if (csvFilename(originalName)) csv.validate(file.getBytes());
        else storage.validateXlsx(file);
        var x = storage.save(file);
        try {
            Long id = repo.insert(uid, originalName, x.name(), x.path(), x.size(), x.type(), "INPUT");
            return Map.of("fileId", id, "name", originalName, "size", file.getSize());
        } catch (RuntimeException e) {
            try {
                storage.delete(x.path());
            } catch (Exception cleanupFailure) {
                e.addSuppressed(cleanupFailure);
            }
            throw e;
        }
    }

    @GetMapping("/{id}/download")
    org.springframework.core.io.Resource download(@PathVariable Long id, Authentication a) throws Exception {
        var f = repo.findOwned(id, (Long) a.getPrincipal());
        if (f == null) throw new ApiException(HttpStatus.NOT_FOUND, "file not found");
        var p = storage.path(f.storagePath());
        if (!Files.exists(p)) throw new ApiException(HttpStatus.NOT_FOUND, "physical file not found");
        return new org.springframework.core.io.FileSystemResource(p);
    }

    private boolean supportedFilename(String originalName) {
        return xlsxFilename(originalName) || csvFilename(originalName);
    }

    private boolean xlsxFilename(String originalName) {
        String leaf = leafName(originalName);
        return leaf.toLowerCase(Locale.ROOT).endsWith(".xlsx");
    }

    private boolean csvFilename(String originalName) {
        String leaf = leafName(originalName);
        return leaf.toLowerCase(Locale.ROOT).endsWith(".csv");
    }

    private String leafName(String originalName) {
        String leaf = originalName.replace('\\', '/');
        return leaf.substring(leaf.lastIndexOf('/') + 1);
    }
}
