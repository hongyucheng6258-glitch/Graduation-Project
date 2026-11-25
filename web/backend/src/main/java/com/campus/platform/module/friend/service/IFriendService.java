package com.campus.platform.module.friend.service;

import com.campus.platform.module.chat.vo.ChatUserVO;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 好友关系服务接口：申请、响应、删除、列表。
 */
public interface IFriendService {

    /** 好友/申请人摘要 */
    record Contact(ChatUserVO user, String status, String greeting, LocalDateTime updatedAt) {}

    /** 我的好友与待处理申请 */
    List<Contact> list(Long userId);

    /** 与某人的好友状态 */
    String status(Long userId, Long peerId);

    /** 发送好友申请 */
    String request(Long userId, Long peerId, String greeting);

    /** 响应好友申请 */
    void respond(Long userId, Long peerId, boolean accept);

    /** 删除好友/撤回申请 */
    void remove(Long userId, Long peerId);
}
