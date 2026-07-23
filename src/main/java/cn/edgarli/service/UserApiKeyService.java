package cn.edgarli.service;

import cn.edgarli.ai.provider.ProviderCatalog;
import cn.edgarli.ai.provider.ProviderProtocol;
import cn.edgarli.ai.provider.ProviderSpec;
import cn.edgarli.common.BizException;
import cn.edgarli.entity.User;
import cn.edgarli.entity.UserApiKey;
import cn.edgarli.mapper.UserApiKeyMapper;
import cn.edgarli.mapper.UserMapper;
import cn.edgarli.web.UserApiKeyRequest;
import cn.edgarli.web.UserApiKeyResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户 API Key 配置服务。
 */
@Service
public class UserApiKeyService {

    private final UserMapper userMapper;
    private final UserApiKeyMapper keyMapper;
    private final ProviderCatalog catalog;

    public UserApiKeyService(UserMapper userMapper, UserApiKeyMapper keyMapper, ProviderCatalog catalog) {
        this.userMapper = userMapper;
        this.keyMapper = keyMapper;
        this.catalog = catalog;
    }

    public List<UserApiKeyResponse> list(Long userId) {
        User user = requireUser(userId);
        return keyMapper.findAllByUserId(userId).stream()
                .map(key -> toResponse(key, user.getDefaultKeyId()))
                .toList();
    }

    public UserApiKeyResponse get(Long userId, Long keyId) {
        User user = requireUser(userId);
        return toResponse(requireKey(userId, keyId), user.getDefaultKeyId());
    }

    @Transactional
    public UserApiKeyResponse create(Long userId, UserApiKeyRequest request) {
        User user = requireUser(userId);
        UserApiKey key = mergeAndValidate(new UserApiKey(), request, true);
        key.setUserId(userId);
        key.setCreateTime(LocalDateTime.now());
        keyMapper.insert(key);

        if (user.getDefaultKeyId() == null && Boolean.TRUE.equals(key.getEnabled())) {
            userMapper.updateDefaultKey(userId, key.getId());
            user.setDefaultKeyId(key.getId());
        }
        return toResponse(key, user.getDefaultKeyId());
    }

    @Transactional
    public UserApiKeyResponse update(Long userId, Long keyId, UserApiKeyRequest request) {
        User user = requireUser(userId);
        UserApiKey key = requireKey(userId, keyId);
        mergeAndValidate(key, request, false);

        if (keyId.equals(user.getDefaultKeyId()) && !Boolean.TRUE.equals(key.getEnabled())) {
            userMapper.updateDefaultKey(userId, null);
            user.setDefaultKeyId(null);
        }
        if (keyMapper.update(key) == 0) {
            throw keyNotFound();
        }
        return toResponse(key, user.getDefaultKeyId());
    }

    @Transactional
    public void delete(Long userId, Long keyId) {
        User user = requireUser(userId);
        requireKey(userId, keyId);
        if (keyId.equals(user.getDefaultKeyId())) {
            userMapper.updateDefaultKey(userId, null);
        }
        if (keyMapper.deleteByIdAndUserId(keyId, userId) == 0) {
            throw keyNotFound();
        }
    }

    @Transactional
    public UserApiKeyResponse setDefault(Long userId, Long keyId) {
        User user = requireUser(userId);
        UserApiKey key = requireKey(userId, keyId);
        if (!Boolean.TRUE.equals(key.getEnabled())) {
            throw BizException.badRequest("禁用的 Key 不能设为默认");
        }
        validateConfiguration(key);
        userMapper.updateDefaultKey(userId, keyId);
        return toResponse(key, keyId);
    }

    public UserApiKey getDefaultForChat(Long userId) {
        User user = requireUser(userId);
        if (user.getDefaultKeyId() == null) {
            throw BizException.conflict("用户没有可用的默认 Key");
        }
        UserApiKey key = keyMapper.findByIdAndUserId(user.getDefaultKeyId(), userId);
        if (key == null || !Boolean.TRUE.equals(key.getEnabled())) {
            throw BizException.conflict("用户没有可用的默认 Key");
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

    private UserApiKey mergeAndValidate(UserApiKey key, UserApiKeyRequest request, boolean creating) {
        if (request == null) {
            throw BizException.badRequest("请求体不能为空");
        }
        key.setName(required(request.getName(), "Key 名称不能为空"));

        ProviderSpec spec = catalog.require(request.getProvider());
        key.setProvider(spec.name());

        String requestedProtocol = trimToNull(request.getProtocol());
        if (requestedProtocol != null) {
            try {
                ProviderProtocol.valueOf(requestedProtocol.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw BizException.badRequest("不支持的协议: " + requestedProtocol);
            }
            key.setProtocol(requestedProtocol.toUpperCase());
        } else {
            key.setProtocol(null); // null = 用 provider 默认协议
        }

        String requestBaseUrl = trimToNull(request.getBaseUrl());
        key.setBaseUrl(requestBaseUrl == null ? spec.defaultBaseUrl() : validateUrl(requestBaseUrl));

        String requestModel = trimToNull(request.getModelName());
        key.setModelName(requestModel == null ? spec.defaultModel() : requestModel);

        key.setEnabled(request.getEnabled() == null
                ? creating || Boolean.TRUE.equals(key.getEnabled())
                : request.getEnabled());

        String requestedKey = trimToNull(request.getApiKey());
        if (creating || requestedKey != null) {
            key.setApiKey(requestedKey);
        }
        validateConfiguration(key);
        return key;
    }

    private void validateConfiguration(UserApiKey key) {
        if (!Boolean.TRUE.equals(key.getEnabled())) {
            return;
        }
        ProviderSpec spec = catalog.require(key.getProvider());
        if (spec.requiresKey() && trimToNull(key.getApiKey()) == null) {
            throw BizException.badRequest("启用 " + spec.displayName() + " 配置时必须填写 API Key");
        }
    }

    private static String validateUrl(String baseUrl) {
        String value = required(baseUrl, "Base URL 不能为空");
        try {
            URI uri = URI.create(value);
            if ((uri.getScheme() == null
                    || (!uri.getScheme().equalsIgnoreCase("http") && !uri.getScheme().equalsIgnoreCase("https")))
                    || uri.getHost() == null) {
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

    private UserApiKeyResponse toResponse(UserApiKey key, Long defaultKeyId) {
        boolean hasApiKey = trimToNull(key.getApiKey()) != null;
        String protocol = key.getProtocol();
        if (protocol == null) {
            protocol = catalog.require(key.getProvider()).protocol().name();
        }
        return new UserApiKeyResponse(
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
        return value.length() <= 4 ? "****" : "****" + value.substring(value.length() - 4);
    }
}
