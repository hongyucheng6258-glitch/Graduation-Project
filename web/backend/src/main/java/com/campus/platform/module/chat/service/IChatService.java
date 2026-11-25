package com.campus.platform.module.chat.service;

import com.campus.platform.common.mapper.MapperQueries;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.campus.platform.module.ai.gateway.SensitiveWordService;
import com.campus.platform.module.chat.dto.ChatSendDTO;
import com.campus.platform.module.chat.dto.GroupCreateDTO;
import com.campus.platform.module.chat.vo.ChatUserVO;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.Objects;

/**
 * ChatService 服务接口。
 */
public interface IChatService {

    ChatConversation createConversation(Long userId, Long targetUserId, String contextType);

    ChatConversation createConversation(Long userId, Long targetUserId, String contextType, Long contextId, String ignoredContextTitle);

    ChatConversation createGroup(Long userId, GroupCreateDTO dto);

    List<ChatUserVO> searchUsers(Long userId, String keyword);

    List<ChatUserVO> groupMembers(Long userId, Long conversationId);

    ChatMessage sendMessage(Long senderId, Long conversationId, ChatSendDTO dto);

    void markRead(Long userId, Long conversationId, Long lastReadMessageId);

    ChatConversation requireMember(Long conversationId, Long userId);

    List<ConversationVO> conversations(Long userId);

    ConversationVO conversation(Long userId, Long conversationId);

    void hideConversation(Long userId, Long conversationId);

    List<ChatMessageVO> history(Long userId, Long conversationId, Long beforeId, int limit);

    long unreadCount(Long userId);

    void block(Long userId, Long blockedUserId);

    void unblock(Long userId, Long blockedUserId);

    List<BlockedUserVO> blockList(Long userId);

    Report reportMessage(Long userId, Long messageId, String reasonType, String reason);
}
