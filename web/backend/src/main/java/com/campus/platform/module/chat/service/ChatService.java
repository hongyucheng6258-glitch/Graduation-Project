package com.campus.platform.module.chat.service;


import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.campus.platform.module.ai.gateway.SensitiveWordService;
import com.campus.platform.module.chat.dto.ChatSendDTO;
import com.campus.platform.module.chat.entity.ChatConversation;
import com.campus.platform.module.chat.entity.ChatConversationMember;
import com.campus.platform.module.chat.entity.ChatMessage;
import com.campus.platform.module.chat.entity.UserBlock;
import com.campus.platform.module.chat.mapper.ChatConversationMapper;
import com.campus.platform.module.chat.mapper.ChatConversationMemberMapper;
import com.campus.platform.module.chat.mapper.ChatMessageMapper;
import com.campus.platform.module.chat.mapper.UserBlockMapper;
import com.campus.platform.module.chat.vo.BlockedUserVO;
import com.campus.platform.module.chat.vo.ChatMessageVO;
import com.campus.platform.module.chat.vo.ConversationVO;
import com.campus.platform.module.chat.websocket.ChatRealtimePublisher;
import com.campus.platform.common.BizException;
import com.campus.platform.common.Constants;
import com.campus.platform.common.ResultCode;
import com.campus.platform.config.SystemConfigHolder;
import com.campus.platform.module.report.entity.Report;
import com.campus.platform.module.upload.entity.UploadResource;
import com.campus.platform.module.user.entity.User;
import com.campus.platform.module.report.mapper.ReportMapper;
import com.campus.platform.module.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ChatService {
    private final ChatConversationMapper conversationMapper;
    private final ChatConversationMemberMapper memberMapper;
    private final ChatMessageMapper messageMapper;
    private final UserBlockMapper blockMapper;
    private final UserMapper userMapper;
    private final ReportMapper reportMapper;
    private final SensitiveWordService sensitiveWordService;
    private final ChatNotificationService notificationService;
    private final ChatRealtimePublisher realtimePublisher;
    private final UploadResourceService uploadResourceService;
    private final ChatContextValidator contextValidator;
    private final SystemConfigHolder systemConfigHolder;
    private final ChatRateLimiter rateLimiter;

    @Transactional
    public ChatConversation createConversation(Long userId, Long targetUserId, String contextType) {
        return createConversation(userId, targetUserId, contextType, null, null);
    }

    @Transactional
    public ChatConversation createConversation(Long userId, Long targetUserId, String contextType,
                                               Long contextId, String ignoredContextTitle) {
        if (userId == null || targetUserId == null || userId.equals(targetUserId)) {
            throw new IllegalArgumentException("不能与自己创建会话");
        }
        User target = userMapper.selectById(targetUserId);
        if (target == null) {
            throw new BizException(ResultCode.NOT_FOUND, "目标用户不存在");
        }
        if (target.getStatus() != null && target.getStatus() != 0) {
            throw new BizException(ResultCode.FORBIDDEN, "目标用户状态异常");
        }
        String trustedContextTitle = contextValidator.validate(userId, targetUserId, contextType, contextId);
        ChatConversation existing = conversationMapper.findByUserPair(userId, targetUserId);
        if (existing != null) {
            restoreMember(existing.getId(), userId);
            restoreMember(existing.getId(), targetUserId);
            if (contextType != null || contextId != null) {
                existing.setContextType(contextType);
                existing.setContextId(contextId);
                existing.setContextTitle(trustedContextTitle);
                conversationMapper.updateById(existing);
            }
            return existing;
        }
        ChatConversation conversation = new ChatConversation();
        conversation.setUser1Id(Math.min(userId, targetUserId));
        conversation.setUser2Id(Math.max(userId, targetUserId));
        conversation.setContextType(contextType);
        conversation.setContextId(contextId);
        conversation.setContextTitle(trustedContextTitle);
        conversationMapper.insertConversation(conversation);
        ChatConversation stored = conversationMapper.findByUserPair(userId, targetUserId);
        if (stored == null) throw new BizException(ResultCode.SYSTEM_ERROR, "会话创建失败");
        memberMapper.insertMemberIfAbsent(stored.getId(), userId);
        memberMapper.insertMemberIfAbsent(stored.getId(), targetUserId);
        restoreMember(stored.getId(), userId);
        restoreMember(stored.getId(), targetUserId);
        return stored;
    }

    @Transactional
    public ChatMessage sendMessage(Long senderId, Long conversationId, ChatSendDTO dto) {
        ChatMessage duplicate = messageMapper.findBySenderAndClientMessageId(senderId, dto.getClientMessageId());
        if (duplicate != null) return duplicate;
        rateLimiter.checkSend(senderId);
        // 私信长度校验（从系统配置读取，管理端可在线调整）
        if ("text".equals(dto.getMessageType()) && dto.getContent() != null) {
            int maxLen = systemConfigHolder.getChatMessageMaxLength();
            if (dto.getContent().length() > maxLen) {
                throw new BizException(ResultCode.BAD_REQUEST, "消息内容不能超过 " + maxLen + " 字");
            }
        }
        ChatConversation conversation = requireMemberForUpdate(conversationId, senderId);
        Long receiverId = conversation.getUser1Id().equals(senderId) ? conversation.getUser2Id() : conversation.getUser1Id();
        if (blockMapper.countEitherDirection(senderId, receiverId) > 0) {
            throw new BizException(ResultCode.FORBIDDEN, "你们之间已存在拉黑关系");
        }
        UploadResource uploadResource = validateContent(senderId, dto);
        ChatMessage message = new ChatMessage();
        message.setConversationId(conversationId);
        message.setSenderId(senderId);
        message.setReceiverId(receiverId);
        message.setClientMessageId(dto.getClientMessageId());
        message.setMessageType(dto.getMessageType());
        message.setContent(dto.getContent().trim());
        message.setStatus(0);
        try {
            messageMapper.insert(message);
        } catch (DuplicateKeyException error) {
            ChatMessage committed = messageMapper.findBySenderAndClientMessageId(senderId, dto.getClientMessageId());
            if (committed != null) return committed;
            throw error;
        }

        uploadResourceService.bindChatMessage(uploadResource, senderId, message.getId());
        if (memberMapper.incrementUnreadAndRestore(conversationId, receiverId) != 1) {
            throw new BizException(ResultCode.SYSTEM_ERROR, "会话成员数据异常");
        }
        ChatConversationMember receiver = memberMapper.findMember(conversationId, receiverId);
        if (receiver == null) throw new BizException(ResultCode.SYSTEM_ERROR, "会话成员数据异常");
        memberMapper.update(null, new LambdaUpdateWrapper<ChatConversationMember>()
                .eq(ChatConversationMember::getConversationId, conversationId)
                .eq(ChatConversationMember::getUserId, senderId)
                .set(ChatConversationMember::getHidden, 0));

        conversation.setLastMessageId(message.getId());
        conversation.setLastMessageSummary("image".equals(dto.getMessageType()) ? "图片" : StrUtil.sub(dto.getContent().trim(), 0, 255));
        conversation.setLastMessageTime(LocalDateTime.now());
        conversationMapper.updateById(conversation);
        User sender = userMapper.selectById(senderId);
        notificationService.remind(receiverId, conversationId, sender == null ? null : sender.getNickname());
        realtimePublisher.messageAfterCommit(message, receiver.getUnreadCount());
        return message;
    }

    @Transactional
    public void markRead(Long userId, Long conversationId, Long lastReadMessageId) {
        ChatConversation conversation = conversationMapper.findByIdForUpdate(conversationId);
        if (conversation == null) throw new BizException(ResultCode.NOT_FOUND, "会话不存在");
        ChatConversationMember member = memberMapper.findMemberForUpdate(conversationId, userId);
        if (member == null) throw new BizException(ResultCode.FORBIDDEN, "无权操作该会话");
        Long effectiveLastReadMessageId = lastReadMessageId;
        if (lastReadMessageId != null) {
            ChatMessage requested = messageMapper.findMessageInConversation(lastReadMessageId, conversationId);
            if (requested == null) {
                throw new BizException(ResultCode.BAD_REQUEST, "已读消息不属于当前会话");
            }
            if (!userId.equals(requested.getReceiverId())) {
                effectiveLastReadMessageId = messageMapper.findLatestReceivedMessageId(conversationId, userId, lastReadMessageId);
            }
        }
        if (effectiveLastReadMessageId == null ||
                (member.getLastReadMessageId() != null && effectiveLastReadMessageId <= member.getLastReadMessageId())) {
            return;
        }
        LocalDateTime readTime = LocalDateTime.now();
        if (memberMapper.markReadMonotonic(conversationId, userId, effectiveLastReadMessageId, readTime) != 1) {
            return;
        }
        int markedCount = messageMapper.markReadUpTo(conversationId, userId, effectiveLastReadMessageId, readTime);
        long remainingUnread = Math.max(0L, (member.getUnreadCount() == null ? 0L : member.getUnreadCount()) - markedCount);
        if (remainingUnread == 0L) notificationService.clear(userId, conversationId);
        Long peerId = conversation.getUser1Id().equals(userId) ? conversation.getUser2Id() : conversation.getUser1Id();
        realtimePublisher.readAfterCommit(userId, peerId, conversationId, effectiveLastReadMessageId, remainingUnread);
    }

    public ChatConversation requireMember(Long conversationId, Long userId) {
        ChatConversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) throw new BizException(ResultCode.NOT_FOUND, "会话不存在");
        requireMemberEntity(conversationId, userId);
        return conversation;
    }

    private ChatConversation requireMemberForUpdate(Long conversationId, Long userId) {
        ChatConversation conversation = conversationMapper.findByIdForUpdate(conversationId);
        if (conversation == null) throw new BizException(ResultCode.NOT_FOUND, "会话不存在");
        ChatConversationMember member = memberMapper.findMemberForUpdate(conversationId, userId);
        if (member == null) throw new BizException(ResultCode.FORBIDDEN, "无权操作该会话");
        return conversation;
    }

    private ChatConversationMember requireMemberEntity(Long conversationId, Long userId) {
        ChatConversationMember member = memberMapper.findMember(conversationId, userId);
        if (member == null) throw new BizException(ResultCode.FORBIDDEN, "无权操作该会话");
        return member;
    }

    private UploadResource validateContent(Long senderId, ChatSendDTO dto) {
        if ("text".equals(dto.getMessageType())) {
            if (StrUtil.isBlank(dto.getContent()) || dto.getContent().trim().length() > 2000) {
                throw new BizException(ResultCode.BAD_REQUEST, "文字消息长度必须为1至2000字");
            }
            if (sensitiveWordService.contains(dto.getContent())) throw new BizException(ResultCode.SENSITIVE_WORD);
            return null;
        }
        String imageUrl = dto.getContent().trim();
        return dto.getResourceId() == null
                ? uploadResourceService.requireOwnedImage(senderId, imageUrl)
                : uploadResourceService.requireOwnedImage(senderId, dto.getResourceId(), imageUrl);
    }

    private void restoreMember(Long conversationId, Long userId) {
        memberMapper.restoreMember(conversationId, userId);
    }

    public List<ConversationVO> conversations(Long userId) {
        List<ChatConversationMember> memberships = memberMapper.selectList(new LambdaQueryWrapper<ChatConversationMember>()
                .eq(ChatConversationMember::getUserId, userId)
                .eq(ChatConversationMember::getHidden, 0));
        return memberships.stream().map(member -> {
            ChatConversation conversation = conversationMapper.selectById(member.getConversationId());
            if (conversation == null) return null;
            Long peerId = conversation.getUser1Id().equals(userId) ? conversation.getUser2Id() : conversation.getUser1Id();
            return ConversationVO.of(conversation, member, userMapper.selectById(peerId));
        }).filter(Objects::nonNull)
                .sorted((left, right) -> {
                    if (left.getLastMessageTime() == null && right.getLastMessageTime() == null) return Long.compare(right.getId(), left.getId());
                    if (left.getLastMessageTime() == null) return 1;
                    if (right.getLastMessageTime() == null) return -1;
                    return right.getLastMessageTime().compareTo(left.getLastMessageTime());
                }).toList();
    }

    public ConversationVO conversation(Long userId, Long conversationId) {
        ChatConversation conversation = requireMember(conversationId, userId);
        ChatConversationMember member = requireMemberEntity(conversationId, userId);
        Long peerId = conversation.getUser1Id().equals(userId) ? conversation.getUser2Id() : conversation.getUser1Id();
        return ConversationVO.of(conversation, member, userMapper.selectById(peerId));
    }

    public void hideConversation(Long userId, Long conversationId) {
        ChatConversationMember member = requireMemberEntity(conversationId, userId);
        member.setHidden(1);
        member.setUnreadCount(0);
        memberMapper.updateById(member);
        notificationService.clear(userId, conversationId);
    }

    public List<ChatMessageVO> history(Long userId, Long conversationId, Long beforeId, int limit) {
        requireMember(conversationId, userId);
        List<ChatMessage> list = messageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getConversationId, conversationId)
                .lt(beforeId != null, ChatMessage::getId, beforeId)
                .orderByDesc(ChatMessage::getId)
                .last("LIMIT " + Math.min(Math.max(limit, 1), 100)));
        return list.stream().map(ChatMessageVO::from).toList();
    }

    public long unreadCount(Long userId) {
        return memberMapper.selectObjs(new LambdaQueryWrapper<ChatConversationMember>()
                .select(ChatConversationMember::getUnreadCount)
                .eq(ChatConversationMember::getUserId, userId)
                .eq(ChatConversationMember::getHidden, 0)).stream()
                .mapToLong(v -> v == null ? 0L : ((Number) v).longValue()).sum();
    }

    public void block(Long userId, Long blockedUserId) {
        if (userId.equals(blockedUserId)) throw new BizException(ResultCode.BAD_REQUEST, "不能拉黑自己");
        if (blockMapper.findBlock(userId, blockedUserId) == null) {
            UserBlock block = new UserBlock();
            block.setUserId(userId);
            block.setBlockedUserId(blockedUserId);
            blockMapper.insert(block);
        }
    }

    public void unblock(Long userId, Long blockedUserId) {
        UserBlock block = blockMapper.findBlock(userId, blockedUserId);
        if (block != null) blockMapper.deleteById(block.getId());
    }

    public List<BlockedUserVO> blockList(Long userId) {
        return blockMapper.selectList(new LambdaQueryWrapper<UserBlock>()
                        .eq(UserBlock::getUserId, userId)
                        .orderByDesc(UserBlock::getId)).stream()
                .map(block -> userMapper.selectById(block.getBlockedUserId()))
                .filter(Objects::nonNull)
                .map(BlockedUserVO::from)
                .toList();
    }

    public Report reportMessage(Long userId, Long messageId, String reasonType, String reason) {
        ChatMessage message = messageMapper.selectById(messageId);
        if (message == null) throw new BizException(ResultCode.NOT_FOUND, "消息不存在");
        requireMember(message.getConversationId(), userId);
        Report report = new Report();
        report.setReporterId(userId);
        report.setTargetType("chat_message");
        report.setTargetId(messageId);
        report.setReasonType(reasonType);
        report.setReason(reason);
        report.setStatus(Constants.REPORT_PENDING);
        reportMapper.insert(report);
        return report;
    }
}
