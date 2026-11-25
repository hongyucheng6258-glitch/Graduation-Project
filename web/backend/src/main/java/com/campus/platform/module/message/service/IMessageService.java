package com.campus.platform.module.message.service;

import com.campus.platform.common.PageResult;
import com.campus.platform.module.message.entity.Message;
import com.campus.platform.module.message.vo.UnreadCountVO;

/**
 * 消息通知服务接口：
 * 业务事件同步写 message 表；前端轮询 unread-count 拉角标。
 */
public interface IMessageService {

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
    void send(Long userId, String type, String title, String content, String bizType, Long bizId);

    /** 消息列表（type 筛选 + 分页） */
    PageResult<Message> list(Long userId, String type, int pageNum, int pageSize);

    /** 未读数（轮询角标） */
    UnreadCountVO unreadCount(Long userId);

    /** 标记单条已读 */
    void markRead(Long userId, Long id);

    /** 清理某业务对象的未读提醒 */
    void clearBusinessNotice(Long userId, String bizType, Long bizId);

    /** 全部已读 */
    void markAllRead(Long userId);
}
