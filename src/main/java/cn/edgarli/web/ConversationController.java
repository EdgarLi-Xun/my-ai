package cn.edgarli.web;

import cn.edgarli.common.Result;
import cn.edgarli.service.ConversationService;
import cn.edgarli.web.dto.ConversationResponse;
import cn.edgarli.web.dto.UpdateConversationRequest;
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
 * <p>
 * 跨用户的资源访问由 service 层拒绝（{@code 4031} {@code ConversationNotFound}），
 * 不在这里再校验 owner——{@link ConversationService#requireOwnedConversation} 已做。
 */
@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @PostMapping
    public Result<ConversationResponse> create() {
        Long userId = UserController.currentUserId();
        return Result.success(conversationService.create(userId));
    }

    @GetMapping
    public Result<List<ConversationResponse>> list(
            @RequestParam(name = "include_deleted", defaultValue = "false") boolean includeDeleted) {
        Long userId = UserController.currentUserId();
        return Result.success(conversationService.list(userId, includeDeleted));
    }

    @PatchMapping("/{id}")
    public Result<ConversationResponse> updateTitle(
            @PathVariable Long id,
            @RequestBody UpdateConversationRequest request) {
        Long userId = UserController.currentUserId();
        return Result.success(conversationService.updateTitle(userId, id, request.title()));
    }

    @DeleteMapping("/{id}")
    public Result<ConversationResponse> softDelete(@PathVariable Long id) {
        Long userId = UserController.currentUserId();
        return Result.success(conversationService.softDelete(userId, id));
    }

    @PostMapping("/{id}/restore")
    public Result<ConversationResponse> restore(@PathVariable Long id) {
        Long userId = UserController.currentUserId();
        return Result.success(conversationService.restore(userId, id));
    }

    @DeleteMapping("/{id}/permanent")
    public Result<Void> hardDelete(@PathVariable Long id) {
        Long userId = UserController.currentUserId();
        conversationService.hardDelete(userId, id);
        return Result.success();
    }
}