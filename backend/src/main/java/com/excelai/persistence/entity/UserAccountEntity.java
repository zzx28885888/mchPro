package com.excelai.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 中文：user_account 表映射。passwordHash 保存 BCrypt 哈希，绝不能存明文密码。
 * English: Mapping for user_account. passwordHash contains a BCrypt hash and must never contain plaintext.
 */
@TableName("user_account")
public class UserAccountEntity {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private String email;
    private String passwordHash;
    private String planCode;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getPlanCode() {
        return planCode;
    }

    public void setPlanCode(String planCode) {
        this.planCode = planCode;
    }
}
