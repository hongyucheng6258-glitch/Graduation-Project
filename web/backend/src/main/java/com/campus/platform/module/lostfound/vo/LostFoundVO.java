package com.campus.platform.module.lostfound.vo;

import com.campus.platform.module.lostfound.entity.LostFound;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 失物招领 VO。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class LostFoundVO extends LostFound {

    private List<String> imageList;

    private String publisherNickname;

    private String publisherAvatar;

    /** 是否本人发布（详情页展示"标记完成"按钮） */
    private Boolean isOwner;
}
