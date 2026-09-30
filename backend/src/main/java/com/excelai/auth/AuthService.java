package com.excelai.auth;

import com.excelai.common.ApiException;
import com.excelai.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
/**
 * 中文：身份认证业务逻辑；密码只以 BCrypt 哈希写入数据库，验证通过后签发 JWT。
 * English: Authentication business logic; passwords are stored as BCrypt hashes and successful login issues a JWT.
 */
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    public AuthService(UserRepository u, PasswordEncoder e, JwtService j) {
        users = u;
        encoder = e;
        jwt = j;
    }

    public Long register(String email, String password) {
        // 中文：在插入之前检查重复邮箱；数据库唯一约束仍是最后一道并发保护。
        // English: Check for duplicate email before insertion; the database unique constraint remains the concurrency-safe backstop.
        if (users.findByEmail(email) != null) throw new ApiException(HttpStatus.CONFLICT, "email already exists");
        return users.create(email, encoder.encode(password));
    }

    public String login(String email, String password) {
        var u = users.findByEmail(email);
        if (u == null || !encoder.matches(password, u.passwordHash()))
            throw new ApiException(HttpStatus.UNAUTHORIZED, "invalid credentials");
        return jwt.generate(u.id(), u.email(), u.planCode());
    }
}
