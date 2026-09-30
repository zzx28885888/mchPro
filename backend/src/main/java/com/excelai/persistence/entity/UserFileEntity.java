package com.excelai.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 中文：user_file 表映射。实体中保留存储路径供服务端使用，API 响应不要直接暴露该字段。
 * English: Mapping for user_file. The server uses storagePath internally; never expose it in API responses.
 */
@TableName("user_file")
public class UserFileEntity {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String originalName;
    private String storedName;
    private String storagePath;
    private long sizeBytes;
    private String contentType;
    private String fileRole;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getOriginalName() {
        return originalName;
    }

    public void setOriginalName(String originalName) {
        this.originalName = originalName;
    }

    public String getStoredName() {
        return storedName;
    }

    public void setStoredName(String storedName) {
        this.storedName = storedName;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public void setStoragePath(String storagePath) {
        this.storagePath = storagePath;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getFileRole() {
        return fileRole;
    }

    public void setFileRole(String fileRole) {
        this.fileRole = fileRole;
    }
}
