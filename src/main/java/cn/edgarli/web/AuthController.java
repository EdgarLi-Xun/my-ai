package cn.edgarli.web;

import cn.edgarli.common.Result;
import cn.edgarli.entity.User;
import cn.edgarli.service.AuthService;
import cn.edgarli.web.dto.LoginDto;
import cn.edgarli.web.dto.RegisterDto;
import cn.edgarli.web.vo.AuthVo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * User authentication REST API.
 * 用户认证 REST API。
 * <p>
 * 提供注册 / 登录 / 当前用户三个端点；登录成功后返回 JWT，
 * 前端把 token 存到 {@code localStorage} 的 {@code myai.token}，
 * 后续请求统一由 {@code App.vue} 注入 {@code Authorization: Bearer <token>}。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 注册新用户并返回 token。
     * Register a new user and return an auth token.
     * <p>
     * 邮箱唯一；密码经 BCrypt 散列后写入 {@code user.password_hash}。
     *
     * @param request 注册请求体 / registration payload (name, email, password)
     * @return 含 token 与当前用户的响应 / auth response with token
     */
    @PostMapping("/register")
    public Result<AuthVo> register(@RequestBody RegisterDto request) {
        return Result.success(authService.register(request.name(), request.email(), request.password()));
    }

    /**
     * 邮箱 + 密码登录，返回 JWT 与当前用户。
     * Sign in by email + password and return a JWT.
     * <p>
     * 密码以 BCrypt 校验；凭据错误抛 {@code BizException}（非 200）。
     *
     * @param request 登录请求体 / login payload (email, password)
     * @return 含 token 与当前用户的响应 / auth response with token
     */
    @PostMapping("/login")
    public Result<AuthVo> login(@RequestBody LoginDto request) {
        return Result.success(authService.login(request.email(), request.password()));
    }

    /**
     * 取当前登录用户信息（从 JWT 中解析）。
     * Return the currently authenticated user (resolved from the JWT).
     * <p>
     * 未登录 → 4010（由 {@link UserController#currentUserId()} 抛）。
     *
     * @return 当前用户实体 / current authenticated user
     */
    @GetMapping("/me")
    public Result<User> me() {
        return Result.success(authService.getCurrentUser());
    }
}
