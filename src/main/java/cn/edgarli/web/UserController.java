package cn.edgarli.web;

import cn.edgarli.common.BizException;
import cn.edgarli.common.Result;
import cn.edgarli.entity.User;
import cn.edgarli.mapper.UserMapper;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户 REST API 控制器。
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserMapper userMapper;

    public UserController(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @GetMapping
    public Result<List<User>> list() {
        return Result.success(userMapper.findAll());
    }

    @GetMapping("/{id}")
    public Result<User> getById(@PathVariable Long id) {
        return Result.success(requireUser(id));
    }

    @PostMapping
    public Result<User> create(@RequestBody UserCreateRequest request) {
        if (request == null || request.name() == null || request.name().isBlank()) {
            throw BizException.badRequest("用户名不能为空");
        }
        User user = new User();
        user.setName(request.name().trim());
        user.setEmail(request.email() == null || request.email().isBlank() ? null : request.email().trim());
        user.setCreateTime(LocalDateTime.now());
        userMapper.insert(user);
        return Result.success(user);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        if (userMapper.deleteById(id) == 0) {
            throw BizException.notFound("用户不存在");
        }
        return Result.success();
    }

    private User requireUser(Long id) {
        User user = userMapper.findById(id);
        if (user == null) {
            throw BizException.notFound("用户不存在");
        }
        return user;
    }
}
