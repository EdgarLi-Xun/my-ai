package cn.edgarli.web;

import cn.edgarli.common.Result;
import cn.edgarli.service.ConversationService;
import cn.edgarli.web.vo.ConversationVo;
import cn.edgarli.web.dto.UpdateConversationDto;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 对话 REST API（ADR 0003，需登录）。
 * Conversation REST API (ADR 0003, login required).
 * <p>
 * 跨用户的资源访问由 service 层拒绝（{@code 4031} {@code ConversationNotFound}），
 * 不在这里再校验 owner——{@link ConversationService#requireOwnedConversation} 已做。
 * Cross-user access is rejected at the service layer (4031); this controller
 * does not re-check ownership — {@link ConversationService#requireOwnedConversation}
 * already does it.
 */
@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    /**
     * 新建一个空对话。
     * Create a new (empty) conversation for the current user.
     *
     * @return 新建对话 / newly created conversation
     */
    @PostMapping
    public Result<ConversationVo> create() {
        Long userId = UserController.currentUserId();
        return Result.success(conversationService.create(userId));
    }

    /**
     * 列出当前用户的所有对话。
     * List conversations owned by the current user.
     *
     * @param includeDeleted 是否包含已软删对话 / whether to include soft-deleted ones
     * @return 对话列表（按 updated_at 倒序）/ conversations ordered by updated_at desc
     */
    @GetMapping
    public Result<List<ConversationVo>> list(
            @RequestParam(name = "include_deleted", defaultValue = "false") boolean includeDeleted) {
        Long userId = UserController.currentUserId();
        return Result.success(conversationService.list(userId, includeDeleted));
    }

    /**
     * 修改对话标题。
     * Update the title of a conversation.
     *
     * @param id 对话 ID / conversation id
     * @param request 新标题 / new title payload
     * @return 更新后的对话 / updated conversation
     */
    @PatchMapping("/{id}")
    public Result<ConversationVo> updateTitle(
            @PathVariable Long id,
            @RequestBody UpdateConversationDto request) {
        Long userId = UserController.currentUserId();
        return Result.success(conversationService.updateTitle(userId, id, request.title()));
    }

    /**
     * 软删除对话（可恢复）。
     * Soft-delete a conversation (recoverable).
     *
     * @param id 对话 ID / conversation id
     * @return 软删后的对话 / conversation after soft delete
     */
    @DeleteMapping("/{id}")
    public Result<ConversationVo> softDelete(@PathVariable Long id) {
        Long userId = UserController.currentUserId();
        return Result.success(conversationService.softDelete(userId, id));
    }

    /**
     * 恢复软删的对话。
     * Restore a soft-delated conversation.
     *
     * @param id 对话 ID / conversation id
     * @return 恢复后的对话 / restored conversation
     */
    @PostMapping("/{id}/restore")
    public Result<ConversationVo> restore(@PathVariable Long id) {
        Long userId = UserController.currentUserId();
        return Result.success(conversationService.restore(userId, id));
    }

    /**
     * 永久删除对话及其全部消息（不可恢复）。
     * Hard-delete the conversation and all of its messages (irreversible).
     *
     * @param id 对话 ID / conversation id
     * @return 空响应 / empty success response
     */
    @DeleteMapping("/{id}/permanent")
    public Result<Void> hardDelete(@PathVariable Long id) {
        Long userId = UserController.currentUserId();
        conversationService.hardDelete(userId, id);
        return Result.success();
    }
}