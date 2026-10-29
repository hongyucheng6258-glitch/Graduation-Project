package com.campus.platform.module.favorite.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 收藏（活动/闲置/失物/动态 统一收藏）。
 */
@Data
@TableName("favorite")
public class Favorite {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 业务类型：idle / activity / lostfound / post */
    private String targetType;

    private Long targetId;

    private LocalDateTime createTime;
}
