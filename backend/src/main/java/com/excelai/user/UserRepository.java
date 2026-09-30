package com.excelai.user;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.excelai.persistence.entity.UserAccountEntity;
import com.excelai.persistence.mapper.UserAccountMapper;
import org.springframework.stereotype.Repository;

/**
 * 中文：账号数据访问适配层。普通增删查改通过 MyBatis-Plus Mapper 完成，外部业务仍使用稳定的 User record。
 * English: Account persistence adapter. MyBatis-Plus handles routine CRUD while callers keep using the stable User record.
 */
@Repository
public class UserRepository {
    private final UserAccountMapper mapper;

    public UserRepository(UserAccountMapper mapper) {
        this.mapper = mapper;
    }

    public record User(Long id, String email, String passwordHash, String planCode) {
    }

    public Long create(String email, String hash) {
        // 中文：实体里的 AUTO 主键由 PostgreSQL identity 生成，并由 MyBatis 写回实体。
        // English: PostgreSQL generates the AUTO identity key and MyBatis writes it back to the entity.
        var entity = new UserAccountEntity();
        entity.setEmail(email);
        entity.setPasswordHash(hash);
        entity.setPlanCode("FREE");
        mapper.insert(entity);
        return entity.getId();
    }

    public User findByEmail(String email) {
        // 中文：QueryWrapper 用数据库列名构造等值条件；email 唯一，因此最多返回一条账号。
        // English: QueryWrapper builds an equality predicate using the database column; email is unique, so at most one row matches.
        var entity = mapper.selectOne(new QueryWrapper<UserAccountEntity>().eq("email", email));
        return toUser(entity);
    }

    public User findById(Long id) {
        return toUser(mapper.selectById(id));
    }

    private User toUser(UserAccountEntity entity) {
        return entity == null ? null : new User(entity.getId(), entity.getEmail(), entity.getPasswordHash(), entity.getPlanCode());
    }
}
