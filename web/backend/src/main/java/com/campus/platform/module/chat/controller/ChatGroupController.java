package com.campus.platform.module.chat.controller;

import com.campus.platform.common.Result;
import com.campus.platform.common.UserContext;
import com.campus.platform.module.chat.dto.GroupCreateDTO;
import com.campus.platform.module.chat.service.IChatGroupService;
import com.campus.platform.module.chat.service.IChatService;
import com.campus.platform.module.chat.vo.ConversationVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatGroupController {
    private final IChatGroupService groups;
    private final IChatService chat;

    public record Invite(@NotNull @Positive Long userId) {}
    public record Decision(@NotNull Boolean accept) {}
    public record Rename(@NotBlank @Size(max = 32) String name) {}
    public record Preferences(Boolean pinned, Boolean muted) {}

    @PostMapping("/groups")
    public Result<ConversationVO> create(@Valid @RequestBody GroupCreateDTO form) {
        Long userId = UserContext.getUid();
        return Result.ok(chat.conversation(userId, groups.create(userId, form)));
    }

    @GetMapping("/group-invitations")
    public Result<List<IChatGroupService.Invitation>> invitations() {
        return Result.ok(groups.invitations(UserContext.getUid()));
    }

    @PostMapping("/groups/{id}/invitations")
    public Result<Void> invite(@PathVariable Long id, @Valid @RequestBody Invite body) {
        groups.invite(UserContext.getUid(), id, body.userId());
        return Result.ok();
    }

    @PutMapping("/groups/{id}/invitation")
    public Result<Void> respond(@PathVariable Long id, @Valid @RequestBody Decision body) {
        groups.respond(UserContext.getUid(), id, body.accept());
        return Result.ok();
    }

    @PutMapping("/groups/{id}/name")
    public Result<Void> rename(@PathVariable Long id, @Valid @RequestBody Rename body) {
        groups.rename(UserContext.getUid(), id, body.name());
        return Result.ok();
    }

    @DeleteMapping("/groups/{id}/membership")
    public Result<Void> leave(@PathVariable Long id) {
        groups.leave(UserContext.getUid(), id);
        return Result.ok();
    }

    @DeleteMapping("/groups/{id}/members/{memberId}")
    public Result<Void> remove(@PathVariable Long id, @PathVariable Long memberId) {
        groups.removeMember(UserContext.getUid(), id, memberId);
        return Result.ok();
    }

    @PutMapping("/conversations/{id}/preferences")
    public Result<Void> preference(@PathVariable Long id, @RequestBody Preferences body) {
        if (body.pinned() != null || body.muted() != null) {
            groups.preference(UserContext.getUid(), id, body.pinned(), body.muted());
        }
        return Result.ok();
    }
}
