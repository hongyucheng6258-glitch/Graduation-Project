package com.campus.platform.module.favorite.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 我的收藏 VO：聚合目标标题/封面，前端可直接渲染列表。
 */
@Data
public class FavoriteVO {

    private Long id;

    private String targetType;

    private Long targetId;

    private String title;

    /** 封面图（base64 data url 或 null） */
    private String image;

    private LocalDateTime createTime;
}
