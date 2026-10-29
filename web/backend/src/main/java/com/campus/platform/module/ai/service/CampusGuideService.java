package com.campus.platform.module.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.platform.common.Constants;
import com.campus.platform.module.activity.entity.Activity;
import com.campus.platform.module.activity.entity.ActivityMember;
import com.campus.platform.module.activity.mapper.ActivityMapper;
import com.campus.platform.module.activity.mapper.ActivityMemberMapper;
import com.campus.platform.module.ai.gateway.AiGatewayService;
import com.campus.platform.module.idle.entity.IdleItem;
import com.campus.platform.module.idle.mapper.IdleItemMapper;
import com.campus.platform.module.lostfound.entity.LostFound;
import com.campus.platform.module.lostfound.mapper.LostFoundMapper;
import com.campus.platform.module.notice.entity.Notice;
import com.campus.platform.module.notice.mapper.NoticeMapper;
import com.campus.platform.module.post.entity.Post;
import com.campus.platform.module.post.mapper.PostMapper;
import com.campus.platform.module.user.entity.User;
import com.campus.platform.module.user.mapper.UserMapper;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * AI 校园向导：把"问问 AI"升级为懂校园的智能体。
 * 根据问题意图从活动/闲置/失物招领/动态/公告等业务模块检索真实数据，
 * 作为上下文注入大模型，让 AI 基于实时数据回答问题（如"周末有哪些活动""我报名的活动"）。
 */
@Service
public class CampusGuideService {

    private static final Logger log = LoggerFactory.getLogger(CampusGuideService.class);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("M月d日 HH:mm");

    // 校园静态信息（示例占位，运营可在此维护或后续迁移到后台配置）
    private static final String STATIC_INFO = "【校园常用信息】\n"
            + "- 图书馆开放时间：周一至周日 8:00-22:30（节假日另行通知）\n"
            + "- 食堂供餐时间：早餐 6:30-9:00 / 午餐 11:00-13:30 / 晚餐 17:00-19:30\n"
            + "- 校医院门诊：8:00-17:30（急诊 24 小时）\n";

    @Resource private ActivityMapper activityMapper;
    @Resource private ActivityMemberMapper memberMapper;
    @Resource private IdleItemMapper idleItemMapper;
    @Resource private LostFoundMapper lostFoundMapper;
    @Resource private PostMapper postMapper;
    @Resource private NoticeMapper noticeMapper;
    @Resource private UserMapper userMapper;
    @Resource private AiGatewayService aiGatewayService;

    /** 向导入口：识别意图 -> 检索业务数据 -> 注入上下文 -> AI 回答 */
    public String ask(Long userId, String question) {
        String intent = detectIntent(question);
        StringBuilder ctx = new StringBuilder();
        switch (intent) {
            case "my" -> appendMy(ctx, userId);
            case "activity" -> appendActivities(ctx);
            case "idle" -> appendIdle(ctx);
            case "lost" -> appendLost(ctx);
            case "post" -> appendPosts(ctx);
            case "notice" -> {
                appendNotices(ctx);
                ctx.append(STATIC_INFO);
            }
            default -> {
                appendActivities(ctx);
                appendIdle(ctx);
                appendLost(ctx);
                appendPosts(ctx);
                appendNotices(ctx);
                ctx.append(STATIC_INFO);
            }
        }
        if (ctx.length() == 0) {
            ctx.append("（当前未检索到相关数据）");
        }
        log.info("AI校园向导 uid={} intent={} ctx=[{}]", userId, intent, ctx);

        Map<String, String> params = new HashMap<>();
        params.put("campus_data", ctx.toString());
        return aiGatewayService.internalChat(userId, Constants.SCENE_CAMPUS_GUIDE, question, params);
    }

    /** 简单规则意图识别（关键词命中即归类；"我的"优先） */
    private String detectIntent(String q) {
        if (q == null) return "all";
        if (containsAny(q, "我的", "我报名", "我参加", "我的活动", "我的发布", "我的签到", "我发布")) return "my";
        if (containsAny(q, "活动", "比赛", "讲座", "演出", "分享会", "读书会", "社团", "招新", "周末", "这周", "本周", "下周", "最近有什么")) return "activity";
        if (containsAny(q, "闲置", "二手", "交易", "换物", "捡漏", "出闲置", "卖东西", "淘")) return "idle";
        if (containsAny(q, "失物", "招领", "丢失", "丢了", "捡到", "钱包", "校园卡", "身份证", "钥匙", "雨伞", "书包")) return "lost";
        if (containsAny(q, "动态", "分享", "话题", "趣事", "新鲜事")) return "post";
        if (containsAny(q, "公告", "通知", "放假", "食堂", "图书馆", "几点", "开放时间", "校历", "校医院")) return "notice";
        return "all";
    }

    private boolean containsAny(String q, String... keys) {
        for (String k : keys) {
            if (q.contains(k)) return true;
        }
        return false;
    }

    // ==================== 数据检索 ====================

    /** 近期活动（审核通过 + 报名中/已满，按开始时间升序） */
    private void appendActivities(StringBuilder ctx) {
        List<Activity> list = activityMapper.selectList(new LambdaQueryWrapper<Activity>()
                .eq(Activity::getAuditStatus, Constants.AUDIT_PASS)
                .in(Activity::getStatus, Constants.ACTIVITY_SIGNING, Constants.ACTIVITY_FULL)
                .orderByAsc(Activity::getStartTime)
                .last("LIMIT 8"));
        if (list.isEmpty()) return;
        ctx.append("【近期活动】\n");
        for (Activity a : list) {
            ctx.append("- ").append(nullTo(a.getTitle(), "未命名活动"))
                    .append(" | ").append(a.getStartTime() == null ? "时间待定" : a.getStartTime().format(FMT))
                    .append(" | ").append(nullTo(a.getLocation(), "地点待定"))
                    .append(" | ").append(Constants.ACTIVITY_SIGNING == a.getStatus() ? "报名中" : "名额已满")
                    .append("\n");
        }
    }

    /** 我的活动报名（最近 5 条，含活动名称/时间/地点/状态） */
    private void appendMy(StringBuilder ctx, Long userId) {
        List<ActivityMember> members = memberMapper.selectList(new LambdaQueryWrapper<ActivityMember>()
                .eq(ActivityMember::getUserId, userId)
                .orderByDesc(ActivityMember::getCreateTime)
                .last("LIMIT 5"));
        if (members.isEmpty()) {
            ctx.append("【我的活动报名】\n- 暂时没有报名记录，去活动广场看看感兴趣的活动吧\n");
            return;
        }
        Set<Long> ids = members.stream().map(ActivityMember::getActivityId).collect(Collectors.toSet());
        Map<Long, Activity> actMap = ids.isEmpty() ? Collections.emptyMap()
                : activityMapper.selectBatchIds(ids).stream()
                        .collect(Collectors.toMap(Activity::getId, Function.identity(), (x, y) -> x));
        ctx.append("【我的活动报名】\n");
        for (ActivityMember m : members) {
            Activity a = actMap.get(m.getActivityId());
            if (a == null) continue;
            ctx.append("- ").append(nullTo(a.getTitle(), "未命名活动"))
                    .append(" | ").append(a.getStartTime() == null ? "时间待定" : a.getStartTime().format(FMT))
                    .append(" | ").append(nullTo(a.getLocation(), "地点待定"))
                    .append(" | ").append(Constants.ACTIVITY_SIGNING == a.getStatus() ? "报名中" : "名额已满")
                    .append("\n");
        }
    }

    /** 闲置在售（审核通过 + 在架） */
    private void appendIdle(StringBuilder ctx) {
        List<IdleItem> list = idleItemMapper.selectList(new LambdaQueryWrapper<IdleItem>()
                .eq(IdleItem::getAuditStatus, Constants.AUDIT_PASS)
                .eq(IdleItem::getStatus, Constants.IDLE_ON_SHELF)
                .orderByDesc(IdleItem::getCreateTime)
                .last("LIMIT 8"));
        if (list.isEmpty()) return;
        ctx.append("【闲置在售】\n");
        for (IdleItem it : list) {
            String expect = nullTo(it.getExpectItem(), "");
            ctx.append("- ").append(nullTo(it.getTitle(), "未命名闲置"))
                    .append(expect.isEmpty() ? "" : " | 想换：" + expect)
                    .append(" | ").append(nullTo(it.getCategory(), "其他"))
                    .append("\n");
        }
    }

    /** 失物招领（审核通过 + 处理中，丢失/拾到分开标注） */
    private void appendLost(StringBuilder ctx) {
        List<LostFound> list = lostFoundMapper.selectList(new LambdaQueryWrapper<LostFound>()
                .eq(LostFound::getAuditStatus, Constants.AUDIT_PASS)
                .eq(LostFound::getStatus, Constants.LF_DOING)
                .orderByDesc(LostFound::getCreateTime)
                .last("LIMIT 8"));
        if (list.isEmpty()) return;
        ctx.append("【失物招领】\n");
        for (LostFound lf : list) {
            String kind = lf.getType() != null && lf.getType() == 1 ? "拾到" : "丢失";
            ctx.append("- [").append(kind).append("] ").append(nullTo(lf.getTitle(), "未命名"))
                    .append(" | ").append(nullTo(lf.getLocation(), "地点待定"))
                    .append(" | ").append(lf.getHappenTime() == null ? "" : lf.getHappenTime().format(FMT))
                    .append("\n");
        }
    }

    /** 校园动态（审核通过，取点赞/评论较高的热门内容） */
    private void appendPosts(StringBuilder ctx) {
        List<Post> list = postMapper.selectList(new LambdaQueryWrapper<Post>()
                .eq(Post::getAuditStatus, Constants.AUDIT_PASS)
                .orderByDesc(Post::getLikeCount)
                .last("LIMIT 5"));
        if (list.isEmpty()) return;
        Set<Long> uids = list.stream().map(Post::getUserId).collect(Collectors.toSet());
        Map<Long, String> nickMap = uids.isEmpty() ? Collections.emptyMap()
                : userMapper.selectBatchIds(uids).stream()
                        .collect(Collectors.toMap(User::getId, User::getNickname, (x, y) -> x));
        ctx.append("【校园动态】\n");
        for (Post p : list) {
            String nick = nickMap.getOrDefault(p.getUserId(), "同学");
            String content = nullTo(p.getContent(), "");
            ctx.append("- ").append(nick).append("：")
                    .append(content.length() > 60 ? content.substring(0, 60) + "…" : content)
                    .append("（👍").append(p.getLikeCount() == null ? 0 : p.getLikeCount())
                    .append(" 💬").append(p.getCommentCount() == null ? 0 : p.getCommentCount()).append("）")
                    .append("\n");
        }
    }

    /** 公告（已发布） */
    private void appendNotices(StringBuilder ctx) {
        List<Notice> list = noticeMapper.selectList(new LambdaQueryWrapper<Notice>()
                .eq(Notice::getStatus, Constants.NOTICE_PUBLISHED)
                .orderByDesc(Notice::getCreateTime)
                .last("LIMIT 3"));
        if (list.isEmpty()) return;
        ctx.append("【校园公告】\n");
        for (Notice n : list) {
            String content = nullTo(n.getContent(), "");
            ctx.append("- ").append(nullTo(n.getTitle(), "公告"))
                    .append(content.isEmpty() ? "" : "：" + (content.length() > 40 ? content.substring(0, 40) + "…" : content))
                    .append("\n");
        }
    }

    private String nullTo(String v, String dft) {
        return v == null || v.isBlank() ? dft : v;
    }
}
