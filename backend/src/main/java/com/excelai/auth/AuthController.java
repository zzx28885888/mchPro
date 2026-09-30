package com.excelai.auth;

import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
/** 中文：公开注册和登录入口；登录成功返回 JWT。English: Public registration and login endpoints; login returns a JWT. */
public class AuthController {
    private final AuthService s;

    public AuthController(AuthService s) {
        this.s = s;
    }

    /**
     * 中文：校验页面提交的认证字段；密码长度限制也适配 BCrypt 的输入范围。
     * English: Validates submitted auth fields; the password length also fits BCrypt's input limit.
     */
    public record Req(@Email @NotBlank String email, @NotBlank @Size(min = 8, max = 72) String password) {
    }

    @PostMapping("/register")
    Map<String, Object> register(@RequestBody Req r) {
        return Map.of("userId", s.register(r.email(), r.password()));
    }

    @PostMapping("/login")
    Map<String, Object> login(@RequestBody Req r) {
        return Map.of("token", s.login(r.email(), r.password()));
    }
}
