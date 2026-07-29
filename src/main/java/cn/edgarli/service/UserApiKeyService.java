package cn.edgarli.service;

import cn.edgarli.entity.UserApiKey;
import cn.edgarli.web.dto.UserApiKeyDto;
import cn.edgarli.web.vo.UserApiKeyVo;

import java.util.List;

/**
 * User API Key configuration service.
 * 用户 API Key 配置服务。
 *
 * @author MyAi
 */
public interface UserApiKeyService {

    /**
     * List all of a user's keys (masked).
     * 列出用户全部 Key（脱敏）。
     *
     * @param userId user id / 用户 ID
     * @return list of keys / Key 列表
     */
    List<UserApiKeyVo> list(Long userId);

    /**
     * Get a single key (masked).
     * 取单条 Key（脱敏）。
     *
     * @param userId user id / 用户 ID
     * @param keyId key id / Key ID
     * @return key / Key
     */
    UserApiKeyVo get(Long userId, Long keyId);

    /**
     * Create a new key.
     * 新增 Key。
     *
     * @param userId user id / 用户 ID
     * @param request request body / 请求体
     * @return newly created key / 新创建的 Key
     */
    UserApiKeyVo create(Long userId, UserApiKeyDto request);

    /**
     * Update a key. Empty {@code apiKey} keeps the existing value.
     * 更新 Key。{@code apiKey} 留空 = 保留原值。
     *
     * @param userId user id / 用户 ID
     * @param keyId key id / Key ID
     * @param request request body / 请求体
     * @return updated key / 更新后的 Key
     */
    UserApiKeyVo update(Long userId, Long keyId, UserApiKeyDto request);

    /**
     * Delete a key.
     * 删除 Key。
     *
     * @param userId user id / 用户 ID
     * @param keyId key id / Key ID
     */
    void delete(Long userId, Long keyId);

    /**
     * Set as default key. Prerequisites: enabled=true and {@code validateConfiguration} passes.
     * 设为默认 Key。前提：enabled=true 且通过 {@code validateConfiguration}。
     *
     * @param userId user id / 用户 ID
     * @param keyId key id / Key ID
     * @return updated key / 更新后的 Key
     */
    UserApiKeyVo setDefault(Long userId, Long keyId);

    /**
     * Get the default key for chat. Used by {@link cn.edgarli.service.ai.ChatService} etc.
     * 取聊天用的默认 Key。供 {@link cn.edgarli.service.ai.ChatService} 等使用。
     *
     * @param userId user id / 用户 ID
     * @return default key entity (with plaintext apiKey) / 默认 Key 实体（含明文 apiKey）
     * @throws cn.edgarli.common.BizException 4035 default key unavailable / 4035 默认 Key 不可用
     */
    UserApiKey getDefaultForChat(Long userId);
}