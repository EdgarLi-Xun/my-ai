package cn.edgarli.web;

import cn.edgarli.common.BizException;
import cn.edgarli.common.Result;
import cn.edgarli.entity.User;
import cn.edgarli.infrastructure.security.AuthPrincipal;
import cn.edgarli.service.UserService;
import cn.edgarli.web.converter.UserConverter;
import cn.edgarli.web.dto.UserCreateDto;
import cn.edgarli.web.vo.UserVo;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户 REST API 控制器（需登录）。
 * User REST API controller (login required).
 *
 * @author MyAi
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * 列出"当前用户自己"（仅 1 项；接口存在是为前端统一管理面板）。
     * List the current user (single entry; exists to keep the management UI uniform).
     *
     * @return 单元素列表 / list with a single element
     */
    @GetMapping
    public Result<List<UserVo>> list() {
        User user = userService.getCurrentUser();
        return Result.success(List.of(UserConverter.toResponse(user)));
    }

    /**
     * 按 ID 取用户（仅可取自己）。
     * Fetch a user by id (self only).
     *
     * @param id 用户 ID / user id
     * @return 用户 VO（剥离 passwordHash 等敏感字段）/ user VO without sensitive fields
     */
    @GetMapping("/{id}")
    public Result<UserVo> getById(@PathVariable Long id) {
        requireOwner(id);
        return Result.success(UserConverter.toResponse(userService.getById(id)));
    }

    /**
     * 管理员创建用户（无 admin 鉴权，依赖调用上下文；当前未对外开放）。
     * Admin-only user creation (no role guard in code; relies on call context).
     *
     * @param request 创建请求 / create payload
     * @return 新建用户 / created user
     */
    @PostMapping
    public Result<UserVo> create(@RequestBody UserCreateDto request) {
        if (request == null) {
            throw BizException.badRequest("请求体不能为空");
        }
        User user = userService.create(request.name(), request.email());
        return Result.success(UserConverter.toResponse(user));
    }

    /**
     * 删除用户（级联删除其全部 Key）。
     * Delete a user (cascades to all of their keys via ON DELETE CASCADE).
     *
     * @param id 用户 ID / user id
     * @return 空响应 / empty success response
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        requireOwner(id);
        userService.delete(id);
        return Result.success();
    }

    /**
     * 校验路径/请求体里的 userId 与登录主体一致，否则抛 4030。
     * Verify the path/request userId matches the current principal; otherwise throw 4030.
     *
     * @param targetUserId 目标用户 ID / target user id
     * @throws BizException 当主体不匹配 / thrown when principal does not match
     */
    private void requireOwner(Long targetUserId) {
        if (!currentUserId().equals(targetUserId)) {
            throw BizException.forbidden("无权操作其他用户");
        }
    }

    /**
     * 取当前登录用户 ID。其它控制器需要把路径或请求体里的 userId 与登录主体对齐时，
     * 统一调用此 helper（不要直接读 SecurityContextHolder）。
     * Resolve the current authenticated user id.
     * Other controllers should call this helper when they need to align a
     * path/body userId with the principal — do not read {@link SecurityContextHolder} directly.
     * <p>
     * 主体为 null 或 userId 缺失 → 抛 4010 unauthenticated。
     * Throws 4010 if the principal is null or has no userId.
     *
     * @return 当前登录用户 ID / current authenticated user id
     */
    static Long currentUserId() {
        AuthPrincipal principal = (AuthPrincipal) SecurityContextHolder.getContext().getAuthentication();
        if (principal == null || principal.getUserId() == null) {
            throw BizException.unauthorized("未登录");
        }
        return principal.getUserId();
    }
}