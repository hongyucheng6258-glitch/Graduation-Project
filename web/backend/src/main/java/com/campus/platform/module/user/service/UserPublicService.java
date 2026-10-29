package com.campus.platform.module.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.platform.common.BizException;
import com.campus.platform.common.Constants;
import com.campus.platform.common.ResultCode;
import com.campus.platform.module.activity.entity.Activity;
import com.campus.platform.module.activity.mapper.ActivityMapper;
import com.campus.platform.module.idle.entity.IdleAppointment;
import com.campus.platform.module.idle.entity.IdleItem;
import com.campus.platform.module.idle.entity.IdleReview;
import com.campus.platform.module.idle.mapper.IdleAppointmentMapper;
import com.campus.platform.module.idle.mapper.IdleItemMapper;
import com.campus.platform.module.idle.mapper.IdleReviewMapper;
import com.campus.platform.module.idle.service.IdleService;
import com.campus.platform.module.lostfound.entity.LostFound;
import com.campus.platform.module.lostfound.mapper.LostFoundMapper;
import com.campus.platform.module.post.entity.Post;
import com.campus.platform.module.post.mapper.PostMapper;
import com.campus.platform.module.user.entity.User;
import com.campus.platform.module.user.mapper.UserMapper;
import com.campus.platform.module.user.vo.UserContentVO;
import com.campus.platform.module.user.vo.UserProfileVO;
import com.campus.platform.module.user.vo.UserReviewVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 他人主页公开查询服务（只读）：信息卡 + 内容聚合 + 评价聚合。
 * 脱敏：不返回手机号/完整学号，仅返回年级粒度；只展示已审核通过的内容。
 */
@Service
@RequiredArgsConstructor
public class UserPublicService {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    private final UserMapper userMapper;
    private final IdleItemMapper idleItemMapper;
    private final PostMapper postMapper;
    private final ActivityMapper activityMapper;
    private final LostFoundMapper lostFoundMapper;
    private final IdleReviewMapper reviewMapper;
    private final IdleAppointmentMapper appointmentMapper;

    /** 公开信息卡 + 统计 */
    public UserProfileVO profile(Long uid) {
        User u = userMapper.selectById(uid);
        if (u == null) {
            throw new BizException(ResultCode.NOT_FOUND, "用户不存在");
        }
        if (u.getStatus() != null && u.getStatus() == 1) {
            throw new BizException(ResultCode.FORBIDDEN, "该用户已被禁用");
        }
        UserProfileVO vo = new UserProfileVO();
        vo.setId(u.getId());
        vo.setNickname(u.getNickname());
        vo.setAvatar(u.getAvatar());
        vo.setGender(u.getGender());
        vo.setBio(u.getBio());
        vo.setCreateTime(u.getCreateTime());
        String sno = u.getStudentNo();
        vo.setGrade(sno != null && sno.length() >= 4 ? sno.substring(0, 4) + "级" : "");
        vo.setIdleCount(idleItemMapper.selectCount(new LambdaQueryWrapper<IdleItem>()
                .eq(IdleItem::getUserId, uid).eq(IdleItem::getAuditStatus, Constants.AUDIT_PASS)));
        vo.setPostCount(postMapper.selectCount(new LambdaQueryWrapper<Post>()
                .eq(Post::getUserId, uid).eq(Post::getAuditStatus, Constants.AUDIT_PASS)));
        List<IdleReview> reviews = reviewMapper.selectList(new LambdaQueryWrapper<IdleReview>()
                .eq(IdleReview::getToUserId, uid));
        vo.setReviewCount((long) reviews.size());
        vo.setAvgScore(reviews.isEmpty() ? null
                : Math.round(reviews.stream().mapToInt(IdleReview::getScore).average().orElse(0) * 10.0) / 10.0);
        return vo;
    }

    /** TA 的闲置（已审核，含在架/预约/已完成/下架状态展示） */
    public List<UserContentVO> idles(Long uid) {
        return idleItemMapper.selectList(new LambdaQueryWrapper<IdleItem>()
                        .eq(IdleItem::getUserId, uid)
                        .eq(IdleItem::getAuditStatus, Constants.AUDIT_PASS)
                        .orderByDesc(IdleItem::getId))
                .stream().map(i -> {
                    UserContentVO vo = new UserContentVO();
                    vo.setId(i.getId());
                    vo.setType("idle");
                    vo.setTitle(i.getTitle());
                    vo.setImage(firstImage(i.getImages()));
                    vo.setStatus(i.getStatus());
                    vo.setStatusText(idleStatusText(i.getStatus()));
                    vo.setExtra(i.getExpectItem() == null || i.getExpectItem().isBlank() ? "" : "期望换物：" + i.getExpectItem());
                    vo.setCreateTime(i.getCreateTime());
                    return vo;
                }).toList();
    }

    /** TA 的动态（已审核） */
    public List<UserContentVO> posts(Long uid) {
        return postMapper.selectList(new LambdaQueryWrapper<Post>()
                        .eq(Post::getUserId, uid)
                        .eq(Post::getAuditStatus, Constants.AUDIT_PASS)
                        .orderByDesc(Post::getId))
                .stream().map(p -> {
                    UserContentVO vo = new UserContentVO();
                    vo.setId(p.getId());
                    vo.setType("post");
                    vo.setTitle(p.getContent() == null ? "" : (p.getContent().length() > 40 ? p.getContent().substring(0, 40) + "…" : p.getContent()));
                    vo.setImage(firstImage(p.getImages()));
                    vo.setStatus(0);
                    vo.setStatusText("");
                    vo.setExtra("❤ " + p.getLikeCount() + " · 💬 " + p.getCommentCount());
                    vo.setCreateTime(p.getCreateTime());
                    return vo;
                }).toList();
    }

    /** TA 发布的活动（已审核） */
    public List<UserContentVO> activities(Long uid) {
        return activityMapper.selectList(new LambdaQueryWrapper<Activity>()
                        .eq(Activity::getUserId, uid)
                        .eq(Activity::getAuditStatus, Constants.AUDIT_PASS)
                        .orderByDesc(Activity::getId))
                .stream().map(a -> {
                    UserContentVO vo = new UserContentVO();
                    vo.setId(a.getId());
                    vo.setType("activity");
                    vo.setTitle(a.getTitle());
                    vo.setImage(firstImage(a.getImages()));
                    vo.setStatus(a.getStatus());
                    vo.setStatusText(activityStatusText(a.getStatus()));
                    vo.setExtra(a.getStartTime() == null ? "" : TIME_FMT.format(a.getStartTime()) + " · " + (a.getLocation() == null ? "" : a.getLocation()));
                    vo.setCreateTime(a.getCreateTime());
                    return vo;
                }).toList();
    }

    /** TA 的失物招领（已审核） */
    public List<UserContentVO> lostfounds(Long uid) {
        return lostFoundMapper.selectList(new LambdaQueryWrapper<LostFound>()
                        .eq(LostFound::getUserId, uid)
                        .eq(LostFound::getAuditStatus, Constants.AUDIT_PASS)
                        .orderByDesc(LostFound::getId))
                .stream().map(l -> {
                    UserContentVO vo = new UserContentVO();
                    vo.setId(l.getId());
                    vo.setType("lostfound");
                    vo.setTitle((l.getType() != null && l.getType() == 1 ? "招领 · " : "寻物 · ") + l.getTitle());
                    vo.setImage(firstImage(l.getImages()));
                    vo.setStatus(l.getStatus());
                    vo.setStatusText(lostStatusText(l.getStatus()));
                    vo.setExtra(l.getLocation() == null ? "" : l.getLocation());
                    vo.setCreateTime(l.getCreateTime());
                    return vo;
                }).toList();
    }

    /** TA 收到的评价 */
    public List<UserReviewVO> reviews(Long uid) {
        List<IdleReview> reviews = reviewMapper.selectList(new LambdaQueryWrapper<IdleReview>()
                .eq(IdleReview::getToUserId, uid)
                .orderByDesc(IdleReview::getId));
        if (reviews.isEmpty()) {
            return List.of();
        }
        Map<Long, User> userMap = userMapper.selectBatchIds(reviews.stream().map(IdleReview::getFromUserId).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        List<Long> appointIds = reviews.stream().map(IdleReview::getAppointmentId).distinct().toList();
        Map<Long, IdleAppointment> appointMap = appointIds.isEmpty() ? Map.of() :
                appointmentMapper.selectBatchIds(appointIds).stream()
                        .collect(Collectors.toMap(IdleAppointment::getId, Function.identity()));
        List<Long> itemIds = appointMap.values().stream().map(IdleAppointment::getItemId).distinct().toList();
        Map<Long, IdleItem> itemMap = itemIds.isEmpty() ? Map.of() :
                idleItemMapper.selectBatchIds(itemIds).stream()
                        .collect(Collectors.toMap(IdleItem::getId, Function.identity()));

        List<UserReviewVO> list = new ArrayList<>();
        for (IdleReview r : reviews) {
            UserReviewVO vo = new UserReviewVO();
            vo.setId(r.getId());
            vo.setScore(r.getScore());
            vo.setContent(r.getContent());
            vo.setCreateTime(r.getCreateTime());
            User from = userMap.get(r.getFromUserId());
            vo.setFromUserId(r.getFromUserId());
            vo.setFromNickname(from == null ? "已注销用户" : from.getNickname());
            vo.setFromAvatar(from == null ? null : from.getAvatar());
            IdleAppointment ap = appointMap.get(r.getAppointmentId());
            IdleItem item = ap == null ? null : itemMap.get(ap.getItemId());
            vo.setItemId(item == null ? null : item.getId());
            vo.setItemTitle(item == null ? "" : item.getTitle());
            list.add(vo);
        }
        return list;
    }

    private String firstImage(String imagesJson) {
        List<String> list = IdleService.parseJson(imagesJson);
        return list.isEmpty() ? null : list.get(0);
    }

    private String idleStatusText(Integer status) {
        if (status == null) return "";
        return switch (status) {
            case 0 -> "在架";
            case 1 -> "已预约";
            case 2 -> "已完成";
            case 3 -> "已下架";
            default -> "";
        };
    }

    private String activityStatusText(Integer status) {
        if (status == null) return "";
        return switch (status) {
            case 0 -> "报名中";
            case 1 -> "已满员";
            case 2 -> "已结束";
            case 3 -> "已下架";
            default -> "";
        };
    }

    private String lostStatusText(Integer status) {
        if (status == null) return "";
        return switch (status) {
            case 0 -> "进行中";
            case 1 -> "已完成";
            case 2 -> "已下架";
            default -> "";
        };
    }
}
