package cn.edgarli.service.impl;

import cn.edgarli.service.UserApiKeyService;
import cn.edgarli.service.ai.provider.ProviderCatalog;
import cn.edgarli.service.ai.provider.ProviderProtocol;
import cn.edgarli.service.ai.provider.ProviderSpec;
import cn.edgarli.common.BizException;
import cn.edgarli.entity.User;
import cn.edgarli.entity.UserApiKey;
import cn.edgarli.mapper.UserApiKeyMapper;
import cn.edgarli.mapper.UserMapper;
import cn.edgarli.infrastructure.audit.Auditable;
import cn.edgarli.web.dto.UserApiKeyDto;
import cn.edgarli.web.vo.UserApiKeyVo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Default implementation of {@link UserApiKeyService}.
 * {@link UserApiKeyService} 默认实现。
 *
 * @author MyAi
 */
@Service
public class UserApiKeyServiceImpl implements UserApiKeyService {

    private final UserMapper userMapper;
    private final UserApiKeyMapper keyMapper;
    private final ProviderCatalog catalog;

    public UserApiKeyServiceImpl(UserMapper userMapper, UserApiKeyMapper keyMapper, ProviderCatalog catalog) {
        this.userMapper = userMapper;
        this.keyMapper = keyMapper;
        this.catalog = catalog;
    }

    /**
     * List all of a user's keys (masked). Read-only, no transaction.
     * 列出用户全部 Key（脱敏）。只读，无需事务。
     *
     * @param userId user id / 用户 ID
     * @return list of masked keys / 脱敏 Key 列表
     */
    @Override
    public List<UserApiKeyVo> list(Long userId) {
        User user = requireUser(userId);
        // 查 user 是为了拿 default_key_id / user needed to fetch default_key_id
        return keyMapper.findAllByUserId(userId).stream()
                .map(key -> toResponse(key, user.getDefaultKeyId()))
                .toList();
    }

    /**
     * Get a single key (masked). Read-only, no transaction.
     * 取单条 Key（脱敏）。只读，无需事务。
     *
     * @param userId user id / 用户 ID
     * @param keyId key id / Key ID
     * @return masked key / 脱敏 Key
     */
    @Override
    public UserApiKeyVo get(Long userId, Long keyId) {
        User user = requireUser(userId);
        return toResponse(requireKey(userId, keyId), user.getDefaultKeyId());
    }

    /**
     * Create a new key. Transaction boundary: insert + auto-set-default run in one transaction.
     * 新增 Key。事务边界：插入与自动设默认在同一事务内。
     * <p>
     * If the user has no default key yet and the new key is enabled, it is auto-promoted to default.
     * 若用户尚无默认 Key 且新 Key 已启用，会自动提升为默认。
     *
     * @param userId user id / 用户 ID
     * @param request request body / 请求体
     * @return newly created key / 新创建的 Key
     */
    @Override
    @Transactional
    @Auditable(action = "USER_API_KEY_CREATE", targetType = "UserApiKey")
    public UserApiKeyVo create(Long userId, UserApiKeyDto request) {
        User user = requireUser(userId);
        UserApiKey key = mergeAndValidate(new UserApiKey(), request, true);
        // 用 DTO 填充并校验字段 / fill and validate fields from DTO
        key.setUserId(userId);
        key.setCreateTime(LocalDateTime.now());
        keyMapper.insert(key);

        if (user.getDefaultKeyId() == null && Boolean.TRUE.equals(key.getEnabled())) {
            // 首个启用 Key 自动设为默认 / first enabled key is auto-promoted to default
            userMapper.updateDefaultKey(userId, key.getId());
            user.setDefaultKeyId(key.getId());
        }
        return toResponse(key, user.getDefaultKeyId());
    }

    /**
     * Update a key. Transaction boundary: validate + DB update + default-key adjustment run in one transaction.
     * 更新 Key。事务边界：校验、DB 更新与默认 Key 调整在同一事务内。
     *
     * @param userId user id / 用户 ID
     * @param keyId key id / Key ID
     * @param request request body / 请求体
     * @return updated key / 更新后的 Key
     */
    @Override
    @Transactional
    @Auditable(action = "USER_API_KEY_UPDATE", targetType = "UserApiKey")
    public UserApiKeyVo update(Long userId, Long keyId, UserApiKeyDto request) {
        User user = requireUser(userId);
        UserApiKey key = requireKey(userId, keyId);
        mergeAndValidate(key, request, false);

        if (keyId.equals(user.getDefaultKeyId()) && !Boolean.TRUE.equals(key.getEnabled())) {
            // 默认 Key 被禁用 → 清空默认指向 / default key disabled → clear default pointer
            userMapper.updateDefaultKey(userId, null);
            user.setDefaultKeyId(null);
        }
        if (keyMapper.update(key) == 0) {
            throw keyNotFound();
        }
        return toResponse(key, user.getDefaultKeyId());
    }

    /**
     * Delete a key. Transaction boundary: default-key adjustment + delete run in one transaction.
     * 删除 Key。事务边界：默认 Key 调整与删除在同一事务内。
     *
     * @param userId user id / 用户 ID
     * @param keyId key id / Key ID
     */
    @Override
    @Transactional
    @Auditable(action = "USER_API_KEY_DELETE", targetType = "UserApiKey")
    public void delete(Long userId, Long keyId) {
        User user = requireUser(userId);
        requireKey(userId, keyId);
        if (keyId.equals(user.getDefaultKeyId())) {
            // 删除默认 Key → 清空默认指向 / deleting default key → clear default pointer
            userMapper.updateDefaultKey(userId, null);
        }
        if (keyMapper.deleteByIdAndUserId(keyId, userId) == 0) {
            throw keyNotFound();
        }
    }

    /**
     * Set as default key. Transaction boundary: validation + default-key update in one transaction.
     * 设为默认 Key。事务边界：校验与默认 Key 更新在同一事务内。
     *
     * @param userId user id / 用户 ID
     * @param keyId key id / Key ID
     * @return updated key / 更新后的 Key
     */
    @Override
    @Transactional
    @Auditable(action = "USER_API_KEY_SET_DEFAULT", targetType = "UserApiKey")
    public UserApiKeyVo setDefault(Long userId, Long keyId) {
        User user = requireUser(userId);
        UserApiKey key = requireKey(userId, keyId);
        if (!Boolean.TRUE.equals(key.getEnabled())) {
            throw BizException.badRequest("禁用的 Key 不能设为默认");
        }
        validateConfiguration(key);
        userMapper.updateDefaultKey(userId, keyId);
        return toResponse(key, keyId);
    }

    /**
     * Get the default key for chat. Returns plaintext apiKey. Read-only, no transaction.
     * 取聊天用的默认 Key，返回含明文 apiKey 的实体。只读，无需事务。
     *
     * @param userId user id / 用户 ID
     * @return default key entity (with plaintext apiKey) / 默认 Key 实体（含明文 apiKey）
     * @throws BizException 4035 default key unavailable / 4035 默认 Key 不可用
     */
    @Override
    public UserApiKey getDefaultForChat(Long userId) {
        User user = requireUser(userId);
        if (user.getDefaultKeyId() == null) {
            // 未设置默认 Key / no default key set
            throw BizException.defaultKeyUnavailable("用户没有可用的默认 Key");
        }
        UserApiKey key = keyMapper.findByIdAndUserId(user.getDefaultKeyId(), userId);
        if (key == null || !Boolean.TRUE.equals(key.getEnabled())) {
            // Key 已被删 / 不属于该用户 / 被禁用 / deleted / not owned / disabled
            throw BizException.defaultKeyUnavailable("用户没有可用的默认 Key");
        }
        validateConfiguration(key);
        return key;
    }

    private User requireUser(Long userId) {
        if (userId == null) {
            throw BizException.badRequest("userId 不能为空");
        }
        User user = userMapper.findById(userId);
        if (user == null) {
            throw BizException.notFound("用户不存在");
        }
        return user;
    }

    private UserApiKey requireKey(Long userId, Long keyId) {
        if (keyId == null) {
            throw keyNotFound();
        }
        UserApiKey key = keyMapper.findByIdAndUserId(keyId, userId);
        if (key == null) {
            throw keyNotFound();
        }
        return key;
    }

    private UserApiKey mergeAndValidate(UserApiKey key, UserApiKeyDto request, boolean creating) {
        if (request == null) {
            throw BizException.badRequest("请求体不能为空");
        }
        key.setName(required(request.getName(), "Key 名称不能为空"));

        ProviderSpec spec = catalog.require(request.getProvider());
        // 校验 provider 在 yml 池中存在 / validate provider exists in the yml pool
        key.setProvider(spec.name());

        String requestedProtocol = trimToNull(request.getProtocol());
        // 协议覆盖项（可空） / protocol override (optional)
        if (requestedProtocol != null) {
            try {
                ProviderProtocol.valueOf(requestedProtocol.toUpperCase());
                // 校验协议名合法 / validate protocol name is legal
            } catch (IllegalArgumentException e) {
                throw BizException.badRequest("不支持的协议: " + requestedProtocol);
            }
            key.setProtocol(requestedProtocol.toUpperCase());
        } else {
            key.setProtocol(null);
        }

        String requestBaseUrl = trimToNull(request.getBaseUrl());
        // Base URL 覆盖项 / Base URL override
        key.setBaseUrl(requestBaseUrl == null ? spec.defaultBaseUrl() : validateUrl(requestBaseUrl));

        String requestModel = trimToNull(request.getModelName());
        // 模型名覆盖项 / model name override
        key.setModelName(requestModel == null ? spec.defaultModel() : requestModel);

        key.setEnabled(request.getEnabled() == null
                ? creating || Boolean.TRUE.equals(key.getEnabled())
                : request.getEnabled());

        String requestedKey = trimToNull(request.getApiKey());
        // 请求中的 API Key 明文 / plaintext API key from request
        if (creating || requestedKey != null) {
            key.setApiKey(requestedKey);
        }
        validateConfiguration(key);
        return key;
    }

    private void validateConfiguration(UserApiKey key) {
        if (!Boolean.TRUE.equals(key.getEnabled())) {
            // 禁用 Key 跳过配置校验 / skip config check for disabled keys
            return;
        }
        ProviderSpec spec = catalog.require(key.getProvider());
        if (spec.requiresKey() && trimToNull(key.getApiKey()) == null) {
            // 启用且要求 Key 的 provider 必须填 API Key / enabled provider that requires key must have apiKey
            throw BizException.badRequest("启用 " + spec.displayName() + " 配置时必须填写 API Key");
        }
    }

    private static String validateUrl(String baseUrl) {
        String value = required(baseUrl, "Base URL 不能为空");
        try {
            URI uri = URI.create(value);
            // 解析为 URI / parse as URI
            if ((uri.getScheme() == null
                    || (!uri.getScheme().equalsIgnoreCase("http") && !uri.getScheme().equalsIgnoreCase("https")))
                    || uri.getHost() == null) {
                // scheme 必须是 http(s) 且 host 非空 / scheme must be http(s) and host non-empty
                throw new IllegalArgumentException();
            }
            return value;
        } catch (IllegalArgumentException ex) {
            throw BizException.badRequest("Base URL 必须是有效的 HTTP(S) 地址");
        }
    }

    private static String required(String value, String message) {
        String result = trimToNull(value);
        if (result == null) {
            throw BizException.badRequest(message);
        }
        return result;
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static BizException keyNotFound() {
        return BizException.notFound("Key 不存在");
    }

    private UserApiKeyVo toResponse(UserApiKey key, Long defaultKeyId) {
        boolean hasApiKey = trimToNull(key.getApiKey()) != null;
        // 是否有 API Key 明文 / whether plaintext apiKey exists
        String protocol = key.getProtocol();
        if (protocol == null) {
            // 协议为空时回落 provider 默认协议 / fall back to provider default protocol
            protocol = catalog.require(key.getProvider()).protocol().name();
        }
        return new UserApiKeyVo(
                key.getId(),
                key.getUserId(),
                key.getName(),
                key.getProvider(),
                protocol,
                mask(key.getApiKey()),
                hasApiKey,
                key.getBaseUrl(),
                key.getModelName(),
                Boolean.TRUE.equals(key.getEnabled()),
                key.getId().equals(defaultKeyId),
                key.getCreateTime());
    }

    private static String mask(String apiKey) {
        String value = trimToNull(apiKey);
        if (value == null) {
            return null;
        }
        // 短 Key 全部遮蔽 / fully mask short keys
        return value.length() <= 4 ? "****" : "****" + value.substring(value.length() - 4);
    }
}