package com.campus.platform.module.friend;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.platform.common.BizException;
import com.campus.platform.common.ResultCode;
import com.campus.platform.module.ai.gateway.SensitiveWordService;
import com.campus.platform.module.chat.mapper.UserBlockMapper;
import com.campus.platform.module.chat.vo.ChatUserVO;
import com.campus.platform.module.friend.mapper.FriendshipMapper;

import com.campus.platform.module.message.service.MessageService;
import com.campus.platform.module.user.entity.User;
import com.campus.platform.module.user.mapper.UserMapper;
import com.campus.platform.utils.RedisUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FriendService {
    private static final String PENDING = "pending";
    private static final String ACCEPTED = "accepted";
    private final FriendshipMapper mapper;
    private final UserMapper users;
    private final UserBlockMapper blocks;
    private final MessageService messages;
    private final SensitiveWordService sensitiveWords;
    private final RedisUtils redis;

    public record Contact(ChatUserVO user, String status, String greeting, LocalDateTime updatedAt) {}

    public List<Contact> list(Long userId) {
        var rows = mapper.selectList(new LambdaQueryWrapper<Friendship>()
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

    public String status(Long userId, Long peerId) {
        return status(mapper.find(userId, peerId), userId);
    }

    @Transactional
    public String request(Long userId, Long peerId, String greeting) {
        if (userId.equals(peerId)) throw new BizException(ResultCode.BAD_REQUEST, "不能添加自己为好友");
        requireAvailable(userId, peerId);
        String text = greeting == null ? "" : greeting.strip();
        if (!text.isEmpty() && sensitiveWords.contains(text)) throw new BizException(ResultCode.SENSITIVE_WORD);

        // The unique pair and upsert also serialize requests sent simultaneously in both directions.
        mapper.insertIfAbsent(userId, peerId, text);
        Friendship row = mapper.lock(userId, peerId);
        if (!"new".equals(row.getStatus()) && !"declined".equals(row.getStatus())) return status(row, userId);
        if ("declined".equals(row.getStatus()) && row.getRequesterId().equals(userId)
                && row.getUpdateTime().isAfter(LocalDateTime.now().minusDays(1))) {
            throw new BizException(ResultCode.FORBIDDEN, "请稍后再发送好友申请");
        }
        row.setStatus(PENDING);
        row.setRequesterId(userId);
        row.setGreeting(text);
        mapper.updateById(row);
        Long count = redis.incr("friends:request:" + userId, 1, TimeUnit.HOURS);
        if (count == null) throw new BizException(ResultCode.SYSTEM_ERROR, "好友申请暂不可用");
        if (count > 20) throw new BizException(ResultCode.FORBIDDEN, "申请过于频繁，请稍后再试");
        notify(peerId, userId, "新的好友申请", "发来了好友申请", row.getId());
        return "outgoing";
    }

    @Transactional
    public void respond(Long userId, Long peerId, boolean accept) {
        Friendship row = mapper.lock(userId, peerId);
        if (row != null && !row.getRequesterId().equals(userId)
                && (accept ? ACCEPTED : "declined").equals(row.getStatus())) return;
        if (row == null || !PENDING.equals(row.getStatus()) || row.getRequesterId().equals(userId)) {
            throw new BizException(ResultCode.BAD_REQUEST, "没有待处理的好友申请");
        }
        if (accept) requireAvailable(userId, peerId);
        row.setStatus(accept ? ACCEPTED : "declined");
        mapper.updateById(row);
        messages.clearBusinessNotice(userId, "friendship", row.getId());
        if (accept) notify(peerId, userId, "好友申请已通过", "接受了你的好友申请", row.getId());
    }

    @Transactional
    public void remove(Long userId, Long peerId) {
        Friendship row = mapper.lock(userId, peerId);
        if (row == null) return;
        if (ACCEPTED.equals(row.getStatus()) || (PENDING.equals(row.getStatus()) && row.getRequesterId().equals(userId))) {
            if (PENDING.equals(row.getStatus())) messages.clearBusinessNotice(peerId, "friendship", row.getId());
            mapper.deleteById(row.getId());
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
