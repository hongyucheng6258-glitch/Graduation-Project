package com.campus.platform.module.friend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.platform.common.BizException;
import com.campus.platform.common.ResultCode;
import com.campus.platform.module.ai.gateway.SensitiveWordService;
import com.campus.platform.module.chat.mapper.UserBlockMapper;
import com.campus.platform.module.chat.vo.ChatUserVO;
import com.campus.platform.module.friend.Friendship;
import com.campus.platform.module.friend.mapper.FriendshipMapper;
import com.campus.platform.module.friend.service.IFriendService;

import com.campus.platform.module.message.service.IMessageService;
import com.campus.platform.module.user.entity.User;
import com.campus.platform.module.user.mapper.UserMapper;
import com.campus.platform.utils.RedisUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class FriendServiceImpl extends ServiceImpl<FriendshipMapper, Friendship> implements IFriendService {
    private static final String PENDING = "pending";
    private static final String ACCEPTED = "accepted";
    private static final String DECLINED = "declined";

    @Resource
    private UserMapper users;

    @Resource
    private UserBlockMapper blocks;

    @Resource
    private IMessageService messages;

    @Resource
    private SensitiveWordService sensitiveWords;

    @Resource
    private RedisUtils redis;

    @Override
    public List<Contact> list(Long userId) {
        var rows = baseMapper.selectList(new LambdaQueryWrapper<Friendship>()
                .and(query -> query.eq(Friendship::getUser1Id, userId).or().eq(Friendship::getUser2Id, userId))
                .in(Friendship::getStatus, PENDING, ACCEPTED)
                .orderByDesc(Friendship::getUpdateTime, Friendship::getId));
        if (rows.isEmpty()) return List.of();
        var people = users.selectBatchIds(rows.stream().map(row -> row.peerId(userId)).toList())
                .stream().filter(user -> Integer.valueOf(0).equals(user.getStatus()))
                .collect(Collectors.toMap(User::getId, Function.identity()));
        return rows.stream()
                .filter(row -> people.containsKey(row.peerId(userId)))
                .map(row -> new Contact(ChatUserVO.from(people.get(row.peerId(userId))), status(row, userId),
                        row.getGreeting(), row.getUpdateTime()))
                .toList();
    }

    @Override
    public String status(Long userId, Long peerId) {
        return status(baseMapper.find(userId, peerId), userId);
    }

    @Transactional
    @Override
    public String request(Long userId, Long peerId, String greeting) {
        if (userId.equals(peerId)) throw new BizException(ResultCode.BAD_REQUEST, "不能添加自己为好友");
        requireAvailable(userId, peerId);
        String text = greeting == null ? "" : greeting.strip();
        if (!text.isEmpty() && sensitiveWords.contains(text)) throw new BizException(ResultCode.SENSITIVE_WORD);

        // The unique pair and upsert also serialize requests sent simultaneously in both directions.
        baseMapper.insertIfAbsent(userId, peerId, text);
        Friendship row = baseMapper.lock(userId, peerId);
        if (!"new".equals(row.getStatus()) && !"declined".equals(row.getStatus())) return status(row, userId);
        if ("declined".equals(row.getStatus()) && row.getRequesterId().equals(userId)
                && row.getUpdateTime().isAfter(LocalDateTime.now().minusDays(1))) {
            throw new BizException(ResultCode.FORBIDDEN, "请稍后再发送好友申请");
        }
        row.setStatus(PENDING);
        row.setRequesterId(userId);
        row.setGreeting(text);
        baseMapper.updateById(row);
        Long count = redis.incr("friends:request:" + userId, 1, TimeUnit.HOURS);
        if (count == null) throw new BizException(ResultCode.SYSTEM_ERROR, "好友申请暂不可用");
        if (count > 20) throw new BizException(ResultCode.FORBIDDEN, "申请过于频繁，请稍后再试");
        notify(peerId, userId, "新的好友申请", "发来了好友申请", row.getId());
        return "outgoing";
    }

    @Transactional
    @Override
    public void respond(Long userId, Long peerId, boolean accept) {
        Friendship friendship = baseMapper.lock(userId, peerId);
        String responseStatus = accept ? ACCEPTED : DECLINED;
        if (friendship != null && !friendship.getRequesterId().equals(userId)
                && responseStatus.equals(friendship.getStatus())) {
            return;
        }
        if (friendship == null || !PENDING.equals(friendship.getStatus()) || friendship.getRequesterId().equals(userId)) {
            throw new BizException(ResultCode.BAD_REQUEST, "没有待处理的好友申请");
        }
        if (accept) {
            requireAvailable(userId, peerId);
        }
        friendship.setStatus(responseStatus);
        baseMapper.updateById(friendship);
        messages.clearBusinessNotice(userId, "friendship", friendship.getId());
        if (accept) {
            notify(peerId, userId, "好友申请已通过", "接受了你的好友申请", friendship.getId());
        }
    }

    @Transactional
    @Override
    public void remove(Long userId, Long peerId) {
        Friendship row = baseMapper.lock(userId, peerId);
        if (row == null) return;
        if (ACCEPTED.equals(row.getStatus()) || (PENDING.equals(row.getStatus()) && row.getRequesterId().equals(userId))) {
            if (PENDING.equals(row.getStatus())) messages.clearBusinessNotice(peerId, "friendship", row.getId());
            baseMapper.deleteById(row.getId());
        }
    }

    private String status(Friendship row, Long userId) {
        if (row == null) return "none";
        return switch (row.getStatus()) {
            case ACCEPTED -> "friend";
            case PENDING -> Objects.equals(row.getRequesterId(), userId) ? "outgoing" : "incoming";
            default -> "none";
        };
    }

    private void requireAvailable(Long userId, Long peerId) {
        User peer = users.selectById(peerId);
        if (peer == null || !Integer.valueOf(0).equals(peer.getStatus())) {
            throw new BizException(ResultCode.NOT_FOUND, "该同学当前不可用");
        }
        if (blocks.countEitherDirection(userId, peerId) > 0) {
            throw new BizException(ResultCode.FORBIDDEN, "当前无法与该同学建立好友关系");
        }
    }

    private void notify(Long recipient, Long actor, String title, String action, Long id) {
        User user = users.selectById(actor);
        String name = user == null || (user.getNickname() == null || user.getNickname().isBlank()) ? "有同学" : user.getNickname();
        messages.send(recipient, "interact", title, name + action, "friendship", id);
    }
}
