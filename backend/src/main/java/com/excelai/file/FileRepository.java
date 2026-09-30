package com.excelai.file;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.excelai.persistence.entity.UserFileEntity;
import com.excelai.persistence.mapper.UserFileMapper;
import org.springframework.stereotype.Repository;

/**
 * 中文：user_file 数据访问层。用 MyBatis-Plus 做插入和归属查询，同时把实体转换为业务 record。
 * English: Persistence adapter for user_file; uses MyBatis-Plus for inserts/owner lookups and maps to a business record.
 */
@Repository
public class FileRepository {
    private final UserFileMapper mapper;

    public FileRepository(UserFileMapper mapper) {
        this.mapper = mapper;
    }

    public record FileRecord(Long id, Long userId, String originalName, String storedName, String storagePath,
                             long sizeBytes, String contentType, String fileRole) {
    }

    public Long insert(Long uid, String name, String stored, String path, long size, String type, String role) {
        var entity = new UserFileEntity();
        entity.setUserId(uid);
        entity.setOriginalName(name);
        entity.setStoredName(stored);
        entity.setStoragePath(path);
        entity.setSizeBytes(size);
        entity.setContentType(type);
        entity.setFileRole(role);
        mapper.insert(entity);
        return entity.getId();
    }

    public FileRecord findOwned(Long id, Long uid) {
        // 中文：把 user_id 放入查询条件实现行级归属约束，不能只按文件 ID 查询。
        // English: Include user_id in the predicate for row-level ownership; never query a user file by ID alone.
        var entity = mapper.selectOne(new QueryWrapper<UserFileEntity>()
                .eq("id", id)
                .eq("user_id", uid));
        return entity == null ? null : new FileRecord(entity.getId(), entity.getUserId(), entity.getOriginalName(),
                entity.getStoredName(), entity.getStoragePath(), entity.getSizeBytes(), entity.getContentType(), entity.getFileRole());
    }
}
