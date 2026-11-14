package com.campus.platform.module.chat.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.campus.platform.common.BizException;
import com.campus.platform.common.ResultCode;
import com.campus.platform.module.ai.gateway.SensitiveWordService;
import com.campus.platform.module.chat.dto.GroupCreateDTO;
import com.campus.platform.module.chat.entity.ChatConversation;
import com.campus.platform.module.chat.entity.ChatConversationMember;
import com.campus.platform.module.chat.entity.ChatGroupInvite;
import com.campus.platform.module.chat.mapper.ChatConversationMapper;
import com.campus.platform.module.chat.mapper.ChatConversationMemberMapper;
import com.campus.platform.module.chat.mapper.ChatGroupInviteMapper;
import com.campus.platform.module.chat.mapper.UserBlockMapper;
import com.campus.platform.module.chat.websocket.ChatRealtimePublisher;
import com.campus.platform.module.message.service.MessageService;
import com.campus.platform.module.user.entity.User;
import com.campus.platform.module.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ChatGroupService {
    private final ChatService chat;
    private final ChatConversationMapper conversations;
    private final ChatConversationMemberMapper members;
    private final ChatGroupInviteMapper invites;
    private final UserMapper users;
    private final UserBlockMapper blocks;
    private final SensitiveWordService sensitiveWords;
    private final MessageService messages;
    private final ChatNotificationService notifications;
    private final ChatRealtimePublisher realtime;

    public record Invitation(Long conversationId, String groupName) {}

    @Transactional
    public Long create(Long userId, GroupCreateDTO form) {
        ChatConversation group = chat.createGroup(userId, form);
        for (Long invitee : form.getMemberIds().stream().filter(id -> !id.equals(userId)).distinct().toList()) {
            addInvite(group, userId, invitee);
        }
        return group.getId();
    }

    @Transactional
    public void invite(Long userId, Long conversationId, Long inviteeId) {
        ChatConversation group = ownedGroup(userId, conversationId);
        User invitee = users.selectById(inviteeId);
        if (invitee == null || !Integer.valueOf(0).equals(invitee.getStatus())
                || blocks.countEitherDirection(userId, inviteeId) > 0) {
            throw new BizException(ResultCode.FORBIDDEN, "当前无法邀请该同学");
        }
        if (members.findMemberForUpdate(conversationId, inviteeId) != null) return;
        var pending = invites.selectList(pending(conversationId));
        if (pending.stream().anyMatch(invite -> inviteeId.equals(invite.getInviteeId()))) return;
        if (pending.size() + members.findMembers(conversationId).size() >= 50) {
            throw new BizException(ResultCode.BAD_REQUEST, "群成员和待确认邀请合计最多 50 人");
        }
        addInvite(group, userId, inviteeId);
    }

    public List<Invitation> invitations(Long userId) {
        return invites.selectList(new LambdaQueryWrapper<ChatGroupInvite>().eq(ChatGroupInvite::getInviteeId, userId)
                .orderByDesc(ChatGroupInvite::getId)).stream().map(invite -> {
            ChatConversation group = conversations.selectById(invite.getConversationId());
            return group == null ? null : new Invitation(group.getId(), group.getGroupName());
        }).filter(Objects::nonNull).toList();
    }

    @Transactional
    public void respond(Long userId, Long conversationId, boolean accept) {
        ChatConversation group = lockGroup(conversationId);
        if (members.findMemberForUpdate(conversationId, userId) != null) return;
        ChatGroupInvite invitation = invites.selectOne(pending(conversationId).eq(ChatGroupInvite::getInviteeId, userId));
        if (invitation == null) {
            if (!accept) return;
            throw new BizException(ResultCode.FORBIDDEN, "没有待确认的入群邀请");
        }
        if (accept) {
            if (blocks.countEitherDirection(group.getGroupOwnerId(), userId) > 0
                    || blocks.countEitherDirection(invitation.getInviterId(), userId) > 0) {
                throw new BizException(ResultCode.FORBIDDEN, "该入群邀请已不可用");
            }
            members.joinGroup(conversationId, userId, group.getLastMessageId());
        }
        invites.deleteById(invitation.getId());
        messages.clearBusinessNotice(userId, "group_invite", conversationId);
        if (accept) publishMembers(conversationId);
    }

    @Transactional
    public void rename(Long userId, Long conversationId, String name) {
        ChatConversation group = ownedGroup(userId, conversationId);
        String title = name.strip();
        if (title.isEmpty()) throw new BizException(ResultCode.BAD_REQUEST, "请输入群聊名称");
        if (sensitiveWords.contains(title)) throw new BizException(ResultCode.SENSITIVE_WORD);
        group.setGroupName(title);
        conversations.updateById(group);
        publishMembers(conversationId);
    }

    @Transactional
    public void leave(Long userId, Long conversationId) {
        ChatConversation group = lockGroup(conversationId);
        ChatConversationMember member = members.findMemberForUpdate(conversationId, userId);
        if (member == null) return;
        members.deleteById(member.getId());
        var remaining = members.findMembers(conversationId);
        if (remaining.isEmpty()) {
            for (ChatGroupInvite invite : invites.selectList(pending(conversationId))) {
                messages.clearBusinessNotice(invite.getInviteeId(), "group_invite", conversationId);
            }
            invites.delete(pending(conversationId));
            // Retain messages and conversation IDs for existing moderation records.
            group.setGroupOwnerId(0L);
            conversations.updateById(group);
        } else if (userId.equals(group.getGroupOwnerId())) {
            group.setGroupOwnerId(remaining.get(0).getUserId());
            conversations.updateById(group);
        }
        realtime.groupCreatedAfterCommit(conversationId, List.of(userId));
        publishMembers(conversationId);
    }

    @Transactional
    public void removeMember(Long userId, Long conversationId, Long target) {
        ownedGroup(userId, conversationId);
        if (userId.equals(target)) throw new BizException(ResultCode.BAD_REQUEST, "请使用退出群聊");
        ChatConversationMember member = members.findMemberForUpdate(conversationId, target);
        if (member == null) return;
        members.deleteById(member.getId());
        realtime.groupCreatedAfterCommit(conversationId, List.of(target));
        publishMembers(conversationId);
    }

    @Transactional
    public void preference(Long userId, Long conversationId, Boolean pinned, Boolean muted) {
        if (conversations.findByIdForUpdate(conversationId) == null) {
            throw new BizException(ResultCode.NOT_FOUND, "会话不存在");
        }
        var member = members.findMemberForUpdate(conversationId, userId);
        if (member == null) throw new BizException(ResultCode.FORBIDDEN, "无权操作该会话");
        members.update(null, new LambdaUpdateWrapper<ChatConversationMember>()
                .eq(ChatConversationMember::getId, member.getId())
                .set(pinned != null, ChatConversationMember::getPinned, Boolean.TRUE.equals(pinned) ? 1 : 0)
                .set(muted != null, ChatConversationMember::getMuted, Boolean.TRUE.equals(muted) ? 1 : 0));
        if (Boolean.TRUE.equals(muted)) notifications.clear(userId, conversationId);
        realtime.groupCreatedAfterCommit(conversationId, List.of(userId));
    }

    private void addInvite(ChatConversation group, Long inviter, Long invitee) {
        invites.add(group.getId(), invitee, inviter);
        messages.send(invitee, "interact", "群聊邀请", "邀请你加入「" + group.getGroupName() + "」",
                "group_invite", group.getId());
    }

    private LambdaQueryWrapper<ChatGroupInvite> pending(Long conversationId) {
        return new LambdaQueryWrapper<ChatGroupInvite>().eq(ChatGroupInvite::getConversationId, conversationId);
    }

    private ChatConversation lockGroup(Long id) {
        ChatConversation group = conversations.findByIdForUpdate(id);
        if (group == null || !group.isGroup()) throw new BizException(ResultCode.NOT_FOUND, "群聊不存在");
        return group;
    }

    private ChatConversation ownedGroup(Long userId, Long id) {
        ChatConversation group = lockGroup(id);
        if (!userId.equals(group.getGroupOwnerId()) || members.findMemberForUpdate(id, userId) == null) {
            throw new BizException(ResultCode.FORBIDDEN, "仅群主可以进行此操作");
        }
        return group;
    }

    private void publishMembers(Long id) {
        realtime.groupCreatedAfterCommit(id, members.findMembers(id).stream().map(ChatConversationMember::getUserId).toList());
    }
}
