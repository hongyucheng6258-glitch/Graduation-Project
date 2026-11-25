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
import com.campus.platform.module.message.service.IMessageService;
import com.campus.platform.module.user.entity.User;
import com.campus.platform.module.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Objects;

/**
 * ChatGroupService 服务接口。
 */
public interface IChatGroupService {

    /** 群邀请摘要 */
    record Invitation(Long conversationId, String groupName) {}

    Long create(Long userId, GroupCreateDTO form);

    void invite(Long userId, Long conversationId, Long inviteeId);

    List<Invitation> invitations(Long userId);

    void respond(Long userId, Long conversationId, boolean accept);

    void rename(Long userId, Long conversationId, String name);

    void leave(Long userId, Long conversationId);

    void removeMember(Long userId, Long conversationId, Long target);

    void preference(Long userId, Long conversationId, Boolean pinned, Boolean muted);
}
