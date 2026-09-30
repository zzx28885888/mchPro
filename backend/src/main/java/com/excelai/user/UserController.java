package com.excelai.user;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/me")
/** 中文：返回当前登录用户的基础资料。English: Returns basic profile data for the authenticated user. */
public class UserController {
    private final UserRepository users;

    public UserController(UserRepository u) {
        users = u;
    }

    @GetMapping
    Map<String, Object> me(Authentication a) {
        var u = users.findById((Long) a.getPrincipal());
        return Map.of("id", u.id(), "email", u.email(), "plan", u.planCode());
    }
}
