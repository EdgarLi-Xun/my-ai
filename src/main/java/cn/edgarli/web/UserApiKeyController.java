package cn.edgarli.web;

import cn.edgarli.common.BizException;
import cn.edgarli.common.Result;
import cn.edgarli.service.UserApiKeyService;
import cn.edgarli.web.dto.UserApiKeyDto;
import cn.edgarli.web.vo.UserApiKeyVo;
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
 * 用户 API Key 配置 REST API（需登录，仅能操作自己的 Key）。
 * User API key management REST API (login required; users may only touch their own keys).
 * <p>
 * 每个端点都先调 {@link #requireOwner} 校验路径里的 userId 与登录主体一致
 * （不一致 → 4030）；Key 明文落地但响应走 {@code mask()}，不回明文。
 * Every endpoint enforces owner match via {@link #requireOwner} (4030 otherwise);
 * the raw key is stored as-is but responses only return a masked value.
 */
@RestController
@RequestMapping("/api/users/{userId}/keys")
public class UserApiKeyController {

    private final UserApiKeyService keyService;

    public UserApiKeyController(UserApiKeyService keyService) {
        this.keyService = keyService;
    }

    /**
     * 列出当前用户的所有 Key（含禁用项）。
     * List all keys belonging to the user (including disabled ones).
     *
     * @param userId 路径里的用户 ID / user id from the path
     * @return Key 列表（响应中明文 Key 被掩码）/ key list with masked secrets
     */
    @GetMapping
    public Result<List<UserApiKeyVo>> list(@PathVariable Long userId) {
        requireOwner(userId);
        return Result.success(keyService.list(userId));
    }

    /**
     * 取单个 Key 详情。
     * Fetch a single key.
     *
     * @param userId 路径里的用户 ID / user id from the path
     * @param keyId Key ID / key id
     * @return Key 详情（明文被掩码）/ key detail with masked secret
     */
    @GetMapping("/{keyId}")
    public Result<UserApiKeyVo> get(@PathVariable Long userId, @PathVariable Long keyId) {
        requireOwner(userId);
        return Result.success(keyService.get(userId, keyId));
    }

    /**
     * 新建一个 Key。
     * Create a new key.
     * <p>
     * 首个启用的 Key 自动设为默认（{@link UserApiKeyService} 内部规则）。
     *
     * @param userId 路径里的用户 ID / user id from the path
     * @param request Key 配置 / key payload
     * @return 新建 Key / newly created key
     */
    @PostMapping
    public Result<UserApiKeyVo> create(
            @PathVariable Long userId,
            @RequestBody UserApiKeyDto request) {
        requireOwner(userId);
        return Result.success(keyService.create(userId, request));
    }

    /**
     * 更新 Key（apiKey 字段空字符串视为保留原值）。
     * Update a key (an empty apiKey string means "keep the existing value").
     *
     * @param userId 路径里的用户 ID / user id from the path
     * @param keyId Key ID / key id
     * @param request 更新字段 / update payload
     * @return 更新后的 Key / updated key
     */
    @PutMapping("/{keyId}")
    public Result<UserApiKeyVo> update(
            @PathVariable Long userId,
            @PathVariable Long keyId,
            @RequestBody UserApiKeyDto request) {
        requireOwner(userId);
        return Result.success(keyService.update(userId, keyId, request));
    }

    /**
     * 删除 Key（默认 Key 被删时 user.default_key_id 置 NULL）。
     * Delete a key (deleting the default key clears user.default_key_id).
     *
     * @param userId 路径里的用户 ID / user id from the path
     * @param keyId Key ID / key id
     * @return 空响应 / empty success response
     */
    @DeleteMapping("/{keyId}")
    public Result<Void> delete(@PathVariable Long userId, @PathVariable Long keyId) {
        requireOwner(userId);
        keyService.delete(userId, keyId);
        return Result.success();
    }

    /**
     * 把指定 Key 设为默认（必须 enabled=true 且配置完整，否则抛 4035）。
     * Mark a key as default (must be enabled with a valid configuration; 4035 otherwise).
     *
     * @param userId 路径里的用户 ID / user id from the path
     * @param keyId Key ID / key id
     * @return 设为默认后的 Key / key after being marked as default
     */
    @PutMapping("/{keyId}/default")
    public Result<UserApiKeyVo> setDefault(@PathVariable Long userId, @PathVariable Long keyId) {
        requireOwner(userId);
        return Result.success(keyService.setDefault(userId, keyId));
    }

    /**
     * 校验路径 userId 与登录主体一致，否则抛 4030。
     * Verify the path userId matches the current principal; otherwise throw 4030.
     *
     * @param targetUserId 路径里的 userId / user id from the path
     * @throws BizException 当主体不匹配 / thrown when principal does not match
     */
    private void requireOwner(Long targetUserId) {
        if (!UserController.currentUserId().equals(targetUserId)) {
            throw BizException.forbidden("无权操作其他用户的 Key");
        }
    }
}
