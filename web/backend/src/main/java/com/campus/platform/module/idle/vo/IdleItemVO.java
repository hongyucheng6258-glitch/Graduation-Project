package com.campus.platform.module.idle.vo;

import com.campus.platform.module.idle.entity.IdleItem;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 闲置物品 VO：图片转数组 + 发布者信息。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class IdleItemVO extends IdleItem {

    /** 图片URL数组（由 images JSON 字符串解析） */
    private List<String> imageList;

    private String publisherNickname;

    private String publisherAvatar;
}
