package com.campus.platform.module.message.service;

import com.campus.platform.module.message.mapper.MessageMapper;
import com.campus.platform.module.message.vo.UnreadCountVO;
import com.campus.platform.module.message.entity.Message;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.platform.common.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 消息通知服务（C7，共享约定 #9 触发点）：
 * 业务事件同步写 message 表；前端轮询 unread-count 拉角标。
 */
@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageMapper messageMapper;

    /**
     * 发送消息（各业务模块调用）。
     *
     * @param userId  接收人
     * @param type    system/interact/audit
     * @param title   标题
     * @param content 内容
     * @param bizType 业务类型（可空）
     * @param bizId   业务ID（可空）
     */
    public void send(Long userId, String type, String title, String content,
                     String bizType, Long bizId) {
        if (userId == null) {
            return;
        }
        Message msg = new Message();
        msg.setUserId(userId);
        msg.setType(type);
        msg.setTitle(StrUtil.sub(title, 0, 64));
        msg.setContent(content == null ? null : StrUtil.sub(content, 0, 500));
        msg.setBizType(bizType);
        msg.setBizId(bizId);
        msg.setIsRead(0);
        messageMapper.insert(msg);
    }

    /** 消息列表（type 筛选 + 分页） */
    public PageResult<Message> list(Long userId, String type, int pageNum, int pageSize) {
        Page<Message> page = messageMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getUserId, userId)
                        .eq(StrUtil.isNotBlank(type), Message::getType, type)
                        .orderByDesc(Message::getId));
        return PageResult.of(page);
    }

    /** 未读数（轮询角标） */
    public UnreadCountVO unreadCount(Long userId) {
        Long count = messageMapper.selectCount(new LambdaQueryWrapper<Message>()
                .eq(Message::getUserId, userId)
                .eq(Message::getIsRead, 0)
                .ne(Message::getType, "private_message"));
        return new UnreadCountVO(count);
    }

    /** 标记单条已读 */
    public void markRead(Long userId, Long id) {
        messageMapper.update(null, new LambdaUpdateWrapper<Message>()
                .eq(Message::getId, id)
                .eq(Message::getUserId, userId)
                .set(Message::getIsRead, 1));
    }

    /** 全部已读 */
    public void markAllRead(Long userId) {
        messageMapper.update(null, new LambdaUpdateWrapper<Message>()
                .eq(Message::getUserId, userId)
                .eq(Message::getIsRead, 0)
                .set(Message::getIsRead, 1));
    }
}
