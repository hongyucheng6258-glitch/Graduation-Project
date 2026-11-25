package com.campus.platform.module.notice.service.impl;

import com.campus.platform.common.mapper.MapperQueries;
import com.campus.platform.module.notice.mapper.NoticeMapper;
import com.campus.platform.module.notice.service.INoticeService;
import com.campus.platform.module.notice.entity.Notice;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.platform.common.BizException;
import com.campus.platform.utils.ImageListJson;
import com.campus.platform.utils.ListedImages;
import com.campus.platform.common.Constants;
import com.campus.platform.common.PageResult;
import com.campus.platform.common.ResultCode;
import com.campus.platform.module.activity.entity.Activity;
import com.campus.platform.module.idle.entity.IdleItem;
import com.campus.platform.module.lostfound.entity.LostFound;
import com.campus.platform.module.activity.mapper.ActivityMapper;
import com.campus.platform.module.idle.mapper.IdleItemMapper;
import com.campus.platform.module.lostfound.mapper.LostFoundMapper;
import com.campus.platform.module.site.vo.HomeAggregateVO;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

/**
 * 公告与首页聚合服务（C5）。
 */
@Service
public class NoticeServiceImpl extends ServiceImpl<NoticeMapper, Notice> implements INoticeService {

    @Resource
    private IdleItemMapper idleItemMapper;

    @Resource
    private ActivityMapper activityMapper;

    @Resource
    private LostFoundMapper lostFoundMapper;

    /** 公告列表（公开，仅已发布） */
    @Override
    public PageResult<Notice> list(int pageNum, int pageSize) {
        Page<Notice> page = baseMapper.selectPage(new Page<>(pageNum, pageSize),
                publishedNotices());
        return PageResult.of(page);
    }

    /** 公告详情（公开，仅已发布） */
    @Override
    public Notice detail(Long id) {
        Notice notice = baseMapper.selectById(id);
        if (notice == null || notice.getStatus() != Constants.NOTICE_PUBLISHED) {
            throw new BizException(ResultCode.NOT_FOUND, "公告不存在或未发布");
        }
        return notice;
    }

    /** 首页聚合（公告轮播 + 各模块最新3条） */
    @Override
    public HomeAggregateVO homeAggregate() {
        HomeAggregateVO home = new HomeAggregateVO();
        home.setNotices(baseMapper.selectList(MapperQueries.limit(publishedNotices(), 5)));
        home.setIdleItems(idleItemMapper.selectList(MapperQueries.limit(new LambdaQueryWrapper<IdleItem>()
                .eq(IdleItem::getAuditStatus, Constants.AUDIT_PASS)
                .eq(IdleItem::getStatus, Constants.IDLE_ON_SHELF)
                .orderByDesc(IdleItem::getId), 3)));
        home.setActivities(activityMapper.selectList(MapperQueries.limit(new LambdaQueryWrapper<Activity>()
                .eq(Activity::getAuditStatus, Constants.AUDIT_PASS)
                .eq(Activity::getStatus, Constants.ACTIVITY_SIGNING)
                // 已结束的活动不再计入首页推荐
                .and(w -> w.isNull(Activity::getEndTime).or().gt(Activity::getEndTime, java.time.LocalDateTime.now()))
                .orderByDesc(Activity::getId), 3)));
        home.setLostFounds(lostFoundMapper.selectList(MapperQueries.limit(new LambdaQueryWrapper<LostFound>()
                .eq(LostFound::getAuditStatus, Constants.AUDIT_PASS)
                .eq(LostFound::getStatus, Constants.LF_DOING)
                .orderByDesc(LostFound::getId), 3)));
        home.getNotices().forEach(row -> row.setCover(ListedImages.url(row.getCover(),
                "/api/media/notice/" + row.getId() + "/0")));
        home.getActivities().forEach(row -> row.setImages(compact("activity", row.getId(), row.getImages())));
        home.getIdleItems().forEach(row -> row.setImages(compact("idle", row.getId(), row.getImages())));
        home.getLostFounds().forEach(row -> row.setImages(compact("lostfound", row.getId(), row.getImages())));
        return home;
    }

    private LambdaQueryWrapper<Notice> publishedNotices() {
        return new LambdaQueryWrapper<Notice>()
                .eq(Notice::getStatus, Constants.NOTICE_PUBLISHED)
                .orderByDesc(Notice::getPublishTime);
    }

    private String compact(String resource, long id, String images) {
        return ImageListJson.toJson(ListedImages.urls(resource, id, ImageListJson.parseJson(images)));
    }
}
