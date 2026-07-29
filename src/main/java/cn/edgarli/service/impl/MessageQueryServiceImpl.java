package cn.edgarli.service.impl;

import cn.edgarli.service.MessageQueryService;
import cn.edgarli.mapper.MessageMapper;
import cn.edgarli.web.vo.MessageVo;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Default implementation of {@link MessageQueryService}.
 * {@link MessageQueryService} 默认实现。
 *
 * @author MyAi
 */
@Service
public class MessageQueryServiceImpl implements MessageQueryService {

    private final MessageMapper messageMapper;
    private final MessageSupport support;

    public MessageQueryServiceImpl(MessageMapper messageMapper, MessageSupport support) {
        this.messageMapper = messageMapper;
        this.support = support;
    }

    /**
     * List conversation messages. Read-only, no transaction.
     * 列对话消息。只读，无需事务。
     *
     * @param userId user id / 用户 ID
     * @param conversationId conversation id / 对话 ID
     * @param includeOrphaned whether to include orphaned messages / 是否包含孤儿消息
     * @return message list (by created_at asc) / 消息列表（按 created_at 升序）
     */
    @Override
    public List<MessageVo> list(Long userId, Long conversationId, boolean includeOrphaned) {
        support.requireOwnedConversation(userId, conversationId);
        // owner 校验，跨用户访问会抛 4031 / owner check; cross-user access throws 4031
        return messageMapper.findByConversationId(conversationId, includeOrphaned).stream()
                .map(support::toResponse)
                .toList();
    }
}