package cn.edgarli.web;

import cn.edgarli.common.Result;
import cn.edgarli.service.UserApiKeyService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户 API Key 配置 REST API。
 */
@RestController
@RequestMapping("/api/users/{userId}/keys")
public class UserApiKeyController {

    private final UserApiKeyService keyService;

    public UserApiKeyController(UserApiKeyService keyService) {
        this.keyService = keyService;
    }

    @GetMapping
    public Result<List<UserApiKeyResponse>> list(@PathVariable Long userId) {
        return Result.success(keyService.list(userId));
    }

    @GetMapping("/{keyId}")
    public Result<UserApiKeyResponse> get(@PathVariable Long userId, @PathVariable Long keyId) {
        return Result.success(keyService.get(userId, keyId));
    }

    @PostMapping
    public Result<UserApiKeyResponse> create(
            @PathVariable Long userId,
            @RequestBody UserApiKeyRequest request) {
        return Result.success(keyService.create(userId, request));
    }

    @PutMapping("/{keyId}")
    public Result<UserApiKeyResponse> update(
            @PathVariable Long userId,
            @PathVariable Long keyId,
            @RequestBody UserApiKeyRequest request) {
        return Result.success(keyService.update(userId, keyId, request));
    }

    @DeleteMapping("/{keyId}")
    public Result<Void> delete(@PathVariable Long userId, @PathVariable Long keyId) {
        keyService.delete(userId, keyId);
        return Result.success();
    }

    @PutMapping("/{keyId}/default")
    public Result<UserApiKeyResponse> setDefault(@PathVariable Long userId, @PathVariable Long keyId) {
        return Result.success(keyService.setDefault(userId, keyId));
    }
}
