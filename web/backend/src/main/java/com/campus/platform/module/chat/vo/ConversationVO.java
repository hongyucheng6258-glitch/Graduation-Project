package com.campus.platform.module.chat.vo;

import com.campus.platform.module.chat.entity.ChatConversation;
import com.campus.platform.module.chat.entity.ChatConversationMember;
import com.campus.platform.module.user.entity.User;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ConversationVO {
    private Long id;
    private Long peerUserId;
    private String peerNickname;
    private String peerAvatar;
    private String lastMessageSummary;
    private LocalDateTime lastMessageTime;
    private Integer unreadCount;
    private String contextType;
    private Long contextId;
    private String contextTitle;

    public static ConversationVO of(ChatConversation c, ChatConversationMember member, User peer) {
        ConversationVO vo = new ConversationVO();
        vo.id = c.getId();
        vo.peerUserId = peer == null ? null : peer.getId();
        vo.peerNickname = peer == null ? null : peer.getNickname();
        vo.peerAvatar = peer == null ? null : peer.getAvatar();
        vo.lastMessageSummary = c.getLastMessageSummary();
        vo.lastMessageTime = c.getLastMessageTime();
        vo.unreadCount = member == null ? 0 : member.getUnreadCount();
        vo.contextType = c.getContextType();
        vo.contextId = c.getContextId();
        vo.contextTitle = c.getContextTitle();
        return vo;
    }
}
