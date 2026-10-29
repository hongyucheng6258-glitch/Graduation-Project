package com.campus.platform.module.chat.controller;

import com.campus.platform.module.chat.dto.ChatReadDTO;
import com.campus.platform.module.chat.dto.ChatReportDTO;
import com.campus.platform.module.chat.dto.ChatSendDTO;
import com.campus.platform.module.chat.dto.ConversationCreateDTO;
import com.campus.platform.module.chat.entity.ChatConversation;
import com.campus.platform.module.chat.entity.ChatMessage;
import com.campus.platform.module.chat.service.ChatService;
import com.campus.platform.module.chat.vo.BlockedUserVO;
import com.campus.platform.module.chat.vo.ChatMessageVO;
import com.campus.platform.module.chat.vo.ConversationVO;
import com.campus.platform.module.chat.websocket.ChatWsTicketService;
import com.campus.platform.common.R;
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
    private final ChatService chatService;
    private final ChatWsTicketService ticketService;

    @PostMapping("/conversations")
    public R<ConversationVO> create(@Valid @RequestBody ConversationCreateDTO dto) {
        ChatConversation value = chatService.createConversation(UserContext.getUid(), dto.getTargetUserId(),
                dto.getContextType(), dto.getContextId(), dto.getContextTitle());
        return R.ok(chatService.conversation(UserContext.getUid(), value.getId()));
    }

    @GetMapping("/conversations")
    public R<List<ConversationVO>> conversations() {
        return R.ok(chatService.conversations(UserContext.getUid()));
    }

    @GetMapping("/conversations/{id}")
    public R<ConversationVO> conversation(@PathVariable Long id) {
        return R.ok(chatService.conversation(UserContext.getUid(), id));
    }

    @DeleteMapping("/conversations/{id}")
    public R<Void> hide(@PathVariable Long id) {
        chatService.hideConversation(UserContext.getUid(), id);
        return R.ok();
    }

    @GetMapping("/conversations/{id}/messages")
    public R<List<ChatMessageVO>> history(@PathVariable Long id,
                                          @RequestParam(required = false) Long beforeId,
                                          @RequestParam(defaultValue = "20") Integer limit) {
        return R.ok(chatService.history(UserContext.getUid(), id, beforeId, limit));
    }

    @PostMapping("/conversations/{id}/messages")
    public R<ChatMessageVO> send(@PathVariable Long id, @Valid @RequestBody ChatSendDTO dto) {
        ChatMessage message = chatService.sendMessage(UserContext.getUid(), id, dto);
        return R.ok(ChatMessageVO.from(message));
    }

    @PutMapping("/conversations/{id}/read")
    public R<Void> read(@PathVariable Long id, @RequestBody(required = false) ChatReadDTO dto) {
        chatService.markRead(UserContext.getUid(), id, dto == null ? null : dto.getLastReadMessageId());
        return R.ok();
    }

    @GetMapping("/unread-count")
    public R<Map<String, Long>> unreadCount() {
        return R.ok(Map.of("count", chatService.unreadCount(UserContext.getUid())));
    }

    @PostMapping("/ws-ticket")
    public R<Map<String, String>> wsTicket() {
        return R.ok(Map.of("ticket", ticketService.issue(UserContext.getUid())));
    }

    @PostMapping("/block/{userId}")
    public R<Void> block(@PathVariable Long userId) {
        chatService.block(UserContext.getUid(), userId);
        return R.ok();
    }

    @DeleteMapping("/block/{userId}")
    public R<Void> unblock(@PathVariable Long userId) {
        chatService.unblock(UserContext.getUid(), userId);
        return R.ok();
    }

    @GetMapping("/block/list")
    public R<List<BlockedUserVO>> blockList() {
        return R.ok(chatService.blockList(UserContext.getUid()));
    }

    @PostMapping("/messages/{id}/report")
    public R<Report> report(@PathVariable Long id, @Valid @RequestBody ChatReportDTO dto) {
        return R.ok(chatService.reportMessage(UserContext.getUid(), id, dto.getReasonType(), dto.getReason()));
    }
}
