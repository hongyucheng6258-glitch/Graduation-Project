package com.campus.platform.module.message.controller;

import com.campus.platform.module.message.service.MessageService;
import com.campus.platform.module.message.vo.UnreadCountVO;
import com.campus.platform.module.message.entity.Message;

import com.campus.platform.common.PageResult;
import com.campus.platform.common.R;
import com.campus.platform.common.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/message")
@RequiredArgsConstructor
public class MessageController {
    private final MessageService messageService;

    @GetMapping("/list")
    public R<PageResult<Message>> list(
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return R.ok(messageService.list(UserContext.getUid(), type, pageNum, pageSize));
    }

    @GetMapping("/unread-count")
    public R<UnreadCountVO> unreadCount() {
        return R.ok(messageService.unreadCount(UserContext.getUid()));
    }

    @PutMapping("/{id}/read")
    public R<Void> markRead(@PathVariable Long id) {
        messageService.markRead(UserContext.getUid(), id);
        return R.ok();
    }

    @PutMapping("/read-all")
    public R<Void> markAllRead() {
        messageService.markAllRead(UserContext.getUid());
        return R.ok();
    }
}
