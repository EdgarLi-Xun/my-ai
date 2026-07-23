package cn.edgarli.web;

import cn.edgarli.common.Result;
import cn.edgarli.entity.User;
import cn.edgarli.service.AuthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户认证 REST API。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public Result<AuthResponse> register(@RequestBody RegisterRequest request) {
        return Result.success(authService.register(request.name(), request.email(), request.password()));
    }

    @PostMapping("/login")
    public Result<AuthResponse> login(@RequestBody LoginRequest request) {
        return Result.success(authService.login(request.email(), request.password()));
    }

    @GetMapping("/me")
    public Result<User> me() {
        return Result.success(authService.getCurrentUser());
    }
}
