package com.campus.platform.module.chat.controller;

import com.campus.platform.module.chat.dto.ChatReadDTO;
import com.campus.platform.module.chat.dto.ChatReportDTO;
import com.campus.platform.module.chat.dto.ChatSendDTO;
import com.campus.platform.module.chat.dto.ConversationCreateDTO;
import com.campus.platform.module.chat.vo.ChatUserVO;
import com.campus.platform.module.chat.entity.ChatConversation;
import com.campus.platform.module.chat.entity.ChatMessage;
import com.campus.platform.module.chat.service.IChatService;
import com.campus.platform.module.chat.vo.BlockedUserVO;
import com.campus.platform.module.chat.vo.ChatMessageVO;
import com.campus.platform.module.chat.vo.ConversationVO;
import com.campus.platform.module.chat.websocket.IChatWsTicketService;
import com.campus.platform.common.Result;
import com.campus.platform.common.UserContext;
import com.campus.platform.module.report.entity.Report;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {
    private final IChatService chatService;
    private final IChatWsTicketService ticketService;

    @PostMapping("/conversations")
    public Result<ConversationVO> create(@Valid @RequestBody ConversationCreateDTO dto) {
        ChatConversation value = chatService.createConversation(UserContext.getUid(), dto.getTargetUserId(),
                dto.getContextType(), dto.getContextId(), dto.getContextTitle());
        return Result.ok(chatService.conversation(UserContext.getUid(), value.getId()));
    }

    @GetMapping("/groups/{id}/members")
    public Result<List<ChatUserVO>> groupMembers(@PathVariable Long id) {
        return Result.ok(chatService.groupMembers(UserContext.getUid(), id));
    }

    @GetMapping("/users")
    public Result<List<ChatUserVO>> searchUsers(@RequestParam String keyword) {
        return Result.ok(chatService.searchUsers(UserContext.getUid(), keyword));
    }

    @GetMapping("/conversations")
    public Result<List<ConversationVO>> conversations() {
        return Result.ok(chatService.conversations(UserContext.getUid()));
    }

    @GetMapping("/conversations/{id}")
    public Result<ConversationVO> conversation(@PathVariable Long id) {
        return Result.ok(chatService.conversation(UserContext.getUid(), id));
    }

    @DeleteMapping("/conversations/{id}")
    public Result<Void> hide(@PathVariable Long id) {
        chatService.hideConversation(UserContext.getUid(), id);
        return Result.ok();
    }

    @GetMapping("/conversations/{id}/messages")
    public Result<List<ChatMessageVO>> history(@PathVariable Long id,
                                          @RequestParam(required = false) Long beforeId,
                                          @RequestParam(defaultValue = "20") Integer limit) {
        return Result.ok(chatService.history(UserContext.getUid(), id, beforeId, limit));
    }

    @PostMapping("/conversations/{id}/messages")
    public Result<ChatMessageVO> send(@PathVariable Long id, @Valid @RequestBody ChatSendDTO dto) {
        ChatMessage message = chatService.sendMessage(UserContext.getUid(), id, dto);
        return Result.ok(ChatMessageVO.from(message));
    }

    @PutMapping("/conversations/{id}/read")
    public Result<Void> read(@PathVariable Long id, @RequestBody(required = false) ChatReadDTO dto) {
        chatService.markRead(UserContext.getUid(), id, dto == null ? null : dto.getLastReadMessageId());
        return Result.ok();
    }

    @GetMapping("/unread-count")
    public Result<Map<String, Long>> unreadCount() {
        return Result.ok(Map.of("count", chatService.unreadCount(UserContext.getUid())));
    }

    @PostMapping("/ws-ticket")
    public Result<Map<String, String>> wsTicket() {
        return Result.ok(Map.of("ticket", ticketService.issue(UserContext.getUid())));
    }

    @PostMapping("/block/{userId}")
    public Result<Void> block(@PathVariable Long userId) {
        chatService.block(UserContext.getUid(), userId);
        return Result.ok();
    }

    @DeleteMapping("/block/{userId}")
    public Result<Void> unblock(@PathVariable Long userId) {
        chatService.unblock(UserContext.getUid(), userId);
        return Result.ok();
    }

    @GetMapping("/block/list")
    public Result<List<BlockedUserVO>> blockList() {
        return Result.ok(chatService.blockList(UserContext.getUid()));
    }

    @PostMapping("/messages/{id}/report")
    public Result<Report> report(@PathVariable Long id, @Valid @RequestBody ChatReportDTO dto) {
        return Result.ok(chatService.reportMessage(UserContext.getUid(), id, dto.getReasonType(), dto.getReason()));
    }
}
