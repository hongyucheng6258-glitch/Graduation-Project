package com.campus.platform.module.chat.service;


import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.campus.platform.module.ai.gateway.SensitiveWordService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import com.campus.platform.module.chat.dto.ChatSendDTO;
import com.campus.platform.module.chat.entity.ChatConversation;
import com.campus.platform.module.chat.entity.ChatConversationMember;
import com.campus.platform.module.chat.entity.ChatMessage;
import com.campus.platform.module.chat.mapper.ChatConversationMapper;
import com.campus.platform.module.chat.mapper.ChatConversationMemberMapper;
import com.campus.platform.module.chat.mapper.ChatMessageMapper;
import com.campus.platform.module.chat.mapper.UserBlockMapper;
import com.campus.platform.module.chat.websocket.ChatRealtimePublisher;
import com.campus.platform.common.BizException;
import com.campus.platform.config.SystemConfigHolder;
import com.campus.platform.module.upload.entity.UploadResource;
import com.campus.platform.module.user.entity.User;
import com.campus.platform.module.report.mapper.ReportMapper;
import com.campus.platform.module.user.mapper.UserMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock private ChatConversationMapper conversationMapper;
    @Mock private ChatConversationMemberMapper memberMapper;
    @Mock private ChatMessageMapper messageMapper;
    @Mock private UserBlockMapper blockMapper;
    @Mock private UserMapper userMapper;
    @Mock private ReportMapper reportMapper;
    @Mock private SensitiveWordService sensitiveWordService;
    @Mock private ChatNotificationService notificationService;
    @Mock private ChatRealtimePublisher realtimePublisher;
    @Mock private UploadResourceService uploadResourceService;
    @Mock private ChatContextValidator contextValidator;
    @Mock private SystemConfigHolder systemConfigHolder;
    @Mock private ChatRateLimiter rateLimiter;

    @InjectMocks private ChatService chatService;

    @BeforeEach
    void setUpSystemConfigDefaults() {
        lenient().when(systemConfigHolder.getChatMessageMaxLength()).thenReturn(2000);
    }

    @BeforeAll
    static void initializeMybatisPlusLambdaMetadata() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "ChatServiceTest");
        assistant.setCurrentNamespace(ChatConversationMemberMapper.class.getName());
        TableInfoHelper.initTableInfo(assistant, ChatConversationMember.class);
    }

    @Test
    void cannotCreateConversationWithSelf() {
        assertThrows(IllegalArgumentException.class, () -> chatService.createConversation(7L, 7L, null));
        verifyNoInteractions(conversationMapper, memberMapper);
    }

    @Test
    void cannotCreateConversationWithMissingUser() {
        when(userMapper.selectById(8L)).thenReturn(null);

        BizException error = assertThrows(BizException.class,
                () -> chatService.createConversation(7L, 8L, null));

        assertEquals("目标用户不存在", error.getMessage());
        verify(conversationMapper, never()).insertConversation(any(ChatConversation.class));
    }

    @Test
    void createConversationReusesExistingConversation() {
        User target = normalUser(8L);
        ChatConversation existing = conversation(99L, 7L, 8L);
        when(userMapper.selectById(8L)).thenReturn(target);
        when(conversationMapper.findByUserPair(7L, 8L)).thenReturn(existing);

        ChatConversation actual = chatService.createConversation(7L, 8L, null);

        assertEquals(99L, actual.getId());
        verify(conversationMapper, never()).insertConversation(any(ChatConversation.class));
    }

    @Test
    void createConversationUsesDatabaseUpsertAndIdempotentMembers() {
        User target = normalUser(8L);
        ChatConversation stored = conversation(99L, 7L, 8L);
        when(userMapper.selectById(8L)).thenReturn(target);
        when(conversationMapper.findByUserPair(7L, 8L)).thenReturn(null, stored);
        when(contextValidator.validate(7L, 8L, "idle", 20L)).thenReturn("二手书");

        ChatConversation actual = chatService.createConversation(7L, 8L, "idle", 20L, "二手书");

        assertSame(stored, actual);
        verify(conversationMapper).insertConversation(argThat(value ->
                value.getUser1Id().equals(7L) && value.getUser2Id().equals(8L)));
        verify(memberMapper).insertMemberIfAbsent(99L, 7L);
        verify(memberMapper).insertMemberIfAbsent(99L, 8L);
        verify(memberMapper).restoreMember(99L, 7L);
        verify(memberMapper).restoreMember(99L, 8L);
    }

    @Test
    void nonMemberCannotReadConversation() {
        when(conversationMapper.selectById(10L)).thenReturn(conversation(10L, 7L, 8L));
        when(memberMapper.findMember(10L, 9L)).thenReturn(null);

        assertThrows(BizException.class, () -> chatService.requireMember(10L, 9L));
    }

    @Test
    void duplicateClientMessageReturnsExistingMessage() {
        ChatMessage existing = new ChatMessage();
        existing.setId(42L);
        when(messageMapper.findBySenderAndClientMessageId(7L, "client-1")).thenReturn(existing);

        ChatMessage actual = chatService.sendMessage(7L, 10L, send("client-1", "text", "hello"));

        assertEquals(42L, actual.getId());
        verify(messageMapper, never()).insert(any(ChatMessage.class));
    }

    @Test
    void blockInEitherDirectionPreventsSending() {
        when(messageMapper.findBySenderAndClientMessageId(7L, "client-2")).thenReturn(null);
        when(conversationMapper.findByIdForUpdate(10L)).thenReturn(conversation(10L, 7L, 8L));
        when(memberMapper.findMemberForUpdate(10L, 7L)).thenReturn(member(10L, 7L, 0));
        when(blockMapper.countEitherDirection(7L, 8L)).thenReturn(1L);

        assertThrows(BizException.class,
                () -> chatService.sendMessage(7L, 10L, send("client-2", "text", "hello")));
        verify(messageMapper, never()).insert(any(ChatMessage.class));
    }

    @Test
    void imageMessageMustUsePlatformResource() {
        when(messageMapper.findBySenderAndClientMessageId(7L, "client-3")).thenReturn(null);
        when(conversationMapper.findByIdForUpdate(10L)).thenReturn(conversation(10L, 7L, 8L));
        when(memberMapper.findMemberForUpdate(10L, 7L)).thenReturn(member(10L, 7L, 0));
        when(blockMapper.countEitherDirection(7L, 8L)).thenReturn(0L);
        doThrow(new BizException(com.campus.platform.common.ResultCode.BAD_REQUEST, "图片资源不存在或不属于当前用户"))
                .when(uploadResourceService).requireOwnedImage(7L, "https://evil.example/a.jpg");

        assertThrows(BizException.class,
                () -> chatService.sendMessage(7L, 10L,
                        send("client-3", "image", "https://evil.example/a.jpg")));
    }

    @Test
    void concurrentDuplicateMessageReturnsCommittedRowWithoutRepeatingSideEffects() {
        ChatConversation conversation = conversation(10L, 7L, 8L);
        ChatMessage committed = new ChatMessage();
        committed.setId(42L);
        when(messageMapper.findBySenderAndClientMessageId(7L, "client-race")).thenReturn(null, committed);
        when(conversationMapper.findByIdForUpdate(10L)).thenReturn(conversation);
        when(memberMapper.findMemberForUpdate(10L, 7L)).thenReturn(member(10L, 7L, 0));
        when(blockMapper.countEitherDirection(7L, 8L)).thenReturn(0L);
        doThrow(new DuplicateKeyException("uk_chat_idempotent"))
                .when(messageMapper).insert(any(ChatMessage.class));

        ChatMessage actual = chatService.sendMessage(7L, 10L, send("client-race", "text", "hello"));

        assertSame(committed, actual);
        verify(memberMapper, never()).incrementUnreadAndRestore(anyLong(), anyLong());
        verify(conversationMapper, never()).updateById(any(ChatConversation.class));
        verifyNoInteractions(notificationService, realtimePublisher);
    }

    @Test
    void sendMessageUsesAtomicUnreadIncrement() {
        ChatConversation conversation = conversation(10L, 7L, 8L);
        ChatConversationMember sender = member(10L, 7L, 0);
        when(messageMapper.findBySenderAndClientMessageId(7L, "client-atomic")).thenReturn(null);
        doNothing().when(rateLimiter).checkSend(7L);
        when(conversationMapper.findByIdForUpdate(10L)).thenReturn(conversation);
        when(memberMapper.findMemberForUpdate(10L, 7L)).thenReturn(sender);
        when(blockMapper.countEitherDirection(7L, 8L)).thenReturn(0L);
        when(memberMapper.incrementUnreadAndRestore(10L, 8L)).thenReturn(1);
        when(memberMapper.findMember(10L, 8L)).thenReturn(member(10L, 8L, 4));

        chatService.sendMessage(7L, 10L, send("client-atomic", "text", "hello"));

        verify(memberMapper).incrementUnreadAndRestore(10L, 8L);
        verify(memberMapper, never()).updateById(any(ChatConversationMember.class));
        verify(realtimePublisher).messageAfterCommit(any(ChatMessage.class), eq(4L));
    }

    @Test
    void markReadRejectsMessageOutsideConversation() {
        when(conversationMapper.findByIdForUpdate(10L)).thenReturn(conversation(10L, 7L, 8L));
        when(memberMapper.findMemberForUpdate(10L, 7L)).thenReturn(member(10L, 7L, 3));
        when(messageMapper.findMessageInConversation(88L, 10L)).thenReturn(null);

        assertThrows(BizException.class, () -> chatService.markRead(7L, 10L, 88L));

        verify(memberMapper, never()).markReadMonotonic(anyLong(), anyLong(), anyLong(), any());
        verifyNoInteractions(notificationService, realtimePublisher);
    }

    @Test
    void markReadCannotAdvanceBeyondLatestReceivedMessage() {
        ChatMessage sentByReader = message(88L, 10L, 7L, 8L);
        when(conversationMapper.findByIdForUpdate(10L)).thenReturn(conversation(10L, 7L, 8L));
        when(memberMapper.findMemberForUpdate(10L, 7L)).thenReturn(member(10L, 7L, 3));
        when(messageMapper.findMessageInConversation(88L, 10L)).thenReturn(sentByReader);
        when(messageMapper.findLatestReceivedMessageId(10L, 7L, 88L)).thenReturn(77L);
        when(memberMapper.markReadMonotonic(eq(10L), eq(7L), eq(77L), any())).thenReturn(1);

        chatService.markRead(7L, 10L, 88L);

        verify(memberMapper).markReadMonotonic(eq(10L), eq(7L), eq(77L), any());
        verify(messageMapper).markReadUpTo(eq(10L), eq(7L), eq(77L), any());
        verify(realtimePublisher).readAfterCommit(7L, 8L, 10L, 77L, 3L);
    }

    @Test
    void partialReadKeepsNewerUnreadCountMonotonic() {
        ChatConversationMember member = member(10L, 7L, 3);
        ChatMessage received = message(88L, 10L, 8L, 7L);
        when(conversationMapper.findByIdForUpdate(10L)).thenReturn(conversation(10L, 7L, 8L));
        when(memberMapper.findMemberForUpdate(10L, 7L)).thenReturn(member);
        when(messageMapper.findMessageInConversation(88L, 10L)).thenReturn(received);
        when(memberMapper.markReadMonotonic(eq(10L), eq(7L), eq(88L), any())).thenReturn(1);
        when(messageMapper.markReadUpTo(eq(10L), eq(7L), eq(88L), any())).thenReturn(1);

        chatService.markRead(7L, 10L, 88L);

        verify(realtimePublisher).readAfterCommit(7L, 8L, 10L, 88L, 2L);
    }

    @Test
    void markReadClearsUnreadAndNotification() {
        ChatConversationMember member = member(10L, 7L, 3);
        ChatMessage received = message(88L, 10L, 8L, 7L);
        when(conversationMapper.findByIdForUpdate(10L)).thenReturn(conversation(10L, 7L, 8L));
        when(memberMapper.findMemberForUpdate(10L, 7L)).thenReturn(member);
        when(messageMapper.findMessageInConversation(88L, 10L)).thenReturn(received);
        when(memberMapper.markReadMonotonic(eq(10L), eq(7L), eq(88L), any())).thenReturn(1);
        when(messageMapper.markReadUpTo(eq(10L), eq(7L), eq(88L), any())).thenReturn(3);

        chatService.markRead(7L, 10L, 88L);

        verify(memberMapper).markReadMonotonic(eq(10L), eq(7L), eq(88L), any());
        verify(messageMapper).markReadUpTo(eq(10L), eq(7L), eq(88L), any());
        verify(notificationService).clear(7L, 10L);
    }

    @Test
    void nullReadCursorDoesNotClearUnreadOrResetStoredCursor() {
        ChatConversationMember member = member(10L, 7L, 3);
        member.setLastReadMessageId(77L);
        when(conversationMapper.findByIdForUpdate(10L)).thenReturn(conversation(10L, 7L, 8L));
        when(memberMapper.findMemberForUpdate(10L, 7L)).thenReturn(member);

        chatService.markRead(7L, 10L, null);

        verify(memberMapper, never()).markReadMonotonic(anyLong(), anyLong(), anyLong(), any());
        verifyNoInteractions(notificationService, realtimePublisher);
    }

    @Test
    void olderReadCursorCannotMoveStoredCursorBackward() {
        ChatConversationMember member = member(10L, 7L, 0);
        member.setLastReadMessageId(90L);
        when(conversationMapper.findByIdForUpdate(10L)).thenReturn(conversation(10L, 7L, 8L));
        when(memberMapper.findMemberForUpdate(10L, 7L)).thenReturn(member);
        when(messageMapper.findMessageInConversation(80L, 10L)).thenReturn(message(80L, 10L, 8L, 7L));

        chatService.markRead(7L, 10L, 80L);

        verify(memberMapper, never()).markReadMonotonic(anyLong(), anyLong(), anyLong(), any());
        verify(messageMapper, never()).markReadUpTo(anyLong(), anyLong(), anyLong(), any());
        verifyNoInteractions(notificationService, realtimePublisher);
    }

    @Test
    void imageMessageBindsOwnedUploadResourceToCommittedMessage() {
        ChatConversation conversation = conversation(10L, 7L, 8L);
        UploadResource resource = new UploadResource();
        resource.setId(55L);
        when(messageMapper.findBySenderAndClientMessageId(7L, "client-image")).thenReturn(null);
        when(conversationMapper.findByIdForUpdate(10L)).thenReturn(conversation);
        when(memberMapper.findMemberForUpdate(10L, 7L)).thenReturn(member(10L, 7L, 0));
        when(blockMapper.countEitherDirection(7L, 8L)).thenReturn(0L);
        when(uploadResourceService.requireOwnedImage(7L, "http://localhost:9000/campus/images/a.jpg")).thenReturn(resource);
        when(memberMapper.incrementUnreadAndRestore(10L, 8L)).thenReturn(1);
        when(memberMapper.findMember(10L, 8L)).thenReturn(member(10L, 8L, 1));
        doAnswer(invocation -> {
            ChatMessage value = invocation.getArgument(0);
            value.setId(66L);
            return 1;
        }).when(messageMapper).insert(any(ChatMessage.class));

        chatService.sendMessage(7L, 10L,
                send("client-image", "image", "http://localhost:9000/campus/images/a.jpg"));

        verify(uploadResourceService).bindChatMessage(resource, 7L, 66L);
    }

    @Test
    void createConversationValidatesContextAgainstRealObject() {
        User target = normalUser(8L);
        when(userMapper.selectById(8L)).thenReturn(target);
        doThrow(new BizException(com.campus.platform.common.ResultCode.FORBIDDEN, "业务对象与目标用户不匹配"))
                .when(contextValidator).validate(7L, 8L, "idle", 20L);

        assertThrows(BizException.class,
                () -> chatService.createConversation(7L, 8L, "idle", 20L, "伪造标题"));

        verify(conversationMapper, never()).insertConversation(any(ChatConversation.class));
    }

    private ChatConversation conversation(Long id, Long user1, Long user2) {
        ChatConversation value = new ChatConversation();
        value.setId(id);
        value.setUser1Id(user1);
        value.setUser2Id(user2);
        return value;
    }

    private ChatConversationMember member(Long conversationId, Long userId, int unread) {
        ChatConversationMember value = new ChatConversationMember();
        value.setId(1L);
        value.setConversationId(conversationId);
        value.setUserId(userId);
        value.setUnreadCount(unread);
        return value;
    }

    private ChatMessage message(Long id, Long conversationId, Long senderId, Long receiverId) {
        ChatMessage value = new ChatMessage();
        value.setId(id);
        value.setConversationId(conversationId);
        value.setSenderId(senderId);
        value.setReceiverId(receiverId);
        return value;
    }

    private User normalUser(Long id) {
        User user = new User();
        user.setId(id);
        user.setStatus(0);
        return user;
    }

    private ChatSendDTO send(String clientId, String type, String content) {
        ChatSendDTO dto = new ChatSendDTO();
        dto.setClientMessageId(clientId);
        dto.setMessageType(type);
        dto.setContent(content);
        return dto;
    }
}
