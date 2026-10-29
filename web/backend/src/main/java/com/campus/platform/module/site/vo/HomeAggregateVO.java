package com.campus.platform.module.site.vo;

import com.campus.platform.module.activity.entity.Activity;
import com.campus.platform.module.idle.entity.IdleItem;
import com.campus.platform.module.lostfound.entity.LostFound;
import com.campus.platform.module.notice.entity.Notice;
import lombok.Data;

import java.util.List;

/**
 * 首页聚合响应（公告轮播 + 各模块最新3条）。
 */
@Data
public class HomeAggregateVO {

    /** 最新公告（轮播） */
    private List<Notice> notices;

    private List<IdleItem> idleItems;

    private List<Activity> activities;

    private List<LostFound> lostFounds;
}
